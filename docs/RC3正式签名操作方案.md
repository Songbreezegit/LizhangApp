# RC3 正式签名操作方案

> 本页为 RC3 历史操作方案，旧版本入口已替换为 verify-rc4-release.ps1。当前发布请使用 [RC4 签名准备](RC4正式签名准备.md)，不得复制旧候选版本命令作为正式批准。

日期：2026-10-10。包名固定为 `com.yangsong.lizhang`。本文提供本人批准后的操作步骤；本轮不创建密钥、不读取私钥或密码、不执行正式签名，不创建正式 Release，不提交商店。

## 1. 本轮状态和需要批准的事项

RC3 常规构建为 `versionName=1.0.0-rc3 / versionCode=23`。Debug 使用开发调试证书；普通 `assembleRelease` 保持未签名候选。两者均不能冒充正式商店包。

RC2 和 RC3 均使用版本代码 23。正式 `1.0.0` 的版本代码须由本人核对已安装 / 已上传 / 已分发的各渠道历史后确认，并通过 `-PofficialVersionCode=<本人确认值>` 显式传入，不能只把版本名改成 `1.0.0`。正式版本应满足实际渠道递增与更新要求，建立全渠道版本代码台账；不靠卸载重装验证升级兼容。依据 [Android 版本管理说明](https://developer.android.com/studio/publish/versioning)。

| 项目 | 本人需确认 |
| --- | --- |
| 正式内容 | 三篇完整中文正文、七语言告知 / 中文回退、网站正文及资料的一致性；不将未审核译文发布为正式法律文本 |
| 主体与政策 | 运营者、公开邮箱、真实生效日期、政策版本、未成年人安排、批准人和日期 |
| 备案安排 | APP 备案或适用单机材料、网站备案及实际证明；分别确认 `app_filing_status` / `app_filing_material` 与网站备案字段 |
| 既有签名身份 | 是否曾以正式身份分发，历史 APK / 公共证书的 SHA-256、使用的别名、本人掌握的受控密钥位置和备份可用性 |
| 正式版本代码 | 所有渠道已用代码与本人批准的下一个正式代码 |
| 本轮正式操作 | 当前内容摘要、最终源码 commit、签名身份和目标产物经本人审阅后，另行明确批准构建 / 签名 |

仓库构建没有配置正式 `signingConfig`。RC2 记录在仓库文件名范围未发现正式密钥文件；RC3 的文件名核查结果以本轮验证报告为准。仓库没有这类文件不能证明用户其他安全位置不存在密钥；本轮不扫描整台电脑或尝试读取任何私有位置。签名身份未确认时，交付候选和本方案。

## 2. 既有证书身份检查

优先由本人提供以前正式 APK 或导出的公共证书，以及已确认的 64 位 SHA-256 指纹。APK 的证书核验不需要私钥，也不需要密钥密码：

```powershell
# 本人提供并允许核对的历史正式 APK；不填写密码参数。
& '<Android SDK Build Tools 路径>\apksigner.bat' verify --verbose --print-certs '<历史正式 APK 路径>'
if ($LASTEXITCODE -ne 0) { throw '历史 APK 签名验证失败，停止正式操作。' }

# 如果本人提供的是公共证书文件，仅读取公开证书身份。
& keytool -printcert -file '<本人提供的公共证书文件路径>'
if ($LASTEXITCODE -ne 0) { throw '公共证书读取失败，停止正式操作。' }
```

证书指纹可用于批准文件和交付报告。完整证书身份可能包含本人信息，不必将证书全量输出或本机文件路径提交 GitHub。不要执行导出私钥、打印密码或把密钥交给未经授权的人。若只有密钥库可核验，由本人在明确授权的本地交互会话中使用 `keytool -list -v -keystore <仓库外位置> -alias <本人确认别名>` 查看证书；密码由本人交互输入，不作为工具参数或日志内容。

Debug 证书不作为正式身份。本轮候选能安装不代表将来正式证书能覆盖该 Debug 安装；生产更新以历史正式安装证书和包名为准，不能用卸载来证明数据保留。

## 3. 没有正式密钥时的首次创建方案

此步骤必须等待本人明确确认“确实不存在需继续使用的正式签名身份，并批准首次创建正式密钥”后才能执行。本轮不执行，脚本也不自动生成或在找不到密钥时创建替代密钥。

1. 本人确定密钥用途为礼账应用签名，选择仓库外受控位置、别名和证书真实身份。建议为礼账单独保管密钥，减少其他应用与礼账相互影响。
2. 在可信本地环境通过 Android Studio 官方签名向导或 JDK `keytool` 交互创建，例如 RSA 3072 位、SHA256withRSA、有效期至少 25 年；算法和期限在实施时再核对官方说明。本人交互设置强密码，不把密码写进参数、Gradle、属性文件、脚本或聊天。
3. 本人保存至少两份独立受保护的离线备份，确认可以读取证书且备份可恢复；密码另行保管。该演练不导出私钥到仓库，也不上传密钥到 GitHub。
4. 导出公共证书，记录证书 SHA-256、有效期和保管责任人；再由本人批准作为首次正式身份。不能仅凭新建成功自动将 `signing_identity_status` 改为 `confirmed`。
5. 在所有准备采用同一更新身份的渠道使用此 App Signing Key；若以后进入 Play App Signing，按第 6 节另行批准相关密钥托管操作。

官方说明要求应用签名密钥长期安全保管，签名证书期限至少 25 年；丢失自行管理的应用签名密钥会失去通常更新能力。参考 [Android 应用签名](https://developer.android.com/studio/publish/app-signing)。

## 4. 内容批准、正式构建与签名

以下是批准后的人工操作流程，不表示本轮已获批准。使用专门签名终端；不要启用终端录制或 `Start-Transcript`，不要将密码输入转存报告。示例路径由本人实际选择，不包含预置身份或密码。

1. 确认最终 RC3 源码 commit 和远端 SHA，一并审阅 Android、网站和文档差异。保留真实草稿，先完成法律正文定稿，再更新元数据与公开批准记录；不通过删掉门禁或盲目替换状态词获取正式包。
2. 运行法律内容摘要与网站一致性校验。`release/legal-approval.properties` 与完整正文元数据须匹配；当前摘要、政策版本、备案确认、既有证书指纹及本人批准日期须对应本次源码。法律摘要覆盖三份完整正文（`privacy.md`、`terms.md`、`help.md`）、元数据，以及七语言的 `privacy_notice.xml`、`legal_strings.xml`、`candidate_release_strings.xml`，当前共 25 个源输入。候选专用提示保留真实候选状态并纳入摘要，正式 UI 不显示它们；正式正文和告知仍必须实际审订。`website_content_sha256` 另行覆盖网站全部 Markdown 正文和网站配置。任何正文、翻译、元数据或上述覆盖内容变化都要重新批准，不能只替换批准状态。
3. 显式传入本人确认的正式版本代码，执行正式内容预检和完整构建。门禁拒绝时停在拒绝原因，不产生“已批准”的替代数据。

```powershell
# 所有命令在本人批准的最终 RC3 工作树根目录执行。
$ErrorActionPreference = 'Stop'
.\scripts\legal-content-digest.ps1 | ConvertTo-Json -Depth 3
# 同时核对网站镜像并输出供本人审阅的 website_content_sha256；不修改批准状态。
& node .\website\scripts\legal-consistency.mjs
if ($LASTEXITCODE -ne 0) { throw '网站与 App 的内容或批准资料不一致。' }
.\scripts\scan-unapproved-legal-content.ps1 -RequireApprovedContent

$officialCode = Read-Host '本人根据全渠道分发历史确认的正式版本代码'
if ($officialCode -notmatch '^[1-9][0-9]*$') { throw '正式版本代码必须是本人确认的正整数。' }
$officialCodeArgument = '-PofficialVersionCode=' + $officialCode
& .\gradlew.bat --continue --no-daemon --console=plain --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-PofficialRelease=true' $officialCodeArgument test lint lintRelease assembleDebug assembleRelease assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) { throw '正式门禁或构建失败，停止签名。' }
```

4. 使用 `verify-rc3-release.ps1 -OfficialRelease -BeforeSigning` 对本次生成的正式未签名 Release APK 完成签前检查：应用 ID、本人批准版本代码、不可调试、权限、测试 / 调试宿主、完整离线正文和 APK 的编译时内容绑定均须匹配。旧候选、Debug、缺少绑定或绑定与当前批准内容不同的旧产物都应被拒绝；不要把 `-BeforeSigning` 当作跳过内容批准的开关。签名前检查通过不表示已经签名，也不等于设备验收。
5. 使用 `sign-release.ps1` 在仓库外新目录执行对齐、签名和签后复核。脚本会在读取授权密钥证书前重复签前检查；密钥与最终输出必须位于 Git 仓库外，目录已存在则拒绝覆盖。密码由工具交互询问，不能填入下列变量或参数。先对齐，再签名，签名后不修改 APK。[Android zipalign 顺序与检查](https://developer.android.com/tools/zipalign)。

```powershell
$signTools = Read-Host 'Android SDK Build Tools 目录'
$apkAnalyzer = Read-Host 'Android SDK apkanalyzer.bat 路径'
$approvedKeyStore = Read-Host '本人批准的仓库外正式密钥库路径'
$approvedKeyAlias = Read-Host '本人批准的正式别名'
$approvedCertificateSha256 = Read-Host '本人批准的正式证书 SHA-256（64 位无冒号）'
$unsignedApk = (Resolve-Path -LiteralPath 'app\build\outputs\apk\release\app-release-unsigned.apk').Path
$privateOutput = Read-Host '仓库外不存在的新签名输出目录'
$preflightArguments = @{
    ApkPath = $unsignedApk
    OfficialRelease = $true
    BeforeSigning = $true
    ExpectedVersionCode = [int]$officialCode
    ExpectedCertificateSha256 = $approvedCertificateSha256
    ApkAnalyzerPath = $apkAnalyzer
    ApkSignerPath = Join-Path $signTools 'apksigner.bat'
    ZipAlignPath = Join-Path $signTools 'zipalign.exe'
}
.\scripts\verify-rc3-release.ps1 @preflightArguments | ConvertTo-Json -Depth 4

# 脚本内部再次预检、核对授权密钥的公共证书，再对齐、签名及完整复核。
$signingArguments = @{
    ApkPath = $unsignedApk
    KeyStorePath = $approvedKeyStore
    KeyAlias = $approvedKeyAlias
    ExpectedCertificateSha256 = $approvedCertificateSha256
    ExpectedVersionCode = [int]$officialCode
    OutputDirectory = $privateOutput
    BuildToolsDirectory = $signTools
}
.\scripts\sign-release.ps1 @signingArguments | ConvertTo-Json -Depth 4
```

项目 minSdk=26，正式检查要求 APK 通过 v2 签名验证；工具可根据支持范围选择其他签名方案。实际官方签名验证须在项目支持的 Android 范围内通过。`apksigner` 默认通过标准输入询问密钥库和私钥密码；本方案不使用 `pass:`、密码文件或敏感环境变量。[apksigner 官方说明](https://developer.android.com/tools/apksigner)。签名脚本当前使用项目默认 `ApkAnalyzerPath`，如本机工具路径不同，须先统一脚本的实际工具路径再操作，不能因为独立预检通过就忽略内部检查失败。

6. 签后由 `verify-rc3-release.ps1 -OfficialRelease` 再检查内容批准、版本、打包绑定、对齐和签名；`apksigner --print-certs` 的最终证书 SHA-256 必须与输入指纹和批准记录一致。证书不符、Debug、旧内容、签名失败或正式版本代码不符均拒绝。任一检查失败时不要发布目录中遗留的 APK；失败文件不因文件名带 `release` 而取得正式身份。脚本不代替本人授权，不创建密钥，不保存密码。
7. 留存仓库外两份证据：`release-verification.json` 记录最终 APK 的结构、内容、版本、签名与对齐检查；`signing-record.json` 记录签名前输入 SHA-256、签后 APK SHA-256、证书指纹和版本代码。另记录最终源码 commit SHA、Build Tools 版本与本人批准资料，两份 JSON 不代替源码提交记录或批准文件。公开报告不含私钥、密码、个人密钥路径、真实账本或备份；需要公开证据时先审阅其中本机路径。
8. 在另外授权的最终发布阶段完成旧正式包覆盖升级与数据保留、商店检测和目标设备验收。本轮的静态 APK / JVM / 构建证据不能代替这些设备检查。

APK 内的 `assets/release-content-binding.properties` 由 `generateReleaseContentBinding` 在构建时生成，放在 generated assets 中，不修改批准输入或真实草稿。它绑定六项：`legal_content_sha256`、`website_content_sha256`、`is_official_release`、`version_name`、`version_code` 和 `policy_version`。签前与签后都应逐项核对；法律和网站摘要不符、模式或版本不符、缺项均须停止。这个绑定记录编译时法律与网站内容，**不是完整源码证明**，不覆盖任意业务代码、所有依赖或设备行为；最终源码 commit、输入 / 输出 APK 摘要与验证报告仍需一并留存。

## 5. Git 与本机私有配置防护

密钥、密码及私有配置放在仓库外。项目忽略规则应覆盖 `.jks`、`.keystore`、`.p12` / `.pfx`、签名属性文件和本机配置；忽略规则不能撤销已经被 Git 跟踪的秘密，因此每次提交前核对实际跟踪文件。不要把密码放入 `gradle.properties`、`release/legal-approval.properties` 或脚本参数；批准文件只记录公开的证书指纹和审批资料。

提交前只核对路径和名称，不输出文件内容：

```powershell
git ls-files | Select-String -Pattern '(?i)(\.(jks|keystore|p12|pfx|pem|key)$|(^|/)(key|keystore|signing|local)\.properties$)'
git diff --cached --name-only
git diff --check
```

若发现已跟踪的密钥或秘密，停止发布并由本人决定密钥泄露处置；不能仅加入 `.gitignore` 后称秘密已经安全，也不自动改写历史或替换正式身份。本轮不运行私钥泄露内容扫描，不输出任何密码。

## 6. 未来 Google Play 与跨商店更新

Android 更新身份由包名和兼容的签名身份共同决定。跨 vivo、OPPO 和 Play 保持 `com.yangsong.lizhang`，使用一致的 **App Signing Key（应用签名密钥）**，并协调版本代码；**Upload Key（上传密钥）** 只是提交到 Play 的身份，不能拿其指纹当用户 APK 的签名身份。

如果希望继续在本地为 vivo / OPPO 自主生成同身份 APK，未来首次配置 Play App Signing 时应选择提供本人掌握的既有应用签名密钥，按照 Play 官方 PEPK / 控制台步骤传输，另行得到本人对密钥托管的明确授权。不要默认让 Google 生成一个与已经分发 APK 不同的新应用签名密钥。本轮不执行密钥上传、托管、轮换或 Play 配置。[Android 应用签名说明](https://developer.android.com/studio/publish/app-signing)。

本轮查阅的 Play 官方帮助已描述新应用默认采用 Google 生成的混合签名身份；需要自有密钥的开发者可在首次开放测试或生产版本推出前，通过 Play App Signing 的更改签名密钥入口提供自有密钥。以后接入时须先审查当时控制台流程，确认各支持 Android 版本最终分发 APK 的证书及更新兼容性，不能直接点击默认发布后才计划统一证书。[Play App Signing 最新操作说明](https://support.google.com/googleplay/android-developer/answer/9842756)。

官方也提供另一条路线：使用 Google 生成的应用签名密钥后，从 Play Console 下载由该密钥签名的通用分发 APK，在其他商店分发。该路线意味着本地不能使用 Google 私钥自行签名，且不能自动覆盖之前不同证书的安装；是否适用需在首次分发前由本人决定，不能事后擅自改变现有身份。[Android App Bundle 常见问题](https://developer.android.com/guide/app-bundle/faq)与 [Play Console 通用 APK 说明](https://support.google.com/googleplay/android-developer/answer/9844279)。

Play 以后可能要求 AAB 与其他审核材料，准备 AAB 属于后续明确授权范围。签名轮换、签名 lineage、开发者账号验证和商店专用要求也在实施时另行审查；不同 Android 版本可能使用不同签名轮换路径，不能把 Play 上传密钥重置理解为替换所有商店的应用签名身份。
