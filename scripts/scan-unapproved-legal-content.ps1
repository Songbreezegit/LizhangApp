# 与 Gradle 正式批准门槛读取同一七语言规则。默认列出候选内容，明确正式检查时发现草稿则失败。
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
    # 候选专用提示仅在候选 UI 展示；仍纳入摘要，不能据此放过正式正文中的草稿。
    if ([IO.Path]::GetFileName($relativePath) -ne 'candidate_release_strings.xml' -and $matcher.IsMatch($content)) { $findings += $relativePath }
}
$checkedCandidateStrings = 0
if ($VerifyCandidateCoverage) {
    foreach ($locale in @('values', 'values-b+zh+Hant', 'values-en', 'values-ja', 'values-ko', 'values-es', 'values-fr')) {
        foreach ($resourceFile in @('candidate_release_strings.xml')) {
            $sourcePath = Join-Path $appRoot "src\main\res\$locale\$resourceFile"
            [xml]$xml = [IO.File]::ReadAllText($sourcePath, [Text.Encoding]::UTF8)
            $candidateNames = @('privacy_notice_candidate', 'legal_candidate_notice')
            foreach ($name in $candidateNames) {
                $node = $xml.SelectSingleNode("/resources/string[@name='$name']")
                if (!$node -or !$matcher.IsMatch($node.InnerText)) {
                    throw "七语言候选门槛漏检：$locale/$resourceFile/$name。"
                }
                $checkedCandidateStrings++
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
    candidate_strings_checked = $checkedCandidateStrings
    approval_granted = $false
}
