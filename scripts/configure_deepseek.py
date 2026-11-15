#!/usr/bin/env python3
"""Save private DeepSeek configuration without exposing credentials."""
import argparse
import getpass
import os
from pathlib import Path
import re
import shlex
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description='配置 DeepSeek，密钥隐藏输入；运行中的后端会自动重新加载。')
    parser.add_argument('--stdin', action='store_true', help='从标准输入读取一行密钥（用于自动化）')
    parser.add_argument('--model', default='deepseek-flash', help='模型名称，默认 deepseek-flash')
    parser.add_argument('--no-restart', action='store_true', help='只保存，不重启后端')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_.:-]{1,100}', args.model):
        parser.error('模型名称格式不正确')
    if args.stdin:
        key = sys.stdin.readline().strip()
    else:
        if not sys.stdin.isatty():
            parser.error('请在终端运行以隐藏输入密钥，自动化时使用 --stdin')
        key = getpass.getpass('请输入 DeepSeek API 密钥（输入不显示）：').strip()
    if not re.fullmatch(r'sk-[A-Za-z0-9_-]{16,200}', key):
        raise ValueError('密钥格式不正确，配置未修改')
    private = ROOT / 'backend/.local'
    private.mkdir(mode=0o700, parents=True, exist_ok=True)
    target = private / 'ai.env'
    if target.is_symlink():
        raise ValueError('配置路径是符号链接，配置未修改')
    staged = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=private, prefix='.ai-', delete=False) as stream:
            staged = Path(stream.name)
            os.fchmod(stream.fileno(), 0o600)
            stream.write('AI_MODE=deepseek\nDEEPSEEK_API_KEY=' + shlex.quote(key) + '\nDEEPSEEK_MODEL=' + shlex.quote(args.model) + '\n')
            stream.flush()
            os.fsync(stream.fileno())
        staged.replace(target)
    finally:
        if staged is not None:
            staged.unlink(missing_ok=True)
    print('DeepSeek 私有配置已保存（仅当前用户可读写，不进入 Git）。', flush=True)
    if args.no_restart:
        print('下次启动后端时生效。')
        return
    import local_app as app
    port_file = ROOT / '.local/backend-port'
    if port_file.exists():
        app.PORTS['backend'] = int(port_file.read_text().strip())
    if not app.owned_pid('backend'):
        print('后端尚未运行，请执行 ./run 启动。')
        return
    print('正在重新加载后端，已登录用户需要重新登录……', flush=True)
    app.stop_one('backend')
    os.environ['SERVER_PORT'] = str(app.PORTS['backend'])
    app.spawn('backend', [sys.executable, str(ROOT / 'backend/scripts/run-demo.py')], ROOT)
    print('DeepSeek 配置已生效。')

if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, RuntimeError):
        print('配置未完成：请检查输入、文件权限及后端日志；密钥不会输出。', file=sys.stderr)
        sys.exit(1)
    except (KeyboardInterrupt, EOFError):
        print('\n已取消。', file=sys.stderr)
        sys.exit(130)
