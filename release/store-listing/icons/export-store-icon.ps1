# 从既有 Android 图标资源导出商店方形 PNG；不修改原图，不构建或运行应用。
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Drawing

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$sourcePaths = @(
    'app/src/main/res/drawable-nodpi/launcher_cat.png',
    'app/src/main/res/drawable/ic_launcher_foreground.xml',
    'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',
    'app/src/main/res/values/launcher_colors.xml'
)
$sourceRecords = @($sourcePaths | ForEach-Object {
    [ordered]@{ path = $_; sha256 = (Get-FileHash -LiteralPath (Join-Path $repoRoot $_) -Algorithm SHA256).Hash.ToLowerInvariant() }
})
$androidNamespace = 'http://schemas.android.com/apk/res/android'
[xml]$adaptive = Get-Content -LiteralPath (Join-Path $repoRoot $sourcePaths[2]) -Raw -Encoding UTF8
[xml]$foreground = Get-Content -LiteralPath (Join-Path $repoRoot $sourcePaths[1]) -Raw -Encoding UTF8
[xml]$colors = Get-Content -LiteralPath (Join-Path $repoRoot $sourcePaths[3]) -Raw -Encoding UTF8
if ($adaptive.'adaptive-icon'.background.GetAttribute('drawable', $androidNamespace) -ne '@color/launcher_background' -or
    $adaptive.'adaptive-icon'.foreground.GetAttribute('drawable', $androidNamespace) -ne '@drawable/ic_launcher_foreground' -or
    $foreground.inset.GetAttribute('drawable', $androidNamespace) -ne '@drawable/launcher_cat') {
    throw 'Android 图标资源链已变化，请先静态核对，不能沿用旧导出方式。'
}
$insetText = $foreground.inset.GetAttribute('inset', $androidNamespace)
if ($insetText -ne '18%') { throw '前景内缩配置已变化，请先核对导出方式。' }
$backgroundHex = ($colors.resources.color | Where-Object { $_.name -eq 'launcher_background' }).InnerText
$backgroundColor = [Drawing.ColorTranslator]::FromHtml($backgroundHex)

# AdaptiveIconDrawable 的子图层比可见视口每边扩展 25%；再应用资源中的 18% 内缩。
# 这里只导出同一彩色图标的方形可见视口，不套用特定厂商的圆形/圆角蒙版。
$outputSize = 512
$supersample = 4
$viewportSize = $outputSize * $supersample
$adaptiveExtraInsetFraction = 0.25
$layerSize = [int]($viewportSize * (1 + 2 * $adaptiveExtraInsetFraction))
$viewportOffset = [int](($layerSize - $viewportSize) / 2)
$resourceInsetPixels = [int][Math]::Truncate($layerSize * 0.18)
$foregroundSize = $layerSize - 2 * $resourceInsetPixels
$foregroundOffset = $resourceInsetPixels - $viewportOffset
$outputPath = Join-Path $PSScriptRoot 'lizhang-store-icon-512.png'
$recordPath = Join-Path $PSScriptRoot 'export-record.json'
$sourceImage = $null
$viewport = $null
$viewportGraphics = $null
$outputImage = $null
$outputGraphics = $null
$resamplingAttributes = $null
try {
    $sourceImage = [Drawing.Image]::FromFile((Join-Path $repoRoot $sourcePaths[0]))
    if ($sourceImage.Width -ne $sourceImage.Height) { throw '原图比例已变化，停止导出。' }
    $sourceSize = @($sourceImage.Width, $sourceImage.Height)
    $viewport = [Drawing.Bitmap]::new($viewportSize, $viewportSize, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $viewportGraphics = [Drawing.Graphics]::FromImage($viewport)
    $viewportGraphics.Clear($backgroundColor)
    $viewportGraphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceOver
    $viewportGraphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
    $viewportGraphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $viewportGraphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $foregroundBounds = [Drawing.Rectangle]::new($foregroundOffset, $foregroundOffset, $foregroundSize, $foregroundSize)
    $viewportGraphics.DrawImage($sourceImage, $foregroundBounds, 0, 0, $sourceImage.Width, $sourceImage.Height, [Drawing.GraphicsUnit]::Pixel)
    $outputImage = [Drawing.Bitmap]::new($outputSize, $outputSize, [Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $outputGraphics = [Drawing.Graphics]::FromImage($outputImage)
    $outputGraphics.Clear($backgroundColor)
    $outputGraphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
    $outputGraphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $outputGraphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    # 镜像边缘采样，避免缩小时从视口外采到透明像素而改变纯色背景边缘。
    $resamplingAttributes = [Drawing.Imaging.ImageAttributes]::new()
    $resamplingAttributes.SetWrapMode([Drawing.Drawing2D.WrapMode]::TileFlipXY)
    $outputBounds = [Drawing.Rectangle]::new(0, 0, $outputSize, $outputSize)
    $outputGraphics.DrawImage($viewport, $outputBounds, 0, 0, $viewportSize, $viewportSize, [Drawing.GraphicsUnit]::Pixel, $resamplingAttributes)
    $outputImage.Save($outputPath, [Drawing.Imaging.ImageFormat]::Png)
} finally {
    foreach ($drawingObject in @($resamplingAttributes, $outputGraphics, $outputImage, $viewportGraphics, $viewport, $sourceImage)) {
        if ($null -ne $drawingObject) { $drawingObject.Dispose() }
    }
}
foreach ($sourceRecord in $sourceRecords) {
    $after = (Get-FileHash -LiteralPath (Join-Path $repoRoot $sourceRecord.path) -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($after -ne $sourceRecord.sha256) { throw '原资源摘要发生变化，请核对；本工具只写派生素材目录。' }
}
$sourceCommit = (& git -C $repoRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw '无法记录来源提交。' }
$record = [ordered]@{
    description = '由现有 Android 图标资源派生的商店方形素材；不是设备截图或产品验收结果。'
    source_commit = $sourceCommit
    sources = $sourceRecords
    source_image_size = $sourceSize
    background = $backgroundHex
    foreground_inset = $insetText
    adaptive_extra_inset_fraction = $adaptiveExtraInsetFraction
    supersample = $supersample
    render = 'System.Drawing，高质量双三次插值，方形视口，不附加厂商蒙版'
    output = [ordered]@{
        path = 'release/store-listing/icons/lizhang-store-icon-512.png'
        width = $outputSize
        height = $outputSize
        format = 'PNG'
        color_mode = 'RGB'
        sha256 = (Get-FileHash -LiteralPath $outputPath -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}
[IO.File]::WriteAllText($recordPath, ($record | ConvertTo-Json -Depth 6) + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
Write-Output '商店图标已导出到 release/store-listing/icons/；Android 原资源摘要保持一致。'
