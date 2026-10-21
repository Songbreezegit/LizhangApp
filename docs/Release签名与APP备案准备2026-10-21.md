# Release 签名与 APP 备案特征信息准备

日期：2026-10-21。仓库：`Songbreezegit/LizhangApp`。分支：`chore/release-signing-preparation`。基线为本轮拉取的最新 `origin/main`：`fc76b584d5bc6b9adcfae1fded21d93095a6302a`。本轮只准备长期签名配置与备案工具，不发布 v1.0.0。

## 已核查的现状与待办

- `applicationId=com.yangsong.lizhang`，版本代码仍为 23；候选版本仍为 `1.0.0-rc2`，正式开关仍由原有内容门禁控制。
- 原密钥库 `D:/AndroidKeys/LizhangApp/lizhang-release.jks` 保留原状。本轮仅看到公开别名 `lizhang`、PKCS12 格式及密钥条目；无密码的公开查询无法取得证书。用户反馈忘记密码，本轮不猜测、不破解、不读取保存的密码。
- Git 历史未发现 `signingConfigs` / `storeFile` 配置，历史交付记录为 Debug 或未签名候选；本轮 GitHub Releases 查询为空，本地无标签。在已检查的 AndroidKeys、AndroidProjects、AndroidBuilds、用户桌面、文档、下载目录中未发现另一份礼账密钥。以上为有限范围证据，不能单独证明仓库外从未发行。
- 用户已进一步确认礼账从未正式对外发行，授权准备不同文件名的新长期密钥；旧 JKS 不删除、不覆盖、不修改。
- **新密钥须由用户本人在 Windows 本机创建和备份。当前未取得新证书，真实模数、MD5、SHA-256 均待导出，不能用示例值代填。**
- `release/legal-approval.properties` 整份保持不变，尤其 `filing_status=pending`、`signing_identity_status=pending`、`signing_certificate_sha256=pending`。不会因创建密钥或导出指纹自动确认备案和签名身份。

## 长期密钥方案

国内应用商店和官网 APK 使用同一把长期 App Signing Key。未来接入 Google Play App Signing 时选择提供同一把应用签名密钥；Upload Key 可以届时单独创建。此次不接入 Play Console，也不上传任何密钥。未来涉及 Google 托管时，由用户本人按 Console 官方流程另行操作。

若以前正式发行过 APK，必须优先找回原身份并核对历史 APK 证书，不能按此流程直接换钥。本项目此次已由用户确认从未正式发行，才进入新钥准备流程。

## 用户在 Windows 创建新密钥

1. 打开 Android Studio → **Build → Generate Signed Bundle / APK → APK → Create new**。
2. 使用不同于原 JKS 的路径，例如 `D:/AndroidKeys/LizhangApp/lizhang-app-signing-2026.jks`，别名可用 `lizhang`。密钥库与 Key 密码由本人设置和保管，不发到聊天，不保存在仓库或日志中。
3. 使用 RSA 3072 或 4096 位（建议 4096），证书有效期至少 25 年，按本人真实情况填写证书 Subject。
4. **部分 Android Studio 版本的 Create new 对话框没有密钥位数设置，不能把默认位数当成 4096。** 如无法明确设置，先取消该创建对话框，改在本机终端运行下方 JDK 命令，再用 **Choose existing** 选择生成的文件；这仍是本人在本地创建和管理密钥。
5. 创建后不必继续向导构建 APK。本轮不要求正式包；原 JKS 和新 JKS 分别保管，新密钥做本人控制的离线加密备份，密码单独保管。

下面命令仅供本人在 Windows PowerShell 手动运行，Codex 不代为创建。JKS 类型支持不同的库密码与 Key 密码；若两个密码相同，在 Key 密码提示处按工具说明操作。密钥目录已存在时无需重新创建：

```powershell
$newKey = 'D:\AndroidKeys\LizhangApp\lizhang-app-signing-2026.jks'
if (Test-Path -LiteralPath $newKey) { throw '目标文件已存在，请使用另一个新文件名。' }
# 本机 JDK 位置不同则调整工具路径。密码由 keytool 在终端交互询问。
& 'C:\Program Files\Java\jdk-17\bin\keytool.exe' -genkeypair -v -storetype JKS -keystore $newKey -alias lizhang -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000
if ($LASTEXITCODE -ne 0) { throw '创建未完成，请本人检查本地提示；不要自动重试或覆盖文件。' }
```

