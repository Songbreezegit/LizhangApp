# 礼账最简官方网站

目标域名：`lizhang.songisle.xyz`。当前仅供本机预览，ICP 备案仍在审核，未部署到公网。

## Windows 本机预览

需要 Node.js 18 或以上，无 npm 依赖，无需安装包。在本仓库根目录的 PowerShell 执行：

```powershell
node website/scripts/build.mjs
node website/scripts/preview.mjs
```

浏览器打开 `http://127.0.0.1:4173/`，按 Ctrl+C 停止。端口占用时可执行 `node website/scripts/preview.mjs 4174`。预览始终只绑定 `127.0.0.1`，不提供绑定公网地址的选项。也可在 website 目录执行 `npm run build` 和 `npm run preview`。

构建产物位于 `website/dist/`，包含首页、`/privacy/`、`/terms/`、`/help/`、`/contact/`，以及静态样式、品牌图片和 404 页面。请通过本机 HTTP 预览，直接双击 HTML 的 file 地址无法正确解析站内绝对路径。生成目录不提交 Git。

## 文档唯一来源

隐私政策、用户协议和帮助页完整读取 `app/src/main/assets/legal/privacy.md`、`terms.md`、`help.md`。网站联系资料读取同目录 `metadata.properties`，不要另外维护一套网站政策或联系信息。

构建会核对文档内容标识、法律元数据、Android `LegalPolicy.CURRENT_VERSION` 和既有发布批准文件中的候选版本是否一致。政策实质更新时须同时更新这些版本，使旧版本确认记录失效、升级用户重新看到告知；不得通过修改版本直接批准正文。

Markdown 仅支持本文使用的标题、段落、列表、表格、粗体、代码和链接。原始 HTML 和全部文本都会转义，链接仅允许已知站内页面或无账号凭据的 HTTPS 地址；不执行嵌入脚本。新增文档语法时应明确扩展构建器，不依赖浏览器执行 Markdown。

品牌图片由构建器从当前 Android 的 `launcher_cat.png` 与 `home_hero_cat.png` 复制，不引入外部图片服务或另一份素材源。样式沿用应用浅色、浅杏与蓝紫色调。

## 本地与发布边界

交付内容为纯静态 HTML、CSS 和本地图片，无运行时 JavaScript、后端、数据库、登录、广告、统计、第三方字体 CDN、Cookie 或浏览器存储。Node 仅用于本机生成和预览，不是正式网站后端。预览不记录访问日志。

本构建只生成带明确本地预览状态及 noindex 的候选页面，不提供正式构建或部署参数。既有 Android 正式发布门禁保留，`release/legal-approval.properties` 的 `approval_status=pending`，离线元数据仍为待批准。没有引入新的签名、复杂版本发布或部署系统。

本轮不操作阿里云、Cloudflare、DNS、备案系统、公网 80/443 或 songisle.xyz 主站。实际响应式效果、浏览器及设备操作由开发者手动验收；未运行自动化测试。

## 备案通过后修改页脚

1. 核对备案通过通知中的实际网站备案号及域名，不能用“审核中”替代备案号。
2. 编辑 `website/site.config.json`，将 `icp.approved` 改为 `true`，把 `icp.number` 的 `null` 改为本人核实的真实号码字符串。当前状态为 `false/null`。
3. 重新执行静态构建。页脚会显示真实号码并链接到工信部备案查询首页 `https://beian.miit.gov.cn/`；审核中状态不会生成备案链接。
4. 这只更新本机页脚，不会部署、修改 DNS 或解除文案批准门禁。正式公开前仍需确认全部运营与法律资料，核对实际服务器日志并更新政策，审阅后移除本地预览与 noindex 标记，再另行授权独立发布。

网站 ICP 与应用备案或其他上架材料分别核对，网站备案通过不能替代应用侧所需确认。
