# 检查具体 Release APK 的候选版本、权限、测试隔离、离线正文及可选正式签名。
[CmdletBinding()]
param(
    [string]$ApkPath = '',
    [string]$ApkAnalyzerPath = 'D:\Android\Sdk\cmdline-tools\latest\bin\apkanalyzer.bat',
    [string]$ApkSignerPath = 'D:\Android\Sdk\build-tools\36.0.0\apksigner.bat',
    [switch]$OfficialRelease,
    [string]$ExpectedCertificateSha256 = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (!$ApkPath) { $ApkPath = Join-Path $repoRoot 'app\build\outputs\apk\release\app-release-unsigned.apk' }
$apkFile = Get-Item -LiteralPath $ApkPath
foreach ($tool in @($ApkAnalyzerPath, $ApkSignerPath)) {
    if (!(Test-Path -LiteralPath $tool -PathType Leaf)) { throw "缺少 Android SDK 工具：$tool" }
}
function Invoke-Analyzer([string[]]$AnalyzerArguments) {
    $result = & $ApkAnalyzerPath @AnalyzerArguments $apkFile.FullName 2>&1
    if ($LASTEXITCODE -ne 0) { throw "APK 检查失败：$($AnalyzerArguments -join ' ')；$($result -join ' ')" }
    # 项目内临时 JVM 参数会使 Java 输出启动提示；保留错误，仅去掉标准环境提示行。
    return (($result | Where-Object { $_ -notmatch '^Picked up (?:JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS):' }) -join "`n").Trim()
}
$applicationId = Invoke-Analyzer @('manifest', 'application-id')
$versionName = Invoke-Analyzer @('manifest', 'version-name')
$versionCode = Invoke-Analyzer @('manifest', 'version-code')
$expectedVersion = if ($OfficialRelease) { '1.0.0' } else { '1.0.0-rc2' }
if ($applicationId -ne 'com.yangsong.lizhang' -or $versionName -ne $expectedVersion -or $versionCode -ne '23') {
    throw "版本不符合本轮检查目标：$applicationId / $versionName / $versionCode。"
}
if ((Invoke-Analyzer @('manifest', 'min-sdk')) -ne '26' -or (Invoke-Analyzer @('manifest', 'target-sdk')) -ne '36') {
    throw 'Release 的 SDK 范围发生变化，需要重新审查。'
}
if ((Invoke-Analyzer @('manifest', 'debuggable')) -ne 'false') { throw 'Release APK 仍可调试。' }
$manifest = Invoke-Analyzer @('manifest', 'print')
if ($manifest -match 'android:testOnly="true"|GiftSaveTestActivity|AndroidJUnitRunner|<instrumentation\b') {
    throw 'Release Manifest 中发现测试宿主或测试专用标志。'
}
$permissions = @((Invoke-Analyzer @('manifest', 'permissions')) -split "`r?`n" | Where-Object { $_ })
$allowedPermissions = @('android.permission.READ_CONTACTS', 'android.permission.POST_NOTIFICATIONS',
    'android.permission.RECEIVE_BOOT_COMPLETED', 'com.yangsong.lizhang.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION')
foreach ($permission in $permissions) {
    if ($permission -notin $allowedPermissions) { throw "Release 出现未审查权限：$permission" }
}
foreach ($requiredPermission in $allowedPermissions[0..2]) {
    if ($requiredPermission -notin $permissions) { throw "Release 缺少已有功能权限：$requiredPermission" }
}
$definedDex = Invoke-Analyzer @('dex', 'packages', '--defined-only')
if ($definedDex -match '(?m)^\s*C\s+.*(?:org\.junit\.|androidx\.test\.|com\.yangsong\.lizhang\..*(?:TestActivity|InstrumentedTest|ViewModelTest)|androidx\.compose\.ui\.tooling\.(?:ComposeViewAdapter|PreviewActivity))') {
    throw 'Release DEX 定义中发现测试或调试宿主；禁止忽略该失败。'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($apkFile.FullName)
try {
    foreach ($name in @('privacy.md', 'terms.md', 'help.md', 'metadata.properties')) {
        $entry = $archive.GetEntry("assets/legal/$name")
        if (!$entry) { throw "APK 缺少完整离线正文：$name" }
        $stream = $entry.Open()
        $hashAlgorithm = [Security.Cryptography.SHA256]::Create()
        try {
            $assetDigest = -join ($hashAlgorithm.ComputeHash($stream) | ForEach-Object { $_.ToString('x2') })
        } finally { $stream.Dispose(); $hashAlgorithm.Dispose() }
        $sourcePath = Join-Path $repoRoot "app\src\main\assets\legal\$name"
        if ($assetDigest -ne (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()) {
            throw "APK 离线正文与当前源码不一致：$name" }
    }
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '(?i)(?:^|/)(?:local\.properties|[^/]+\.(?:jks|keystore|p12))$') {
            throw 'APK 包含本机配置或密钥文件。'
        }
    }
} finally { $archive.Dispose() }

$signatureOutput = & $ApkSignerPath verify --verbose --print-certs $apkFile.FullName 2>&1
$signatureExitCode = $LASTEXITCODE
$signatureText = $signatureOutput -join "`n"
$legalDigest = & (Join-Path $PSScriptRoot 'legal-content-digest.ps1')
if ($OfficialRelease) {
    $approvalSource = [IO.File]::ReadAllText((Join-Path $repoRoot 'release\legal-approval.properties'), [Text.Encoding]::UTF8)
    function Get-ApprovedValue([string]$Name) {
        $match = [Regex]::Match($approvalSource, "(?m)^$([Regex]::Escape($Name))\s*=\s*([^\r\n]*)\r?$")
        if (!$match.Success) { throw "正式 APK 缺少批准记录：$Name。" }
        return $match.Groups[1].Value.Trim()
    }
    if ((Get-ApprovedValue 'approval_status') -ne 'approved' -or
        (Get-ApprovedValue 'signing_identity_status') -ne 'confirmed' -or
        (Get-ApprovedValue 'approved_content_sha256') -ne $legalDigest.legal_content_sha256) {
        throw '正式 APK 的内容批准或签名身份记录不完整，或批准摘要与当前内容不一致。'
    }
    if ($ExpectedCertificateSha256 -notmatch '^[a-fA-F0-9]{64}$') { throw '正式签名检查必须提供本人已确认的既有证书 SHA-256 指纹。' }
    if ($ExpectedCertificateSha256.ToLowerInvariant() -ne (Get-ApprovedValue 'signing_certificate_sha256').ToLowerInvariant()) {
        throw '手工输入的证书指纹与发布批准文件不一致。'
    }
    if ($signatureExitCode -ne 0) { throw '正式 APK 的签名验证失败。' }
    $actualFingerprints = @([Regex]::Matches($signatureText, 'certificate SHA-256 digest:\s*([a-fA-F0-9]{64})') |
        ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() })
    if ($actualFingerprints.Count -ne 1 -or $actualFingerprints[0] -ne $ExpectedCertificateSha256.ToLowerInvariant()) {
        throw '签名证书与本人批准的既有签名身份不一致。'
    }
    if ($signatureText -notmatch 'Verified using v2 scheme.*true') { throw '正式 APK 未通过 v2 签名验证。' }
}
[PSCustomObject]@{
    apk = $apkFile.FullName
    application_id = $applicationId
    version_name = $versionName
    version_code = [int]$versionCode
    sha256 = (Get-FileHash -LiteralPath $apkFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    bytes = $apkFile.Length
    permissions = $permissions
    legal_content_sha256 = $legalDigest.legal_content_sha256
    legal_input_count = $legalDigest.input_count
    signature_verified = ($signatureExitCode -eq 0)
    signature_identity_checked = [bool]$OfficialRelease
    candidate = !$OfficialRelease
    device_tests_run = $false
}
