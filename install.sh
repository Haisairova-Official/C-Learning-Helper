#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$ROOT"
source deploy/launcher/env.sh
case "${1:-}" in
    -h|--help) echo '用法：./install.sh [--check]。--check 仅检查环境。安装后使用 ./run [--port 端口]。'; exit 0;;
    ''|--check) ;;
    *) echo '用法：./install.sh [--check]' >&2; exit 2;;
esac
(( $# <= 1 )) || exit 2
[[ $(uname -s) == Linux ]] || { echo 'Windows 请运行 install.bat。' >&2; exit 1; }
if [[ $(id -u) == 0 && -n ${SUDO_USER:-} && $SUDO_USER != root ]]; then
    exec sudo -u "$SUDO_USER" -H bash "$ROOT/install.sh" "$@"
fi
[[ $(id -u) != 0 ]] || { echo '请使用普通 Linux 账号运行安装。需要安装系统包时脚本会调用 sudo。' >&2; exit 1; }
packages=()
requires=()
if command -v dnf >/dev/null; then
    manager=dnf; pkg_python=python3.11; pkg_cpp=gcc-c++; pkg_db=mariadb-server; pkg_cap=libcap
elif command -v apt-get >/dev/null; then
    manager=apt; pkg_python=python3; pkg_cpp=g++; pkg_db=mariadb-server; pkg_cap=libcap2-bin
elif command -v pacman >/dev/null; then
    manager=pacman; pkg_python=python; pkg_cpp=gcc; pkg_db=mariadb; pkg_cap=libcap
else
    manager=other; pkg_python=python3; pkg_cpp=g++; pkg_db=mariadb-server; pkg_cap=libcap
fi
need() { packages+=("$1"); requires+=("$2"); }
python_runtime >/dev/null || need "$pkg_python" 'Python 3.8+'
if [[ ! -r /etc/ssl/certs/ca-certificates.crt && ! -r /etc/pki/tls/certs/ca-bundle.crt ]]; then need ca-certificates ca-certificates; fi
for cmd in curl tar unzip gcc; do command -v "$cmd" >/dev/null || need "$cmd" "$cmd"; done
command -v g++ >/dev/null || need "$pkg_cpp" g++
command -v setcap >/dev/null || need "$pkg_cap" setcap
if ! command -v mariadbd >/dev/null && ! command -v mysqld >/dev/null; then need "$pkg_db" MariaDB; fi
if command -v mariadbd >/dev/null && ! command -v mariadb-install-db >/dev/null && ! command -v mysql_install_db >/dev/null; then need "$pkg_db" mariadb-install-db; fi
if [[ "${1:-}" == --check ]]; then
    result=0
    if (( ${#requires[@]} )); then echo "缺少系统组件：${requires[*]}"; result=1; fi
    if python=$(python_runtime); then "$python" deploy/launcher/toolchains.py --check || result=1; fi
    [[ -d /sys/fs/cgroup && -x /usr/bin/gcc && -x /usr/bin/g++ ]] || { echo '缺少 Linux cgroup 或 /usr/bin 下的 GCC/G++'; result=1; }
    for file in .local/bin/go-judge .local/bin/c-assist-web frontend/dist/index.html backend/target/c-assist-backend-0.1.0-SNAPSHOT.jar; do
        [[ -s $file ]] || { echo "尚待安装：$file"; result=1; }
    done
    if [[ $result == 0 ]]; then
        if [[ -f .local/system-judge.env ]]; then
            "$python" deploy/launcher/check_judge.py --system-env .local/system-judge.env || result=1
        else
            "$python" deploy/launcher/check_judge.py || result=1
        fi
    fi
    exit "$result"
fi
if (( ${#packages[@]} )); then
    command -v sudo >/dev/null || { echo "当前账号需要 sudo 安装这些系统组件：${requires[*]}。请由管理员安装后重试。" >&2; exit 1; }
    echo "安装缺少的系统组件：${requires[*]}"
    case "$manager" in
        apt) sudo apt-get update; sudo apt-get install -y "${packages[@]}";;
        dnf) sudo dnf install -y "${packages[@]}";;
        pacman) sudo pacman -S --needed --noconfirm "${packages[@]}";;
        *) echo "请用发行版包管理器安装：${requires[*]}，再运行 ./install.sh" >&2; exit 1;;
    esac
fi
python=$(python_runtime)
"$python" deploy/launcher/toolchains.py
if [[ -x "$tools_dir/jdk/bin/java" ]]; then export JAVA_HOME="$tools_dir/jdk"; else export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"; fi
umask 077
mkdir -p .local/bin
stage=$(mktemp -d "$ROOT/.local/build.XXXXXX")
trap 'rm -rf -- "$stage"' EXIT
echo '构建隔离评测服务、网页入口和应用……'
GOBIN="$stage" go install github.com/criyle/go-judge/cmd/go-judge@v1.14.0
go build -o "$stage/c-assist-web" deploy/launcher/web.go
(cd backend && sh ./mvnw -B -ntp -DskipTests package)
(cd frontend && npm ci --include=dev --no-audit --no-fund && npm run build)
mv -f "$stage/go-judge" .local/bin/go-judge
if ! cmp -s "$stage/c-assist-web" .local/bin/c-assist-web; then mv -f "$stage/c-assist-web" .local/bin/c-assist-web; fi
system_env="$ROOT/.local/system-judge.env"
if [[ -f "$system_env" ]]; then
    "$python" deploy/launcher/check_judge.py --system-env "$system_env" || {
        token=$(sed -n 's/^JUDGE_TOKEN=//p' "$system_env")
        sudo bash deploy/launcher/system-judge.sh <<<"$token"
        "$python" deploy/launcher/check_judge.py --system-env "$system_env"
    }
elif ! "$python" deploy/launcher/check_judge.py; then
    echo '当前普通用户会话无法提供完整隔离，正在安装 systemd 评测服务和独立编译环境。'
    [[ -d /run/systemd/system ]] || { echo '当前会话需要可用的 systemd 以启动隔离评测服务。' >&2; exit 1; }
    token=$("$python" -c 'import secrets; print(secrets.token_hex(32))')
    sudo bash deploy/launcher/system-judge.sh <<<"$token"
    printf 'JUDGE_ENABLED=true\nJUDGE_BASE_URL=http://127.0.0.1:5050\nJUDGE_TOKEN=%s\n' "$token" > "$stage/system-judge.env"
    mv "$stage/system-judge.env" "$system_env"
    "$python" deploy/launcher/check_judge.py --system-env "$system_env"
fi
if [[ $(cat /proc/sys/net/ipv4/ip_unprivileged_port_start) -gt 80 ]] &&
   ! systemctl is-active --quiet c-assist-lan-http.socket 2>/dev/null &&
   ! getcap .local/bin/c-assist-web | grep -q cap_net_bind_service; then
    echo '为项目的网页入口授予监听 80 端口的权限。'
    sudo setcap cap_net_bind_service=ep "$ROOT/.local/bin/c-assist-web"
fi
echo '安装完成。在项目根目录执行 ./run 或 ./run --port 9000。'
