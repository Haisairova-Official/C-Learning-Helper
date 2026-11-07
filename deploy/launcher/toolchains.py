"""Install missing user-local build tools from vendor archives with SHA-256 verification."""
import hashlib
import json
from pathlib import Path
import platform
import re
import subprocess
import sys
import tempfile

TOOLS = Path(__file__).resolve().parents[2] / '.local/tools'
ARCH = {'x86_64': 'amd64', 'aarch64': 'arm64'}.get(platform.machine())


def output(*args):
    try:
        return subprocess.check_output(args, stderr=subprocess.STDOUT, text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        return ''


def installed(name):
    if name == 'jdk':
        for tool in ('java', 'javac'):
            version = re.search(r'(?:version "|javac )(\d+)', output(tool, '-version'))
            if not version or int(version[1]) < 21:
                return False
        return True
    if name == 'node':
        v = re.match(r'v(\d+)\.(\d+)', output('node', '-v'))
        if not v:
            return False
        major, minor = map(int, v.groups())
        return ((major == 20 and minor >= 19) or (major, minor) >= (22, 12)) and bool(output('npm', '-v'))
    v = re.search(r'go(\d+)\.(\d+)', output('go', 'version'))
    return bool(v and tuple(map(int, v.groups())) >= (1, 26))


def curl(url, *args):
    return subprocess.check_output(['curl', '-fsSL', '--retry', '3', '--connect-timeout', '15', *args, url])


def archive(name):
    if not ARCH:
        raise SystemExit('自动下载工具链支持 x86_64 / aarch64。请为当前架构安装 JDK 21、Node 和 Go 后重试。')
    if name == 'go':
        releases = json.loads(curl('https://go.dev/dl/?mode=json&include=all'))
        file = next(f for r in releases if r['stable'] and r['version'].startswith('go1.26.')
                    for f in r['files'] if f['os'] == 'linux' and f['arch'] == ARCH and f['kind'] == 'archive')
        return 'https://go.dev/dl/' + file['filename'], file['sha256']
    vendor_arch = 'x64' if ARCH == 'amd64' else ARCH
    if name == 'node':
        base = 'https://nodejs.org/dist/latest-v22.x/'
        for line in curl(base + 'SHASUMS256.txt').decode().splitlines():
            digest, filename = line.split()
            if filename.endswith('-linux-' + vendor_arch + '.tar.gz'):
                return base + filename, digest
        raise SystemExit('Node.js 官方清单中缺少当前架构的安装包')
    url = ('https://api.adoptium.net/v3/assets/latest/21/hotspot?os=linux&image_type=jdk&architecture='
           + ('aarch64' if ARCH == 'arm64' else 'x64'))
    pkg = json.loads(curl(url))[0]['binary']['package']
    return pkg['link'], pkg['checksum']


def install(name):
    TOOLS.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=TOOLS) as stage:
        folder = Path(stage)
        url, expected = archive(name)
        bundle = folder / 'download'
        curl(url, '-o', str(bundle))
        digest = hashlib.sha256()
        with bundle.open('rb') as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b''):
                digest.update(chunk)
        if digest.hexdigest() != expected:
            raise SystemExit(name + ' 下载校验失败，请重新执行 install.sh')
        unpacked = folder / 'unpacked'
        unpacked.mkdir()
        subprocess.run(['tar', '-xf', str(bundle), '--strip-components=1', '-C', str(unpacked)], check=True)
        target = TOOLS / name
        old = folder / 'previous'
        if target.exists():
            target.rename(old)
        try:
            unpacked.rename(target)
        except OSError:
            if old.exists():
                old.rename(target)
            raise


if __name__ == '__main__':
    missing = []
    for tool in ('jdk', 'node', 'go'):
        if not installed(tool) and '--check' not in sys.argv:
            print('安装项目专用工具链 ' + tool, flush=True)
            install(tool)
        if not installed(tool):
            missing.append(tool)
    if missing:
        sys.exit('缺少或版本不符：' + ', '.join(missing))
    print('JDK、Node/npm、Go 版本符合要求')
