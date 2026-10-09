# 礼账 v1.0.0 RC4 自动化验证报告

日期：2026-10-10。分支：`release/v1.0.0-rc4`。远端最新 RC3 与已验收提交一致：`359b33626bb0d33e72ed0aa81ce214fa09ed56d6`，RC4 从该提交直接创建。完整交付 SHA 见最终交付回复和远端分支；本报告不把技术检查当作政策、备案、设备或商店批准。

## 冻结与修改范围

- 常规 `com.yangsong.lizhang / 1.0.0-rc4 / 23`；正式 `1.0.0` 保留 `officialRelease=true` 与独立内容批准检查，正式 versionCode 仍由本人核对实际分发历史。
- 网站 `icpApproved=false`、`reviewStatus=draft`；根及网站批准镜像中的网站备案、独立 APP 材料、签名身份与内容批准继续 pending。审核版页脚为普通“ICP备案审核中”，没有虚构号码或正式备案查询链接。
- 修复共享规则不识别“备案审核中”的缺口；正式批准场景也拒绝把该状态当作备案号码。新增缺号、网站未确认、网站完成但 APP 材料未确认，以及只改网站号码的拒绝回归。
- 三正文只更新 RC4 候选标识、RC3 审查基线和真实待备案描述，App/网站逐字节镜像；元数据、七语言候选提示、批准镜像与摘要重新校验。候选政策版本更新会按现有规则要求重新明确同意，不清空账本或引导状态。
- 签名支持自定义实际工具路径；拒绝非法版本/别名、仓库内或经过重解析点的密钥/输出、脏源码、旧输入和未批准资料。先签 `signing-pending.apk`，签后检查和证据 JSON 准备完成后最后重命名，失败不产生最终名称的 APK。
- 删除 4 个未被代码、测试或动态资源读取引用的旧头像 PNG、未使用的旧单色图标 XML，以及 7 语言中 8 个仅有定义的普通字符串（56 条）。实际桌面图标不引用被删单色图，当前头像使用姓名文字，不改变页面视觉。
- 删除旧 RC2 APK 验证入口，RC3 入口升级为 RC4；历史签名说明明确归档并指向当前流程。全部有效 JVM、仪器、截图测试和专项设备验证脚本保留。`home_hero_cat`、`home_record_hint`、`contacts_first_hint` 虽在 Release lint 中提示无引用，但仍用于仪器回归，保留。

对 RC3 的保护核验：`app/src/main/java/`、`app/schemas/`、主 Manifest、`website/deploy/` 均无差异。900ms 启动动画、CSV 公式防护、Room、备份格式、业务页面和现有网络边界保留。原 `D:\AndroidProjects\LizhangApp` RC2 工作区的 README 草稿、旧隐私草稿、网站文件和附件未重置、删除或暂存。

## Android 检查

JDK 17、既有 SDK 36；TEMP/TMP 和 UnixDomainSocket 临时目录仅设置在本次构建进程，路径为工作树 `build/rc4-check/tmp`，没有修改全局环境。

```powershell
.\gradlew.bat --continue --no-daemon --console=plain --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' test lint lintRelease assembleDebug assembleRelease assembleDebugAndroidTest :app:printLegalContentDigest
```

| 检查 | 实际结果 |
| --- | --- |
| Debug JVM 全量单元测试 | 32 类、198/198；失败、错误、跳过均 0。 |
| Release JVM 全量单元测试 | 32 类、198/198；失败、错误、跳过均 0。两变体合计 396，独立用例数 198。 |
| lint / lintRelease | Debug 0 错误、33 警告；Release 0 错误、38 警告，均无提示。没有新增 baseline、降低严重度或删有效断言。 |
| assembleDebug / assembleRelease | 两轮完整链成功；候选 Debug 仅调试身份，Release 未签名。最终 APK 静态结果见产物表。 |
| assembleDebugAndroidTest | 仪器源码编译和独立测试 APK 打包成功；设备执行 0 项，未验收。 |
| 首次完整任务链 | BUILD SUCCESSFUL，8 分 36 秒，154 个任务全部执行，日志 `gradle-full.log`。 |
| 最终完整任务链 | 删除最后一个无用图标并恢复候选后，BUILD SUCCESSFUL，6 分 40 秒，154 个任务（57 执行、97 已更新），日志 `gradle-final.log`；两种 JVM 测试重新执行且均通过。 |

