# RC2 签名与发布检查

日期：2026-10-10。分支：`release/v1.0.0-rc2`，Android 验收基线 `b6adf6ba516ed280bb1c7882b78cfa36e4449b1a`。本轮不提交商店，不操作网站、阿里云 ECS、DNS 或备案。测试实际数量和产物摘要见本轮验收报告。

## 候选版本和正式版本

现有构建为 `versionCode=22 / versionName=0.9.12`。RC2 将版本代码递增到 **23**，常规 Debug / Release 均为 **1.0.0-rc2**。候选字样、分支或构建成功均不表示已经正式发布。默认 `assembleRelease` 继续生成未签名候选 `app/build/outputs/apk/release/app-release-unsigned.apk`，不配置、创建或替换正式密钥。

仅显式 `-PofficialRelease=true` 时使用 `versionName=1.0.0`。该开关使 `preBuild` 依赖 `verifyOfficialReleaseContent`，任何批准资料或正文检查失败都中断正式构建。普通候选构建无需将待确认信息伪装为已批准。

## 本人内容批准门槛

开发者本人须审阅三篇完整正文、七语言首次告知和文档回退提示，确认它们与最终版本实际行为一致。七语言文案不等同于七篇完整政策译文；当前完整正文采用简体中文，其他语言明确告知并回退到同一全文。关键数据处理说明不因语言切换而省略。

公开资料分别保存在 `app/src/main/assets/legal/metadata.properties` 和 `release/legal-approval.properties`，必须一致：

| 字段 | 必须由本人确认的内容 |
| --- | --- |
| `operator_name` | 实际运营者姓名或依法采用的主体名称 |
| `contact_email` | 实际有效并可处理隐私请求的联系邮箱 |
| `effective_date` | 正式文本生效日期，`YYYY-MM-DD` |
| `policy_version` | 与 `LegalPolicy.CURRENT_VERSION` 相同的政策版本；RC2 候选为 `1.0.0-rc2-policy-v1` |
| `filing_record` | 真实备案资料；适用性、编号或不适用理由均由本人确认，不能编造 |
| `reviewed_by` | 实际审阅批准人 |
| `approval_status` | 本人实际批准完成后，两文件才可填写 `approved` |

发布批准文件还须填写实际 `approved_at` 日期、`filing_status=confirmed`、`signing_identity_status=confirmed`、既有正式证书的 64 位 `signing_certificate_sha256` 和当前 `approved_content_sha256`。证书指纹属于身份核对信息，不包含私钥或密码。

批准检查要求上述值完整，邮箱和日期格式正确，三篇正文及七语言资源齐全；共享规则 `release/unapproved-content-pattern.txt` 识别七语言实际使用的待批准标记，包括 `pending`、TODO、待确认、未批准、草稿、審核稿、draft、candidate、草案、초안、borrador、projet 及对应等待批准／尚未生效说明。正式批准时必须实际审订正文和告知文案，移除候选说明及候选元数据注释。不能只填写批准表绕过未定稿正文。若采用新政策版本，须同时更新正文、元数据和 `LegalPolicy.CURRENT_VERSION`，让再次告知按版本识别。

摘要覆盖 **assets/legal 下所有文件**，以及 **res/values* 下文件名以 legal 或 privacy 开头的 XML**；包括完整正文、元数据、七语言 `privacy_notice.xml` / `legal_strings.xml`。对文件按 App 相对路径序号排序，逐项拼接 `路径 + LF + 文件字节 SHA-256 + LF`，再取 UTF-8 字节 SHA-256。任一翻译、文件名、元数据或正文变化都须重新审阅批准；换行变化也会改变摘要。

