"""Probe the installed judge with two bounded compiler checks before reporting installation success."""
import argparse
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]


def check(base, token):
    headers = {'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'}
    with urllib.request.urlopen(urllib.request.Request(base + '/config', headers=headers), timeout=3) as r:
        runner = json.load(r)['runnerConfig']
    if not {'cpu', 'memory', 'pids'}.issubset(runner.get('cgroupControllers', [])) or not runner.get('seccomp'):
        raise RuntimeError('评测环境缺少 CPU、内存、进程或 seccomp 限制')
    for compiler, std, language, header in [('gcc', 'c17', 'c', 'stdio.h'), ('g++', 'c++17', 'c++', 'vector')]:
        cmd = {'args': ['/usr/bin/' + compiler, '-std=' + std, '-x', language, '-fsyntax-only', '-'],
               'env': ['PATH=/usr/bin:/bin', 'LC_ALL=C'],
               'files': [{'content': '#include <' + header + '>\nint main(){return 0;}\n'},
                         {'name': 'stdout', 'max': 4096}, {'name': 'stderr', 'max': 4096}],
               'cpuLimit': 10_000_000_000, 'clockLimit': 20_000_000_000, 'memoryLimit': 536870912, 'procLimit': 16}
        req = urllib.request.Request(base + '/run', data=json.dumps({'cmd':[cmd]}).encode(), headers=headers)
        with urllib.request.urlopen(req, timeout=25) as r:
            result = json.load(r)[0]
        if result['status'] != 'Accepted' or result.get('exitStatus', 0) != 0:
            raise RuntimeError(compiler + ' 隔离编译检查失败：' + result['status'])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--system-env', type=Path)
    args = parser.parse_args()
    if args.system_env:
        env = dict(line.split('=', 1) for line in args.system_env.read_text().splitlines())
        check(env['JUDGE_BASE_URL'], env['JUDGE_TOKEN'])
    else:
        with tempfile.TemporaryDirectory() as stage:
            with socket.socket() as sock:
                sock.bind(('127.0.0.1', 0))
                addr = '127.0.0.1:' + str(sock.getsockname()[1])
            token = secrets.token_hex(32)
            logpath = ROOT / '.local/installation-judge.log'
            with logpath.open('wb') as log:
                judge = subprocess.Popen([str(ROOT / '.local/bin/go-judge'), '-http-addr=' + addr,
                    '-cgroup-prefix=cassist-install-' + str(os.getpid()), '-parallelism=1', '-pre-fork=1',
                    '-no-fallback', '-silent'], cwd=stage, env={**os.environ, 'ES_AUTH_TOKEN':token},
                    stdin=subprocess.DEVNULL, stderr=log, stdout=log)
            try:
                for _ in range(70):
                    if judge.poll() is not None:
                        raise RuntimeError('普通用户评测启动失败，详见 .local/installation-judge.log')
                    try:
                        check('http://' + addr, token)
                        break
                    except urllib.error.URLError:
                        time.sleep(.1)
                else:
                    raise RuntimeError('隔离环境检查超时')
            finally:
                if judge.poll() is None:
                    judge.terminate()
                    judge.wait(timeout=8)
    print('隔离环境、C 和 C++ 编译器均已就绪')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as e:
        raise SystemExit(str(e))