## 本机 Gradle 签名配置

将 `release/signing.properties.example` 复制到仓库根目录 `release-signing.local.properties`（已忽略），仅填写非秘密字段：

```properties
storeFile=D:/AndroidKeys/LizhangApp/lizhang-app-signing-2026.jks
keyAlias=lizhang
storeType=JKS
```

也可把此配置放在仓库外，通过当前进程变量 `LIZHANG_SIGNING_PROPERTIES` 指定其路径。`storeType` 可不填，由 JDK 自动识别，不能从 `.jks` 扩展名推断实际格式。Windows properties 路径统一用正斜杠。

元数据还支持当前进程环境变量，优先于配置文件：

| 环境变量 | 内容 |
| --- | --- |
| `LIZHANG_SIGNING_STORE_FILE` | 仓库外密钥库路径 |
| `LIZHANG_SIGNING_KEY_ALIAS` | 所选长期密钥的别名 |
| `LIZHANG_SIGNING_STORE_TYPE` | 可选的实际密钥库类型 |
| `LIZHANG_SIGNING_STORE_PASSWORD` | 当前进程中临时输入的密钥库密码 |
| `LIZHANG_SIGNING_KEY_PASSWORD` | 当前进程中临时输入的 Key 密码 |

密码不能放在 properties、Gradle 参数或源码中，也不要通过 `setx` 持久化到用户或系统环境。不要记录终端会话、开启调试日志、构建扫描或让凭据进入云端构建。

**以下仅供未来备案、身份和正文全部批准后的本人本地构建使用，本轮不运行。** 在本机终端隐藏输入密码，使用无持久守护进程、无配置缓存、无构建扫描的命令，并在结束后清除当前进程变量：

```powershell
$storeSecret = Read-Host '密钥库密码（隐藏输入）' -AsSecureString
$keySecret = Read-Host 'Key 密码（隐藏输入）' -AsSecureString
try {
    $env:LIZHANG_SIGNING_STORE_PASSWORD = [Net.NetworkCredential]::new('', $storeSecret).Password
    $env:LIZHANG_SIGNING_KEY_PASSWORD = [Net.NetworkCredential]::new('', $keySecret).Password
    .\gradlew.bat --no-daemon --no-configuration-cache --no-scan '-PofficialRelease=true' :app:assembleRelease
    if ($LASTEXITCODE -ne 0) { throw '正式构建被阻止或失败，请本人核对本机门禁信息。' }
} finally {
    [Environment]::SetEnvironmentVariable('LIZHANG_SIGNING_STORE_PASSWORD', $null, 'Process')
    [Environment]::SetEnvironmentVariable('LIZHANG_SIGNING_KEY_PASSWORD', $null, 'Process')
    $storeSecret.Dispose()
    $keySecret.Dispose()
}
```

Gradle 仅在 `officialRelease=true` 时为 Release 绑定正式 `signingConfigs`。保留原有 `verifyOfficialReleaseContent`，并新增 `verifyReleaseSigning`：检查完整配置、仓库外路径、非 Debug、RSA 公钥、证书有效期，以及实际公开证书 SHA-256 与本人批准的指纹一致。配置错误仅输出固定提示，不附带底层异常。缺配置时不会回退到 Debug 密钥或产出正式签名 APK。正式构建拒绝配置缓存，避免签名参数被缓存到磁盘。

普通 Release 继续为明确的未签名候选 `app-release-unsigned.apk`，版本名带 `rc2`，不能安装或当正式发行包；Debug 行为不变。直接调用 Release 打包、签名任务也连接正式签名门禁。`.gitignore` 额外保护密钥、证书、私有配置和本地备案导出，但忽略规则不能替代本人保管，禁止强制添加这些文件。

## 导出真实备案信息（无需构建 APK）

创建新密钥后，在本分支工作树根目录的 Windows PowerShell / Windows Terminal 中运行：

```powershell
.\scripts\export-android-filing-info.ps1 `
    -KeyStorePath 'D:\AndroidKeys\LizhangApp\lizhang-app-signing-2026.jks' `
    -KeyAlias 'lizhang' `
    -JdkHome 'C:\Program Files\Java\jdk-17'
