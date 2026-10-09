# 礼账文档站

这是与松屿主站项目隔离的静态网站目录，目标域名为 `lizhang.songisle.xyz`。当前是本地审核稿，未部署到阿里云；不会修改 `songisle.xyz` 的 Cloudflare Pages、根域解析或主站源码。

## 文档审核入口

- [隐私政策](content/privacy.md)
- [用户协议](content/terms.md)
- [帮助中心](content/help.md)
- [常见问题](content/faq.md)
- [联系开发者](content/contact.md)
- [应用行为与代码证据](docs/应用行为审查.md)
- [备份与提醒代码证据](docs/备份提醒审查.md)
- [服务器部署与备案前后清单](docs/服务器部署说明.md)
- [验证结果与未验收边界](docs/测试结果.md)
- [Android 上架前独立修复事项](../docs/上架前修复事项.md)

本轮候选版本为 Android `1.0.0-rc3 / 23`，从已验收 RC2 提交 `d0a71a893ce662a44ab7b8a9e9f3f174e51bad42` 整合文档站。运营者、联系方式、生效日期、未成年人安排和网站日志保留等均待本人确认，法律全文保留真实候选审核稿状态。App 中可离线查看同一隐私政策、用户协议与帮助全文；加载失败时不能在首次告知中确认。

## 本地使用

需要 Node.js 20 或以上。无第三方 npm 依赖，无需 `npm install`。

```powershell
cd D:\AndroidProjects\LizhangApp\website
npm run build
npm test
npm run preview
```

浏览器访问 `http://127.0.0.1:4173/`。预览只监听本机，按 Ctrl+C 停止；不会开放服务器公网端口。在另一终端执行 `npm run test:http` 可验证 HTTP 内容与响应头。这不是手机或电脑浏览器的渲染验收。

`../app/src/main/assets/legal/{privacy,terms,help}.md` 是三份共享全文的规范维护源，`content/` 中同名文件严格镜像；网站导航、常见问题和联系说明可以独立维护，但不能与规范全文矛盾。审核并修改规范正文、告知资源或批准资料后，在仓库内执行 `npm run legal:sync` 更新镜像和摘要，再运行 `npm test`。同步只复制真实状态，不产生批准、不删除候选标记。`npm run legal:check` 对照 Android 源文件和批准资料，发现任一偏移立即失败。

`legal/` 保存 App 法律元数据、公开批准资料镜像及 Android 内容输入清单，覆盖完整正文、七语言告知资源和候选提示资源。网站构建验证镜像文件哈希与清单整体 SHA-256；在完整仓库内还会逐项对比 Android 真源。单独复制 `website/` 后，无需 Android 工程、Gradle、第三方 npm 依赖或网络即可构建，独立镜像校验仍会运行。

`scripts/build.mjs` 生成六个页面、404、静态资源和 SHA-256 文件清单到 `dist/`；`dist/` 不提交到 Git。清单记录应用版本、政策标识、审核基线和 Android 内容摘要。正式服务器仅上传获批的 `dist/`，不上传文档源文件、批准配置、部署脚本或应用源码。

## 网站设计与信息处理

网站采用暖白背景、蓝橘点缀和清楚的阅读层次。手机使用单列布局，电脑使用正文与目录两列布局；包含跳过导航、键盘焦点、可滚动表格、减少动画偏好和打印样式。字体、CSS、SVG 均为本地资源。网页无需 JavaScript，不使用广告、统计、登录、数据库、Cookie、浏览器存储或在线表单。

浏览器访问服务器仍会产生 IP 与 HTTP 请求信息。Nginx 模板关闭访问日志；错误日志和系统安全日志的最终保留规则待确认。`noindex` 和 robots 禁止抓取用于审核状态，不能替代 loopback 监听、安全组和防火墙访问限制。

## 备案前与正式构建

