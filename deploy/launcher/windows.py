"""Native Windows installer and service lifecycle for one checkout."""
import argparse
import contextlib
import csv
import ctypes
from ctypes import wintypes
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
STATE = ROOT / '.local/windows'
PRIVATE = STATE / 'private'
TOOLS = STATE / 'tools'
BIN = STATE / 'bin'
MANIFEST = json.loads((HERE / 'windows-tools.json').read_text(encoding='utf-8'))
JAR = ROOT / 'backend/target/c-assist-backend-0.1.0-SNAPSHOT.jar'
DIST = ROOT / 'frontend/dist'
DB_BIN = TOOLS / 'mariadb/bin'
GCC_BIN = TOOLS / 'gcc/bin'
JUDGE_URL = 'http://127.0.0.1:15050'
NO_WINDOW = 0x08000000


def save(path, text):
    tmp = path.with_suffix('.new')
    tmp.write_text(text, encoding='utf-8')
    tmp.replace(path)


def secure_directory():
    PRIVATE.mkdir(parents=True, exist_ok=True)
    identity = subprocess.check_output(['whoami.exe','/user','/fo','csv','/nh'], encoding='mbcs', errors='replace')
    sid = next(csv.reader([identity.strip()]))[-1]
    if not re.fullmatch(r'S-1-[0-9-]+', sid):
        raise RuntimeError('无法识别 Windows 账户。')
    subprocess.run(['icacls.exe', str(PRIVATE), '/inheritance:r', '/grant:r',
                    '*' + sid + ':(OI)(CI)F', '*S-1-5-18:(OI)(CI)F'], check=True, stdout=subprocess.DEVNULL)


@contextlib.contextmanager
def locked():
    import msvcrt
    STATE.mkdir(parents=True, exist_ok=True)
    with (STATE / 'launcher.lock').open('a+b') as lock:
        lock.seek(0)
        try:
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError as exc:
            raise RuntimeError('另一安装或启动操作正在进行。') from exc
        try:
            yield
        finally:
            lock.seek(0)
            msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)


def fetch(url, destination, digest):
    for attempt in range(3):
        try:
            h = hashlib.sha256()
            with urllib.request.urlopen(url, timeout=90) as response, destination.open('wb') as out:
                while chunk := response.read(1024 * 1024):
                    out.write(chunk)
                    h.update(chunk)
            if digest and h.hexdigest() != digest:
                raise RuntimeError('下载校验失败。' + destination.name)
            return
        except OSError:
            if attempt == 2:
                raise
            time.sleep(2)


def tool(name, check):
    spec = MANIFEST[name]
    target = TOOLS / name
    exe = target / spec['file']
    if exe.is_file() and (target / '.installed-sha256').is_file() and (target / '.installed-sha256').read_text().strip() == spec['sha256']:
        return exe
    if check:
        raise RuntimeError('缺少 ' + name + '，请先运行 install.bat。')
    if target.exists():
        raise RuntimeError('已有不完整或不同版本的工具目录，请移走后重试：' + str(target))
    print('安装 ' + name + ' …', flush=True)
    TOOLS.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=TOOLS) as d:
        stage = Path(d)
        archive = stage / (name + '.zip')
        fetch(spec['url'], archive, spec['sha256'])
        with zipfile.ZipFile(archive) as z:
            z.extractall(stage / 'unpacked')
        candidates = [p for p in (stage / 'unpacked').iterdir() if (p / spec['file']).is_file()]
        if len(candidates) != 1:
            raise RuntimeError('工具压缩包结构异常：' + name)
        folder = candidates[0]
        (folder / '.installed-sha256').write_text(spec['sha256'])
        if name == 'gcc':
            subprocess.run(['icacls.exe', str(folder), '/grant:r', '*S-1-15-2-1:(OI)(CI)(RX)'],
                           check=True, stdout=subprocess.DEVNULL)
        folder.rename(target)
    return exe


