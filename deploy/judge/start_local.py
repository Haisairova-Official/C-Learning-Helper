#!/usr/bin/env python3
"""Start the checkout's pinned, unprivileged go-judge and verify its isolation settings."""
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = Path('/tmp') / ('cassist-judge-' + str(os.getuid()))
BIN = ROOT / '.local/bin/go-judge'
ENV_FILE = RUNTIME / 'judge.env'
PID_FILE = RUNTIME / 'judge.pid'
URL = 'http://127.0.0.1:15050'


def owned_pid():
    try:
        pid = int(PID_FILE.read_text())
        proc = Path('/proc') / str(pid)
        binary = proc.joinpath('cmdline').read_bytes().split(b'\0')[0]
        if proc.stat().st_uid == os.getuid() and binary in {os.fsencode(BIN), b'/tmp/cassist-judge-bin/go-judge'}:
            return pid
    except (OSError, ValueError, IndexError):
        pass
    return None


def ready(token, base=URL):
    req = urllib.request.Request(base + '/config', headers={'Authorization': 'Bearer ' + token})
    with urllib.request.urlopen(req, timeout=2) as response:
        runner = json.load(response).get('runnerConfig', {})
    if not {'cpu', 'memory', 'pids'}.issubset(runner.get('cgroupControllers', [])) or runner.get('seccomp') is not True:
        raise RuntimeError('评测缺少 cpu/memory/pids 控制器或 seccomp，详见部署说明的隔离环境一节。')


def main():
    if os.getuid() == 0:
        raise RuntimeError('请从普通用户会话启动评测服务。')
    system_env = ROOT / '.local/system-judge.env'
    if system_env.is_file():
        settings = dict(line.split('=', 1) for line in system_env.read_text().splitlines())
        ready(settings['JUDGE_TOKEN'], settings['JUDGE_BASE_URL'])
        print('systemd 隔离评测服务已就绪')
        return
    os.umask(0o077)
    RUNTIME.mkdir(mode=0o700, exist_ok=True)
    if RUNTIME.is_symlink() or RUNTIME.stat().st_uid != os.getuid() or RUNTIME.stat().st_mode & 0o077:
        raise RuntimeError('评测运行目录所有者或权限异常')
    token = secrets.token_hex(32)
    if ENV_FILE.exists():
        values = dict(line.split('=', 1) for line in ENV_FILE.read_text().splitlines())
        token = values.get('JUDGE_TOKEN') or token
    if owned_pid():
        ready(token)
        print('复用已就绪的隔离评测服务')
        return
    with socket.socket() as sock:
        if sock.connect_ex(('127.0.0.1', 15050)) == 0:
            raise RuntimeError('15050 已由其他进程占用')
    if not BIN.exists():
        BIN.parent.mkdir(mode=0o700, exist_ok=True, parents=True)
        subprocess.run(['go', 'install', 'github.com/criyle/go-judge/cmd/go-judge@v1.14.0'],
                       env={**os.environ, 'GOBIN': str(BIN.parent)}, check=True)
    config = RUNTIME / 'judge.env.new'
    config.write_text(f'JUDGE_ENABLED=true\nJUDGE_BASE_URL={URL}\nJUDGE_TOKEN={token}\n')
    config.replace(ENV_FILE)
    with (RUNTIME / 'judge.log').open('ab') as log:
        p = subprocess.Popen([str(BIN), '-http-addr=127.0.0.1:15050', '-parallelism=2', '-pre-fork=2',
            '-no-fallback', '-silent', '-file-timeout=5m', '-output-limit=16777216', '-copy-out-limit=16777216'],
            cwd=RUNTIME, env={**os.environ, 'GIN_MODE': 'release', 'ES_AUTH_TOKEN': token},
            stdin=subprocess.DEVNULL, stdout=log, stderr=log, start_new_session=True)
    try:
        for _ in range(80):
            if p.poll() is not None:
                raise RuntimeError(f'隔离评测未能启动。请检查 cgroup 委派、用户命名空间和 {RUNTIME}/judge.log')
            try:
                ready(token)
            except urllib.error.URLError:
                time.sleep(.1)
                continue
            PID_FILE.write_text(str(p.pid))
            print('隔离评测已就绪')
            return
        raise RuntimeError('隔离评测未在预期时间就绪')
    except BaseException:
        if p.poll() is None:
            p.terminate()
            p.wait(timeout=10)
        raise


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.SubprocessError) as exc:
        sys.exit(str(exc))
