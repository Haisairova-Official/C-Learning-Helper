#!/usr/bin/env python3
"""Start a pinned, unprivileged go-judge for local integration. Prints no credentials."""
import json, os, pathlib, secrets, shutil, subprocess, time, urllib.request

uid = os.getuid()
if uid == 0:
    raise SystemExit("This local helper must run as an ordinary user")
runtime = pathlib.Path('/tmp') / f'cassist-judge-{uid}'
if runtime.is_symlink():
    raise SystemExit("Unsafe runtime directory")
runtime.mkdir(mode=0o700, exist_ok=True)
if runtime.stat().st_uid != uid:
    raise SystemExit("Runtime directory ownership mismatch")
runtime.chmod(0o700)
pidfile = runtime / 'judge.pid'
envfile = runtime / 'judge.env'
if pidfile.exists() and envfile.exists():
    oldpid = int(pidfile.read_text())
    cmdline = pathlib.Path(f'/proc/{oldpid}/cmdline')
    if cmdline.exists() and b'go-judge' in cmdline.read_bytes():
        print(f'Already running; environment file: {envfile}')
        raise SystemExit(0)
binary = pathlib.Path('/tmp/cassist-judge-bin/go-judge')
if not binary.is_file():
    if not shutil.which('go'):
        raise SystemExit('Go is required to build the pinned local go-judge')
    subprocess.run(['go','install','github.com/criyle/go-judge/cmd/go-judge@v1.14.0'],
                   env={**os.environ,'GOBIN':str(binary.parent)},check=True)
key = secrets.token_hex(32)
fd = os.open(envfile, os.O_CREAT | os.O_TRUNC | os.O_WRONLY | os.O_NOFOLLOW, 0o600)
with os.fdopen(fd,'w') as stream:
    stream.write(f'JUDGE_ENABLED=true\nJUDGE_BASE_URL=http://127.0.0.1:15050\nJUDGE_TOKEN={key}\n')
log = open(runtime/'judge.log','ab')
proc = subprocess.Popen([str(binary),'-http-addr=127.0.0.1:15050','-parallelism=2','-pre-fork=2',
        '-no-fallback','-silent','-file-timeout=5m','-output-limit=16777216','-copy-out-limit=16777216'],
        env={**os.environ,'GIN_MODE':'release','ES_AUTH_TOKEN':key},stdin=subprocess.DEVNULL,
        stdout=log,stderr=log,start_new_session=True)
log.close()
try:
    for _ in range(50):
        if proc.poll() is not None:
            raise RuntimeError('go-judge exited during startup')
        try:
            config=json.load(urllib.request.urlopen('http://127.0.0.1:15050/config',timeout=1))
            runner=config.get('runnerConfig',{})
            if not {'cpu','memory','pids'}.issubset(runner.get('cgroupControllers',[])) or runner.get('seccomp') is not True:
                raise RuntimeError('Required resource controllers/seccomp are unavailable')
            pidfile.write_text(str(proc.pid))
            print(f'Local go-judge ready; PID {proc.pid}; environment file: {envfile}')
            break
        except urllib.error.URLError:
            time.sleep(.1)
    else:
        raise RuntimeError('go-judge readiness timed out')
except Exception:
    proc.terminate()
    raise
