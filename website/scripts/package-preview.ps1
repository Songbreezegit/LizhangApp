# 只打包已构建的审核静态文件和本机启动器；不启动服务、不请求 HTTP、不操作生产环境。
[CmdletBinding()]
param([string]$ArchiveName = 'Lizhang-website-review.zip')

$ErrorActionPreference = 'Stop'
if ([IO.Path]::GetFileName($ArchiveName) -ne $ArchiveName -or !$ArchiveName.EndsWith('.zip')) {
    throw 'ArchiveName 只能是 .zip 文件名，不能包含路径。'
}
$websiteRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$projectRoot = [IO.Path]::GetFullPath((Join-Path $websiteRoot '..'))
$distRoot = Join-Path $websiteRoot 'dist'
$info = Get-Content -LiteralPath (Join-Path $distRoot 'BUILD-INFO.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($info.mode -ne 'review' -or $info.deployable -ne $false) { throw '仅允许打包审核模式；请先生成 dist。' }
$deliveryRoot = Join-Path $projectRoot 'build\deliveries\privacy-entry-preview'
$stagingRoot = Join-Path $projectRoot ('build\website-review-' + [Guid]::NewGuid().ToString('N'))
$archivePath = Join-Path $deliveryRoot $ArchiveName
if (Test-Path -LiteralPath $archivePath) { throw '交付包已存在，请使用新的 ArchiveName；不覆盖既有交付。' }
New-Item -ItemType Directory -Force -Path $deliveryRoot,$stagingRoot | Out-Null
# 只纳入构建器约定的公开产物，不递归打包源码、未知文件或旧目录。
$files = @('index.html','privacy/index.html','terms/index.html','help/index.html','contact/index.html',
    '404.html','robots.txt','BUILD-INFO.json','assets/style.css','assets/logo-cat.png','assets/home-cat.png')
foreach ($relative in $files) {
    $target = Join-Path $stagingRoot ('dist/' + $relative)
    New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($target)) | Out-Null
    Copy-Item -LiteralPath (Join-Path $distRoot $relative) -Destination $target
}
New-Item -ItemType Directory -Path (Join-Path $stagingRoot 'scripts') | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'preview.mjs') -Destination (Join-Path $stagingRoot 'scripts/preview.mjs')
Copy-Item -LiteralPath (Join-Path $websiteRoot 'preview/START-PREVIEW.cmd') -Destination (Join-Path $stagingRoot 'START-PREVIEW.cmd')
Copy-Item -LiteralPath (Join-Path $websiteRoot 'preview/README.txt') -Destination (Join-Path $stagingRoot 'README.txt')
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::CreateFromDirectory($stagingRoot, $archivePath, [IO.Compression.CompressionLevel]::Optimal, $false)
$archive = Get-Item -LiteralPath $archivePath
[PSCustomObject]@{ file = $archive.FullName; bytes = $archive.Length;
    sha256 = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant();
    preview_status = '尚未启动服务；未测试，等待用户本人验收' } | ConvertTo-Json