def prepare_environment():
    for path in [TOOLS/'jdk/bin', TOOLS/'go/bin', TOOLS/'node']:
        os.environ['PATH'] = str(path) + os.pathsep + os.environ['PATH']
    os.environ.update(JAVA_HOME=str(TOOLS/'jdk'), GOTOOLCHAIN='local', CASSIST_COMPILER_BIN=str(GCC_BIN))


def check_vc_runtime(check):
    if all((Path(os.environ['SystemRoot'])/'System32'/lib).is_file() for lib in ('msvcp140.dll','vcruntime140_1.dll')):
        return
    if check:
        raise RuntimeError('缺少 Microsoft Visual C++ x64 运行库。')
    print('安装 Microsoft Visual C++ x64 运行库 …', flush=True)
    with tempfile.TemporaryDirectory(dir=STATE) as d:
        exe = Path(d) / 'vc_redist.x64.exe'
        fetch('https://aka.ms/vs/17/release/vc_redist.x64.exe', exe, None)
        env = dict(os.environ, CASSIST_REDIST=str(exe))
        script = ('& { $s = Get-AuthenticodeSignature -LiteralPath $env:CASSIST_REDIST; '
                  'if ($s.Status -ne "Valid" -or $s.SignerCertificate.Subject -notmatch "O=Microsoft Corporation,") {exit 1}; '
                  '$p=Start-Process -FilePath $env:CASSIST_REDIST -ArgumentList "/install","/passive","/norestart" -Verb RunAs -Wait -PassThru; exit $p.ExitCode }')
        proc = subprocess.run(['powershell.exe','-NoProfile','-Command',script], env=env)
        if proc.returncode == 3010:
            raise RuntimeError('运行库安装完成。请重启 Windows 后再运行 install.bat。')
        proc.check_returncode()


def read_json(url, token=None, data=None, method=None, timeout=5):
    headers = {'Content-Type':'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(url, data=None if data is None else json.dumps(data).encode(),
                                 headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return json.load(response) if method != 'DELETE' else None


def judge_ready(url, token):
    return read_json(url + '/config', token).get('runnerConfig', {}).get('windowsIsolation') == 'appcontainer-job-v1'


def compiler_check(url, token):
    base = {'env':[], 'files':[{'content':''},{'name':'stdout','max':65536},{'name':'stderr','max':65536}],
            'cpuLimit':10000000000, 'clockLimit':20000000000, 'memoryLimit':536870912,'procLimit':16,
            'copyOut':['stdout','stderr'],'copyOutMax':16777216}
    for cc, std, name, source in [
            ('gcc','c17','main.c','#include <stdio.h>\nint main(void){puts("42");}\n'),
            ('g++','c++17','main.cpp','#include <iostream>\nint main(){std::cout << 42 << std::endl;}\n')]:
        compile_cmd = dict(base, args=['/usr/bin/' + cc,'-std=' + std,name,'-o','main'],
                           copyIn={name:{'content':source}}, copyOutCached=['main?'])
        result = read_json(url+'/run', token, {'cmd':[compile_cmd]}, timeout=30)[0]
        file_id = result.get('fileIds', {}).get('main')
        if result['status'] != 'Accepted' or not file_id:
            raise RuntimeError(cc + ' 隔离编译自检失败：' + str(result)[:1200])
        try:
            cmd = dict(base,args=['./main'],copyIn={'main':{'fileId':file_id}})
            result = read_json(url+'/run',token,{'cmd':[cmd]},timeout=30)[0]
            if result['status'] != 'Accepted' or result.get('files',{}).get('stdout','').strip() != '42':
                raise RuntimeError(cc + ' 隔离运行自检失败：' + str(result)[:1200])
        finally:
            read_json(url+'/file/'+file_id, token, method='DELETE')


def judge_command(port):
    return [BIN/'go-judge.exe', '-http-addr=127.0.0.1:'+str(port), '-parallelism=2', '-pre-fork=0',
            '-file-timeout=5m', '-output-limit=16777216','-copy-out-limit=16777216','-silent']


def wait_ready(test, proc, name, seconds=90):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if proc is not None and proc.poll() is not None:
            raise RuntimeError(name + ' 已退出，请检查 ' + str(PRIVATE/(name+'.log')))
        try:
            if test():
                return
        except (OSError, ValueError):
            pass
        time.sleep(.25)
    raise RuntimeError(name + ' 启动超时，请检查 ' + str(PRIVATE/(name+'.log')))


def judge_probe():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0))
        port = sock.getsockname()[1]
    token = secrets.token_hex(32)
    url = 'http://127.0.0.1:'+str(port)
    with tempfile.TemporaryDirectory(dir=PRIVATE) as work, (PRIVATE/'judge-check.log').open('ab') as log:
        proc = subprocess.Popen(judge_command(port), cwd=work, stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                                env=dict(os.environ, ES_AUTH_TOKEN=token), creationflags=NO_WINDOW)
        try:
            wait_ready(lambda:judge_ready(url,token),proc,'judge-check',20)
            compiler_check(url,token)
        finally:
            proc.terminate()
            proc.wait(timeout=10)


