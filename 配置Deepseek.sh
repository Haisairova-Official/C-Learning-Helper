#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
source "$ROOT/deploy/launcher/env.sh"
python=$(python_runtime) || { echo '请先安装 Python 3。' >&2; exit 1; }
exec "$python" "$ROOT/scripts/configure_deepseek.py" "$@"
