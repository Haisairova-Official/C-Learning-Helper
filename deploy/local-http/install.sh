#!/usr/bin/env bash
set -euo pipefail

if [[ $(id -u) != 0 ]]; then
  echo '请使用 sudo bash 执行此安装脚本。' >&2
  exit 1
fi

config_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
unit_names=(c-assist-lan-http.socket c-assist-lan-http.service)

test -x /usr/lib/systemd/systemd-socket-proxyd
systemd-analyze verify "$config_dir/c-assist-lan-http.socket" "$config_dir/c-assist-lan-http.service"

for unit_name in "${unit_names[@]}"; do
  unit_target="/etc/systemd/system/$unit_name"
  existing_path=$(systemctl show --property=FragmentPath --value "$unit_name" 2>/dev/null || true)
  if [[ -n "$existing_path" && "$existing_path" != "$unit_target" ]]; then
    echo "发现同名服务，停止安装：$existing_path" >&2
    exit 1
  fi
  if [[ -e "$unit_target" || -L "$unit_target" ]]; then
    if [[ -L "$unit_target" ]] || ! cmp -s -- "$config_dir/$unit_name" "$unit_target"; then
      echo "保留已有配置，停止安装：$unit_target" >&2
      exit 1
    fi
  fi
done

if [[ -n $(ss -H -ltn 'sport = :80') ]] && ! systemctl is-active --quiet c-assist-lan-http.socket; then
  echo '80 端口已被其他服务占用，停止安装。' >&2
  exit 1
fi

# Verify the existing frontend without changing any application processes.
curl --noproxy '*' --fail --silent --show-error --max-time 5 http://127.0.0.1:5173/ >/dev/null

for unit_name in "${unit_names[@]}"; do
  install -m 0644 -- "$config_dir/$unit_name" "/etc/systemd/system/$unit_name"
done
systemctl daemon-reload
systemctl enable --now c-assist-lan-http.socket
curl --noproxy '*' --fail --silent --show-error --max-time 5 http://127.0.0.1/api/auth/csrf >/dev/null
echo '80 端口入口已启动：浏览器打开 http://本机局域网IP/，无需填写端口。'
