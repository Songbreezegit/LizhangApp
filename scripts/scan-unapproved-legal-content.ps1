# 与 Gradle 正式批准门槛读取同一七语言规则；公开资源只呈现阅读提示，审批状态保留在内部配置。
[CmdletBinding()]
param([switch]$RequireApprovedContent, [switch]$VerifyCandidateCoverage)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$appRoot = Join-Path $repoRoot 'app'
$pattern = [IO.File]::ReadAllText((Join-Path $repoRoot 'release\unapproved-content-pattern.txt'), [Text.Encoding]::UTF8).Trim()
$matcher = [Regex]::new($pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
$inputs = & (Join-Path $PSScriptRoot 'legal-content-digest.ps1')
$findings = @()
foreach ($relativePath in $inputs.inputs) {
    $content = [IO.File]::ReadAllText((Join-Path $appRoot $relativePath), [Text.Encoding]::UTF8)
    if ($matcher.IsMatch($content)) { $findings += $relativePath }
}
$checkedResourceStrings = 0
if ($VerifyCandidateCoverage) {
    foreach ($locale in @('values', 'values-b+zh+Hant', 'values-en', 'values-ja', 'values-ko', 'values-es', 'values-fr')) {
        foreach ($resourceFile in @('privacy_notice.xml', 'legal_strings.xml')) {
            $sourcePath = Join-Path $appRoot "src\main\res\$locale\$resourceFile"
            [xml]$xml = [IO.File]::ReadAllText($sourcePath, [Text.Encoding]::UTF8)
            # 保留旧开关兼容调用方，改为检查现行七语言阅读提示是否齐备，不再要求用户界面含草稿标签。
            $resourceNames = if ($resourceFile -eq 'privacy_notice.xml') {
                @('privacy_notice_title', 'privacy_notice_agree', 'privacy_notice_decline')
            } else {
                @('legal_candidate_notice', 'legal_language_fallback', 'legal_content_version', 'legal_online_explanation') }
            foreach ($name in $resourceNames) {
                $node = $xml.SelectSingleNode("/resources/string[@name='$name']")
                if (!$node -or [string]::IsNullOrWhiteSpace($node.InnerText) -or $matcher.IsMatch($node.InnerText)) {
                    throw "七语言阅读提示缺失或混入未批准说明：$locale/$resourceFile/$name。"
                }
                $checkedResourceStrings++
            }
        }
    }
}
if ($RequireApprovedContent -and $findings.Count -gt 0) {
    throw "正式内容检查失败：$($findings.Count) 个文件仍包含七语言待批准标记。"
}
[PSCustomObject]@{
    input_count = $inputs.input_count
    unapproved_file_count = $findings.Count
    unapproved_files = $findings
    public_resource_strings_checked = $checkedResourceStrings
    approval_granted = $false
}