普通 `npm run build` 始终输出审核版。正式构建为 `node scripts/build.mjs --public`，当前会明确拒绝：必须先完成文档审核，补齐非空主体、有效邮箱、有效生效日期及备案号，并在 `site.config.json` 确认 `reviewStatus: approved` 和严格布尔值 `icpApproved: true`。字符串 `"false"` 或数字 `1` 不算备案批准；正式域名固定为 `lizhang.songisle.xyz`。这些配置不是备案证明，须先获得真实备案结果与用户批准。

正式网站还必须使用 App 同一份公开批准记录：`legal/metadata.properties` 与 `legal/legal-approval.properties` 的批准状态、主体、邮箱、生效日期、政策版本、备案资料、审核者及未成年人安排完全一致；批准摘要须等于当前 Android 内容输入摘要。`website_content_sha256` 另外覆盖网站配置和全部 Markdown 正文，网站增加帮助、FAQ 或联系说明后，同样需要重新审查并批准摘要。网站配置必须与这份记录一致，应用版本须为 `1.0.0`，版本代码须匹配获批记录且大于已确认实际分发历史中的最高代码。修改正文、App 告知译文或批准字段后，旧摘要会失效；只把网站配置改为 approved 不能批准任何新内容。`npm run legal:sync` 与 `npm run legal:check` 会显示网站摘要，供本人核对，不写入批准字段。

`filing_record` 对应政策中的网站公开备案资料；APP 备案或适用的发行材料另外记录于批准文件 `app_filing_status` 与 `app_filing_material`，正式构建要求本人已确认这两项。网站ICP备案不能替代 APP 材料，构建脚本不自行判定单机应用是否适用某种处理方式。

正式正文必须人工定稿。原文或渲染后的可见正文中保留“审核稿”“尚未生效”“尚未通过审核”“本稿”“未确认”“待填写”等状态说明时，构建直接拒绝，不会自动删除这些文字来伪造审核结论。当前 `content/` 和 `site.config.json` 仍是真实待确认状态，本轮未填写虚构主体、邮箱或备案信息。所有页面通过预校验后才替换 `dist/`；校验失败会保留原产物，不生成半套正式页面。

有效定稿的正式构建中，首页、五个正文页及 404 均不显示审核横幅或审核页脚。正常页面提供 HTTPS canonical 并允许收录，404 仍保持 `noindex,nofollow` 且不输出 canonical。`npm test` 用专用临时目录中的合成样例验证正式通过与拒绝路径、摘要失效、两侧内容偏移及独立网站构建，不改真实政策或审批配置，不连接服务器，也不发布测试产物。单独网站运行测试时，仅跳过缺少 Android 真源的仓库对照项；网站自带镜像校验和正式批准测试照常执行。

部署脚本只负责用户授权后的受限服务器初始化、静态版本更新和回滚。正式 HTTPS 模板与后续上线清单供人工审查，脚本不会自动修改 DNS、签发证书或开放 Web 端口。

## 当前交付边界

没有可识别的已授权 ECS 连接地址、账户和密钥 agent，尚未读取服务器状态。需要补充的是服务器地址、SSH 端口、允许的管理员来源、部署操作授权，以及用户在本机安全绑定的 SSH agent 与已核验 host key；不需要阿里云主账号密码、AccessKey Secret 或 SSH 私钥。

Windows 可以构建和预览网站；上传脚本采用 Linux/WSL 的 GNU tar 与 OpenSSH，服务器端采用 Ubuntu 24.04。脚本语法和归档安全检查不等于已完成真实 Ubuntu 部署，详见测试结果。

## 分支整合边界

文档站两次有效提交 `9a0fde4` 与 `9ae6412` 已整合到 `release/v1.0.0-rc3`，保留 RC1、RC2 已验收 Android 改动。本轮不修改 `main`、线上网站、阿里云服务器、DNS、备案或松屿 Cloudflare 主站；候选产物供审查，正式操作仍须本人批准。网站历史审查记录保留其原始基线，RC3 验证结论与剩余发布事项以仓库 `docs/v1.0.0正式发布清单.md` 为准。
