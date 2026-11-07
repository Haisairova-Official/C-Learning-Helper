#!/usr/bin/env python3
"""Root run entry for the native stack and its public HTTP listener."""
import argparse
import fcntl
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import sys
import time
import urllib.request

import local_app as app

ROOT, STATE = app.ROOT, app.STATE
WEB = STATE / 'bin/c-assist-web'
BACKEND_PORT = STATE / 'backend-port'
SOCKET_UNIT = 'c-assist-lan-http.socket'


def port_number(value):
    if not value.isascii() or not value.isdecimal() or not 1 <= int(value) <= 65535:
        raise argparse.ArgumentTypeError('端口必须是 1 至 65535 的整数')
    return int(value)


def gateway_pid(record):
    try:
        pid = int(record.read_text())
        proc = Path('/proc') / str(pid)
        if proc.stat().st_uid == os.getuid() and proc.joinpath('cmdline').read_bytes().split(b'\0')[0] == os.fsencode(WEB):
            return pid
    except (OSError, ValueError):
        pass
    return None


def legacy_socket():
    # Only reuse the exact published socket and proxy pair (including absence of overrides).
    for unit in (SOCKET_UNIT, 'c-assist-lan-http.service'):
        try:
            path = subprocess.check_output(['systemctl', 'show', '-p', 'FragmentPath', '--value', unit], text=True).strip()
            dropins = subprocess.check_output(['systemctl', 'show', '-p', 'DropInPaths', '--value', unit], text=True)
            if dropins.strip() or Path(path).read_bytes() != (ROOT / 'deploy/local-http' / unit).read_bytes():
                return False
        except (OSError, subprocess.SubprocessError):
            return False
    return subprocess.run(['systemctl', 'is-active', '--quiet', SOCKET_UNIT]).returncode == 0


def verify_http(port):
    base = 'http://127.0.0.1:' + str(port)
    with urllib.request.urlopen(base + '/', timeout=3) as r:
        if r.status != 200 or '<div id="app">' not in r.read(65536).decode():
            raise RuntimeError('网页入口响应不符合预期')
    with urllib.request.urlopen(base + '/api/auth/csrf', timeout=3) as r:
        if not json.load(r).get('token'):
            raise RuntimeError('后端 API 未就绪')


def start_gateway(port, internal, host):
    record = STATE / f'web-{port}.pid'
    if gateway_pid(record) or (port == 5173 and app.owned_pid('frontend')):
        verify_http(port)
        return
    with (STATE / f'web-{port}.log').open('ab') as log:
        process = subprocess.Popen([str(WEB), '--host', host, '--port', str(port), '--backend-port', str(internal),
                                    '--dist', str(ROOT / 'frontend/dist')], stdout=log, stderr=log,
                                   stdin=subprocess.DEVNULL, start_new_session=True)
    try:
        for _ in range(80):
            if process.poll() is not None:
                raise RuntimeError(f'网页启动失败，请检查 .local/web-{port}.log（端口占用或低端口权限）')
            try:
                verify_http(port)
            except (OSError, ValueError):
                time.sleep(.1)
                continue
            record.write_text(str(process.pid))
            return
        raise RuntimeError('网页健康检查超时')
    except BaseException:
        if process.poll() is None:
            process.terminate()
            process.wait(timeout=8)
        raise


def core_start(public):
    stored = int(BACKEND_PORT.read_text()) if BACKEND_PORT.exists() else 8080
    if app.owned_pid('backend'):
        internal = stored
    else:
        internal = 18081 if public == 8080 else 8080
        if internal == public:
            internal = 18082
    app.PORTS['backend'] = internal
    os.environ['SERVER_PORT'] = str(internal)
    os.environ['CASSIST_FRESH_INSTALL'] = '1'
    if app.open_port(internal) and not app.owned_pid('backend'):
        raise RuntimeError(f'内部 API 端口 {internal} 已被其他程序占用')
    app.spawn('database', [sys.executable, str(ROOT / 'backend/scripts/start-demo-db.py')], ROOT)
    subprocess.run([sys.executable, str(ROOT / 'deploy/judge/start_local.py')], cwd=ROOT, check=True)
    app.spawn('backend', [sys.executable, str(ROOT / 'backend/scripts/run-demo.py')], ROOT)
    BACKEND_PORT.write_text(str(internal))
    return internal