```powershell
# 在本项目根目录执行，只输出待审摘要，不表示批准。
.\scripts\legal-content-digest.ps1 | ConvertTo-Json -Depth 3
.\gradlew.bat --no-daemon --console=plain :app:printLegalContentDigest

# RC2 门控回归：七语言各 5 条实际候选提示共 35 条均须被识别。
.\scripts\scan-unapproved-legal-content.ps1 -VerifyCandidateCoverage | ConvertTo-Json -Depth 3
# 正式内容预检；当前候选状态下应失败。
.\scripts\scan-unapproved-legal-content.ps1 -RequireApprovedContent

# 本轮 RC2 的待确认状态下，下述检查应失败；不得删除检查来获得正式包。
.\gradlew.bat --no-daemon --console=plain :app:verifyOfficialReleaseContent
```

本人完成正文批准、填妥公开资料、核对当前摘要并审查最终提交后，才执行正式版本构建：

```powershell
.\gradlew.bat --continue --no-daemon --console=plain --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-PofficialRelease=true' test lint assembleDebug assembleRelease assembleDebugAndroidTest
```

机器检查只核对资料完整性和批准记录与当前内容的一致性，不能替代本人对真实性、法律文本和适用情况的判断；通过它也不表示正式签名、设备验收或商店批准已完成。正式版本仍输出未签名 Release，签名须经过下面的既有身份核对。

RC2 实际执行扫描回归：**35 / 35** 条七语言候选提示被规则识别，**18 / 18** 个内容输入仍含待批准标记；`RequireApprovedContent` 应明确拒绝。该数量属于发布门控扫描，不混计为 Android JVM 测试或设备测试数量。

## 既有签名身份确认和保管

当前构建没有正式 `signingConfig`。本轮在仓库文件名范围未发现 `.jks`、`.keystore` 或 `.p12`；这不证明开发者其他安全位置不存在正式密钥。没有扫描用户磁盘或读取密码，也没有生成替代密钥。

优先确认以前使用的正式密钥及签名证书，明确它是否为现有发布身份、保管位置、可用备份和授权操作人。若应用已经分发，须与以前正式 APK 的证书指纹比对；Debug 证书不是正式身份。本轮不改变签名身份或启用新的商店签名方案。若确实没有可用正式密钥，继续保持候选，由本人作出后续决策。

密钥放在仓库外受控位置；路径和别名由本人在本机交互输入，密码由 `keytool` / `apksigner` 交互询问。不要将密码写到命令行、Gradle 文件、脚本、批准文件、终端日志或提交。不提交密钥、备份、密码文件或敏感环境配置。

## 本人批准后的外部 APK 签名步骤

下面为人工操作说明，本轮不执行签名。工具路径按本机 Android SDK 配置调整。所有输出文件放入新的 `build` 检查目录，禁止覆盖已有产物。

```powershell
$signingTools = 'D:\Android\Sdk\build-tools\36.0.0'
$existingKeyStore = Read-Host '已确认的现存正式密钥路径（仓库外）'
$existingKeyAlias = Read-Host '已确认的现存正式密钥别名'
if (!(Test-Path -LiteralPath $existingKeyStore -PathType Leaf)) { throw '现存密钥不存在，停止签名；不得创建替代密钥。' }

# 交互输入密码；与本人已确认的历史正式 APK / 证书指纹核对。
& keytool -list -v -keystore $existingKeyStore -alias $existingKeyAlias
if ($LASTEXITCODE -ne 0) { throw '无法读取现存签名证书，停止操作。' }

$signingRun = Read-Host '新检查目录的名称（例如由本人指定的日期编号）'
if ($signingRun -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,80}$') { throw '检查目录名称不合规则。' }
$signingOutput = Join-Path (Join-Path (Get-Location).Path 'build') $signingRun
if (Test-Path -LiteralPath $signingOutput) { throw '检查目录已存在，禁止覆盖。' }
New-Item -ItemType Directory -Path $signingOutput | Out-Null
$unsignedApk = 'app\build\outputs\apk\release\app-release-unsigned.apk'
$alignedApk = Join-Path $signingOutput 'Lizhang-1.0.0-aligned-unsigned.apk'
$signedApk = Join-Path $signingOutput 'Lizhang-1.0.0-release.apk'

# 对齐在签名前完成；不用覆盖选项，不改变签名身份。
& (Join-Path $signingTools 'zipalign.exe') -P 16 -v 4 $unsignedApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw 'APK 对齐失败。' }
& (Join-Path $signingTools 'apksigner.bat') sign --ks $existingKeyStore --ks-key-alias $existingKeyAlias --out $signedApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw 'APK 签名失败。' }
& (Join-Path $signingTools 'apksigner.bat') verify --verbose --print-certs $signedApk
if ($LASTEXITCODE -ne 0) { throw 'APK 签名验证失败。' }
& (Join-Path $signingTools 'zipalign.exe') -c -P 16 -v 4 $signedApk
if ($LASTEXITCODE -ne 0) { throw '签名 APK 对齐验证失败。' }

$approvedCertificate = Read-Host '本人批准的既有正式证书 SHA-256（64 位无冒号）'
.\scripts\verify-rc2-release.ps1 -ApkPath $signedApk -OfficialRelease -ExpectedCertificateSha256 $approvedCertificate | ConvertTo-Json -Depth 3
```