首次告知、明确同意、拒绝与加载失败重试已做源码复核，并包含在全量 JVM 验收中：`PrivacyConsentViewModelTest` 9 项验证两份全文加载、明确点击和保存成功才放行；失败或只有一份正文时不能同意；重试成功不自动同意；拒绝/重建不放行；写盘失败仍拦截。`PrivacyConsentPolicyTest` 6 项覆盖版本门禁。MainActivity 在同意前不挂载业务导航，AppContainer 对账本、通讯录和提醒处理保留检查。UI 与真实设备的触摸/窗口流程本轮没有执行；相关仪器测试只编译打包。

## 法律、网站与门禁

- App 与网站 `privacy.md`、`terms.md`、`help.md` 字节完全一致；元数据和批准记录镜像一致。Node、PowerShell 与 Gradle 交叉核对 25 个 Android 法律输入和 6 个网站内容/配置输入。
- 网站 `npm test` 98/98，通过、0 失败、0 跳过；候选构建 12 文件。正式合成通过及拒绝测试包含“审核中”不能作为号码、网站与 APP 材料独立和批准摘要变化。
- 本地 preview 的首页、五文档、样式/插画/robots 返回 200，缺失页 404；HEAD、POST 限制和 noindex/CSP/nosniff/no-referrer/no-store 检查通过。只使用 127.0.0.1 的本任务端口，结束后确认服务已关闭。
- 真实网站 `--public` 退出 1，拒绝当前备案和未批准文案；没有覆盖候选站点、构建或部署正式站点。
- 独立无 Android 插件的隔离工程调用同一生产 Gradle 门禁：28/28，通过；合成批准仅在临时目录，没有替换真实资料，也没有生成正式 APK。
- 真实 Android `'-PofficialRelease=true' '-PofficialVersionCode=24' assembleRelease` 退出 1，在 `verifyOfficialReleaseContent` 因真实 `approval_status=pending` 拒绝，候选 Release 摘要前后相同。24 只用于负向检查，不是本人确认的正式代码；缺备案号、审核中号码及网站/APP 分离的具体拒绝另由生产门禁合成回归验证。
- 首次负向检查的本机中文诊断被错误解码，报告匹配未成功；实际 Gradle 已在批准门禁拒绝。改以 task、公开字段与退出状态校验后重跑确认，保留首轮日志，不把报告脚本失败当作正式构建通过。随后恢复并复跑默认候选完整链。

```text
Android legal_content_sha256（25 输入）
951b9920a88dff7ee0062cc6190571c0479da7e6bfb7247a799075307e8cd04e
网站 website_content_sha256（6 输入）
71c96ffb927a52fa181ff365d5582323c2e50cee128871a0c370928976d1472c
```

法律输入保持 LF。修改任何法律正文、告知/翻译、元数据、网站配置或 FAQ/contact，都必须重新同步、计算并由本人批准对应摘要，不能只改页脚。

## 签名准备与商店交付

公开 `signing_identity_status` 与 `signing_certificate_sha256` 仍 pending，无法确认已授权的正式证书身份。仅检查当前仓库安全文件名，Git 跟踪清单没有正式密钥或私有签名属性；未扫描个人磁盘、读取密码、创建新密钥或执行真实签名。Debug 证书不代表正式身份。

签名脚本隔离回归最终 42/42，通过，调用生产脚本副本，原生命令由内存 mock 实现，输入是不可安装的合成 ZIP，未使用真实私钥或密码。覆盖输入、路径、工具、门禁、版本/权限/测试隔离/内容绑定、证书、对齐、签名、签后错误、源码变化、记录写入和最终重命名失败；所有拒绝路径没有最终名 APK，非法输入在独立进程退出 1，mock 恢复检查通过。独立复核曾确认旧实现签后失败遗留最终名文件，补强后只读核验最终名仅成功案例存在；此项已关闭。合成签名通过不能证明真实正式证书可用或设备覆盖升级通过。

