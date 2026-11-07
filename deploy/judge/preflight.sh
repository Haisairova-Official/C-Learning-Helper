#!/bin/sh
set -eu
judge_binary=/usr/local/libexec/go-judge
judge_rootfs=/var/lib/c-assist-judge/rootfs
if [ -z "${ES_AUTH_TOKEN:-}" ]; then
    echo "Judge authentication token is not configured" >&2
    exit 1
fi
[ -x "$judge_binary" ] || { echo "Pinned go-judge binary is missing" >&2; exit 1; }
[ -d /sys/fs/cgroup ] || { echo "cgroup filesystem is missing" >&2; exit 1; }
for item in usr/bin/gcc usr/bin/g++ bin lib etc/ld.so.cache; do
    [ -e "$judge_rootfs/$item" ] || { echo "Compiler rootfs is incomplete" >&2; exit 1; }
    judge_resolved=$(realpath "$judge_rootfs/$item")
    case "$judge_resolved" in
        "$judge_rootfs"/*) ;;
        *) echo "Compiler rootfs contains a host-pointing symlink" >&2; exit 1 ;;
    esac
done
# Require the fail-closed flag supported by the pinned go-judge build.
"$judge_binary" --help 2>&1 | grep -q -- 'no-fallback' || {
    echo "go-judge does not support required no-fallback behavior" >&2
    exit 1
}
# This is only a configuration precheck. Actual resource/namespace isolation needs acceptance tests.
