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
    [string]$KeyToolPath = 'keytool',
    [string]$ApkPath = ''
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (!$ApkPath) { $ApkPath = Join-Path $repoRoot 'app\build\outputs\apk\release\app-release-unsigned.apk' }
if ($ExpectedCertificateSha256 -notmatch '^[a-fA-F0-9]{64}$') { throw '必须提供本人批准的正式证书 SHA-256。' }
if ($ExpectedVersionCode -lt 1 -or $ExpectedVersionCode -gt 2100000000) { throw '版本代码必须是 1 至 2100000000 之间的整数。' }
if ([string]::IsNullOrWhiteSpace($KeyAlias) -or $KeyAlias -match '[\r\n]') { throw '正式密钥别名不能为空或包含换行。' }
if ($KeyAlias -ieq 'androiddebugkey') { throw '禁止把 Android 调试身份用作正式签名。' }
function Test-InGitDirectory([string]$Directory) {
    $current = [IO.DirectoryInfo]::new($Directory)
    while ($current) {
        if (Test-Path -LiteralPath (Join-Path $current.FullName '.git')) { return $true }
        $current = $current.Parent
    }
    return $false
}
# 符号链接和目录联接会使字面路径的“仓库外”检查失效，密钥及输出路径一律拒绝重解析点。
function Assert-NoReparsePoint([string]$Path) {
    $current = [IO.Path]::GetFullPath($Path)
    while ($current) {
        if (Test-Path -LiteralPath $current) {
            $item = Get-Item -LiteralPath $current -Force
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw '密钥和输出路径不能经过符号链接或目录联接。' }
        }
        $parent = [IO.Directory]::GetParent($current)
        $current = if ($parent) { $parent.FullName } else { $null }
    }
}
$keyFile = Get-Item -LiteralPath $KeyStorePath
if ($keyFile.PSIsContainer -or $keyFile.FullName.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw '正式密钥必须是已存在、本人授权且位于仓库外的文件。'
}
$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $outputPath) { throw '签名输出目录已存在，禁止覆盖。' }
Assert-NoReparsePoint $keyFile.FullName
Assert-NoReparsePoint $outputPath
if ((Test-InGitDirectory $keyFile.DirectoryName) -or (Test-InGitDirectory $outputPath)) { throw '密钥和最终签名输出目录必须位于 Git 仓库外。' }
foreach ($tool in @((Join-Path $BuildToolsDirectory 'zipalign.exe'), (Join-Path $BuildToolsDirectory 'apksigner.bat'), $ApkAnalyzerPath)) {
    if (!(Test-Path -LiteralPath $tool -PathType Leaf)) { throw "缺少签名检查工具：$tool" }
}
if (!(Get-Command $KeyToolPath -ErrorAction SilentlyContinue)) { throw '缺少 JDK keytool 工具。' }
$apkFile = Get-Item -LiteralPath $ApkPath
if ($apkFile.PSIsContainer) { throw '待签名输入必须是 APK 文件。' }
$ApkPath = $apkFile.FullName
$unsignedSha256 = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
function Assert-CleanSource([string]$ExpectedCommit = '') {
    $commit = & git -C $repoRoot rev-parse HEAD
    if ($LASTEXITCODE -ne 0 -or $commit -notmatch '^[a-fA-F0-9]{40,64}$') { throw '无法核验正式签名的源码提交。' }
    $changes = & git -C $repoRoot status --porcelain --untracked-files=normal
    if ($LASTEXITCODE -ne 0 -or $changes) { throw '正式签名前须审查并提交全部发布源码，使用干净工作树。' }
    if ($ExpectedCommit -and $commit.Trim() -ne $ExpectedCommit) { throw '签名期间源码提交发生变化，禁止交付。' }
    return $commit.Trim()
}
# 完整门禁必须先通过；操作者提供参数不能代替仓库中绑定内容摘要的真实批准。
$sourceCommit = Assert-CleanSource
& (Join-Path $repoRoot 'gradlew.bat') -p $repoRoot --no-daemon --console=plain "-PofficialVersionCode=$ExpectedVersionCode" :app:verifyOfficialReleaseContent | Out-Host
if ($LASTEXITCODE -ne 0) { throw '正式发布批准未通过，停止签名。' }
$approval = [IO.File]::ReadAllText((Join-Path $repoRoot 'release\legal-approval.properties'))
$matches = [Regex]::Matches($approval, '(?m)^signing_certificate_sha256=([a-fA-F0-9]{64})\r?$')
if ($matches.Count -ne 1 -or $matches[0].Groups[1].Value.ToLowerInvariant() -ne $ExpectedCertificateSha256.ToLowerInvariant()) { throw '请求的正式证书与批准记录不一致或记录重复。' }
$verificationArguments = @{
    OfficialRelease = $true
    ExpectedVersionCode = $ExpectedVersionCode
    ExpectedCertificateSha256 = $ExpectedCertificateSha256
    ApkSignerPath = Join-Path $BuildToolsDirectory 'apksigner.bat'
    ZipAlignPath = Join-Path $BuildToolsDirectory 'zipalign.exe'
    ApkAnalyzerPath = $ApkAnalyzerPath
}
# 在访问授权密钥前拒绝候选、Debug、旧版或与当前批准内容不一致的输入。
$null = & (Join-Path $PSScriptRoot 'verify-rc4-release.ps1') @verificationArguments -ApkPath $ApkPath -BeforeSigning
# 固定公共证书输出语言以识别指纹；密码仍由 keytool 交互询问，不导出私钥或输出整份证书。
$certificate = & $KeyToolPath '-J-Duser.language=en' '-J-Duser.country=US' -list -v -keystore $keyFile.FullName -alias $KeyAlias
if ($LASTEXITCODE -ne 0) { throw '授权证书读取失败。' }
if (($certificate -join "`n") -match 'CN=Android Debug(?:,|\s|$)') { throw '授权别名指向 Android 调试证书，停止正式签名。' }
$fingerprint = [Regex]::Match(($certificate -join "`n"), 'SHA256:\s*([A-Fa-f0-9:]+)')
if (!$fingerprint.Success -or $fingerprint.Groups[1].Value.Replace(':', '').ToLowerInvariant() -ne $ExpectedCertificateSha256.ToLowerInvariant()) { throw '密钥公共证书与本人批准身份不一致，未执行签名。' }
if ((Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $unsignedSha256) { throw '签前检查期间 APK 已改变，停止签名。' }
New-Item -ItemType Directory -Path $outputPath | Out-Null
$aligned = Join-Path $outputPath 'Lizhang-1.0.0-aligned-unsigned.apk'
$signedPending = Join-Path $outputPath 'Lizhang-1.0.0-signing-pending.apk'
$signed = Join-Path $outputPath 'Lizhang-1.0.0-release.apk'
& (Join-Path $BuildToolsDirectory 'zipalign.exe') -P 16 -v 4 $ApkPath $aligned
if ($LASTEXITCODE -ne 0) { throw '签名前对齐失败。' }
# apksigner 自行交互询问密码，禁止将密码写在命令行或脚本日志。
& (Join-Path $BuildToolsDirectory 'apksigner.bat') sign --ks $keyFile.FullName --ks-key-alias $KeyAlias --out $signedPending $aligned
if ($LASTEXITCODE -ne 0) { throw '签名失败。' }
$result = & (Join-Path $PSScriptRoot 'verify-rc4-release.ps1') @verificationArguments -ApkPath $signedPending
if ((Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $unsignedSha256) { throw '签名期间输入 APK 已改变，禁止交付。' }
$null = Assert-CleanSource $sourceCommit
# 记录先准备完成；只有最后重命名成功才存在正式文件名。失败目录中的 pending APK 不得发布。
$result.apk = $signed
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $outputPath 'release-verification.json') -Encoding utf8
[PSCustomObject]@{
    unsigned_apk_sha256 = $unsignedSha256
    source_commit = $sourceCommit
    signed_apk_sha256 = $result.sha256
    signing_certificate_sha256 = $ExpectedCertificateSha256.ToLowerInvariant()
    version_code = $ExpectedVersionCode
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputPath 'signing-record.json') -Encoding utf8
# 不使用覆盖选项；重命名是最后一个文件操作，失败不留下最终名 APK。
Move-Item -LiteralPath $signedPending -Destination $signed
$result
