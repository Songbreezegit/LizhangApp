# 网站完整说明和配置的待审摘要；不修改或授予批准。
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\', '/')
$inputFiles = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'website\content') -Recurse -File | Where-Object { $_.Extension -eq '.md' })
$inputFiles += Get-Item -LiteralPath (Join-Path $repoRoot 'website\site.config.json')
$relativePaths = [string[]]@($inputFiles | ForEach-Object { $_.FullName.Substring($repoRoot.Length + 1).Replace('\', '/') })
[Array]::Sort($relativePaths, [StringComparer]::Ordinal)
$canonical = [Text.StringBuilder]::new()
foreach ($relativePath in $relativePaths) {
    $digest = (Get-FileHash -LiteralPath (Join-Path $repoRoot $relativePath) -Algorithm SHA256).Hash.ToLowerInvariant()
    [void]$canonical.Append($relativePath).Append("`n").Append($digest).Append("`n")
}
$algorithm = [Security.Cryptography.SHA256]::Create()
try { $digest = -join ($algorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes($canonical.ToString())) | ForEach-Object { $_.ToString('x2') }) }
finally { $algorithm.Dispose() }
[PSCustomObject]@{ website_content_sha256 = $digest; input_count = $relativePaths.Length; inputs = $relativePaths }
