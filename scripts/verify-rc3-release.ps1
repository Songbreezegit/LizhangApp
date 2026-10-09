# 检查具体 Release APK 的候选版本、权限、测试隔离、离线正文及可选正式签名。
[CmdletBinding()]
param(
    [string]$ApkPath = '',
    [string]$ApkAnalyzerPath = 'D:\Android\Sdk\cmdline-tools\latest\bin\apkanalyzer.bat',
    [string]$ApkSignerPath = 'D:\Android\Sdk\build-tools\36.0.0\apksigner.bat',
    [string]$ZipAlignPath = 'D:\Android\Sdk\build-tools\36.0.0\zipalign.exe',
    [switch]$OfficialRelease,
    [switch]$BeforeSigning,
    [string]$ExpectedCertificateSha256 = '',
    [int]$ExpectedVersionCode = 23
)

$ErrorActionPreference = 'Stop'
if ($BeforeSigning -and !$OfficialRelease) { throw '签名前核验仅用于已批准的正式未签名输入。' }
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (!$ApkPath) { $ApkPath = Join-Path $repoRoot 'app\build\outputs\apk\release\app-release-unsigned.apk' }
$apkFile = Get-Item -LiteralPath $ApkPath
foreach ($tool in @($ApkAnalyzerPath, $ApkSignerPath, $ZipAlignPath)) {
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
$expectedVersion = if ($OfficialRelease) { '1.0.0' } else { '1.0.0-rc3' }
if (!$OfficialRelease -and $ExpectedVersionCode -ne 23) { throw 'RC3 候选版本代码必须为 23。' }
if ($applicationId -ne 'com.yangsong.lizhang' -or $versionName -ne $expectedVersion -or [int]$versionCode -ne $ExpectedVersionCode) {
    throw "版本不符合本轮检查目标：$applicationId / $versionName / $versionCode。"
}
if ((Invoke-Analyzer @('manifest', 'min-sdk')) -ne '26' -or (Invoke-Analyzer @('manifest', 'target-sdk')) -ne '36') {
    throw 'Release 的 SDK 范围发生变化，需要重新审查。'
}
if ((Invoke-Analyzer @('manifest', 'debuggable')) -ne 'false') { throw 'Release APK 仍可调试。' }
& $ZipAlignPath -c -P 16 4 $apkFile.FullName | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Release APK 对齐验证失败。' }
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

$legalDigest = & (Join-Path $PSScriptRoot 'legal-content-digest.ps1')
$websiteDigest = & (Join-Path $PSScriptRoot 'website-content-digest.ps1')
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($apkFile.FullName)
try {
    $bindingEntry = $archive.GetEntry('assets/release-content-binding.properties')
    if (!$bindingEntry) { throw 'APK 缺少编译时法律内容绑定，拒绝旧产物。' }
    $reader = [IO.StreamReader]::new($bindingEntry.Open(), [Text.Encoding]::UTF8)
    try { $bindingText = $reader.ReadToEnd() } finally { $reader.Dispose() }
    function Get-BindingValue([string]$Name) {
        $bindingMatches = [Regex]::Matches($bindingText, "(?m)^$([Regex]::Escape($Name))=([^\r\n]*)\r?$")
        if ($bindingMatches.Count -ne 1) { throw "APK 编译时内容绑定的 $Name 缺失或重复。" }
        $bindingMatches[0].Groups[1].Value
    }
    $expectedOfficial = if ($OfficialRelease) { 'true' } else { 'false' }
    $metadataText = [IO.File]::ReadAllText((Join-Path $repoRoot 'app\src\main\assets\legal\metadata.properties'))
    $policyVersion = [Regex]::Match($metadataText, '(?m)^policy_version=([^\r\n]*)').Groups[1].Value
    if ((Get-BindingValue 'legal_content_sha256') -ne $legalDigest.legal_content_sha256 -or
        (Get-BindingValue 'website_content_sha256') -ne $websiteDigest.website_content_sha256 -or
        (Get-BindingValue 'is_official_release') -ne $expectedOfficial -or
        (Get-BindingValue 'version_name') -ne $expectedVersion -or
        (Get-BindingValue 'version_code') -ne "$ExpectedVersionCode" -or
        (Get-BindingValue 'policy_version') -ne $policyVersion) { throw 'APK 编译时法律资源、网站内容、版本或正式模式与当前批准输入不一致。' }
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
    $hasV1Signature = $false
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '^META-INF/[^/]+\.(RSA|DSA|EC|SF)$') { $hasV1Signature = $true }
        if ($entry.FullName -match '(?i)(?:^|/)(?:local\.properties|[^/]*(?:signing|keystore)[^/]*\.properties|\.env(?:\.[^/]+)?|[^/]+\.(?:jks|keystore|p12|pfx|pem|key|password))$') {
            throw 'APK 包含本机配置或密钥文件。'
        }
    }
} finally { $archive.Dispose() }
# 即使已有签名损坏、verify 返回非零，也拒绝将其误当全新未签名输入。
$apkBytes = [IO.File]::ReadAllBytes($apkFile.FullName)
$endOffset = -1
for ($offset = $apkBytes.Length - 22; $offset -ge [Math]::Max(0, $apkBytes.Length - 65557); $offset--) {
    if ([BitConverter]::ToUInt32($apkBytes, $offset) -eq 0x06054b50 -and
        $offset + 22 + [BitConverter]::ToUInt16($apkBytes, $offset + 20) -eq $apkBytes.Length) { $endOffset = $offset; break }
}
if ($endOffset -lt 0) { throw 'APK ZIP 目录结构无效。' }
$centralOffset = [BitConverter]::ToUInt32($apkBytes, $endOffset + 16)
if ($centralOffset -gt $apkBytes.Length -or $centralOffset -lt 16) { throw 'APK ZIP 目录偏移无效。' }
$hasSigningBlock = [Text.Encoding]::ASCII.GetString($apkBytes, $centralOffset - 16, 16) -eq 'APK Sig Block 42'