```

脚本隐藏询问密钥库密码；**不需要 Key 密码**。密码只在内存中短暂传给本机 keytool 子进程环境，不出现在命令行、父进程环境、文件或日志；失败不重试。脚本先使用 JDK `keytool -exportcert` 导出单张公开 X.509 证书，再通过 JDK 17 的 Java 源码启动模式解析公开数据。无需 JadxGUI、OpenSSL、额外下载或管理员权限。不会修改原 JKS。

若已有本人导出的单张公开证书，也可不打开密钥库：

```powershell
.\scripts\export-android-filing-info.ps1 -PublicCertificatePath 'D:\PrivateFiling\lizhang-public.cer' -KeyAlias 'lizhang' -JdkHome 'C:\Program Files\Java\jdk-17'
```

此模式的别名由本人提供，仅作来源标记；X.509 证书自身不包含密钥库别名，也不能据此证明私钥仍可用。只接受单张公开证书，不输入私钥、JKS、P12 或 APK。

默认输出固定为 **`build/filing/android-filing-info.txt`**，脚本核对该路径受 Git 忽略后才导出。临时公开证书在本地解析完成后清理。只有本次完整成功才更新报告；失败后历史报告仍可能存在，应检查生成时间，不能当作本次结果。所有输出只留在本地，不能自动上传。

| 输出字段 | 用途 / 格式 |
| --- | --- |
| `Package Name` | `com.yangsong.lizhang` |
| `RSA Modulus` | 完整原始正整数，默认十进制，并显式标明 `Decimal (base 10)` |
| `RSA Modulus Hex` | 同一模数的十六进制，无 `0x`、冒号、空格或 ASN.1 符号填充 `00` |
| `RSA Modulus Decimal` | 同一模数完整十进制，供交叉核对 |
| `Certificate MD5` | 同一张 X.509 证书 DER 编码的 32 位十六进制摘要，无冒号或空格 |
| `Certificate SHA-256` | 同一张证书 DER 编码的 64 位十六进制摘要 |
| `Certificate Subject` / `Certificate Validity` | 主体和 UTC 有效期 |
| `Key Alias` / `Key Size` | 所选别名与实际 RSA 位数 |

阿里云官方说明中的 Android 公钥对应签名证书 RSA Modulus，MD5 字段填写 **证书 MD5 的十六进制值**。公钥字段按当前控制台的进制要求选择完整 Decimal 或 Hex；不能将十进制数字当成十六进制，也不能填写 Base64 证书全文。模数由 `RSAPublicKey.getModulus()` 的正整数直接转换，去除的是编码符号填充，不是任意截断有效模数。

## 包名与 APK 证书一致性边界

证书本身没有 Android `applicationId`。本报告中的包名来自项目约定，本轮已静态核对 Gradle；没有构建 APK，因此尚不能宣称最终 APK 的清单与签名已核验。

未来由用户在本机最终 APK 上使用 SDK `apkanalyzer manifest application-id` 核对包名，用 `apksigner verify --verbose --print-certs` 核对实际签名和同一张证书 SHA-256，且与本次报告及本人批准记录一致后，才能确认包名与签名链路一致。用同一 RSA 公钥重签发一张不同证书也可能改变 MD5 / SHA-256；不能仅核对模数。

## 本轮执行边界

仅做源码静态审阅、PowerShell 语法解析、JDK 公开证书解析工具的源码编译及 Git 差异/忽略规则核对。未执行产品测试、Lint、浏览器自动化、模拟器、实机或 APK/AAB 构建；不把工具源码语法核对称为产品测试通过。全部产品验证与最终验收由用户本人完成。没有改动法律正文、Room、页面、版本代码、网站或 ECS，没有上传 JKS、APK 或任何签名秘密，不合并 main。

参考：

- [阿里云：填写 App 特征信息](https://help.aliyun.com/zh/icp-filing/basic-icp-service/user-guide/fill-in-app-feature-information)
- [Android 官方：应用签名与 Play App Signing](https://developer.android.com/studio/publish/app-signing)
- [JDK 官方：keytool 的公开证书导出与密码环境参数](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html)
