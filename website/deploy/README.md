# 礼账现有 ECS 对接、发布与回滚准备

日期：2026-10-19（Asia/Shanghai）。本文件是下一轮经用户明确授权后使用的方案；本轮未连接 ECS，未执行维护、安装、sudo、Nginx 校验、HTTP 请求、DNS、证书或公网发布操作。

## 历史依据与当前阻塞

已阅读本地 `D:\code\ecs-ops` 对应 [Songbreezegit/ecs-ops](https://github.com/Songbreezegit/ecs-ops) 的归档：提交 `98d098c50e66053fe222eba0d6b392c4a9bc7411`，包括 README、最终交付、Ubuntu 更新、Nginx、回滚报告、安全组清单、`maintenance.sh` 和 `templates/nginx-loopback.conf`。这些报告在 2026-10-19 归档，记录的服务器状态来自 **2026-10-10，并非当前实时状态**。

历史报告记录管理用户为 adminuser，公钥 SSH 连接成功；配置文件关闭 root 登录及密码认证，但完整有效配置、云端安全边界尚未全部核验。安全组优化未完成；UFW 当时未启用，运行时防火墙规则仍待检查；Nginx/Certbot 未安装；维护包已准备，未获 sudo 授权执行。这里不将这些历史状态视为今日事实。

以下条件未完成时，保持正式发布阻断：

1. 用户完成法律正文和 App／网页的全部测试验收，确认实际生效日期、日志说明、批准人、批准日期及当前内容摘要。
2. 重新核对 `渝ICP备2026025309号-1` 所覆盖的域名、主体杨松与实际阿里云接入信息；必要接入手续尚未确认，不能因已取得编号而跳过。
3. 单独获准复核服务器发行版、软件包、目录真实类型、站点/服务/监听、SSH 有效配置及备用管理通道；不重置 SSH、账户、密钥或防火墙。
4. 审计实例所有附属安全组、共享资源、IPv4/IPv6、规则 ID 与实际 UFW/nftables/iptables 状态，保存精确规则备份。安全组放行和主机监听分别核验。
5. 核对必要系统维护和安装条件。若继续采用 ecs-ops 冻结维护包，先在其仓库按当前版本与来源重新审查；本仓库不复制、执行或改写其 maintenance/bootstrap/SSH 脚本，不取消原 guard，不默认其旧固定包版本仍可安装。
6. 在另行授权的维护窗口完成必要更新、Nginx/Certbot 安装与服务准备。优先复用现有官方包、`nginx.service`、`certbot.timer`、日志轮转及维护流程，不新增 Node 服务、Docker、数据库或 Redis。
7. 单独批准礼账子域名 DNS、80/443、公网 HTTPS、证书申请与续期安排。松屿根域和其他 Cloudflare Workers/Supabase 配置保持原部署。

不要求用户提供 SSH 私钥、sudo 密码或云 AccessKey。既有安全交互认证由用户自己控制；没有可用授权条件时记录阻塞，不放宽管理权限。

## 目录、权限与旧方案兼容

优先复用 `/var/www/lizhang-privacy`，部署前重新检查解析后的真实路径、挂载、所有权、链接及既有内容。建议最终结构：

```text
/var/www/lizhang-privacy/
  releases/<批准日期>-<commit>/  # 仅审核后的生产静态文件
  current -> releases/<批准日期>-<commit>
  acme/.well-known/acme-challenge/
```

目录及版本文件由 root 管理写权限；静态目录建议 root:root 0755、文件 0644，Nginx worker 只读，不授予写权限，不使用 777。`acme` 为独立挑战 Web 根；证书及私钥继续由 Certbot 在 `/etc/letsencrypt` 管理，私钥不进入 Web 根、上传包或 Git。普通用户上传到 `/home/adminuser/lizhang-upload/<release-id>` 等私有暂存目录（0700）；普通用户上传不自动获得生产发布权。

**首次切换的重要兼容条件**：旧 `ecs-ops/maintenance.sh` 会把 `current` 创建为真实目录，并在 `safe_path` 中拒绝符号链接。它还限定 `sites-enabled` 只出现既有托管站点，且会写入占位页。若实际已运行过该维护包，必须先清点、备份并保留原 `current` 及站点，再在单独审核的变更中转换成版本链接；不能直接在非空目录上执行链接覆盖。转换及公网启用后，不得再次直接运行旧维护包来“更新网站”，应先在 ecs-ops 审查与新布局及站点清单的兼容性。本轮不修改其 owner 标记或摘要。

系统配置备份、运维日志与 ecs-ops 既有 `/var/backups/ecs-ops/<run-id>`、`/var/log/ecs-ops/` 体系衔接；站点变更另外记录准确文件、旧指纹、版本与 current 指向，不能伪称本轮已生成服务器备份。

## 三个 Nginx 模板

- `nginx-loopback.conf`：候选内容的回环模板，沿用 `127.0.0.1:8080`、`/var/www/lizhang-privacy/current` 和原日志路径，增加真实 404 与禁止收录。与 `/etc/nginx/sites-available/lizhang-privacy-loopback` 是同一监听角色，须审阅后替换准确站点，不能把两个 default_server 并列启用。
- `nginx-http.conf`：仅礼账 `server_name`，HTTP-01 使用独立 `acme` Web 根，其余路径跳转固定 HTTPS 域名，避免使用任意 Host 构建跳转。
- `nginx-https.conf`：证书存在后使用的静态 HTTPS 模板；只允许 TLS 1.2/1.3，沿用目录、必要日志、静态 CSP 和安全头。无后端代理。`try_files ... =404` 与 `error_page 404 /404.html` 保留 404 状态，错误文档内部访问且 HTML 自带 noindex；不将未知路径回退为首页 200。

这三个配置均未运行 `nginx -t` 或服务验收。不存在服务器证书时，不启用 HTTPS 模板。公网模板不带回环模板的整站 `X-Robots-Tag`。不要从回环审核站点复制此响应头到生产配置。CSP 只允许本地 CSS/图片，无脚本、外部字体或表单。

首次 HTTP-01 签发时，先在**已获授权的** 80 站点中保留挑战路径，将其余 `location /` 暂设为 `return 404`，不公开审核文件。取得真实证书并完成后续用户验收后，才启用 HTTPS 模板及 HTTP 跳转。不得为启动 Nginx 使用自签证书冒充正式证书，或开放默认欢迎页。HSTS 等待实际 HTTPS 验收后另行评估，不预设 includeSubDomains/preload，以免影响主站。

## DNS 与最小网络变更清单

Cloudflare 仅新增/更新 `lizhang` 子域的 A 记录到**部署前核实的** ECS 公网 IPv4；初始方案使用 DNS only，保持 `Cloudflare DNS → ECS Nginx`。如改为代理模式，必须重新审阅证书路径、客户端 IP、日志与接入安排；不能默默改变部署链路。未核验 IPv6 时不新增 AAAA，也不新增 IPv6 监听或放行。

| 对象 | 下轮拟变更 | 执行前要求 |
| --- | --- | --- |
| 阿里云安全组 | 仅站点所需 TCP 80/443；公网可达范围按批准方案核定 | 先审计全部附属组/共享范围及规则 ID，记录原始规则；不清空规则，不修改现有 SSH 管理规则 |
| UFW / 实际主机防火墙 | 与 80/443 方案一致的准确规则 | 先核对真实启用状态、现有规则和 IPv6；历史 UFW 未启用不意味着可以直接 enable/reset；保持原管理连接策略 |
| TCP 22 | 本轮无变更；后续沿用已批准管理方式 | 可信来源、有效 SSH 配置和备用通道独立审阅，不为网站开放密码/root 登录 |
| 其他端口 | 无需新增 | 不开放 3000/5173/8080、数据库或 Redis 公网端口；8080 仅回环 |

本清单不是公网授权，也不是云安全审计结果。安全组优化与网站资料可独立推进，但公网发布前须具备所需审计和授权。

## 证书与日志

优先使用实际已安装且经过审核的 Certbot。后续可选 HTTP-01 webroot：域名为 `lizhang.songisle.xyz`，webroot 为 `/var/www/lizhang-privacy/acme`，公开联系邮箱为 `3173887065@qq.com`；CA 条款须由用户确认。不在本轮签发证书或执行续期 dry-run。DNS-01 需要额外 DNS 写授权，不能用它绕过边界。

复用现有 `certbot.timer`，不要创建重复 cron/timer。续期成功后通过 deploy hook 在 Nginx 配置校验成功时 reload；先检查现有 hook、路径、权限与重复调用。真实证书链、域名、HTTPS 跳转和续期均由用户在后续授权阶段验收。Certbot 当前官方文档可能高于 Ubuntu 既有包版本，实际可用参数以届时安装版本为准。

Nginx 模板沿用 `/var/log/nginx/lizhang-privacy-access.log` 和 `lizhang-privacy-error.log warn`。保留时间不在本轮猜测：需复核继承的 `log_format`、是否记录 URI 查询参数/IP/User-Agent/Referer、error/system/security 日志、`/etc/logrotate.d/nginx`、轮转频率/份数/压缩/备份及实际访问权限。复核后在法律元数据的 `website_log_scope`、`website_log_retention` 写入准确描述，将 `website_log_review_status=confirmed`，再重新审阅法律摘要。网站日志不包含 App 主动上传的账本；不据此声称服务器没有访问记录。

## 静态产物上传与发布

下一轮必须先确认批准仍对应当前 Git 提交、法律摘要与网站摘要。审核包 `dist/`、Debug APK 和源码不得放入公网目录；仅使用明确经过生产门禁生成的 `dist-production/`。`BUILD-INFO.json` 标记 mode 和摘要，不能靠将审核目录重命名为生产目录完成批准。

1. 在本地从已批准版本生成生产包，保留 commit、两份批准记录及产物 SHA-256。通过用户既有安全 SSH/SFTP 通道上传到 adminuser 私有暂存目录；不发送凭据，不新建管理账户。
2. 服务器上对照本地摘要读取上传包，检查包成员是否仅含 HTML/CSS/图片、robots 与构建信息，拒绝绝对路径、`..`、链接、设备文件或未知文件。确认目标释放到既定目录内，不能对未知归档直接 root 解压。
3. 管理员在已授权变更中创建新的 `releases/<release-id>`，设置准确权限；保留旧版本。确认生产模式、当前摘要和五页文件后再切换 current。不要覆盖正在服务的版本目录。
4. 首次从真实 current 目录转换为链接须按前文单独处理；已是版本链接后，可在同一文件系统、同一受控父目录建立临时链接，再原子重命名替换 current。切换前记录旧指向并验证两端解析路径都属于既定 releases；不递归删除/移动未知路径。
5. 只更新静态 current 通常无需重载 Nginx；配置变更才按运维流程在实际配置校验成功后 reload。所有浏览器、HTTP、设备与线上验收由用户执行。本轮没有执行上述任何步骤。

## 失败与回滚

保留前一已批准发布目录、文件摘要、旧 current 指向和本次精确配置备份。内容有误时，管理员在授权范围内将 current 原子指回先前**已核实**的版本，保留失败目录和记录供排查；首次目录转换则依其单独审阅的备份记录恢复，不提供通用覆盖整个 `/var/www` 的命令。

需要恢复 Nginx 配置时只恢复本次变更的准确文件，并确认当前指纹仍属于本次修改；配置校验成功后 reload。配置回滚不能被写成软件包降级或系统恢复完成；已运行的维护包按 ecs-ops 自身回滚报告处理。DNS 或安全组若后续曾获准变更，仅按保存的准确旧记录/规则 ID 回滚，不清空安全组、不扩大 SSH 来源、不重置 UFW。

本轮没有生产变更，因此没有已执行的服务器回滚操作，也没有当前可用性结论。

## 公安联网备案信息准备

| 信息 | 当前可整理内容 |
| --- | --- |
| 站点名称/用途 | 礼账 LiZhang；个人礼金记账工具的产品说明、法律文档和帮助官网 |
| 网站负责人/联系邮箱 | 杨松；3173887065@qq.com |
| 域名/ICP | lizhang.songisle.xyz；渝ICP备2026025309号-1 |
| 服务提供方式 | 计划阿里云中国大陆 ECS / Nginx 纯静态页面，无账号、表单、数据库及统计追踪 |
| 公网 IP、接入商、机房/接入信息、负责人核验材料 | 部署前按真实服务及受理平台要求核对；不公开个人住址、学校地址或身份证明 |
| 公安联网备案编号及链接 | 尚未取得，本轮不提交，不展示虚构编号。将来通过 `site.config.json` 的独立 publicSecurity 字段填写真实编号与官方链接 |

网站 ICP 与 APP 备案/上架材料分别核对。该表不是备案受理结果、资格证明或材料已齐全的结论。

## 参考资料

模板中的真实 404 保留方式参考 [Nginx core：error_page / internal / try_files](https://nginx.org/en/docs/http/ngx_http_core_module.html)；webroot 与续期成功后 hook 参考 [Certbot 官方使用说明](https://eff-certbot.readthedocs.io/en/stable/using.html)。这些是文档依据，不是本轮对服务器执行的校验。
