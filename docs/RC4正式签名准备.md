# RC4 正式签名准备

日期：2026-10-10。当前分支 `release/v1.0.0-rc4`，常规候选为 `1.0.0-rc4 / 23`；正式 `1.0.0` 仍使用独立构建入口和本人批准门禁。包名保持 `com.yangsong.lizhang`。本轮只完成技术准备，不创建密钥、不签正式 APK、不提交商店。

## 当前可确认的签名身份

`release/legal-approval.properties` 的 `signing_identity_status`、`signing_certificate_sha256` 仍为 `pending`，没有已批准的正式证书身份。构建未配置正式 `signingConfig`；Debug 使用调试证书，普通 Release 仍为未签名候选。核查仅查看当前仓库跟踪文件和顶层安全文件名，没有发现正式密钥或私有签名配置；这不证明本人在其他受控位置没有密钥。本轮未扫描个人磁盘、未读取密码、未创建替代密钥。

网站 ICP 与 APP 备案或平台认可的单机适用材料分别管理。网站 `icpApproved=false`、发布批准备案字段 `pending` 的情况下继续拒绝正式构建和签名；“ICP备案审核中”不作为正式号码或批准证据。资料更新和重新批准的完整流程见 [备案通过后发布步骤](备案通过后发布步骤.md)。

## 本人最终发布时的最少操作

1. 提供并确认真实网站 ICP 号码、APP 备案或平台认可的适用证明，以及运营者、公开邮箱、政策生效日期和最终正文；按备案流程同步更新全部批准资料、网站镜像和两个内容摘要，完成本人审阅批准。仅取得网站 ICP 不能代替 APP 材料。
2. 选择下面的“复用身份”或“首次身份”流程，确认应用签名证书 SHA-256、受控密钥及备份，并核对所有渠道已分发的最高版本代码；正式版本代码必须高于台账最高值，由本人明确确认，不能默认把候选 23 当作正式值。
3. 完成最终代码审查，提交全部正式资料并使用干净工作树；执行正文一致性、正式门禁、完整自动化检查与正式未签名构建。任何拒绝都须修复真实资料后重跑，不能删除门禁或填入合成批准数据。
4. 在本人专用本地终端交互输入密钥路径、别名、公开指纹和仓库外新输出目录，执行 `scripts/sign-release.ps1`。密码由 `keytool`、`apksigner` 交互询问。签后通过全部检查、保留成功记录后，再进行另外授权的设备升级/数据保留验收和商店提交。

以上是未来操作流程；当前内容、备案和签名身份未获全部批准，停在 RC4 候选。本轮未执行实机或模拟器测试，也未验收旧正式包覆盖升级。

## 复用身份与首次身份

| 情况 | 操作及停止条件 |
| --- | --- |
| 已有正式分发或曾为礼账创建正式证书 | 优先提供历史正式 APK 或公共证书，只核对 SHA-256；与本人确认的仓库外密钥库、别名证书一致后复用。密钥遗失、来源不清、指纹不符时停止，不能自动创建替代密钥。 |
| 确实没有需复用的正式身份 | 先由本人明确批准首次创建。再在可信本地环境使用 Android Studio 签名向导或 `keytool` 交互创建仓库外密钥，使用当前官方支持的算法和至少 25 年证书期限；密码不进命令行、脚本、属性或日志。本人保留独立受保护的备份并验证恢复能力，记录公开指纹后，才批准为首次正式身份。 |
| 只有本轮 Debug APK | Debug 可安装不等于正式签名身份，不能拿调试证书或 `androiddebugkey` 作为正式身份。正式包不能按通常同证书规则覆盖 Debug 安装；不能通过卸载来宣称升级数据保留通过。 |

