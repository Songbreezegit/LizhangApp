#!/usr/bin/env bash
# 服务器端：显式指定历史版本摘要，保留全部历史版本供审核。
set -euo pipefail
if [[ $# != 1 || ! $1 =~ ^[a-f0-9]{64}$ ]]; then
    printf '用法：rollback.sh 历史版本SHA256\n' >&2
    exit 2
fi
exec 9>/srv/lizhang-docs/site/.deployment.lock
flock -x 9
exec /usr/bin/python3 /usr/local/lib/lizhang-docs/release.py rollback "$1"
