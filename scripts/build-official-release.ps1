#requires -Version 7.2
# 仅在本人 Windows 终端运行；密码隐藏输入，只供本机 JDK/Gradle 使用，不上传产物。
[CmdletBinding()]
param(
    [string]$SigningPropertiesPath = $env:LIZHANG_SIGNING_PROPERTIES,
    [Parameter(Mandatory = $true)][string]$FilingReportPath,
    [string]$DeliveryDirectory = '',
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$AndroidSdkRoot = $env:ANDROID_HOME
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest
# 每次必须由本人隐藏输入，不继承其他会话遗留的签名密码。
[Environment]::SetEnvironmentVariable('LIZHANG_SIGNING_STORE_PASSWORD', $null, 'Process')
[Environment]::SetEnvironmentVariable('LIZHANG_SIGNING_KEY_PASSWORD', $null, 'Process')
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runDirectory = Join-Path $repoRoot 'build/official-release'
[IO.Directory]::CreateDirectory($runDirectory) | Out-Null
$receiptPath = Join-Path $runDirectory 'status.json'
$stage = 'preflight'
$storeSecret = $null
$keySecret = $null
$publicCertificate = Join-Path $runDirectory ([Guid]::NewGuid().ToString('N') + '.cer')
$savedEnvironment = @{}
$publicEnvironmentNames = @('JAVA_HOME', 'ANDROID_HOME', 'ANDROID_SDK_ROOT', 'TEMP', 'TMP',
    'JAVA_TOOL_OPTIONS', 'LIZHANG_SIGNING_PROPERTIES', 'LIZHANG_SIGNING_STORE_FILE',
    'LIZHANG_SIGNING_KEY_ALIAS', 'LIZHANG_SIGNING_STORE_TYPE')

function Write-Status([string]$Phase, $Result = $null) {
    [ordered]@{ phase = $Phase; updated_at = [DateTimeOffset]::Now.ToString('o'); result = $Result } |
        ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $receiptPath -Encoding utf8
}
function Read-PublicProperties([string]$Path) {
    $values = @{}
    foreach ($line in [IO.File]::ReadAllLines($Path, [Text.Encoding]::UTF8)) {
        if ($line -match '^\s*(?:[#!]|$)') { continue }
        if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') { throw '公开配置格式不正确。' }
        if ($values.ContainsKey($Matches[1])) { throw '公开配置包含重复字段。' }
        $values[$Matches[1]] = $Matches[2]
    }
    return $values
}
function Read-FilingReport([string]$Path) {
    $text = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8)
    $result = @{}
    foreach ($field in @('Package Name', 'Key Alias', 'Key Algorithm', 'Key Size', 'Certificate MD5', 'Certificate SHA-256')) {
        $match = [Regex]::Match($text, "(?m)^$([Regex]::Escape($field)):\s*([^\r\n]+)\r?$")
        if (!$match.Success) { throw "备案公开报告缺少字段：$field" }
        $result[$field] = $match.Groups[1].Value.Trim()
    }
    if ($result['Package Name'] -ne 'com.yangsong.lizhang' -or $result['Key Algorithm'] -ne 'RSA' -or
        $result['Key Size'] -ne '2048' -or $result['Certificate SHA-256'] -notmatch '^[A-Fa-f0-9]{64}$' -or
        $result['Certificate MD5'] -notmatch '^[A-Fa-f0-9]{32}$') { throw '备案报告不符合礼账既有 RSA 2048 签名身份。' }
    return $result
}
function Set-SecretEnvironment([string]$Name, [Security.SecureString]$Secret) {
    $buffer = [IntPtr]::Zero
    try {
        $buffer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
        [Environment]::SetEnvironmentVariable($Name, [Runtime.InteropServices.Marshal]::PtrToStringBSTR($buffer), 'Process')
    } finally {
        if ($buffer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($buffer) }
    }
}
function Export-PublicCertificate($Configuration, [Security.SecureString]$Secret) {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = Join-Path $JdkHome 'bin/keytool.exe'
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in @('-exportcert', '-keystore', $Configuration.storeFile, '-alias', $Configuration.keyAlias,
        '-file', $publicCertificate, '-storepass:env', 'LIZHANG_FILING_STORE_PASSWORD')) { $start.ArgumentList.Add($argument) }
    if ($Configuration.storeType) { $start.ArgumentList.Add('-storetype'); $start.ArgumentList.Add($Configuration.storeType) }
    $buffer = [IntPtr]::Zero
    $process = $null
    try {
        $buffer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
        $start.Environment['LIZHANG_FILING_STORE_PASSWORD'] = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($buffer)
        $process = [Diagnostics.Process]::new()
        $process.StartInfo = $start
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        [void]$stdout.GetAwaiter().GetResult()
        [void]$stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw '公开证书导出失败。' }
    } catch {
        throw '公开证书导出失败；请在本机核对配置和密码，不自动重试。'
    } finally {
        [void]$start.Environment.Remove('LIZHANG_FILING_STORE_PASSWORD')
        if ($buffer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($buffer) }
        if ($null -ne $process) { $process.Dispose() }
    }
}

