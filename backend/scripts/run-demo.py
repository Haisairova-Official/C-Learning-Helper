#!/usr/bin/env python3
"""Start the local demo with private env files; never print credential values."""
import os
from pathlib import Path
import re
import shlex
import socket
import hashlib
import shutil
import tempfile

base = Path(__file__).resolve().parents[1]
env = dict(os.environ)
ready_file = base / '.local/backend.ready'
ready_file.unlink(missing_ok=True)
env['CASSIST_READY_FILE'] = str(ready_file)

def read_env(path, allowed):
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        key, value = (line[7:] if line.startswith('export ') else line).split('=', 1)
        if key not in allowed or not re.fullmatch(r'[A-Z0-9_]+', key):
            raise SystemExit('Unexpected environment key in ' + str(path))
        values = shlex.split(value)
        if len(values) != 1:
            raise SystemExit('Invalid environment value in ' + str(path))
        env[key] = values[0]

read_env(base / '.local/demo.env', {'DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'SPRING_PROFILES_ACTIVE', 'SESSION_COOKIE_SECURE', 'SERVER_PORT'})
accounts = base / '.local/initial-accounts.env'
if accounts.exists():
    read_env(accounts, {'APP_DEMO_PASSWORD_' + user for user in ('ADMIN', 'TEACHER', 'STUDENT', 'STUDENT2')})
ai_env = base / '.local/ai.env'
if ai_env.exists():
    if ai_env.stat().st_mode & 0o077:
        raise SystemExit('Private AI configuration must have permissions 600.')
    read_env(ai_env, {'AI_MODE', 'DEEPSEEK_API_KEY', 'DEEPSEEK_MODEL'})
judge_env = Path('/tmp') / f'cassist-judge-{os.getuid()}' / 'judge.env'
if (base.parent / '.local/system-judge.env').exists():
    judge_env = base.parent / '.local/system-judge.env'
if judge_env.exists():
    read_env(judge_env, {'JUDGE_ENABLED', 'JUDGE_BASE_URL', 'JUDGE_TOKEN'})
env['SPRING_PROFILES_ACTIVE'] = 'demo'
port = int(env.get('SERVER_PORT', '8080'))
with socket.socket() as sock:
    if sock.connect_ex(('127.0.0.1', port)) == 0:
        raise SystemExit(f'Port {port} is already in use; refusing to replace an existing process.')
jar = base / 'target/c-assist-backend-0.1.0-SNAPSHOT.jar'
if not jar.exists():
    raise SystemExit('Build backend with ./mvnw package first.')
java_args = ['java', '-Duser.timezone=UTC']
engine_file = base / '.local/database-engine'
if env['DB_URL'].startswith('jdbc:mariadb://127.0.0.1:33077/') and (not engine_file.exists() or engine_file.read_text().strip() == 'mysql'):
    public_key = base / '.local/mysql-data/public_key.pem'
    if not public_key.is_file():
        raise SystemExit('The private MySQL public key is missing; start the demo database first.')
    # Pass the filesystem path as a JDBC property, not an encoded URL fragment.
    # Spring's bracket map syntax preserves the exact Connector/J property name;
    # execvpe preserves the Unicode path as one argument without shell escaping.
    java_args.append('-Dspring.datasource.hikari.data-source-properties[serverRsaPublicKeyFile]=' + str(public_key))
# Run an immutable snapshot: rebuilding target/ must never replace a live JVM's JAR.
runtime_dir = base / '.local/runtime'
runtime_dir.mkdir(mode=0o700, exist_ok=True)
runtime_dir.chmod(0o700)
with tempfile.NamedTemporaryFile(dir=runtime_dir, prefix='.staging-', suffix='.jar', delete=False) as staging:
    staged_path = Path(staging.name)
    with jar.open('rb') as source:
        shutil.copyfileobj(source, staging)
    staging.flush()
    os.fsync(staging.fileno())
with staged_path.open('rb') as staged_file:
    checksum = hashlib.sha256()
    for chunk in iter(lambda: staged_file.read(1024 * 1024), b''):
        checksum.update(chunk)
    digest = checksum.hexdigest()
runtime_jar = runtime_dir / ('c-assist-' + digest + '.jar')
if runtime_jar.exists():
    staged_path.unlink()
else:
    staged_path.replace(runtime_jar)
    runtime_jar.chmod(0o400)
java_args.extend(['-jar', str(runtime_jar)])
os.chdir(base)
(base / '.local/backend.pid').write_text(str(os.getpid()))
print(f'Starting local demo on 127.0.0.1:{port}; logs: backend/.local/backend.log', flush=True)
log = os.open(base / '.local/backend.log', os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
os.dup2(log, 1)
os.dup2(log, 2)
os.close(log)
os.execvpe('java', java_args, env)
