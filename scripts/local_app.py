#!/usr/bin/env python3
"""Manage only this checkout's local demo services; no system services are changed."""
import argparse
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
STATE=ROOT/'.local'
STATE.mkdir(mode=0o700,exist_ok=True)
STATE.chmod(0o700)
PIDFILES={'database':ROOT/'backend/.local/mysql.pid','backend':ROOT/'backend/.local/backend.pid','frontend':STATE/'frontend.pid'}
MARKERS={'database':str(ROOT/'backend/.local/mysql-data'),'backend':str(ROOT/'backend/target/c-assist-backend-0.1.0-SNAPSHOT.jar'),'frontend':str(ROOT/'frontend/node_modules/vite/bin/vite.js')}
PORTS={'database':33077,'backend':8080,'frontend':5173}

def open_port(port):
    with socket.socket() as sock:
        sock.settimeout(.25)
        return sock.connect_ex(('127.0.0.1',port))==0

def owned_pid(service):
    path=PIDFILES[service]
    if not path.exists(): return None
    try:
        pid=int(path.read_text().strip())
        cmd=Path(f'/proc/{pid}/cmdline').read_bytes()
        owned_marker=MARKERS[service].encode() in cmd
        if service=='backend':
            owned_marker=owned_marker or str(ROOT/'backend/.local/runtime/c-assist-').encode() in cmd
        if owned_marker and Path(f'/proc/{pid}').stat().st_uid==os.getuid(): return pid
    except (ValueError,OSError): pass
    return None

def stop_one(service):
    pid=owned_pid(service)
    if pid is None:
        if open_port(PORTS[service]): raise RuntimeError(f'{service} port belongs to an unrecognized process; refusing to stop it')
        return
    os.kill(pid,signal.SIGTERM)
    for _ in range(150):
        if owned_pid(service) is None or not open_port(PORTS[service]): break
        time.sleep(.1)
    if open_port(PORTS[service]): raise RuntimeError(f'{service} did not stop; refusing to force kill')
    print(f'Stopped {service}',flush=True)

def spawn(service,args,cwd):
    if open_port(PORTS[service]):
        if not owned_pid(service): raise RuntimeError(f'Port {PORTS[service]} is in use by an unrecognized process')
        print(f'Reusing {service}',flush=True)
        return
    with open(STATE/(service+'.log'),'ab') as log:
        process=subprocess.Popen(args,cwd=cwd,stdin=subprocess.DEVNULL,stdout=log,stderr=log,start_new_session=True)
    PIDFILES[service].parent.mkdir(parents=True,exist_ok=True)
    PIDFILES[service].write_text(str(process.pid))
    for _ in range(300):
        if process.poll() is not None: raise RuntimeError(f'{service} exited; see .local/{service}.log or backend/.local logs')
        try:
            if service=='database': ready=open_port(PORTS[service])
            else:
                url=f'http://127.0.0.1:{PORTS[service]}'+('/api/auth/csrf' if service=='backend' else '/')
                with urllib.request.urlopen(url,timeout=1) as response: ready=response.status==200
            if ready:
                print(f'Ready {service}: 127.0.0.1:{PORTS[service]}',flush=True)
                return
        except (OSError,urllib.error.URLError): pass
        time.sleep(.1)
    raise RuntimeError(f'{service} readiness timed out; inspect local logs')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['start','stop','restart','status'],nargs='?',default='start')
    args=parser.parse_args()
    if args.action=='status':
        for service in PORTS: print(service,'running' if owned_pid(service) and open_port(PORTS[service]) else 'stopped')
        print('judge','listening' if open_port(15050) else 'stopped')
        return
    if args.action in ('stop','restart'):
        for service in ('frontend','backend','database'): stop_one(service)
        if args.action=='stop':
            runtime=Path('/tmp')/f'cassist-judge-{os.getuid()}'
            pidfile=runtime/'judge.pid'
            if pidfile.exists():
                try:
                    pid=int(pidfile.read_text())
                    if b'/tmp/cassist-judge-bin/go-judge' in Path(f'/proc/{pid}/cmdline').read_bytes():
                        os.kill(pid,signal.SIGTERM)
                        print('Stopped local judge',flush=True)
                except (OSError,ValueError): pass
            return
    if not (ROOT/'backend/target/c-assist-backend-0.1.0-SNAPSHOT.jar').exists():
        subprocess.run(['./mvnw','-B','-ntp','package'],cwd=ROOT/'backend',check=True)
    if not (ROOT/'frontend/node_modules/vite/bin/vite.js').exists():
        subprocess.run(['npm','ci'],cwd=ROOT/'frontend',check=True)
    spawn('database',[sys.executable,str(ROOT/'backend/scripts/start-demo-db.py')],ROOT)
    subprocess.run([sys.executable,str(ROOT/'deploy/judge/start_local.py')],cwd=ROOT,check=True)
    spawn('backend',[sys.executable,str(ROOT/'backend/scripts/run-demo.py')],ROOT)
    node=shutil.which('node')
    if not node: raise RuntimeError('Node.js is required')
    spawn('frontend',[node,str(ROOT/'frontend/node_modules/vite/bin/vite.js'),'--mode','api','--port','5173','--strictPort'],ROOT/'frontend')
    (STATE/'services.json').write_text(json.dumps({name:owned_pid(name) for name in PORTS},indent=2))
    print('Open http://127.0.0.1:5173/ — local demo; real judging; AI follows private backend configuration.',flush=True)

if __name__=='__main__':
    try: main()
    except (RuntimeError,subprocess.CalledProcessError) as error:
        print('Startup error:',error,file=sys.stderr)
        raise SystemExit(1)
