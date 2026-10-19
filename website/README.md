# 礼账纯静态官网

域名 `lizhang.songisle.xyz`；个人运营者杨松，联系邮箱 `3173887065@qq.com`，网站 ICP `渝ICP备2026025309号-1`。本轮尚未部署，实际生效日期、法律正文批准、接入/服务器和日志复核仍待用户完成。旧交付文档中的“备案审核中”属于历史记录，当前状态以共享元数据和本说明为准。

## Windows 审核预览

需要 Node.js 18 或以上，无 npm 依赖。在仓库根目录执行：

```powershell
node website/scripts/build.mjs --review
node website/scripts/preview.mjs
```

必须先启动上述预览进程，等终端显示“服务已启动”，再由用户在同一台 Windows 电脑打开终端显示的地址。保持进程窗口打开，Ctrl+C 停止；停止后回环地址不可继续使用。备用端口为 `node website/scripts/preview.mjs 4174`。本轮没有启动服务、浏览器或执行 HTTP 验收，不提供已验证可访问的平台预览入口。也可在 website 目录执行 `npm run build:review` 和 `npm run preview`。

需要可解压交付包时，在仓库根目录构建后执行 `powershell -NoProfile -File website/scripts/package-preview.ps1`。ZIP 位于 `build/deliveries/privacy-entry-preview/`，包含完整 dist、`scripts/preview.mjs`、`START-PREVIEW.cmd` 与简明 README.txt；有同名包时拒绝覆盖，可用 `-ArchiveName 新文件名.zip`。用户完整解压后双击 START-PREVIEW.cmd，先看终端启动结果再打开浏览器。脚本不安装 Node、不修改防火墙、不启动浏览器、不访问公网。

`127.0.0.1` 指向浏览器所在的电脑；远程执行环境中的回环地址不能当成用户 Windows 的地址。未启动本机服务时只打开链接可能收到 ERR_CONNECTION_REFUSED。本包交付不表示当前已存在可访问的预览服务，也不是公网官网部署。

审核产物位于 `website/dist/`，包含五个页面、404、robots、本地 CSS/品牌图及 `BUILD-INFO.json`。审核横幅、noindex/禁止抓取仅用于审核交付，审批状态不进入法律正文；即使批准字段后来填完也不会变成生产产物。不要把审核目录改名后上传公网。直接双击 HTML 无法正确解析站内绝对路径，应使用本机 HTTP 预览。

## 唯一正文与配置关联

