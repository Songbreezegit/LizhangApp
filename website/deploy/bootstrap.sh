#!/usr/bin/env bash
# 仅在已获服务器操作授权、已核验安全组和恢复控制台后，由管理员执行。
# 不配置域名、签发证书、开放 Web 端口或重启服务器。
set -euo pipefail
export LC_ALL=C
umask 027
die() { printf '初始化已停止：%s\n' "$*" >&2; exit 1; }
usage() {
    printf '%s\n' '用法：sudo bash bootstrap.sh --deploy-user 用户 --public-key 公钥.pub --admin-cidr 管理来源CIDR --ssh-port 22 --confirm-empty-dedicated-host [--apply-updates] [--console-run]'
}
deploy_user='' public_key='' admin_cidr='' ssh_port='' confirmed=0 updates=0 console_run=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --deploy-user|--public-key|--admin-cidr|--ssh-port)
            [[ $# -ge 2 ]] || die '参数缺少值'
            case "$1" in
                --deploy-user) deploy_user=$2;; --public-key) public_key=$2;;
                --admin-cidr) admin_cidr=$2;; --ssh-port) ssh_port=$2;;
            esac
            shift 2;;
        --confirm-empty-dedicated-host) confirmed=1; shift;;
        --apply-updates) updates=1; shift;;
        --console-run) console_run=1; shift;;
        --help) usage; exit 0;;
        *) die "未知参数：$1";;
    esac
