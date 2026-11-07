#!/usr/bin/env python3
"""Start the checkout's private MariaDB or MySQL without modifying a system database."""
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import tempfile

def start_database(base, port=33077, basedir=None):
    local = base / '.local'
    os.umask(0o077)
    local.mkdir(mode=0o700, exist_ok=True)
    data = local / 'mysql-data'
    bootstrap = local / 'mysql-bootstrap.sql'
    config = local / 'demo.env'
    engine_file = local / 'database-engine'
    accounts = local / 'initial-accounts.env'
    base_args = ['--basedir=' + basedir] if basedir else []

    # Data directories aren't interchangeable between MariaDB and MySQL.
    expected = engine_file.read_text().strip() if engine_file.exists() else ('mysql' if data.exists() else None)
    for candidate in ('mariadbd', 'mysqld'):
        binary = shutil.which(candidate)
        if binary:
            version = subprocess.check_output([binary, '--no-defaults', '--version'], text=True)
            engine = 'mariadb' if 'mariadb' in version.lower() else 'mysql'
            if expected is None or expected == engine:
                break
    else:
        raise SystemExit('需要与已有数据匹配的数据库服务端：' + (expected or 'MariaDB') + '。请运行 ./install.sh。')

    with socket.socket() as sock:
        if sock.connect_ex(('127.0.0.1', port)) == 0:
            raise SystemExit(f'数据库端口 {port} 已占用，请检查已有服务。')

    if not data.exists():
        if any(path.exists() for path in (bootstrap, config, accounts, engine_file)):
            raise SystemExit('检测到没有数据目录的旧数据库配置。请先检查并恢复原数据，或在新目录安装。')
        with tempfile.TemporaryDirectory(dir=local, prefix='db-init-') as stage:
            stage = Path(stage)
            new_data = stage / 'data'
            new_data.mkdir()
            if engine == 'mariadb':
                init = shutil.which('mariadb-install-db') or shutil.which('mysql_install_db')
                if not init:
                    raise SystemExit('缺少 mariadb-install-db，请运行 ./install.sh。')
                command = [init, '--no-defaults', *base_args, '--auth-root-authentication-method=normal',
                           '--skip-test-db', '--datadir=' + str(new_data)]
            else:
                command = [binary, '--no-defaults', *base_args, '--initialize-insecure', '--datadir=' + str(new_data)]
            with (local / 'mysql-init.log').open('ab') as log:
                if subprocess.run(command, stdout=log, stderr=log).returncode:
                    raise SystemExit('数据库初始化失败，详见 backend/.local/mysql-init.log；可以修复依赖后重试。')
            password = secrets.token_urlsafe(28)
            (stage / bootstrap.name).write_text(
                "ALTER USER 'root'@'localhost' IDENTIFIED BY '" + secrets.token_urlsafe(32) + "';\n"
                "CREATE DATABASE IF NOT EXISTS c_assist CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n"
                f"CREATE USER IF NOT EXISTS 'c_assist'@'127.0.0.1' IDENTIFIED BY '{password}';\n"
                "GRANT ALL ON c_assist.* TO 'c_assist'@'127.0.0.1';\n")
            (stage / config.name).write_text(f'DB_URL=jdbc:mariadb://127.0.0.1:{port}/c_assist\n'
                f'DB_USERNAME=c_assist\nDB_PASSWORD={password}\nSPRING_PROFILES_ACTIVE=demo\nSESSION_COOKIE_SECURE=false\n')
            (stage / engine_file.name).write_text(engine + '\n')
            if os.environ.get('CASSIST_FRESH_INSTALL') == '1':
                (stage / accounts.name).write_text(''.join(
                    f'APP_DEMO_PASSWORD_{user}={secrets.token_urlsafe(20)}\n'
                    for user in ('ADMIN', 'TEACHER', 'STUDENT', 'STUDENT2')))
            for path in (bootstrap, config, engine_file, accounts):
                generated = stage / path.name
                if generated.exists():
                    generated.replace(path)
            new_data.rename(data)
    if not bootstrap.is_file() or not config.is_file():
        raise SystemExit('已有数据缺少私有配置，请恢复备份。')
    # A short private socket path works even if the project has a long Unicode path.
    socket_dir = Path(tempfile.gettempdir()) / ('cassist-db-' + str(os.getuid()))
    socket_dir.mkdir(mode=0o700, exist_ok=True)
    if socket_dir.is_symlink() or socket_dir.stat().st_uid != os.getuid() or socket_dir.stat().st_mode & 0o077:
        raise SystemExit('数据库 socket 目录所有者或权限异常')
    args = [binary, '--no-defaults', *base_args, f'--datadir={data}', f'--socket={socket_dir}/db-{port}.sock',
            f'--pid-file={local}/mysql.pid', f'--log-error={local}/mysql.log', '--bind-address=127.0.0.1',
            f'--port={port}', '--skip-name-resolve', f'--init-file={bootstrap}']
    if engine == 'mysql':
        args.append('--mysqlx=0')
    os.execv(binary, args)


if __name__ == '__main__':
    start_database(Path(__file__).resolve().parents[1])
