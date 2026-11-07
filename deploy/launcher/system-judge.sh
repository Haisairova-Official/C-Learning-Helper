#!/usr/bin/env bash
# Install the project's existing systemd judge on machines without usable rootless cgroups.
set -euo pipefail
[[ $(id -u) == 0 ]] || exit 1
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
conf=/etc/c-assist-judge
work=/var/lib/c-assist-judge
binary=/usr/local/libexec/go-judge
unit=/etc/systemd/system/c-assist-judge.service
preflight=/usr/local/libexec/c-assist-judge-preflight.sh
identity=$(printf '%s\n%s' "$root" "${SUDO_UID:?请从 install.sh 运行}" | sha256sum | cut -d' ' -f1)
if [[ -f "$conf/installer-id" && $(cat "$conf/installer-id") == "$identity" ]]; then
    :
else
    for path in "$conf" "$work" "$binary" "$unit" "$preflight"; do
        [[ ! -e "$path" && ! -L "$path" ]] || { echo "已有其他部署占用 $path，请核对后再安装。" >&2; exit 1; }
    done
    [[ -z $(systemctl show c-assist-judge.service -p FragmentPath --value) ]] || exit 1
    install -d -m 0700 "$conf"
    printf '%s\n' "$identity" > "$conf/installer-id"
fi
IFS= read -r token
[[ $token =~ ^[0-9a-f]{64}$ ]] || exit 1
if ! systemctl is-active --quiet c-assist-judge.service && ss -H -ltn 'sport = :5050' | grep -q .; then
    echo '5050 端口已被其他服务占用。' >&2; exit 1
fi
if getent passwd 20000 >/dev/null || getent group 20000 >/dev/null; then
    echo '评测模板中的保留 UID/GID 20000 已有用途，请按 deploy/judge/README.md 配置独立身份。' >&2; exit 1
fi
install -d -m 0700 "$work"
if [[ ! -d "$work/rootfs" ]]; then
    stage=$(mktemp -d "$work/.install.XXXXXX")
    trap 'rm -rf -- "$stage"' EXIT
    source /etc/os-release
    if command -v dnf >/dev/null; then
        dnf -y --installroot="$stage" --releasever="${VERSION_ID%%.*}" --setopt=install_weak_deps=False \
            --setopt="module_platform_id=${PLATFORM_ID:-platform:el8}" install gcc gcc-c++ glibc-devel libstdc++-devel
    elif command -v apt-get >/dev/null; then
        apt-get install -y debootstrap
        case "$ID:$(dpkg --print-architecture)" in
            ubuntu:amd64) mirror=https://archive.ubuntu.com/ubuntu;;
            ubuntu:*) mirror=https://ports.ubuntu.com/ubuntu-ports;;
            debian:*) mirror=https://deb.debian.org/debian;;
            *) echo '请参照 deploy/judge/README.md 准备编译 rootfs。'; exit 1;;
        esac
        debootstrap --variant=minbase --include=gcc,g++,libc6-dev "$VERSION_CODENAME" "$stage" "$mirror"
    else
        echo '请按 deploy/judge/README.md 配置当前发行版的编译 rootfs。' >&2; exit 1
    fi
    [[ -x "$stage/usr/bin/gcc" && -x "$stage/usr/bin/g++" && -f "$stage/etc/ld.so.cache" ]]
    mv "$stage" "$work/rootfs"
fi
install -d -m 0755 /usr/local/libexec
install -m 0755 "$root/.local/bin/go-judge" "$binary"
install -m 0755 "$root/deploy/judge/preflight.sh" "$preflight"
install -m 0600 "$root/deploy/config/judge/mount.yaml" "$conf/mount.yaml"
if [[ ! -e "$work/rootfs/lib64" ]]; then sed -i '/^  - type: bind$/{N;/source: .*\/lib64$/{N;N;d;};P;D;}' "$conf/mount.yaml"; fi
umask 077
printf 'ES_AUTH_TOKEN=%s\n' "$token" > "$conf/judge.env"
install -m 0644 "$root/deploy/systemd/c-assist-judge.service" "$unit"
systemctl daemon-reload
systemctl enable c-assist-judge.service
systemctl restart c-assist-judge.service