隐私政策、协议、帮助的唯一正文分别为 `app/src/main/assets/legal/privacy.md`、`terms.md`、`help.md`。共享公开资料在同目录 `metadata.properties`，生效日期仅在 `effective_date` 配置一处。Android 的 `generateOfflineLegalAssets` 在构建时用普通字符串替换展开模板并校对版本，网站构建从同一源生成网页；缺值、残留标记或版本不符时拒绝生成。APK 的 assets/legal/*.md 来自已展开资源，许可等其他资产原样保留，Android 运行时不再解释元数据或模板正则。

`LegalPolicy.CURRENT_VERSION`、元数据与 `release/legal-approval.properties` 的 `policy_version` 一致，本次更新为 `legal-copy-policy-20261019`；内部版本不显示在正文或法律页中，公开文档版本为 `1.0.0`。实质内容更新须同步内部版本，沿用既有确认记录逻辑重新告知升级用户。首次告知在同一卡片内滚动显示完整隐私政策和用户协议，两份正文末尾再显示两个 App 内全文链接；全文读取故障不会禁用同意按钮。只有主动同意且确认记录保存成功才能进入业务页面，独立全文页返回原滚动位置，告知页拒绝/返回退出，既有账本和业务初始化保护保留。旧文档中的摘要版布局与“文档读取失败不能进入”分别是历史行为。

源文档保留 `{{effective_date}}` 模板；共享元数据暂为 `effective_date=pending`。审核构建省略日期整行，联系页也省略未填的日期，不把模板、pending 或“尚未生效”渲染为正文。正式 Android / 网站构建均要求真实、严格的 YYYY-MM-DD 日期及相应本人批准，不能借审核模式发布生产版本。

官网访问的专用说明位于 `website/content/privacy-website.md`，日志事实唯一配置为同目录 `privacy-website.properties`，包含实际范围、保留及删除规则、必要服务提供方及其处理范围。只有日志状态为 confirmed 且各字段有效时才生成该说明，并接到网站隐私政策正文末尾；不进入 Android 资产。尚未核实时，审核页在法律正文外展示交付提示，生产构建拒绝。网站专用正文及配置均纳入网站批准摘要，通用隐私政策仍只有 Android 资产内的一份。

`website/site.config.json` 保留原有 `domain` 和 `icp.approved/number` 数据结构，现为已取得真实 ICP 编号的 `true/渝ICP备2026025309号-1`；构建同时核对元数据及批准配置中的号码和域名。所有正常公开页面页脚统一从同一配置显示编号并链接工信部。`publicSecurity` 为独立未来入口，尚未取得编号时 number/url 都为 null，不展示公安备案号。

共享元数据中的 `operator_details_status=confirmed` 仅表示运营者公开资料已确认。法律正文两个 `approval_status`、发布批准日期/人/摘要、签名身份等仍 pending，不能互相代替。网站 ICP 与 APP 备案资料及 Android 签名身份分别核对。

七语言法律页仅展示阅读说明和公开版本，移除未使用的首次告知审批提示。禁止发布标记规则保持不变，七语言资源仍纳入法律内容摘要与正式发布扫描；内部审批资料继续阻止未批准发布。`scan-unapproved-legal-content.ps1` 的旧 `VerifyCandidateCoverage` 开关兼容保留，改为检查现行阅读提示齐备且不含审批措辞。`verify-rc2-release.ps1` 改为对比构建时展开的 Markdown 资产。两份核验脚本本轮均未运行。批准输入通过 `.gitattributes` 统一 LF，避免 Windows/Linux 换行差异改变摘要。

## 正式批准与生产构建（本轮不执行）

下面步骤仅供用户完成验收和部署复核后使用，不代表批准已发生：

1. 在共享元数据填写本人确定的真实 `effective_date`（严格 YYYY-MM-DD）；在 `website/content/privacy-website.properties` 据实填写网站日志范围、准确保存期限和删除规则、必要接入方及其处理范围，实际复核完成后才将 `website_log_review_status=confirmed`。公开政策/协议版本为 `1.0.0`，实质变更时同步内部告知版本。
2. 核对真实法律全文、七语言提示和功能后，由用户本人填写元数据及 `release/legal-approval.properties` 的法律批准状态、相同 reviewed_by、effective_date、真实 approved_at。整份法律正文批准必须为 approved；本轮不代填。
3. 只有核对备案域名、主体、实际接入和服务器现状后，才将 site.config 的四个 `deploymentReview.*Status` 设为 confirmed，填写真实 reviewedBy/reviewedAt；用户还应完成 [部署准备清单](deploy/README.md)。本文不授权 ECS、DNS 或公网操作。
4. 先运行审核构建，读取输出的 `legal_content_sha256` 与 `site_content_sha256`，审阅这组准确输入。法律摘要与现有 Gradle/PowerShell 使用相同路径、字节、逐文件 SHA-256 和排序；可按需用原摘要工具计算，摘要计算不是批准。
5. 将本人批准的法律摘要写入 `release/legal-approval.properties` 的 approved_content_sha256；另在 site.config.productionApproval 填入 approved、真实 reviewedBy/approvedAt 和 approvedSiteSha256。网站摘要绑定法律摘要、网站源码、官网访问补充说明及日志配置、公开配置、Nginx 模板及品牌图，排除 productionApproval 自身以免自引用。它不是 zip/APK 文件哈希。
6. 批准记录和摘要都齐全后，在仓库根目录执行 `node website/scripts/build.mjs --production`（或 website 目录 `npm run build:production`）。产物在独立 `website/dist-production/`。缺少批准、真实日期、日志/接入复核或摘要不符会在写入产物前拒绝；失败不清除磁盘上可能存在的旧生产目录，切勿把旧目录误当作本次构建。正式交付时核对新构建输出和 BUILD-INFO。

生产正常页有 HTTPS canonical，无审核横幅且不强制 noindex，robots 允许抓取；404 仍禁止收录，HTTP 404 状态由本地预览和 Nginx 模板按各自配置提供。构建不会启动/部署服务器或自动修改批准。**本轮未尝试生产构建，也未执行门禁的正反例测试；实现仅作源码静态审阅。**

网站不要求填写 Android 签名指纹来构建网页；原 Android `officialRelease=true` 的备案、签名身份与指纹门禁仍独立保留。仅有网站批准不能使 Android 自动变为正式版本。

网站日志已从应用通用正文分离，Android 正式门禁不再要求网站日志配置；网站生产门禁独立要求准确日志和接入事实。本轮均未授予批准、运行生产构建或验证门禁。

## 隐私与交付范围

保留原页面布局、CSS 和品牌图片，未重构 UI。正文采用安全 Markdown 子集，原始 HTML 和普通文本转义，链接只允许既有站内页面或不含账户凭据的 HTTPS 地址。图片继续从 Android 绘制资源读取。

无运行时 JavaScript、账号、云数据库、表单、广告、统计、Cookie/浏览器存储或第三方字体 CDN。Node.js 仅用于本地构建/预览。正式 Nginx 可能处理 IP、请求及错误/安全日志，具体日志以实际部署复核后的配置和隐私政策为准，不能声称服务器绝对不记录访问。

新部署/回滚资料见 [website/deploy/README.md](deploy/README.md)，复用 ecs-ops 目录与维护边界。两种生成目录、APK、密钥及凭据均不提交 Git。本轮仅交付审核静态包和 Debug APK；未测试，等待用户验收，不合并 main、不发布标签、不改主站、不操作生产环境。
