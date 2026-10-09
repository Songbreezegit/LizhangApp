# 与 app/build.gradle.kts 的内容批准摘要保持一致。该操作只计算摘要，不修改或批准文案。
[CmdletBinding()]
param([string]$AppDirectory = '')

$ErrorActionPreference = 'Stop'
if (!$AppDirectory) { $AppDirectory = Join-Path $PSScriptRoot '..\app' }
$appRoot = [IO.Path]::GetFullPath($AppDirectory).TrimEnd('\', '/')
$assetRoot = Join-Path $appRoot 'src\main\assets\legal'
if (!(Test-Path -LiteralPath $assetRoot -PathType Container)) { throw '缺少离线法律文档目录。' }
$inputFiles = @(Get-ChildItem -LiteralPath $assetRoot -Recurse -File)
$resourceRoot = Join-Path $appRoot 'src\main\res'
$inputFiles += @(Get-ChildItem -LiteralPath $resourceRoot -Recurse -File | Where-Object {
    $_.Directory.Name.StartsWith('values', [StringComparison]::Ordinal) -and
    $_.Extension -eq '.xml' -and ($_.Name.StartsWith('legal', [StringComparison]::Ordinal) -or
        $_.Name.StartsWith('privacy', [StringComparison]::Ordinal) -or $_.Name -eq 'candidate_release_strings.xml')
})
$relativePaths = [string[]]@($inputFiles | ForEach-Object { $_.FullName.Substring($appRoot.Length + 1).Replace('\', '/') })
[Array]::Sort($relativePaths, [StringComparer]::Ordinal)
$canonical = [Text.StringBuilder]::new()
foreach ($relativePath in $relativePaths) {
    $fileDigest = (Get-FileHash -LiteralPath (Join-Path $appRoot $relativePath) -Algorithm SHA256).Hash.ToLowerInvariant()
    [void]$canonical.Append($relativePath).Append("`n").Append($fileDigest).Append("`n")
}
$digestAlgorithm = [Security.Cryptography.SHA256]::Create()
try {
    $digest = -join ($digestAlgorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes($canonical.ToString())) | ForEach-Object { $_.ToString('x2') })
} finally { $digestAlgorithm.Dispose() }
[PSCustomObject]@{ legal_content_sha256 = $digest; input_count = $relativePaths.Length; inputs = $relativePaths }