独立 [商店交付目录](../store-listing/v1.0.0/README.md) 含 8 份文档：应用文案、权限隐私、两平台图标/截图规格、5 张功能宣传规划、审核流程、材料联系方式和官方来源。官方公开要求与制作建议、登录后后台待核对项分开记录。没有宣传云同步、账号、OCR、图片上传或准点提醒保证；没有使用真实账本素材。成品图标和设备截图未制作，属于后续正式包素材准备。

## Git、证据和停止边界

`origin/main=6493392ca6740aa7314e4e10d1c97c7931849b03` 是 RC3/RC4 的祖先。RC3 相对 main 已包含 14 个整合提交（含文档站）；RC4 直接继承，未重复合并。后续审查可在 main 未前进时选择 fast-forward 或 PR merge commit；若 main 前进，另行在批准的整合分支处理并重跑受影响检查。详见 [正式发布清单](v1.0.0正式发布清单.md)。本轮不合并 main、不强推、不创建正式 Release、标签或商店提交。

证据保存在工作树忽略的 `build/rc4-check/`、`build/signing-script-regression/` 和 `app/build/`：完整 Gradle 日志、JUnit/lint 汇总、两类真实拒绝日志/JSON、28 项门禁报告、42 项签名回归报告、网站测试/构建/本地 HTTP 日志和 APK 核验/摘要。合成数据和二进制不提交 Git。

预期官方链接为 [隐私政策](https://lizhang.songisle.xyz/privacy/)、[用户协议](https://lizhang.songisle.xyz/terms/)、[帮助中心](https://lizhang.songisle.xyz/help/)。本轮读取工具未能访问，线上可访问性和正式正文未验收；不能把本地链接通过当作线上已通过。没有操作 Cloudflare、阿里云 ECS、DNS 或现有线上网站。

技术完成范围与仍待资料见 [备案通过后发布步骤](备案通过后发布步骤.md)、[RC4 正式签名准备](RC4正式签名准备.md) 和 [正式发布清单](v1.0.0正式发布清单.md)。仍等待真实网站 ICP、独立 APP 备案/平台认可材料、运营与政策资料、正式签名身份、实际分发历史，以及本人最终代码审查。实机/模拟器/仪器执行、真实正式签名、覆盖升级、线上及商店均未验收。

## 最终 APK 静态结果

Release 经 `scripts/verify-rc4-release.ps1` 完整通过：包名、候选版本、minSdk 26/targetSdk 36、不可调试、Manifest/DEX 无测试和调试宿主、固定权限、4 份离线资产与当前源码字节一致、6 字段内容绑定和 16 KB 页对齐。Release 没有 v1/v2 签名材料，签名验证按未签名候选预期不通过；不能提交商店。生成的 Release BuildConfig 保持 DEBUG=false、IS_OFFICIAL_RELEASE=false。

Release 权限仅 READ_CONTACTS、POST_NOTIFICATIONS、RECEIVE_BOOT_COMPLETED 和 AndroidX 签名级 DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION；没有 INTERNET、WRITE_CONTACTS、testOnly 或仪器入口。Debug 版本/包名、调试 v2 签名、同一 6 字段绑定和 4 份完整资产已检查；Debug 自带用于开发的测试宿主，不是正式包。独立 androidTest APK 只编译打包，未运行。

内容绑定仅绑定批准输入、构建模式与版本，不等于完整源码证明；最终源码 commit 与具体 APK SHA-256 一并留存。

| 产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 1765555 | `3dfe0f80b50e65b2d25b9734749a2c6e6d3289e0d651c474f376d2ec10d89396` |
| `app/build/outputs/apk/debug/app-debug.apk` | 29711170 | `1725fe48b05f92cfb22bb0c235af26e99704a67c3cc4dfa966e0fb92817e075e` |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 22238503 | `1aa08db98f5305c210e77b1b3c1e2dfd97dc9287d44a9dc9b24bee8c86cc97f8` |

最终候选静态检查完成后，Git 工作区差异和暂存区 diff --check 均通过；README、docs、商店目录及网站正文的本地 Markdown 文件链接检查无缺失。修改文件完整清单见 [RC4 修改文件清单](RC4修改文件清单.txt)。未执行设备安装或播放验收。
