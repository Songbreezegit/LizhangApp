#!/usr/bin/env bash
# 服务器端：无需 root 或 sudo。必须由已配置的部署用户调用。
set -euo pipefail
if [[ $# != 2 || ! $2 =~ ^[a-f0-9]{64}$ ]]; then
    printf '用法：deploy-release.sh /srv/lizhang-docs/uploads/SHA256.tar.gz SHA256\n' >&2
    exit 2
fi
exec 9>/srv/lizhang-docs/site/.deployment.lock
flock -x 9
exec /usr/bin/python3 /usr/local/lib/lizhang-docs/release.py install "$1" "$2"