def install(check):
    check_vc_runtime(check)
    for name in MANIFEST:
        tool(name, check)
    if not check:
        if any(owned(p) for p in PRIVATE.glob('*.pid.json')):
            raise RuntimeError('更新前请运行 run.bat stop。')
        BIN.mkdir(exist_ok=True)
        (BIN/'verified-judge.sha256').unlink(missing_ok=True)
        subprocess.run([sys.executable, str(HERE/'winjudge/build.py'), str(BIN/'go-judge.exe')],check=True)
        subprocess.run(['go','build','-o',str(BIN/'c-assist-web.exe'),str(HERE/'web.go')],check=True,cwd=ROOT)
        subprocess.run([str(ROOT/'backend/mvnw.cmd'),'-B','-ntp','-DskipTests','package'],cwd=ROOT/'backend',check=True)
        npm = str(TOOLS/'node/npm.cmd')
        subprocess.run([npm,'ci','--include=dev','--no-audit','--no-fund'],cwd=ROOT/'frontend',check=True)
        subprocess.run([npm,'run','build'],cwd=ROOT/'frontend',check=True)
    for p in [BIN/'c-assist-web.exe',BIN/'go-judge.exe',DIST/'index.html',JAR]:
        if not p.is_file():
            raise RuntimeError('缺少安装产物，请运行 install.bat：' + str(p))
    judge_probe()
    with (BIN/'go-judge.exe').open('rb') as binary:
        save(BIN/'verified-judge.sha256', hashlib.file_digest(binary, 'sha256').hexdigest())
    print('Windows 环境和 C / C++ 隔离编译运行自检通过。运行 run.bat，或 run.bat --port 9000。')


# Validate PID and creation time on the same process handle, including during stop.
def process_handle(pid, access):
    api = ctypes.WinDLL('kernel32',use_last_error=True)
    api.OpenProcess.argtypes = [wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]
    api.OpenProcess.restype = wintypes.HANDLE
    handle = api.OpenProcess(access | 0x1000 | 0x100000, False, pid)
    return api, wintypes.HANDLE(handle)


def creation(api, handle):
    times = [wintypes.FILETIME() for _ in range(4)]
    if not api.GetProcessTimes(handle, *(ctypes.byref(t) for t in times)):
        raise ctypes.WinError(ctypes.get_last_error())
    t = times[0]
    return (t.dwHighDateTime << 32) | t.dwLowDateTime


def owned(record, stop=False):
    if not record.exists():
        return False
    item = json.loads(record.read_text())
    api, h = process_handle(item['pid'], 1 if stop else 0)
    if not h.value:
        return False
    try:
        if creation(api,h) != item['created'] or api.WaitForSingleObject(h,0) != 258:
            return False
        if stop:
            if not api.TerminateProcess(h,0) or api.WaitForSingleObject(h,10000) != 0:
                raise RuntimeError('停止进程失败：' + record.stem)
        return True
    finally:
        api.CloseHandle(h)


def require_free(port, host):
    with socket.socket() as probe:
        probe.setsockopt(socket.SOL_SOCKET,socket.SO_EXCLUSIVEADDRUSE,1)
        try:
            probe.bind((host,port))
        except OSError as exc:
            raise RuntimeError(f'端口 {port} 已被占用或保留，请检查占用程序或使用 --port 选择网页端口。') from exc


