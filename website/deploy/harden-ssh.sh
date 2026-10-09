#!/usr/bin/env bash
# 独立步骤：仅在管理员和部署账户均已完成新的公钥会话测试后执行。
# 不修改 SSH 端口、不重启 ssh.socket、不把部署账户加入 sudo。
set -euo pipefail
export LC_ALL=C
umask 077
die() { printf 'SSH 加固已停止：%s\n' "$*" >&2; exit 1; }
admin_user='' admin_ip='' admin_port='' deploy_ip='' deploy_port='' confirmed=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --admin-user|--admin-client-ip|--admin-client-port|--deploy-client-ip|--deploy-client-port)
            [[ $# -ge 2 ]] || die '参数缺少值'
            case "$1" in
                --admin-user) admin_user=$2;; --admin-client-ip) admin_ip=$2;;
                --admin-client-port) admin_port=$2;; --deploy-client-ip) deploy_ip=$2;;
                --deploy-client-port) deploy_port=$2;;
            esac
            shift 2;;
        --confirm-recovery-console) confirmed=1; shift;;
        --help)
            printf '用法：sudo bash harden-ssh.sh --admin-user ubuntu --admin-client-ip IP --admin-client-port 来源端口 --deploy-client-ip IP --deploy-client-port 来源端口 --confirm-recovery-console\n'
            exit 0;;
        *) die "未知参数：$1";;
    esac
done
[[ $EUID == 0 && $confirmed == 1 ]] || die '需要已有管理员权限且明确确认恢复控制台可用'
[[ $admin_user =~ ^[a-z][a-z0-9_-]{1,31}$ && $admin_user != root ]] || die '必须使用非 root 的维护管理员'
[[ $admin_port =~ ^[0-9]{1,5}$ && $deploy_port =~ ^[0-9]{1,5}$ ]] || die '来源端口无效'
(( 10#$admin_port >= 1 && 10#$admin_port <= 65535 && 10#$deploy_port >= 1 && 10#$deploy_port <= 65535 )) || die '来源端口超出范围'
[[ -f /etc/lizhang-docs/bootstrap-state && ! -L /etc/lizhang-docs/bootstrap-state ]] || die '尚未完成受限初始化'
read -r deploy_user admin_cidr ssh_port < /etc/lizhang-docs/bootstrap-state
[[ $admin_user != "$deploy_user" ]] || die '维护管理员与无 sudo 的部署用户必须分开'
id -nG "$admin_user" | tr ' ' '\n' | grep -qx sudo || die '维护管理员不属于 sudo 组'
! id -nG "$deploy_user" | tr ' ' '\n' | grep -qx sudo || die '部署用户意外拥有 sudo 组权限，先审查'
python3 - "$admin_cidr" "$admin_ip" "$deploy_ip" <<'PY'
import ipaddress, sys
try:
    network = ipaddress.ip_network(sys.argv[1], strict=True)
    for text in sys.argv[2:]:
        address = ipaddress.ip_address(text)
        if address not in network or str(address) != text:
            raise ValueError('测试会话来源必须位于授权 CIDR 中并使用标准 IP 格式')
except ValueError as error:
    print(error, file=sys.stderr)
    sys.exit(1)
PY
# 来源端口取自新会话 SSH_CONNECTION，不是服务器 SSH 监听端口。
# 以服务器认证日志证明 Accepted publickey，而非仅凭用户口头确认或环境变量。
auth_log=$(journalctl -u ssh.service -t sshd _UID=0 --since '-10 minutes' --no-pager -o cat)
for proof in "$admin_user $admin_ip $admin_port" "$deploy_user $deploy_ip $deploy_port"; do
    read -r proof_user proof_ip proof_port <<<"$proof"
    grep -Fq "Accepted publickey for $proof_user from $proof_ip port $proof_port ssh2:" <<<"$auth_log" || die '最近 10 分钟缺少对应的新公钥认证日志；保持旧会话，重做新会话验证'
done
[[ -d /etc/ssh/sshd_config.d && ! -L /etc/ssh/sshd_config.d ]] || die 'SSH 配置片段目录不可用'
# 既有 Match 块可能覆盖身份认证规则，须先由维护管理员单独审查。
if grep -Ei '^[[:space:]]*Match[[:space:]]' /etc/ssh/sshd_config /etc/ssh/sshd_config.d/*.conf 2>/dev/null | grep -q .; then
    die '既有 SSH Match 配置需要人工审查，不自动改写'
fi
target=/etc/ssh/sshd_config.d/00-lizhang-docs-key-only.conf
[[ ! -L $target ]] || die '目标 SSH 配置为符号链接'
if [[ -f $target ]] && ! grep -q '^# lizhang-docs managed key-only$' "$target"; then
    die '目标配置已存在但不属于本工具'
fi
backup=$(mktemp /etc/ssh/sshd_config.d/.lizhang-backup-XXXXXX)
had_target=0
if [[ -f $target ]]; then cp -- "$target" "$backup"; had_target=1; fi
committed=0
restore() {
    if [[ $committed == 0 ]]; then
        if [[ $had_target == 1 ]]; then install -o root -g root -m 600 "$backup" "$target"; else rm -f -- "$target"; fi
        /usr/sbin/sshd -t || true
    fi
    rm -f -- "$backup"
}
trap restore EXIT
cat > "$target" <<'CONF'
# lizhang-docs managed key-only
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
CONF
chmod 600 "$target"
/usr/sbin/sshd -t
for proof in "$admin_user $admin_ip" "$deploy_user $deploy_ip"; do
    read -r proof_user proof_ip <<<"$proof"
    effective=$(/usr/sbin/sshd -T -C "user=$proof_user,host=localhost,addr=$proof_ip")
    for required in 'pubkeyauthentication yes' 'passwordauthentication no' 'kbdinteractiveauthentication no' 'permitrootlogin no'; do
        grep -qx "$required" <<<"$effective" || die 'SSH 实际生效配置与密钥认证要求不符，已恢复本工具原配置'
    done
done
if systemctl is-active --quiet ssh.service; then
    systemctl reload ssh.service
elif systemctl is-active --quiet ssh.socket; then
    printf 'ssh.socket 正在使用 socket activation；下次启动 ssh.service 时读取新配置，本脚本不改端口或重启 socket。\n'
else
    die 'SSH 服务和 socket 均未启用，需人工审查'
fi
committed=1
printf 'SSH 密钥加固已写入并校验；请保持旧管理员会话，再次开启管理员和部署账户的新会话完成验收。\n'
