# 礼账纯静态官网

官网已完成首次生产部署，域名为 `lizhang.songisle.xyz`，运营者为杨松（个人），邮箱为 `3173887065@qq.com`，ICP 备案号为 `渝ICP备2026025309号-1`。

**官网 v1.1 已按用户本轮明确授权完成生产静态版本切换**，服务器记录时间为 2026-10-20 23:22:15（北京时间）。批准的 UI 源提交为 `a60e9fef7b5aa57ca262a64fc979b1fda086664c`，原部署交付基线为 `42bbde5dc5b05d04b3ada3179ee31f05ab88dce9`；本轮独立发布记录分支为 `deploy/website-mobile-ui-v1.1-20261020`，不合并 main。见 [v1.1 生产发布记录](docs/生产发布v1.1-20261020.md) 和 [逐文件摘要](docs/生产文件摘要v1.1-20261020.txt)。

以下内容保留 v1.1 审核阶段的设计与预览说明。有关“尚未发布”“待批准”“本轮不执行生产构建”的表述均描述此前审核阶段；当前生产状态以上述发布记录为准。用户在本会话随后明确授权固定 UI 版本发布，本轮仅更新网站批准摘要，原法律批准、生效日期、日志与备案核对状态均保留。**生产切换完成不等于产品验收通过，全部产品测试仍由用户本人负责。**

## v1.1 视觉规则

采用已安装的 `frontend-design` Skill，保留现有 Logo、小猫插画、浅蓝 / 杏橘 / 奶白配色、柔和背景及半透明圆角卡片。

- 使用设备本地中文无衬线字体，无字体 CDN；正文 16px / 1.72，移动法律正文 15.5px / 1.75，功能说明 15px，正常字距，不禁用浏览器缩放。数值以默认 16px 根字号为例，实际使用 rem 随字体设置缩放。
- 功能卡片在小于 48rem 时单列，48rem 起两列，68.75rem 起四列；序号和标题同排，说明使用完整行宽，卡片由内容决定高度。
- 小于 64rem 的文档目录采用默认收起的原生 details / summary，展开后为单列。64rem 起显示左侧 sticky 目录。两种导航使用同一组已有锚点，由 CSS 互斥显示，不复制正文 ID。
- 手机品牌独立成行，五个导航入口在自身区域水平滚动；保留当前页面选中态和键盘焦点。
- 小猫以原图等比显示并靠右取景，消化左侧透明留白，取消固定宽度和负偏移；手机插画宽度随容器变化，桌面保持文字与插画并排。
- 收紧备份区、法律入口和页脚间距；保留普通备份 / 表格未加密、恢复覆盖账本、提醒与设置不在备份中的全部提醒。
- 联系页突出运营者、隐私联系邮箱和邮件按钮，保留反馈要素、隐私请求及异议处理说明。政策日期、版本和未成年人规则继续由正式法律文档提供。

完整规则、静态审阅范围及后续项目见 [v1.1 设计与交付记录](docs/移动端视觉与阅读体验v1.1.md)。

## Windows 审核预览

需要已安装的 Node.js 18 或以上版本，无 npm 依赖。在仓库根目录执行：

```powershell
node website/scripts/build.mjs --review
node website/scripts/preview.mjs
```

第二条命令由用户自行执行。等终端显示“服务已启动”后，在同一台 Windows 电脑的浏览器打开终端显示的地址。默认地址为 `http://127.0.0.1:4173/`，备用端口使用 `node website/scripts/preview.mjs 4174`。保持窗口打开，按 Ctrl+C 停止。本轮未启动预览服务，地址未经访问验证。

生成完整 Windows 审核 ZIP：

```powershell
powershell -NoProfile -File website/scripts/package-preview.ps1 -ArchiveName Lizhang-website-mobile-ui-v1.1-review-20261020.zip
```

ZIP 位于当前独立工作区的 `build/deliveries/privacy-entry-preview/`，包含 `dist/`、`scripts/preview.mjs`、`START-PREVIEW.cmd` 和 `README.txt`。如同名包已存在，改用新文件名，脚本拒绝覆盖。完整解压后双击 START-PREVIEW.cmd，再按启动窗口说明打开浏览器。启动器依赖本机 Node.js，不内置或自动安装运行时，不修改防火墙，不自动启动浏览器。

`127.0.0.1` 指向浏览器所在的电脑。不能直接双击 dist 内 HTML，站内路径需要本地 HTTP 服务。预览仅监听回环地址，不开放公网。

## 正文、批准与生产保护

隐私政策、协议和帮助的唯一正文仍是 `app/src/main/assets/legal/` 下的 Markdown，共享公开资料来自该目录 `metadata.properties`。官网访问补充说明来自 `website/content/privacy-website.md` 与 `privacy-website.properties`。本轮没有修改上述正文、章节、元数据、生效日期、法律批准或实际日志事实。

`website/site.config.json`、`release/legal-approval.properties`、`website/scripts/legal.mjs`、`website/scripts/markdown.mjs` 及 `website/deploy/` 保持基线内容。域名、备案号、部署复核和原法律批准不重新填写，也不重置。未操作 DNS、HTTPS 证书及续期、安全组、SSH、Nginx 或线上站点。

**build.mjs 和 style.css 参与网站输入摘要计算。本轮修改后，原 productionApproval.approvedSiteSha256 不再批准当前页面。** 审核构建计算的新摘要只是候选输入标识，不代表用户批准；原批准摘要和批准日期保留原值。本轮不尝试生产构建、不修改门禁、不用旧产物交付新版本。用户完成视觉验收后，须明确批准新的准确网站摘要，再另行安排生产构建和部署。

审核输出位于 `website/dist/`，包括五个页面、404、robots、本地 CSS / 原品牌图和 `BUILD-INFO.json`。页面保留审核横幅与 noindex，robots 禁止抓取；即使原配置含历史 approved，也不会成为可部署产物。不要上传审核目录或改名为生产目录。

既有生产构建流程、干净输出与内部构建信息隔离逻辑没有改动。生产部署资料见 [部署说明](deploy/README.md)；本文及 v1.1 审核包不授权任何生产操作。

## 交付与验收边界

本次只修改 website 下的页面生成、CSS 和必要说明；未修改 Android 代码，不重新生成 APK，不新增前端框架、依赖、运行时 JavaScript、表单或云服务。不合并 main，不改部署分支，不发布公网。

仓库中找到的是品牌资源与截图测试基线，未找到用户明确批准可公开使用的真实 App 截图。本轮不使用这些基线替代实机素材，不编造截图、不增加空白区；待用户提供经过脱敏且允许公开的真实截图后另行迭代。

本会话未提供此前五张实机截图，本轮依据用户任务说明实施；若需逐图对齐，请将原图补充到会话。

**本轮仅完成源码修改与审核包构建，没有执行任何测试、Lint、浏览器验证、实机验证或生产部署。全部测试和最终验收由用户本人进行。**