def spawn(name, args, env, ready, port, host='127.0.0.1'):
    record = PRIVATE/(name+'.pid.json')
    if owned(record):
        wait_ready(ready,None,name)
        return
    require_free(port,host)
    with (PRIVATE/(name+'.log')).open('ab') as log:
        proc = subprocess.Popen(args,stdin=subprocess.DEVNULL,stdout=log,stderr=log,cwd=PRIVATE,env=env,creationflags=NO_WINDOW)
    try:
        api,h = process_handle(proc.pid,0)
        try:
            stamp = creation(api,h)
        finally:
            api.CloseHandle(h)
        save(record,json.dumps({'pid':proc.pid,'created':stamp}))
        wait_ready(ready,proc,name,120)
    except BaseException:
        if proc.poll() is None:
            proc.terminate()
            proc.wait(timeout=10)
        record.unlink(missing_ok=True)
        raise


def instance():
    target = PRIVATE/'instance'
    if not target.exists():
        with tempfile.TemporaryDirectory(dir=PRIVATE) as tmp:
            stage = Path(tmp)/'instance'
            stage.mkdir()
            password, root_password, token = (secrets.token_hex(32) for _ in range(3))
            # Initialization is local and offline; the instance is published as one unit.
            subprocess.run([DB_BIN/'mariadb-install-db.exe', '--datadir='+str(stage/'data'),
                            '--password='+root_password,'--port=33077','--silent'],check=True,
                           stdout=subprocess.DEVNULL)
            (stage/'settings.json').write_text(json.dumps({'db':password,'judge':token}),encoding='utf-8')
            (stage/'admin.cnf').write_text('[client]\nuser=root\npassword='+root_password+'\n',encoding='utf-8')
            (stage/'bootstrap.sql').write_text(
                "CREATE DATABASE IF NOT EXISTS c_assist CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n"
                f"CREATE USER IF NOT EXISTS 'c_assist'@'127.0.0.1' IDENTIFIED BY '{password}';\n"
                "GRANT ALL ON c_assist.* TO 'c_assist'@'127.0.0.1';\n",encoding='utf-8')
            (stage/'initial-accounts.env').write_text(''.join('APP_DEMO_PASSWORD_'+name+'='+secrets.token_hex(12)+'\n'
                for name in ('ADMIN','TEACHER','STUDENT','STUDENT2')),encoding='utf-8')
            stage.rename(target)
    return target, json.loads((target/'settings.json').read_text(encoding='utf-8'))


def database_admin(folder, action):
    return subprocess.run([DB_BIN/'mariadb-admin.exe','--defaults-file='+str(folder/'admin.cnf'),
                           '--protocol=tcp','--host=127.0.0.1','--port=33077','--connect-timeout=2',action],
                          stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=15).returncode == 0