Push-Location $repoRoot
try {
    Write-Status $stage
    if ($env:OS -ne 'Windows_NT') { throw '正式签名入口仅供本人 Windows 本机使用。' }
    if ((& git status --porcelain --untracked-files=normal)) { throw '请先提交本轮源码及脚本；不覆盖未提交工作。' }
    if ($LASTEXITCODE -ne 0 -or (& git branch --show-current) -ne 'release/v1.0.0-signed-apk') {
        throw '必须在独立正式签名分支执行。'
    }
    if (!$SigningPropertiesPath) { $SigningPropertiesPath = Join-Path $repoRoot 'release-signing.local.properties' }
    try { $configuration = Read-PublicProperties $SigningPropertiesPath } catch { throw '无法读取本机非秘密签名配置。' }
    foreach ($name in $configuration.Keys) {
        if ($name -notin 'storeFile', 'keyAlias', 'storeType') { throw '签名配置只能包含路径、别名和可选类型，禁止保存密码。' }
    }
    # 未声明类型时交给 JDK/Gradle 自动识别，不能按 .jks 扩展名推断格式。
    if (!$configuration.ContainsKey('storeType')) { $configuration.storeType = '' }
    if (!$configuration.storeFile -or !$configuration.keyAlias -or $configuration.storeFile.Contains('\') -or
        $configuration.keyAlias -ieq 'androiddebugkey') { throw '请按签名配置示例填写绝对路径（正斜杠）与正式别名。' }
    try {
        $store = Get-Item -LiteralPath $configuration.storeFile
        if ($store.PSIsContainer -or $store.Name -ieq 'debug.keystore' -or ![IO.Path]::IsPathRooted($configuration.storeFile)) { throw '路径无效。' }
        $ancestor = $store.Directory
        while ($null -ne $ancestor) {
            if (Test-Path -LiteralPath (Join-Path $ancestor.FullName '.git')) { throw '密钥必须位于 Git 仓库之外。' }
            $ancestor = $ancestor.Parent
        }
    } catch { throw '无法确认既有密钥库位于仓库之外；请在本机核对，不输出实际路径。' }
    $history = Read-FilingReport $FilingReportPath
    if ([IO.Path]::GetFullPath($FilingReportPath) -ieq (Join-Path $repoRoot 'build/filing/android-filing-info.txt')) {
        throw '原备案报告必须与本次公开报告分开保存，不能覆盖历史依据。'
    }
    if ($history['Key Alias'] -ne $configuration.keyAlias) { throw '所选别名与原备案公开报告不一致。' }
    if (!$JdkHome) { $JdkHome = Split-Path (Split-Path (Get-Command java.exe).Source -Parent) -Parent }
    if (!$AndroidSdkRoot) { $AndroidSdkRoot = $env:ANDROID_SDK_ROOT }
    foreach ($tool in @((Join-Path $JdkHome 'bin/keytool.exe'), (Join-Path $JdkHome 'bin/java.exe'),
        (Join-Path $AndroidSdkRoot 'cmdline-tools/latest/bin/apkanalyzer.bat'), (Join-Path $AndroidSdkRoot 'build-tools/36.0.0/apksigner.bat'))) {
        if (!(Test-Path -LiteralPath $tool -PathType Leaf)) { throw '缺少本机 JDK 或 Android SDK 工具。' }
    }
    if (!$DeliveryDirectory) { $DeliveryDirectory = Join-Path $repoRoot 'build/deliveries/v1.0.0-official-release' }
    $DeliveryDirectory = [IO.Path]::GetFullPath($DeliveryDirectory)
    # 目录尚未存在时，从已有祖先确认 Git 忽略规则。
    $existingParent = $DeliveryDirectory
    while (!(Test-Path -LiteralPath $existingParent -PathType Container)) { $existingParent = Split-Path $existingParent -Parent }
    $deliveryRepository = & git -C $existingParent rev-parse --show-toplevel
    if ($LASTEXITCODE -ne 0) { throw '交付目录必须位于仓库的 Git 忽略目录内。' }
    & git -C $deliveryRepository check-ignore --quiet -- (Join-Path $DeliveryDirectory 'LiZhang-v1.0.0-25-release.apk')
    if ($LASTEXITCODE -ne 0) { throw '正式 APK 交付目录未被 Git 忽略。' }
    $deliveryApk = Join-Path $DeliveryDirectory 'LiZhang-v1.0.0-25-release.apk'
    if ((Test-Path -LiteralPath $deliveryApk) -or (Test-Path -LiteralPath (Join-Path $DeliveryDirectory 'release-report.md'))) {
        throw '交付目录已有 APK 或报告；停止以避免覆盖。'
    }
    $approvalPath = Join-Path $repoRoot 'release/legal-approval.properties'
    $approval = Read-PublicProperties $approvalPath
    $legalDigest = & (Join-Path $PSScriptRoot 'legal-content-digest.ps1')
    if ($approval.approval_status -ne 'approved' -or $approval.effective_date -ne '2026-11-03' -or
        $approval.policy_version -ne 'legal-final-v1.0.0-20261103' -or
        $approval.approved_content_sha256 -ne $legalDigest.legal_content_sha256) { throw '既有法律批准资料不完整或内容摘要发生变化。' }
    foreach ($name in $publicEnvironmentNames) { $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
    $commonGitDirectory = (& git rev-parse --path-format=absolute --git-common-dir).Trim()
    $tempDirectory = Join-Path (Split-Path $commonGitDirectory -Parent) 'build/sign-tmp'
    [IO.Directory]::CreateDirectory($tempDirectory) | Out-Null
    $env:JAVA_HOME = $JdkHome
    $env:ANDROID_HOME = $AndroidSdkRoot
    $env:ANDROID_SDK_ROOT = $AndroidSdkRoot
    $env:TEMP = $tempDirectory
    $env:TMP = $tempDirectory
    $env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=' + $tempDirectory.Replace('\', '/')
    $env:LIZHANG_SIGNING_PROPERTIES = [IO.Path]::GetFullPath($SigningPropertiesPath)
    $env:LIZHANG_SIGNING_STORE_FILE = $configuration.storeFile
    $env:LIZHANG_SIGNING_KEY_ALIAS = $configuration.keyAlias
    $env:LIZHANG_SIGNING_STORE_TYPE = $configuration.storeType
    $stage = 'awaiting-local-passwords'
    Write-Status $stage
    Write-Host '仅制作本地正式 APK；不上传、不发布、不创建密钥。'
    $storeSecret = Read-Host '请输入既有密钥库密码（隐藏输入）' -AsSecureString
    if ($storeSecret.Length -eq 0) { throw '未输入密钥库密码，停止。' }
    $keySecret = Read-Host '请输入 Key 密码（隐藏输入；与密钥库相同时直接回车）' -AsSecureString
    if ($keySecret.Length -eq 0) { $keySecret.Dispose(); $keySecret = $storeSecret.Copy() }
    $stage = 'checking-certificate'
    Write-Status $stage
    Export-PublicCertificate $configuration $storeSecret
    & (Join-Path $PSScriptRoot 'export-android-filing-info.ps1') -PublicCertificatePath $publicCertificate -KeyAlias $configuration.keyAlias -JdkHome $JdkHome | Out-Null
    $current = Read-FilingReport (Join-Path $repoRoot 'build/filing/android-filing-info.txt')
    foreach ($name in $history.Keys) {
        if ($current[$name] -ine $history[$name]) { throw "实际 X.509 证书与原备案公开报告不一致：$name；停止构建。" }
    }
    $certificateSha = $current['Certificate SHA-256'].ToLowerInvariant()
    Write-Host "原备案证书核对通过：RSA 2048；SHA-256 $certificateSha"
    # 本次本人已授权：仅在实际证书与原备案报告一致后确认以下三个字段。
    $updates = [ordered]@{ filing_status = 'confirmed'; signing_identity_status = 'confirmed'; signing_certificate_sha256 = $certificateSha }
    $approvalText = [IO.File]::ReadAllText($approvalPath, [Text.Encoding]::UTF8)
    foreach ($name in $updates.Keys) {
        if ($approval[$name] -ne 'pending' -and $approval[$name] -ine $updates[$name]) { throw "既有发行审批字段与本次核对结果冲突：$name" }
        $approvalText = [Regex]::Replace($approvalText, "(?m)^$name=[^\r\n]*", "$name=$($updates[$name])")
    }
    [IO.File]::WriteAllText($approvalPath, $approvalText, [Text.UTF8Encoding]::new($false))
    # 在密码进入 Gradle 环境前仅提交发行配置，使产物对应一个干净、明确的源码提交。
    & git diff --quiet -- release/legal-approval.properties
    if ($LASTEXITCODE -eq 1) {
        & git add -- release/legal-approval.properties
        if ($LASTEXITCODE -ne 0) { throw '发行配置暂存失败。' }
        & git commit -m 'chore(release): confirm filed certificate for v1.0.0 signing'
        if ($LASTEXITCODE -ne 0) { throw '发行配置提交失败。' }
    } elseif ($LASTEXITCODE -ne 0) { throw '无法检查发行配置差异。' }
    if ((& git status --porcelain --untracked-files=normal)) { throw '构建前工作区不是干净状态。' }
    $sourceSha = (& git rev-parse HEAD).Trim()
    $stage = 'building-official-apk'
    Write-Status $stage ([ordered]@{ source_git_sha = $sourceSha; certificate_sha256 = $certificateSha; certificate_matches_filing = $true })
    try {
        Set-SecretEnvironment 'LIZHANG_SIGNING_STORE_PASSWORD' $storeSecret
        Set-SecretEnvironment 'LIZHANG_SIGNING_KEY_PASSWORD' $keySecret
        & (Join-Path $repoRoot 'gradlew.bat') --no-daemon --no-configuration-cache --no-scan --offline --max-workers=1 --console=plain '-Pkotlin.compiler.execution.strategy=in-process' '-PofficialRelease=true' ':app:assembleRelease' 2>&1 |
            Tee-Object -FilePath (Join-Path $runDirectory 'gradle-build.log')
        $buildExitCode = $LASTEXITCODE
    } finally {
        [Environment]::SetEnvironmentVariable('LIZHANG_SIGNING_STORE_PASSWORD', $null, 'Process')
        [Environment]::SetEnvironmentVariable('LIZHANG_SIGNING_KEY_PASSWORD', $null, 'Process')
        $storeSecret.Dispose(); $storeSecret = $null
        $keySecret.Dispose(); $keySecret = $null
    }
    if ($buildExitCode -ne 0) { throw "正式 Release 构建失败，退出码 $buildExitCode；未交付 APK。" }
    $stage = 'verifying-official-apk'
    Write-Status $stage
    $apkPath = Join-Path $repoRoot 'app/build/outputs/apk/release/app-release.apk'
    $verification = & (Join-Path $PSScriptRoot 'verify-rc2-release.ps1') -ApkPath $apkPath -OfficialRelease -ExpectedCertificateSha256 $certificateSha `
        -ApkAnalyzerPath (Join-Path $AndroidSdkRoot 'cmdline-tools/latest/bin/apkanalyzer.bat') -ApkSignerPath (Join-Path $AndroidSdkRoot 'build-tools/36.0.0/apksigner.bat')
    if (!$verification.signature_identity_checked -or !$verification.signature_verified) { throw '正式 APK 验收未通过。' }
    if ((& git rev-parse HEAD).Trim() -ne $sourceSha -or (& git status --porcelain --untracked-files=normal)) { throw '构建期间源码发生变化，停止交付。' }
    [IO.Directory]::CreateDirectory($DeliveryDirectory) | Out-Null
    Copy-Item -LiteralPath $apkPath -Destination $deliveryApk
    if ((Get-FileHash -LiteralPath $deliveryApk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $verification.sha256) { throw '交付副本哈希不一致。' }
    $completedAt = [DateTimeOffset]::Now.ToString('o')
    $report = @"
# 礼账 v1.0.0 正式签名 APK 本地交付报告

- 源码 Git SHA：$sourceSha
- 分支：release/v1.0.0-signed-apk
- 构建完成时间：$completedAt
- applicationId：$($verification.application_id)
- versionName / versionCode：$($verification.version_name) / $($verification.version_code)
- minSdk / targetSdk：$($verification.min_sdk) / $($verification.target_sdk)
- APK 字节数：$($verification.bytes)
- APK SHA-256：$($verification.sha256)
- 证书 SHA-256：$certificateSha
- 证书 MD5：$($current['Certificate MD5'])
- 签名身份：原备案 RSA 2048，别名 $($configuration.keyAlias)；实际 X.509 指纹与原备案报告一致。
- APK 签名：apksigner 验证成功，v2 签名通过，单一签名证书与批准记录一致。
- Manifest 权限：$($verification.permissions -join '、')
- APP 备案号：$($verification.app_filing_number)，已在实际 APK 中核对；无 INTERNET 权限。
- 法律内容 SHA-256：$($verification.legal_content_sha256)
- 法律版本 / 生效日期：legal-final-v1.0.0-20261103 / 2026-11-03，批准摘要保持不变。
- 法律资产：三份完整已展开正文与本次生成资产哈希一致；无内部审批元数据或遗留模板。
- 发行隔离：不可调试，无 testOnly、仪器测试宿主、测试类定义、本机签名配置及密钥文件。
- 产物路径：$deliveryApk
- 原始签名 APK：$apkPath
- 构建日志（仅本机忽略目录）：$(Join-Path $runDirectory 'gradle-build.log')

本入口仅完成本机正式签名构建与 APK 静态验收，其他自动化检查见源码交付记录；未操作设备或重复 M1.4 实机验收，未修改网站、上传商店、公开分发或合并 main。后续商店资格、材料及是否提交由本人另行决定。
"@
    $report | Set-Content -LiteralPath (Join-Path $DeliveryDirectory 'release-report.md') -Encoding utf8
    $result = [ordered]@{ source_git_sha = $sourceSha; completed_at = $completedAt; delivery_apk = $deliveryApk;
        certificate_matches_filing = $true; certificate_md5 = $current['Certificate MD5']; verification = $verification }
    $result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $DeliveryDirectory 'verification.json') -Encoding utf8
    Write-Status 'complete' $result
    Write-Host "本地正式 APK 已通过校验：$deliveryApk"
} catch {
    Write-Status 'failed' ([ordered]@{ failed_stage = $stage })
    throw
} finally {
    [Environment]::SetEnvironmentVariable('LIZHANG_SIGNING_STORE_PASSWORD', $null, 'Process')
    [Environment]::SetEnvironmentVariable('LIZHANG_SIGNING_KEY_PASSWORD', $null, 'Process')
    if ($null -ne $storeSecret) { $storeSecret.Dispose() }
    if ($null -ne $keySecret) { $keySecret.Dispose() }
    if (Test-Path -LiteralPath $publicCertificate -PathType Leaf) { Remove-Item -LiteralPath $publicCertificate -Force }
    foreach ($name in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process') }
    Pop-Location
}
