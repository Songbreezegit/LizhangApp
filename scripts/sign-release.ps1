# 仅由开发者批准后手动调用；不创建密钥、不接收密码参数、不覆盖既有产物。
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$KeyStorePath,
    [Parameter(Mandatory)][string]$KeyAlias,
    [Parameter(Mandatory)][string]$ExpectedCertificateSha256,
    [Parameter(Mandatory)][int]$ExpectedVersionCode,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$BuildToolsDirectory = 'D:\Android\Sdk\build-tools\36.0.0',
    [string]$ApkAnalyzerPath = 'D:\Android\Sdk\cmdline-tools\latest\bin\apkanalyzer.bat',
    [string]$ApkPath = ''
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (!$ApkPath) { $ApkPath = Join-Path $repoRoot 'app\build\outputs\apk\release\app-release-unsigned.apk' }
if ($ExpectedCertificateSha256 -notmatch '^[a-fA-F0-9]{64}$') { throw '必须提供本人批准的正式证书 SHA-256。' }
if ($KeyAlias -ieq 'androiddebugkey') { throw '禁止把 Android 调试身份用作正式签名。' }
$keyFile = Get-Item -LiteralPath $KeyStorePath
if ($keyFile.PSIsContainer -or $keyFile.FullName.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw '正式密钥必须是已存在、本人授权且位于仓库外的文件。'
}
$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $outputPath) { throw '签名输出目录已存在，禁止覆盖。' }
function Test-InGitDirectory([string]$Directory) {
    $current = [IO.DirectoryInfo]::new($Directory)
    while ($current) {
        if (Test-Path -LiteralPath (Join-Path $current.FullName '.git')) { return $true }
        $current = $current.Parent
    }
    return $false
}
if ((Test-InGitDirectory $keyFile.DirectoryName) -or (Test-InGitDirectory $outputPath)) { throw '密钥和最终签名输出目录必须位于 Git 仓库外。' }
foreach ($name in @('zipalign.exe', 'apksigner.bat')) {
    if (!(Test-Path -LiteralPath (Join-Path $BuildToolsDirectory $name) -PathType Leaf)) { throw "缺少签名工具：$name" }
}
# 完整门禁必须先通过；操作者提供参数不能代替仓库中绑定内容摘要的真实批准。
$sourceCommit = & git -C $repoRoot rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw '无法核验正式签名的源码提交。' }
$workingChanges = & git -C $repoRoot status --porcelain --untracked-files=normal
if ($LASTEXITCODE -ne 0 -or $workingChanges) { throw '正式签名前须审查并提交全部发布源码，使用干净工作树。' }
& (Join-Path $repoRoot 'gradlew.bat') -p $repoRoot --no-daemon --console=plain "-PofficialVersionCode=$ExpectedVersionCode" :app:verifyOfficialReleaseContent
if ($LASTEXITCODE -ne 0) { throw '正式发布批准未通过，停止签名。' }
$approval = [IO.File]::ReadAllText((Join-Path $repoRoot 'release\legal-approval.properties'))
$match = [Regex]::Match($approval, '(?m)^signing_certificate_sha256=([a-fA-F0-9]{64})\r?$')
if (!$match.Success -or $match.Groups[1].Value.ToLowerInvariant() -ne $ExpectedCertificateSha256.ToLowerInvariant()) { throw '请求的正式证书与批准记录不一致。' }
# 在访问授权密钥前拒绝候选、Debug、旧版或与当前批准内容不一致的输入。
& (Join-Path $PSScriptRoot 'verify-rc3-release.ps1') -ApkPath $ApkPath -OfficialRelease -BeforeSigning -ExpectedVersionCode $ExpectedVersionCode -ExpectedCertificateSha256 $ExpectedCertificateSha256 -ApkSignerPath (Join-Path $BuildToolsDirectory 'apksigner.bat') -ZipAlignPath (Join-Path $BuildToolsDirectory 'zipalign.exe') -ApkAnalyzerPath $ApkAnalyzerPath | Out-Host
# keytool 交互询问密码；只核对授权别名的公共证书，不导出私钥。
$certificate = & keytool -list -v -keystore $keyFile.FullName -alias $KeyAlias
if ($LASTEXITCODE -ne 0) { throw '授权证书读取失败。' }
if (($certificate -join "`n") -match 'CN=Android Debug(?:,|\s|$)') { throw '授权别名指向 Android 调试证书，停止正式签名。' }
$fingerprint = [Regex]::Match(($certificate -join "`n"), 'SHA256:\s*([A-Fa-f0-9:]+)')
if (!$fingerprint.Success -or $fingerprint.Groups[1].Value.Replace(':', '').ToLowerInvariant() -ne $ExpectedCertificateSha256.ToLowerInvariant()) { throw '密钥公共证书与本人批准身份不一致，未执行签名。' }
New-Item -ItemType Directory -Path $outputPath | Out-Null
$aligned = Join-Path $outputPath 'Lizhang-1.0.0-aligned-unsigned.apk'
$signed = Join-Path $outputPath 'Lizhang-1.0.0-release.apk'
& (Join-Path $BuildToolsDirectory 'zipalign.exe') -P 16 -v 4 $ApkPath $aligned
if ($LASTEXITCODE -ne 0) { throw '签名前对齐失败。' }
# apksigner 自行交互询问密码，禁止将密码写在命令行或脚本日志。
& (Join-Path $BuildToolsDirectory 'apksigner.bat') sign --ks $keyFile.FullName --ks-key-alias $KeyAlias --out $signed $aligned
if ($LASTEXITCODE -ne 0) { throw '签名失败。' }
$result = & (Join-Path $PSScriptRoot 'verify-rc3-release.ps1') -ApkPath $signed -OfficialRelease -ExpectedVersionCode $ExpectedVersionCode -ExpectedCertificateSha256 $ExpectedCertificateSha256 -ApkSignerPath (Join-Path $BuildToolsDirectory 'apksigner.bat') -ZipAlignPath (Join-Path $BuildToolsDirectory 'zipalign.exe') -ApkAnalyzerPath $ApkAnalyzerPath
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $outputPath 'release-verification.json') -Encoding utf8
[PSCustomObject]@{
    unsigned_apk_sha256 = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    source_commit = $sourceCommit.Trim()
    signed_apk_sha256 = $result.sha256
    signing_certificate_sha256 = $ExpectedCertificateSha256.ToLowerInvariant()
    version_code = $ExpectedVersionCode
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputPath 'signing-record.json') -Encoding utf8
$result