$signatureOutput = & $ApkSignerPath verify --verbose --print-certs $apkFile.FullName 2>&1
$signatureExitCode = $LASTEXITCODE
$signatureText = $signatureOutput -join "`n"
& (Join-Path $repoRoot 'gradlew.bat') -p $repoRoot --no-daemon --console=plain :app:verifyLegalContentConsistency | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'APK内容的App与网站一致性检查失败。' }
if ($OfficialRelease) {
    & (Join-Path $repoRoot 'gradlew.bat') -p $repoRoot --no-daemon --console=plain "-PofficialVersionCode=$ExpectedVersionCode" :app:verifyOfficialReleaseContent | Out-Host
    if ($LASTEXITCODE -ne 0) { throw '正式 APK 的完整发布批准检查失败。' }
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
    if ($BeforeSigning -and ($signatureExitCode -eq 0 -or $hasV1Signature -or $hasSigningBlock)) { throw '签名前输入已有签名材料，拒绝替换其身份。' }
    if (!$BeforeSigning -and $signatureExitCode -ne 0) { throw '正式 APK 的签名验证失败。' }
    $actualFingerprints = @([Regex]::Matches($signatureText, 'certificate SHA-256 digest:\s*([a-fA-F0-9]{64})') |
        ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() })
    if (!$BeforeSigning -and ($actualFingerprints.Count -ne 1 -or $actualFingerprints[0] -ne $ExpectedCertificateSha256.ToLowerInvariant())) {
        throw '签名证书与本人批准的既有签名身份不一致。'
    }
    if (!$BeforeSigning -and $signatureText -notmatch 'Verified using v2 scheme.*true') { throw '正式 APK 未通过 v2 签名验证。' }
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
    website_content_sha256 = $websiteDigest.website_content_sha256
    build_content_binding_verified = $true
    signature_verified = ($signatureExitCode -eq 0)
    signature_material_present = ($hasV1Signature -or $hasSigningBlock)
    alignment_verified = $true
    signature_identity_checked = ([bool]$OfficialRelease -and !$BeforeSigning)
    before_signing = [bool]$BeforeSigning
    candidate = !$OfficialRelease
    device_tests_run = $false
}