def stop():
    for record in STATE.glob('web-*.pid'):
        pid = gateway_pid(record)
        if pid:
            os.kill(pid, signal.SIGTERM)
            for _ in range(50):
                if not gateway_pid(record):
                    break
                time.sleep(.1)
        record.unlink(missing_ok=True)
    if BACKEND_PORT.exists():
        app.PORTS['backend'] = int(BACKEND_PORT.read_text())
    for service in ('frontend', 'backend', 'database'):
        if app.owned_pid(service):
            app.stop_one(service)
    sys.path.insert(0, str(ROOT / 'deploy/judge'))
    import start_local as judge
    pid = judge.owned_pid()
    if pid:
        os.kill(pid, signal.SIGTERM)
        for _ in range(80):
            if not judge.owned_pid():
                break
            time.sleep(.1)
        else:
            raise RuntimeError('隔离评测服务仍在停止中，请稍后重试')


def main():
    parser = argparse.ArgumentParser(description='启动 C 课堂，默认监听 0.0.0.0:80')
    parser.add_argument('action', nargs='?', choices=('start', 'stop', 'status'), default='start')
    parser.add_argument('--port', type=port_number, default=80)
    args = parser.parse_args()
    os.umask(0o077)
    if args.action == 'stop':
        stop()
        return
    if args.action == 'status':
        if BACKEND_PORT.exists():
            app.PORTS['backend'] = int(BACKEND_PORT.read_text())
        for service in ('database', 'backend'):
            print(service, '运行中' if app.owned_pid(service) and app.open_port(app.PORTS[service]) else '未启动')
        judge_port = 5050 if (STATE / 'system-judge.env').exists() else 15050
        print('judge', '监听中' if app.open_port(judge_port) else '未启动')
        for record in sorted(STATE.glob('web-*.pid')):
            if gateway_pid(record):
                port = int(record.stem.split('-')[1])
                print('HTTP', port)
        if legacy_socket() and app.open_port(5173):
            print('HTTP 80 (systemd)')
        return
    required = [WEB, STATE / 'bin/go-judge', ROOT / 'frontend/dist/index.html',
                ROOT / 'backend/target/c-assist-backend-0.1.0-SNAPSHOT.jar']
    if not all(p.is_file() for p in required):
        raise RuntimeError('尚未安装完整，请先在根目录运行 ./install.sh（Windows 使用 install.bat）。')
    if os.getuid() == 0:
        raise RuntimeError('请以运行安装程序的普通账号执行 ./run。')
    port = args.port
    forwarded = port == 80 and app.open_port(80) and legacy_socket()
    listen = 5173 if forwarded else port
    if listen in (33077, 15050):
        raise RuntimeError(f'{listen} 是本项目的内部服务端口，请选择其他 --port')
    if not gateway_pid(STATE / f'web-{listen}.pid') and not (listen == 5173 and app.owned_pid('frontend')):
        with socket.socket() as probe:
            probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                probe.bind(('127.0.0.1' if forwarded else '0.0.0.0', listen))
            except PermissionError:
                caps = subprocess.run(['getcap', str(WEB)], capture_output=True, text=True)
                if 'cap_net_bind_service' not in caps.stdout:
                    raise RuntimeError(f'没有监听 {listen} 的权限，请重新运行 ./install.sh，或使用 --port 9000')
            except OSError as exc:
                raise RuntimeError(f'端口 {listen} 已被占用，请使用 ./run --port 其他端口') from exc
    internal = core_start(port)
    start_gateway(listen, internal, '127.0.0.1' if forwarded else '0.0.0.0')
    verify_http(port)
    suffix = '' if port == 80 else ':' + str(port)
    print(f'打开 http://localhost{suffix}/ ；局域网设备使用这台电脑的 IP。')
    if (ROOT / 'backend/.local/initial-accounts.env').is_file():
        print('首次登录密码见 backend/.local/initial-accounts.env（ADMIN 对应 admin）。')


if __name__ == '__main__':
    try:
        with (STATE / 'launcher.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            main()
    except (RuntimeError, OSError, ValueError, subprocess.SubprocessError) as error:
        sys.exit('启动管理失败：' + str(error))