def start(port):
    if not (BIN/'c-assist-web.exe').is_file() or not JAR.is_file():
        raise RuntimeError('请先执行 install.bat。')
    marker = BIN/'verified-judge.sha256'
    with (BIN/'go-judge.exe').open('rb') as binary:
        if not marker.is_file() or hashlib.file_digest(binary,'sha256').hexdigest() != marker.read_text():
            raise RuntimeError('本机隔离评测尚未验证，请执行 install.bat --check。')
    if port in (33077,15050):
        raise RuntimeError('该端口用于内部服务，请选择其他网页端口。')
    web = 'web-'+str(port)
    if not owned(PRIVATE/(web+'.pid.json')):
        require_free(port,'0.0.0.0')
    folder, secrets_db = instance()
    env = dict(os.environ)
    backend_port_file = PRIVATE/'backend-port'
    running = owned(PRIVATE/'backend.pid.json')
    internal = int(backend_port_file.read_text()) if running else (18081 if port == 8080 else 8080)
    api = f'http://127.0.0.1:{internal}/api/auth/csrf'
    bootstrap = folder/'bootstrap.sql'
    db = [DB_BIN/'mariadbd.exe','--no-defaults','--console','--basedir='+str(TOOLS/'mariadb'),
          '--datadir='+str(folder/'data'),'--bind-address=127.0.0.1','--port=33077','--skip-name-resolve']
    if bootstrap.exists():
        db.append('--init-file='+str(bootstrap))
    spawn('database',db,env,lambda:database_admin(folder,'ping'),33077)
    token = secrets_db['judge']
    spawn('judge',judge_command(15050), dict(env,ES_AUTH_TOKEN=token),lambda:judge_ready(JUDGE_URL,token),15050)
    ready = PRIVATE/'backend.ready'
    if not running:
        ready.unlink(missing_ok=True)
        save(backend_port_file,str(internal))
    env.update(SPRING_PROFILES_ACTIVE='demo', DB_USERNAME='c_assist', DB_PASSWORD=secrets_db['db'],
               DB_URL='jdbc:mariadb://127.0.0.1:33077/c_assist', SERVER_ADDRESS='127.0.0.1',
               SERVER_PORT=str(internal),CASSIST_READY_FILE=str(ready),JUDGE_ENABLED='true',
               JUDGE_BASE_URL=JUDGE_URL,JUDGE_TOKEN=token)
    env.update(line.split('=',1) for line in (folder/'initial-accounts.env').read_text().splitlines())
    spawn('backend',[TOOLS/'jdk/bin/java.exe','-Duser.timezone=UTC','-jar',JAR],env,
          lambda:ready.is_file() and bool(read_json(api).get('token')),internal)
    bootstrap.unlink(missing_ok=True)
    url = f'http://127.0.0.1:{port}'
    def website_ready():
        with urllib.request.urlopen(url+'/settings',timeout=3) as response:
            return b'<div id="app">' in response.read(65536) and bool(read_json(url+'/api/auth/csrf').get('token'))
    spawn(web,[BIN/'c-assist-web.exe','--port',str(port),'--backend-port',str(internal),'--dist',DIST],
          dict(os.environ),website_ready,port,'0.0.0.0')
    print('打开 '+url+'/ 。局域网设备使用本机 IP。\n初始账户密码见 '+str(folder/'initial-accounts.env'))


def manage(action):
    records = sorted(PRIVATE.glob('web-*.pid.json')) + [PRIVATE/(s+'.pid.json') for s in ('backend','judge','database')]
    for record in records:
        live = owned(record)
        if action == 'status':
            print(record.name.split('.')[0], '运行中' if live else '已停止')
            continue
        if live and record.name == 'database.pid.json':
            if not database_admin(PRIVATE/'instance','shutdown'):
                raise RuntimeError('数据库未能正常关闭，请检查 database.log。')
            wait_ready(lambda:not owned(record),None,'database',25)
        elif live:
            owned(record,stop=True)
        record.unlink(missing_ok=True)
    if action == 'stop':
        print('服务已停止。')


def main():
    parser = argparse.ArgumentParser()
    actions = parser.add_subparsers(dest='action',required=True)
    actions.add_parser('install').add_argument('--check',action='store_true')
    run = actions.add_parser('run')
    run.add_argument('--port',type=int,default=80)
    run.add_argument('command',choices=['start','status','stop'],default='start',nargs='?')
    args = parser.parse_args()
    if os.name != 'nt':
        parser.error('Windows 入口要求原生 Windows。Linux 请运行 ./install.sh。')
    if not 1 <= getattr(args,'port',80) <= 65535:
        parser.error('端口须在 1 至 65535 之间。')
    with locked():
        secure_directory()
        prepare_environment()
        if args.action == 'install':
            install(args.check)
        elif args.command == 'start':
            start(args.port)
        else:
            manage(args.command)


if __name__ == '__main__':
    try:
        main()
    except (OSError,RuntimeError,ValueError,subprocess.SubprocessError,zipfile.BadZipFile) as error:
        # Database initialization arguments may contain a temporary password.
        message = '依赖命令退出，状态码 '+str(error.returncode) if isinstance(error,subprocess.CalledProcessError) else str(error)
        sys.exit('操作失败：'+message)
