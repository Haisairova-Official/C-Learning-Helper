#!/usr/bin/env python3
"""Project-private MySQL demonstration DB. Production uses the configured MariaDB."""
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess

base = Path(__file__).resolve().parents[1]
local = base / '.local'
local.mkdir(mode=0o700, exist_ok=True)
data = local / 'mysql-data'
bootstrap = local / 'mysql-bootstrap.sql'
config = local / 'demo.env'
binary = shutil.which('mysqld')
if not binary:
    raise SystemExit('A local MySQL mysqld binary is required for this optional demo script. Configure MariaDB separately for production.')
with socket.socket() as sock:
    if sock.connect_ex(('127.0.0.1', 33077)) == 0:
        raise SystemExit('Port 33077 is already in use. Existing processes are not modified.')
if not data.exists():
    data.mkdir(mode=0o700)
    result = subprocess.run([binary, '--no-defaults', '--initialize-insecure', f'--datadir={data}', f'--log-error={local}/mysql-init.log'])
    if result.returncode:
        raise SystemExit('Database initialization failed; inspect the private initialization log.')
    app_password = secrets.token_urlsafe(28)
    root_password = secrets.token_urlsafe(32)
    bootstrap.write_text(
        f"ALTER USER 'root'@'localhost' IDENTIFIED BY '{root_password}';\n"
        "CREATE DATABASE IF NOT EXISTS c_assist CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n"
        f"CREATE USER IF NOT EXISTS 'c_assist'@'127.0.0.1' IDENTIFIED BY '{app_password}';\n"
        "GRANT ALL ON c_assist.* TO 'c_assist'@'127.0.0.1';\n")
    config.write_text('DB_URL=jdbc:mariadb://127.0.0.1:33077/c_assist\nDB_USERNAME=c_assist\n'
                      f'DB_PASSWORD={app_password}\nSPRING_PROFILES_ACTIVE=demo\nSESSION_COOKIE_SECURE=false\n')
    bootstrap.chmod(0o600)
    config.chmod(0o600)
if not bootstrap.exists() or not config.exists():
    raise SystemExit('Existing private database has missing configuration; refusing to reset it.')
print('Starting project-private demo DB on 127.0.0.1:33077; production target remains MariaDB.', flush=True)
os.execv(binary, [binary, '--no-defaults', f'--datadir={data}', '--socket=/tmp/c-assist-demo-mysql.sock',
                 f'--pid-file={local}/mysql.pid', f'--log-error={local}/mysql.log', '--bind-address=127.0.0.1',
                 '--port=33077', '--mysqlx=0', '--skip-name-resolve', f'--init-file={bootstrap}'])