[Android 官方应用签名说明](https://developer.android.com/studio/publish/app-signing)规定证书有效期至少 25 年，并区分应用签名密钥与上传密钥。本文不预置密钥创建命令，也不授权本轮创建。

历史证书只读核对示例：

```powershell
# 仅处理本人已提供并允许核对的历史 APK 或公共证书；不包含密码参数。
& '<Build Tools目录>\apksigner.bat' verify --verbose --print-certs '<历史正式APK>'
if ($LASTEXITCODE -ne 0) { throw '历史 APK 证书验证失败，停止。' }
& keytool -printcert -file '<公共证书文件>'
if ($LASTEXITCODE -ne 0) { throw '公共证书读取失败，停止。' }
```

指纹可记录在公开批准文件，密钥路径、完整个人证书信息、备份位置和密码不提交 Git。需要从密钥库确认公共证书时，由本人在明确授权的本地交互会话中调用 `keytool -list -v -keystore <仓库外路径> -alias <本人确认别名>`；不导出私钥。

## 本人批准后的检查与签名命令

以下命令使用 PowerShell 7，只能在真实批准资料已补齐、最终源码已提交后执行。签名终端不要开启 `Start-Transcript`、录屏或把密码输入转存到报告。

```powershell
$ErrorActionPreference = 'Stop'
$officialCodeText = Read-Host '本人核对全渠道历史后批准的正式版本代码'
$officialCode = 0
if (![int]::TryParse($officialCodeText, [ref]$officialCode) -or $officialCode -lt 1 -or $officialCode -gt 2100000000) {
    throw '正式版本代码必须是 1 至 2100000000 之间的整数。'
}
$officialCodeArgument = '-PofficialVersionCode=' + $officialCode

& node .\website\scripts\legal-consistency.mjs
if ($LASTEXITCODE -ne 0) { throw 'App 与网站的正文、批准资料或摘要不一致。' }
.\scripts\scan-unapproved-legal-content.ps1 -RequireApprovedContent
& .\gradlew.bat --no-daemon --console=plain $officialCodeArgument :app:verifyOfficialReleaseContent
if ($LASTEXITCODE -ne 0) { throw '正式批准未通过，停止。' }
& .\gradlew.bat --continue --no-daemon --console=plain --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-PofficialRelease=true' $officialCodeArgument test lint lintRelease assembleDebug assembleRelease assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) { throw '正式门禁或自动化检查失败，停止签名。' }
Push-Location website
try {
    & npm.cmd test
    if ($LASTEXITCODE -ne 0) { throw '网站测试失败。' }
    & npm.cmd run build -- --public
    if ($LASTEXITCODE -ne 0) { throw '网站正式内容构建失败。' }
} finally { Pop-Location }
.\scripts\test-official-release-gate.ps1
.\scripts\test-sign-release.ps1

$signTools = Read-Host 'Android SDK Build Tools目录'
$apkAnalyzer = Read-Host 'Android SDK apkanalyzer.bat路径'
$keyTool = Read-Host 'JDK keytool路径或命令名'
$approvedKeyStore = Read-Host '本人批准的仓库外既有正式密钥库路径'
$approvedKeyAlias = Read-Host '本人批准的正式别名'
$approvedCertificate = Read-Host '本人批准的应用签名证书SHA-256（64位无冒号）'
$privateOutput = Read-Host '仓库外不存在的新签名输出目录'
$signingArguments = @{
    KeyStorePath = $approvedKeyStore
    KeyAlias = $approvedKeyAlias
    ExpectedCertificateSha256 = $approvedCertificate
    ExpectedVersionCode = $officialCode
    OutputDirectory = $privateOutput
    BuildToolsDirectory = $signTools
    ApkAnalyzerPath = $apkAnalyzer
    KeyToolPath = $keyTool
    ApkPath = (Resolve-Path -LiteralPath 'app\build\outputs\apk\release\app-release-unsigned.apk').Path
}
.\scripts\sign-release.ps1 @signingArguments | ConvertTo-Json -Depth 4
```

脚本在读取密钥公共证书前执行正式批准和未签名输入检查；只有 `1.0.0`、已确认正式版本代码、与当前内容摘要绑定的正式未签名 Release 能进入签名。候选、Debug、测试宿主、未审查权限、旧绑定或已有签名材料均拒绝。密钥和输出不得位于任何 Git 仓库，也不能经过符号链接或目录联接；既有输出目录拒绝覆盖。全程不接收密码参数、不生成密钥。

对齐在签名前完成，签名后不再修改 APK；签后重复检查版本、正文绑定、批准状态、16 KB 页对齐、v2 签名和唯一批准证书。还检查原始输入摘要和源码 commit 未发生变化。依据 [apksigner 官方顺序与验证](https://developer.android.com/tools/apksigner)及 [zipalign 官方说明](https://developer.android.com/tools/zipalign)。

签名先输出 `Lizhang-1.0.0-signing-pending.apk`。全部检查通过且仓库外的 `release-verification.json`、`signing-record.json` 准备成功后，最后一步才重命名为 `Lizhang-1.0.0-release.apk`。记录含 APK 摘要、版本、证书及源码 commit。任何检查或记录写入失败都以非零退出且不出现最终名 APK；若最后重命名失败，可留下已准备的证据 JSON，但仍没有最终 APK，不得宣称发布成功。失败目录的 pending、中间文件和证据均保留，不得提交商店；另选新目录重跑。公开检查报告前先审阅本机路径信息。

## vivo、OPPO、Google Play 签名一致性

礼账目前采用同一功能和包名；未来若希望跨商店更新，保持同一应用签名身份，并统一管理版本代码。[Android 官方多商店更新说明](https://developer.android.com/google/play/app-updates)要求同包名、相同证书或有效轮换证明及兼容版本代码；签名不兼容导致的卸载重装会删除应用数据，不能作为成功升级路径。本轮不采用签名轮换或 lineage。

| 平台 | 核对结果与未来操作 |
| --- | --- |
| vivo | [vivo 官方安全白皮书 §4.2](https://privacy.vivo.com.cn/static/pdf/security-book-pdf.pdf)说明 OriginOS 对更新进行签名一致性验证；这是平台签名原则。具体开放平台签名变更后台流程本轮未确认，提交时还须复核本人控制台当前要求，不能把同包名当成可随意换证书。 |
| OPPO | [官方应用上架 FAQ](https://open.oppomobile.com/bbs/forum.php?mod=viewthread&page=1&tid=6305)要求出现“签名不一致”时先检查包内签名；确需换签才按平台指引申请。正常首次和后续升级都复用已批准身份，不主动申请换签。 |
| Google Play | [Android 官方应用签名说明](https://developer.android.com/studio/publish/app-signing)明确，多商店共用签名时应在配置 Play App Signing 时提供自有应用签名密钥。Play 的 Upload Key 只验证上传人，用户收到的 APK 用 App Signing Key 签名，不能拿上传证书冒充应用签名证书。 |

2026-10-10 查阅的 [Play 官方当前流程](https://support.google.com/googleplay/android-developer/answer/9842756)已包含 Google 默认生成的混合签名及 Android 17+ / 较旧设备的不同密钥安排。首次开放测试或生产发布前，应按当时控制台流程选择提供自有应用签名密钥，并逐个支持的系统范围核对实际分发 APK 的证书与更新兼容；不能先接受默认新身份再假定与 vivo/OPPO 同证书。密钥托管、PEPK 传输、上传密钥、AAB 及轮换均在未来本人另行授权后办理，本轮不执行。

另一种官方支持的路线是使用 Google 生成的应用签名身份，再下载 Play 已签名的通用 APK 分发到其他商店。该路线不能让本地取得 Google 私钥自行签名，也不会自动兼容此前不同证书的安装；须在首次分发前由本人选择，不能擅自切换。[Play 官方其他渠道分发说明](https://support.google.com/googleplay/android-developer/answer/9842756)。

## 本轮安全回归证据与边界

```powershell
# 当前候选可执行；完全隔离、无真实密码和签名，不修改真实批准记录。
.\scripts\test-sign-release.ps1
# 当前候选实际 APK 的结构和内容检查由 RC4 总体验收运行。
.\scripts\verify-rc4-release.ps1 | ConvertTo-Json -Depth 4
```

签名脚本隔离回归 **42/42** 通过，明细为 `build/signing-script-regression/signing-script-results.json`。测试调用生产脚本副本，使用临时合成正文、非安装 ZIP、占位文件和内存工具 mock，覆盖输入、路径、工具、门禁、版本、权限、测试隔离、内容绑定、证书、对齐、签名及签后失败，还覆盖记录写入、最终重命名失败和每条拒绝分支都没有最终名 APK；验证 mock 函数已恢复原状，并在独立 PowerShell 进程验证非法输入非零退出。没有真实私钥、没有密码，也没有真实加密签名；该证据不宣称正式证书可用、真实签名成功、商店认可或设备升级已验收。真实批准门禁的合成通过/拒绝测试和完整 Android 构建结果另见 RC4 自动化报告。

当前仍等待本人提供真实备案/适用证明、最终运营及政策资料、正式证书身份与分发历史，完成最终审阅后才可启动正式构建和签名。