done
[[ $EUID == 0 ]] || die '需要已有管理员使用 sudo 执行'
[[ $confirmed == 1 ]] || die '必须明确确认这是已审查的专用服务器，并已有恢复控制台'
[[ $deploy_user =~ ^[a-z][a-z0-9_-]{2,31}$ && $deploy_user != root && $deploy_user != ubuntu && $deploy_user != www-data ]] || die '部署账户名称无效，必须为新建独立账户'
[[ $ssh_port =~ ^[0-9]{1,5}$ ]] || die 'SSH 端口无效'
(( 10#$ssh_port >= 1 && 10#$ssh_port <= 65535 )) || die 'SSH 端口超出范围'
ssh_port=$((10#$ssh_port))
[[ -f $public_key && ! -L $public_key ]] || die '必须提供普通公钥文件；禁止提供私钥'
[[ $(grep -cve '^[[:space:]]*$' "$public_key") == 1 ]] || die '公钥文件必须只有一行公钥'
public_key_text=$(cat "$public_key")
public_key_text=${public_key_text%$'\r'}
[[ $public_key_text != *$'\n'* && $public_key_text != *$'\r'* ]] || die '公钥文件不得包含前置空行或多行内容'
grep -Eq '^ssh-ed25519 [A-Za-z0-9+/]+={0,2}([[:space:]].*)?$' <<<"$public_key_text" || die '仅接受不含 authorized_keys 选项的 ed25519 公钥'
ssh-keygen -l -f "$public_key" >/dev/null || die '公钥格式验证失败'
command -v python3 >/dev/null || die 'Ubuntu 镜像缺少 Python 3，需管理员先审查并安装'
# shellcheck disable=SC1091
source /etc/os-release
[[ $ID == ubuntu && $VERSION_ID == 24.04 ]] || die '此脚本仅支持 Ubuntu 24.04'
admin_cidr=$(python3 - "$admin_cidr" "${SSH_CONNECTION:-}" "$console_run" <<'PY'
import ipaddress, sys
try:
    network = ipaddress.ip_network(sys.argv[1], strict=True)
    minimum = 24 if network.version == 4 else 64
    if network.prefixlen < minimum or network.is_multicast or network.is_unspecified:
        raise ValueError('SSH 来源必须为明确网段，IPv4 至少 /24，IPv6 至少 /64；不接受 /0')
    connection = sys.argv[2].split()
    if sys.argv[3] != '1':
        if len(connection) != 4 or ipaddress.ip_address(connection[0]) not in network:
            raise ValueError('当前 SSH 来源不在授权 CIDR 中，或未保留 SSH_CONNECTION；控制台执行须显式 --console-run')
    print(network)
except ValueError as error:
    print(str(error), file=sys.stderr)
    sys.exit(1)
PY
) || die '管理员来源校验失败'
if [[ $console_run == 0 ]]; then
    read -r _ _ _ current_port <<<"$SSH_CONNECTION"
    [[ $current_port == "$ssh_port" ]] || die '指定 SSH 端口与当前连接不一致'
fi
command -v ss >/dev/null || die '缺少 ss，请先人工检查网络环境'
public_listeners=$(ss -H -ltn '( sport = :80 or sport = :443 )')
if [[ -n $public_listeners ]]; then
    die '检测到已有 80/443 监听；保留现有服务并停止，须人工确认迁移边界'
fi
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
for name in bootstrap.sh harden-ssh.sh deploy-release.sh rollback.sh release.py nginx.conf nginx-site.conf nginx-validate.conf lizhang-docs.service; do
    [[ -f $script_dir/$name && ! -L $script_dir/$name ]] || die "缺少普通源码或模板：$name"
done
[[ $(grep -Ec '^[[:space:]]*listen[[:space:]]' "$script_dir/nginx-site.conf") == 1 ]] || die '受限模板必须只包含一个监听地址'
grep -Eq '^[[:space:]]*listen 127\.0\.0\.1:8080;' "$script_dir/nginx-site.conf" || die '受限模板监听地址不正确'
config_dir=/etc/lizhang-docs
state=$config_dir/bootstrap-state
managed=0
if [[ -e $config_dir || -L $config_dir ]]; then
    [[ -d $config_dir && ! -L $config_dir && -f $state && ! -L $state && $(stat -c %U "$config_dir") == root ]] || die '配置目录已存在但不属于本工具'
    [[ $(stat -c %U "$state") == root ]] || die '初始化状态所有权不正确'
    [[ $(cat "$state") == "$deploy_user $admin_cidr $ssh_port" ]] || die '已有账户、来源或端口与参数不一致；禁止自动放宽'
    managed=1
fi
for path in "$config_dir/nginx.conf" "$config_dir/site.conf" "$config_dir/validate.conf" "$config_dir/deploy-user" "$config_dir/ufw-added.baseline" /etc/systemd/system/lizhang-docs.service; do
    [[ ! -L $path ]] || die "目标配置为符号链接：$path"
done
for path in /srv /srv/lizhang-docs /srv/lizhang-docs/site /srv/lizhang-docs/site/releases /srv/lizhang-docs/uploads /usr/local /usr/local/lib /usr/local/lib/lizhang-docs; do
    [[ ! -L $path ]] || die "管理路径为符号链接：$path"
done
if [[ $managed == 0 ]]; then
    ! getent passwd "$deploy_user" >/dev/null || die '账户已存在，不自动修改既有账户'
    [[ ! -e /srv/lizhang-docs && ! -e /usr/local/lib/lizhang-docs && ! -e /etc/systemd/system/lizhang-docs.service ]] || die '检测到非本工具管理的既有资源'
    [[ ! -e /etc/nginx ]] && ! command -v nginx >/dev/null || die '既有 Nginx 配置或软件需要人工审查，不覆盖或禁用'
else
    [[ $(systemctl is-enabled nginx.service 2>/dev/null || true) == masked ]] || die '默认 Nginx 的屏蔽状态已改变，保留并停止供人工审查'
    existing_home=$(getent passwd "$deploy_user" | cut -d: -f6)
    [[ -n $existing_home && ! -L $existing_home/.ssh && ! -L $existing_home/.ssh/authorized_keys ]] || die '已有账户的 SSH 路径不安全'
    expected_key=$(printf 'from="%s",no-agent-forwarding,no-X11-forwarding,no-pty,permitopen="127.0.0.1:8080" %s' "$admin_cidr" "$public_key_text")
    [[ $(cat "$existing_home/.ssh/authorized_keys") == "$expected_key" ]] || die '已安装公钥与参数不一致，密钥更换须单独审查'
fi
if command -v ufw >/dev/null; then
    current_ufw_added=$(ufw show added)
    current_ufw_status=$(ufw status verbose)
    if [[ $managed == 1 ]]; then
        [[ -f $config_dir/ufw-added.baseline && -f $config_dir/ufw-status.baseline && ! -L $config_dir/ufw-status.baseline ]] || die '缺少原始防火墙基线'
        [[ $current_ufw_added == "$(cat "$config_dir/ufw-added.baseline")" && $current_ufw_status == "$(cat "$config_dir/ufw-status.baseline")" ]] || die '防火墙规则或默认策略已改变，保留并停止供人工审查'
        grep -q '^Status: active' <<<"$current_ufw_status" || die '原防火墙已停用，需要人工审查'
    else
        ! grep -q '^Status: active' <<<"$current_ufw_status" || die '既有 UFW 已启用，保留并停止供人工审查'
        ! grep -q '^ufw ' <<<"$current_ufw_added" || die '既有 UFW 存在规则，保留并停止供人工审查'
    fi
fi
if [[ $managed == 0 ]]; then
    if command -v nft >/dev/null && [[ -n $(nft list ruleset) ]]; then
        die '检测到既有 nftables 规则，保留并停止'
    fi
    if command -v iptables-save >/dev/null && iptables-save | grep -Eq '^-A |^:[^ ]+ (DROP|REJECT) '; then
        die '检测到既有 iptables 规则或拒绝策略，保留并停止'
    fi
fi
[[ ! -e /usr/sbin/policy-rc.d && ! -L /usr/sbin/policy-rc.d ]] || die '既有服务启动策略需要人工审查，不覆盖'
policy_created=0
cleanup() {
    if [[ $policy_created == 1 && -f /usr/sbin/policy-rc.d && ! -L /usr/sbin/policy-rc.d ]] && grep -q '^# lizhang-docs temporary policy$' /usr/sbin/policy-rc.d; then
        rm -- /usr/sbin/policy-rc.d
    fi
}
trap cleanup EXIT
# 安装和更新期间禁止软件包启动默认 Nginx 或重启其他服务。
printf '#!/bin/sh\n# lizhang-docs temporary policy\nexit 101\n' > /usr/sbin/policy-rc.d
chmod 755 /usr/sbin/policy-rc.d
policy_created=1
export DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=l
apt-get update
if [[ $updates == 1 ]]; then apt-get -y -o Dpkg::Options::=--force-confold upgrade; fi
apt-get install -y -o Dpkg::Options::=--force-confold nginx ufw curl ca-certificates
cleanup
policy_created=0
public_listeners=$(ss -H -ltn '( sport = :80 or sport = :443 )')
if [[ -n $public_listeners ]]; then
    die '安装后出现 80/443 监听；不更改该服务，需管理员立即审查'
fi
# 首次安装的新默认服务仅做禁用和屏蔽，避免下一次开机启用软件包默认 80 端口。
# 对已有 Nginx 的首次操作已在上方拒绝；本工具只启动 lizhang-docs.service。
if [[ $managed == 0 ]]; then
    systemctl disable nginx.service
    systemctl mask nginx.service
fi
install -d -o root -g root -m 755 "$config_dir" /srv/lizhang-docs /usr/local/lib/lizhang-docs
if [[ $managed == 0 ]]; then
    useradd --create-home --user-group --shell /bin/bash "$deploy_user"
    passwd -l "$deploy_user" >/dev/null
    user_home=$(getent passwd "$deploy_user" | cut -d: -f6)
    install -d -o "$deploy_user" -g "$deploy_user" -m 700 "$user_home/.ssh"
    printf 'from="%s",no-agent-forwarding,no-X11-forwarding,no-pty,permitopen="127.0.0.1:8080" %s\n' "$admin_cidr" "$public_key_text" > "$user_home/.ssh/authorized_keys"
    chown "$deploy_user:$deploy_user" "$user_home/.ssh/authorized_keys"
    chmod 600 "$user_home/.ssh/authorized_keys"
    install -d -o "$deploy_user" -g "$deploy_user" -m 755 /srv/lizhang-docs/site /srv/lizhang-docs/site/releases
    install -d -o "$deploy_user" -g "$deploy_user" -m 700 /srv/lizhang-docs/uploads
    install -d -o root -g root -m 755 /srv/lizhang-docs/site/releases/initial /srv/lizhang-docs/site/releases/initial/public
    printf '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>礼账受限测试站</title><p>等待部署审核后的静态产物。</p></html>\n' > /srv/lizhang-docs/site/releases/initial/public/index.html
    chmod 644 /srv/lizhang-docs/site/releases/initial/public/index.html
    ln -s releases/initial/public /srv/lizhang-docs/site/current
    chown -h "$deploy_user:$deploy_user" /srv/lizhang-docs/site/current
fi
validation_root=/srv/lizhang-docs/site/.nginx-validation
for path in "$validation_root" "$validation_root/client_body" "$validation_root/proxy" "$validation_root/fastcgi" "$validation_root/uwsgi" "$validation_root/scgi"; do
    [[ ! -L $path ]] || die "校验临时目录为符号链接：$path"
done
install -d -o "$deploy_user" -g "$deploy_user" -m 700 "$validation_root" "$validation_root/client_body" "$validation_root/proxy" "$validation_root/fastcgi" "$validation_root/uwsgi" "$validation_root/scgi"
for name in deploy-release.sh rollback.sh release.py; do
    [[ -f $script_dir/$name && ! -L $script_dir/$name ]] || die "缺少普通工具源码：$name"
    [[ ! -L /usr/local/lib/lizhang-docs/$name ]] || die '工具目标路径为符号链接'
    install -o root -g root -m 755 "$script_dir/$name" "/usr/local/lib/lizhang-docs/$name"
done
for pair in 'nginx.conf nginx.conf' 'nginx-site.conf site.conf' 'nginx-validate.conf validate.conf'; do
    read -r source_name target_name <<<"$pair"
    [[ -f $script_dir/$source_name && ! -L $script_dir/$source_name && ! -L $config_dir/$target_name ]] || die 'Nginx 模板或目标路径不安全'
    install -o root -g root -m 644 "$script_dir/$source_name" "$config_dir/$target_name"
done
[[ ! -L /etc/systemd/system/lizhang-docs.service ]] || die '服务文件为符号链接'
install -o root -g root -m 644 "$script_dir/lizhang-docs.service" /etc/systemd/system/lizhang-docs.service
printf '%s\n' "$deploy_user" > "$config_dir/deploy-user"
chmod 644 "$config_dir/deploy-user"
# 干净的新主机才创建防火墙规则；重复执行时不修改已有规则。
if [[ $managed == 0 ]]; then
    ufw default deny incoming
    ufw default allow outgoing
    ufw allow from "$admin_cidr" to any port "$ssh_port" proto tcp comment 'lizhang-docs-ssh'
    ufw --force enable
    ufw show added > "$config_dir/ufw-added.baseline"
    ufw status verbose > "$config_dir/ufw-status.baseline"
    printf '%s %s %s\n' "$deploy_user" "$admin_cidr" "$ssh_port" > "$state"
    chmod 600 "$state" "$config_dir/ufw-added.baseline" "$config_dir/ufw-status.baseline"
fi
install -d -o root -g root -m 755 /run/lizhang-docs
/usr/sbin/nginx -t -c "$config_dir/nginx.conf"
systemctl daemon-reload
systemctl enable lizhang-docs.service
if systemctl is-active --quiet lizhang-docs.service; then
    systemctl reload lizhang-docs.service
else
    systemctl start lizhang-docs.service
fi
curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8080/ >/dev/null
mapfile -t web_addresses < <(ss -H -ltn '( sport = :8080 )' | awk '{print $4}')
[[ ${#web_addresses[@]} == 1 && ${web_addresses[0]} == '127.0.0.1:8080' ]] || die '8080 监听地址与受限方案不符，需管理员立即审查'
public_listeners=$(ss -H -ltn '( sport = :80 or sport = :443 )')
if [[ -n $public_listeners ]]; then
    die '最终检查发现 80/443 监听，需管理员立即审查；本脚本不会删除现有服务'
fi
printf '受限站点初始化完成；仅监听 127.0.0.1:8080，尚未做密钥认证加固。\n'
if [[ -f /var/run/reboot-required ]]; then printf '系统提示需重启；本脚本未重启，请另行批准维护窗口。\n'; fi
printf '请保留当前管理员会话，用独立用户开启新密钥会话验证，再执行 harden-ssh.sh。\n'
