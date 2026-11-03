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
$expectedVersion = '1.0.0'
$expectedVersionCode = '25'
if ($applicationId -ne 'com.yangsong.lizhang' -or $versionName -ne $expectedVersion -or $versionCode -ne $expectedVersionCode) {
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
$legalAssets = @{}
$filingNumberFound = $false
$filingSource = [IO.File]::ReadAllText((Join-Path $repoRoot 'release/app-filing.properties'), [Text.Encoding]::UTF8)
$filingMatch = [Regex]::Match($filingSource, '(?m)^app_filing_number=([^\r\n]+)\r?$')
if (!$filingMatch.Success -or $filingMatch.Groups[1].Value -ne '渝ICP备2026025309号-2A') {
    throw 'APP 备案配置与本次正式安装包目标不一致。'
}
$filingNumber = $filingMatch.Groups[1].Value
try {
    if ($archive.GetEntry('assets/legal/metadata.properties')) {
        throw 'APK 包含内部法律审批元数据 assets/legal/metadata.properties。'
    }
    foreach ($name in @('privacy.md', 'terms.md', 'help.md')) {
        $entry = $archive.GetEntry("assets/legal/$name")
        if (!$entry) { throw "APK 缺少完整离线正文：$name" }
        $stream = $entry.Open()
        $hashAlgorithm = [Security.Cryptography.SHA256]::Create()
        try {
            $assetDigest = -join ($hashAlgorithm.ComputeHash($stream) | ForEach-Object { $_.ToString('x2') })
        } finally { $stream.Dispose(); $hashAlgorithm.Dispose() }
        # Markdown 在构建时展开日期等公开字段，必须比对本次构建的离线资产，不能比对原始模板。
        $sourcePath = Join-Path $repoRoot "app\build\generated\offlineLegalAssets\legal\$name"
        if ($assetDigest -ne (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()) {
            throw "APK 离线正文与本次生成资产不一致：$name" }
        $reader = [IO.StreamReader]::new($entry.Open(), [Text.Encoding]::UTF8)
        try { $legalAssets[$name] = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ([string]::IsNullOrWhiteSpace($legalAssets[$name]) -or
            $legalAssets[$name] -match '\{\{|\}\}|(?i)\bpending\b|approval_status|approved_content_sha256|source_branch|code_baseline|signing_certificate_sha256|legal-final-v') {
            throw "APK 离线正文包含未展开模板、内部记录或未批准内容：$name"
        }
    }
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '(?i)(?:^|/)(?:local\.properties|release-signing[^/]*\.properties|(?:key|keystore)\.properties|[^/]+\.(?:jks|keystore|p12|pfx|key|pem))$') {
            throw 'APK 包含本机配置或密钥文件。'
        }
        # 公钥签名块 META-INF/*.RSA 不属于私钥；仅拒绝实际私钥文件头。
        $stream = $entry.Open()
        try {
            $prefix = [byte[]]::new(256)
            $count = $stream.Read($prefix, 0, $prefix.Length)
            if ([Text.Encoding]::ASCII.GetString($prefix, 0, $count) -match '-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----') {
                throw 'APK 包含私钥内容。'
            }
        } finally { $stream.Dispose() }
        if ($entry.FullName -match '^classes(?:\d+)?\.dex$') {
            # DEX 字符串池的中文 BMP 字符与 UTF-8 字节相同，核对实际 APK 中的完整编号。
            $reader = [IO.StreamReader]::new($entry.Open(), [Text.Encoding]::UTF8)
            try { if ($reader.ReadToEnd().Contains($filingNumber)) { $filingNumberFound = $true } }
            finally { $reader.Dispose() }
        }
    }
    if (!$filingNumberFound) { throw 'APK DEX 中未找到完整 APP 备案号。' }
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
        (Get-ApprovedValue 'filing_status') -ne 'confirmed' -or
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
    if ($signatureText -notmatch 'Signer #1 key algorithm:\s*RSA\s*(?:\r?\n|$)' -or
        $signatureText -notmatch 'Signer #1 key size \(bits\):\s*2048\s*(?:\r?\n|$)') {
        throw '正式 APK 未使用本次已备案的 RSA 2048 签名身份。'
    }
    $metadataSource = [IO.File]::ReadAllText((Join-Path $repoRoot 'app/src/main/assets/legal/metadata.properties'), [Text.Encoding]::UTF8)
    $effectiveDate = Get-ApprovedValue 'effective_date'
    $policyVersion = Get-ApprovedValue 'policy_version'
    $operatorName = Get-ApprovedValue 'operator_name'
    foreach ($name in @('effective_date', 'policy_version', 'operator_name', 'approval_status')) {
        if ($metadataSource -notmatch "(?m)^$name=$([Regex]::Escape((Get-ApprovedValue $name)))\r?$") {
            throw "正式 APK 的法律元数据与批准记录不一致：$name"
        }
    }
    foreach ($name in @('privacy.md', 'terms.md')) {
        if (!$legalAssets[$name].Contains("版本：$expectedVersion") -or
            !$legalAssets[$name].Contains("生效日期：$effectiveDate") -or
            !$legalAssets[$name].Contains("运营主体：$operatorName（个人开发者）")) {
            throw "正式 APK 的法律版本、日期或运营主体不完整：$name"
        }
    }
}
[PSCustomObject]@{
    apk = $apkFile.FullName
    application_id = $applicationId
    version_name = $versionName
    version_code = [int]$versionCode
    min_sdk = 26
    target_sdk = 36
    sha256 = (Get-FileHash -LiteralPath $apkFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    bytes = $apkFile.Length
    permissions = $permissions
    legal_content_sha256 = $legalDigest.legal_content_sha256
    legal_input_count = $legalDigest.input_count
    signature_verified = ($signatureExitCode -eq 0)
    signature_identity_checked = [bool]$OfficialRelease
    certificate_sha256 = if ($OfficialRelease) { $actualFingerprints[0] } else { $null }
    signing_algorithm = if ($OfficialRelease) { 'RSA' } else { $null }
    signing_key_bits = if ($OfficialRelease) { 2048 } else { $null }
    app_filing_number = $filingNumber
    app_filing_number_found = $filingNumberFound
    legal_assets_checked = @('privacy.md', 'terms.md', 'help.md')
    internal_legal_metadata_absent = $true
    candidate = !$OfficialRelease
    device_tests_run = $false
}
