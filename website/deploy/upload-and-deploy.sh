#!/usr/bin/env bash
# 本地 Linux/WSL：使用已绑定且获授权的 SSH agent，私钥始终留在用户自己的设备。
# 严格校验已核验的服务器 host key，只上传 dist，不上传源码或服务器配置。
set -euo pipefail
umask 077
die() { printf '上传已停止：%s\n' "$*" >&2; exit 1; }
dist='' host='' user='' port='' known_hosts='' authorized=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --dist|--host|--user|--port|--known-hosts)
            [[ $# -ge 2 ]] || die '参数缺少值'
            case "$1" in
                --dist) dist=$2;; --host) host=$2;; --user) user=$2;;
                --port) port=$2;; --known-hosts) known_hosts=$2;;
            esac
            shift 2;;
        --authorized-access) authorized=1; shift;;
        --help)
            printf '用法：bash upload-and-deploy.sh --dist ../dist --host 已授权服务器地址 --user lizhang-deploy --port 22 --known-hosts 已核验known_hosts文件 --authorized-access\n'
            exit 0;;
        *) die "未知参数：$1";;
    esac
done
[[ $authorized == 1 ]] || die '必须先获得该服务器部署授权，再显式传入 --authorized-access'
[[ $user =~ ^[a-z][a-z0-9_-]{2,31}$ && $user != root ]] || die '必须指定独立部署用户'
[[ $host =~ ^[A-Za-z0-9][A-Za-z0-9.-]*$ || $host =~ ^[a-fA-F0-9:]+$ ]] || die '服务器地址格式无效'
[[ $port =~ ^[0-9]{1,5}$ ]] || die '端口无效'
(( 10#$port >= 1 && 10#$port <= 65535 )) || die '端口超出范围'
port=$((10#$port))
[[ -d $dist && ! -L $dist ]] || die 'dist 必须为实际的构建产物目录'
[[ -f $known_hosts && ! -L $known_hosts ]] || die '需要用户已核验的 known_hosts 普通文件'
known_hosts=$(realpath -- "$known_hosts")
[[ $known_hosts != *'"'* && $known_hosts != *$'\n'* ]] || die 'known_hosts 路径包含禁止字符'
for command in ssh scp ssh-add ssh-keygen tar sha256sum python3 realpath; do command -v "$command" >/dev/null || die "缺少工具：$command"; done
ssh-add -l >/dev/null || die '没有可用的已授权 SSH agent；请用户自行绑定密钥，勿发送私钥'
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
python3 - "$dist" "$script_dir/release.py" <<'PY'
import importlib.util, pathlib, sys
spec = importlib.util.spec_from_file_location('lizhang_release', sys.argv[2])
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
module.tree_manifest(pathlib.Path(sys.argv[1]).resolve())
print('本地 dist 静态文件、路径和大小检查通过。')
PY
tar --version | grep -q 'GNU tar' || die '请使用 Linux/WSL 的 GNU tar，以保证产物可重复打包'
task_tmp=$(mktemp -d "${TMPDIR:-/tmp}/lizhang-upload.XXXXXXXX")
archive=$task_tmp/site.tar.gz
cleanup() { rm -f -- "$archive"; rmdir -- "$task_tmp"; }
trap cleanup EXIT
tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -czf "$archive" -C "$dist" .
sha=$(sha256sum "$archive" | cut -d' ' -f1)
[[ $sha =~ ^[a-f0-9]{64}$ ]] || die '产物摘要无效'
(( $(wc -c < "$archive") <= 20 * 1024 * 1024 )) || die '发布包超过 20 MiB 限制'
ssh_options=(-F /dev/null -o BatchMode=yes -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=\"$known_hosts\"" -o PreferredAuthentications=publickey -o PasswordAuthentication=no -o KbdInteractiveAuthentication=no -o IdentityFile=none -o IdentitiesOnly=no -o ForwardAgent=no -o ClearAllForwardings=yes -o ControlMaster=no -o ControlPath=none -o ConnectTimeout=10)
ssh "${ssh_options[@]}" -l "$user" -p "$port" "$host" "test -d /srv/lizhang-docs/uploads && test ! -L /srv/lizhang-docs/uploads && test ! -L /srv/lizhang-docs/uploads/$sha.tar.gz && test -x /usr/local/lib/lizhang-docs/deploy-release.sh"
scp_host=$host
if [[ $host == *:* ]]; then scp_host="[$host]"; fi
scp "${ssh_options[@]}" -P "$port" -- "$archive" "$user@$scp_host:/srv/lizhang-docs/uploads/$sha.tar.gz"
ssh "${ssh_options[@]}" -l "$user" -p "$port" "$host" "/usr/local/lib/lizhang-docs/deploy-release.sh /srv/lizhang-docs/uploads/$sha.tar.gz $sha"
printf '远端受限发布完成，版本 SHA-256：%s\n' "$sha"
printf '本脚本未修改 DNS、签发证书或开放公网端口。\n'