步骤依据 Android 官方 [apksigner](https://developer.android.com/tools/apksigner) 与 [zipalign](https://developer.android.com/tools/zipalign) 说明：先对齐再签名，签名后不再修改 APK；使用 `verify --print-certs` 核对签名与证书。本机 `keytool` 输出只用于身份核对，不要求将完整证书信息或密钥路径提交到仓库。

## 候选产物检查流程

1. 确认工作分支、最终 commit、远端同 SHA；保留本轮原有未提交草稿和附件。运行 `git diff --check`。
2. 执行 `test lint assembleDebug assembleRelease assembleDebugAndroidTest`，统计 Debug / Release JVM 测试数量和全部失败；发生失败时修复后完整重跑，不删除断言或降低要求。必要时使用进程内项目 `TEMP` / `TMP` 和 `JAVA_TOOL_OPTIONS` 解决 Windows loopback 限制，不修改全局环境。
3. 运行 `:app:lintRelease` 与 `:app:dependencies --configuration releaseRuntimeClasspath`，保存报告，审查运行时依赖无测试 SDK 或新的联网服务。
4. 对本轮具体未签名候选运行 `scripts/verify-rc2-release.ps1`；工具会核对 `1.0.0-rc2 / 23`、应用 ID、minSDK 26 / targetSDK 36、不可调试、固定权限、测试组件 / DEX 隔离及打包的完整离线正文与源码一致性，输出 APK 与待审内容摘要。该脚本依据官方 [apkanalyzer](https://developer.android.com/tools/apkanalyzer) 提供的清单和 DEX 定义查询能力。
5. 再核对生成的 Release `BuildConfig.DEBUG=false`；源中存在诊断类不表示 Release 会运行日志。当前日志开关与触摸 / 启动诊断均受 `BuildConfig.DEBUG` 控制，未新增记录联系人或金额的日志。
6. 单列实际未运行的设备测试：首次安装 / 拒绝 / 确认、升级和数据保留、通讯录 / 通知拒绝、离线正文及浏览器返回、深浅色 / 字号 / 七语言切换。仪器测试编译成功不计为设备通过。
7. 候选 APK 与验收报告交由本人审查。正式文本、运营者资料、备案、版本和签名身份未批准时，保持候选，不进行商店提交或网站操作。

```powershell
.\scripts\verify-rc2-release.ps1 | ConvertTo-Json -Depth 3
```

自动脚本是特定检查范围内的可重复证据；没有验证实体设备行为、所有依赖字节码安全或第三方网站可用性。候选签名校验按预期不通过，不能将未签名 APK 标记为正式可安装包。正式签名检查同时要求已确认的证书指纹和 v2 签名验证；即使通过仍应审阅内容批准记录及最终产物。
