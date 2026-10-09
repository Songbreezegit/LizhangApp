# 在临时隔离目录调用生产脚本的副本。命令全部由内存 mock 实现，不使用密钥、不产生可安装 APK。
[CmdletBinding()]
param([string]$ReportDirectory = '')
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (!$ReportDirectory) { $ReportDirectory = Join-Path $repoRoot 'build\signing-script-regression' }
$ReportDirectory = [IO.Path]::GetFullPath($ReportDirectory)
$fixtureRoot = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) ('Temp\lizhang-signing-fixture-' + [Guid]::NewGuid().ToString('N'))
$fixtureRepo = Join-Path $fixtureRoot 'repo'
$fixtureScripts = Join-Path $fixtureRepo 'scripts'
$fixtureTools = Join-Path $fixtureRoot 'tools'
$fixtureLegal = Join-Path $fixtureRepo 'app\src\main\assets\legal'
foreach ($path in @($fixtureScripts, $fixtureTools, $fixtureLegal, (Join-Path $fixtureRepo 'app\src\main\res'), (Join-Path $fixtureRepo 'website\content'), (Join-Path $fixtureRepo 'release'), (Join-Path $fixtureRepo '.git'))) {
    New-Item -ItemType Directory -Path $path -Force | Out-Null
}
foreach ($name in @('sign-release.ps1', 'verify-rc4-release.ps1', 'legal-content-digest.ps1', 'website-content-digest.ps1')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $fixtureScripts $name)
}
$utf8 = [Text.UTF8Encoding]::new($false)
function Write-Fixture([string]$Path, [string]$Value) { [IO.File]::WriteAllText($Path, $Value, $utf8) }
foreach ($name in @('privacy.md', 'terms.md', 'help.md')) {
    Write-Fixture (Join-Path $fixtureLegal $name) "# 合成正文`n`n仅用于脚本测试。`n"
    Write-Fixture (Join-Path $fixtureRepo "website\content\$name") "# 合成正文`n`n仅用于脚本测试。`n"
}
Write-Fixture (Join-Path $fixtureLegal 'metadata.properties') "policy_version=1.0.0-fixture-v1`n"
Write-Fixture (Join-Path $fixtureRepo 'website\site.config.json') ('{"synthetic":true}' + "`n")
$tools = @{
    analyzer = Join-Path $fixtureTools 'apkanalyzer.bat'
    signer = Join-Path $fixtureTools 'apksigner.bat'
    aligner = Join-Path $fixtureTools 'zipalign.exe'
    keytool = Join-Path $fixtureTools 'keytool-fixture.bat'
    gradle = Join-Path $fixtureRepo 'gradlew.bat'
}
foreach ($tool in $tools.Values) { Write-Fixture $tool '此占位工具只允许由回归脚本内存 mock 调用。' }
$keyPath = Join-Path $fixtureRoot 'public-identity.fixture'
Write-Fixture $keyPath '此文件不是密钥，仅测试路径校验。'
$legalDigest = & (Join-Path $fixtureScripts 'legal-content-digest.ps1')
$websiteDigest = & (Join-Path $fixtureScripts 'website-content-digest.ps1')
$approvalPath = Join-Path $fixtureRepo 'release\legal-approval.properties'
$approvalText = "approval_status=approved`nsigning_identity_status=confirmed`nsigning_certificate_sha256=$('a' * 64)`napproved_content_sha256=$($legalDigest.legal_content_sha256)`n"
Write-Fixture $approvalPath $approvalText
Add-Type -AssemblyName System.IO.Compression.FileSystem
$unsigned = Join-Path $fixtureRoot 'unsigned.fixture.zip'
function New-FixtureZip([string]$VersionName = '1.0.0', [bool]$Official = $true, [bool]$Signature = $false, [bool]$Stale = $false) {
    if (Test-Path -LiteralPath $unsigned) { Remove-Item -LiteralPath $unsigned }
    $zip = [IO.Compression.ZipFile]::Open($unsigned, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($name in @('privacy.md', 'terms.md', 'help.md', 'metadata.properties')) {
            [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $fixtureLegal $name), "assets/legal/$name")
        }
        $entry = $zip.CreateEntry('assets/release-content-binding.properties')
        $writer = [IO.StreamWriter]::new($entry.Open(), $utf8)
        $digest = if ($Stale) { '0' * 64 } else { $legalDigest.legal_content_sha256 }
        try { $writer.Write("legal_content_sha256=$digest`nwebsite_content_sha256=$($websiteDigest.website_content_sha256)`nis_official_release=$($Official.ToString().ToLowerInvariant())`nversion_name=$VersionName`nversion_code=24`npolicy_version=1.0.0-fixture-v1`n") }
        finally { $writer.Dispose() }
        if ($Signature) {
            $writer = [IO.StreamWriter]::new($zip.CreateEntry('META-INF/FIXTURE.SF').Open(), $utf8)
            try { $writer.Write('仅用于测试已有签名材料拒绝，不是真签名。') } finally { $writer.Dispose() }
        }
    } finally { $zip.Dispose() }
}
$priorFixtureState = $global:LizhangSigningFixture
$priorExitCode = $global:LASTEXITCODE
$global:LizhangSigningFixture = @{ scenario = ''; calls = [Collections.Generic.List[string]]::new(); version = '1.0.0'; signed = $false }
$mockNames = @('git', 'Set-Content', 'Move-Item') + @($tools.Values)
$priorFunctions = @{}
foreach ($name in $mockNames) {
    $existing = Get-Item -LiteralPath ('Function:' + $name) -ErrorAction SilentlyContinue
    $priorFunctions[$name] = if ($existing) { $existing.ScriptBlock } else { $null }
}
$gitMock = {
    $CommandArguments = [string[]]$args
    $state = $global:LizhangSigningFixture
    $state.calls.Add('git')
    $global:LASTEXITCODE = 0
    if ('rev-parse' -in $CommandArguments) {
        if ($state.scenario -eq 'changed-commit' -and $state.signed) { return 'b' * 40 }
        return 'a' * 40
    }
    if ($state.scenario -eq 'dirty-source') { return ' M synthetic-source' }
    if ($state.scenario -eq 'git-failure') { $global:LASTEXITCODE = 1 }
}
$gradleMock = {
    $CommandArguments = [string[]]$args
    $state = $global:LizhangSigningFixture
    $state.calls.Add('gradle')
    $global:LASTEXITCODE = if ($state.scenario -eq 'gate-failure') { 1 } else { 0 }
}
$analyzerMock = {
    $CommandArguments = [string[]]$args
    $state = $global:LizhangSigningFixture
    $state.calls.Add('analyzer')
    $global:LASTEXITCODE = 0
    if ($state.scenario -eq 'analyzer-failure') { $global:LASTEXITCODE = 1; return '合成分析器失败' }
    switch ($CommandArguments[1]) {
        'application-id' { if ($state.scenario -eq 'wrong-package') { 'com.invalid.fixture' } else { 'com.yangsong.lizhang' } }
        'version-name' { $state.version }
        'version-code' { if ($state.scenario -eq 'wrong-code') { '25' } else { '24' } }
        'min-sdk' { '26' }
        'target-sdk' { '36' }
        'debuggable' { if ($state.scenario -eq 'debuggable') { 'true' } else { 'false' } }
        'print' { if ($state.scenario -eq 'test-host') { '<manifest><instrumentation /></manifest>' } else { '<manifest />' } }
        'permissions' {
            'android.permission.READ_CONTACTS'; 'android.permission.POST_NOTIFICATIONS'; 'android.permission.RECEIVE_BOOT_COMPLETED'
            if ($state.scenario -eq 'extra-permission') { 'android.permission.INTERNET' }
        }
        'packages' { if ($state.scenario -eq 'test-dex') { 'C org.junit.Fixture' } else { 'C com.yangsong.lizhang.MainActivity' } }
    }
}
$keytoolMock = {
    $CommandArguments = [string[]]$args
    $state = $global:LizhangSigningFixture
    $state.calls.Add('keytool')
    $global:LASTEXITCODE = 0
    if ($state.scenario -eq 'certificate-read-failure') { $global:LASTEXITCODE = 1; return }
    if ($state.scenario -eq 'debug-certificate') { 'Owner: CN=Android Debug' } else { 'Owner: CN=Synthetic Fixture' }
    $fingerprint = if ($state.scenario -eq 'wrong-certificate') { 'b' * 64 } else { 'a' * 64 }
    'SHA256: ' + $fingerprint
    if ($state.scenario -eq 'changed-input') {
        [IO.File]::AppendAllText($state.input, '合成修改')
    }
}
$zipalignMock = {
    $CommandArguments = [string[]]$args
    $state = $global:LizhangSigningFixture
    $checking = '-c' -in $CommandArguments
    $state.calls.Add($(if ($checking) { 'align-check' } else { 'align' }))
    $global:LASTEXITCODE = 0
    if ($checking) {
        if ($state.scenario -eq 'alignment-check-failure') { $global:LASTEXITCODE = 1 }
    } elseif ($state.scenario -eq 'alignment-failure') { $global:LASTEXITCODE = 1 }
    else { [IO.File]::Copy($CommandArguments[-2], $CommandArguments[-1]) }
}
$signerMock = {
    $CommandArguments = [string[]]$args
    $state = $global:LizhangSigningFixture
    $global:LASTEXITCODE = 0
    if ($CommandArguments[0] -eq 'sign') {
        $state.calls.Add('sign')
        if ($state.scenario -eq 'sign-failure') { $global:LASTEXITCODE = 1; return }
        $outputIndex = [Array]::IndexOf($CommandArguments, '--out') + 1
        [IO.File]::Copy($CommandArguments[-1], $CommandArguments[$outputIndex])
        $state.signed = $true
    } else {
        $state.calls.Add('signature-check')
        if ($CommandArguments[-1] -notlike '*Lizhang-1.0.0-signing-pending.apk') { $global:LASTEXITCODE = 1; return '合成未签名输入' }
        if ($state.scenario -eq 'signature-check-failure') { $global:LASTEXITCODE = 1; return '合成签名验证失败' }
        'Verified using v2 scheme (APK Signature Scheme v2): ' + $(if ($state.scenario -eq 'missing-v2') { 'false' } else { 'true' })
        'Signer #1 certificate SHA-256 digest: ' + $(if ($state.scenario -eq 'wrong-final-certificate') { 'b' * 64 } else { 'a' * 64 })
    }
}
$recordMock = {
    param([Parameter(ValueFromPipeline=$true)]$Value, [string]$LiteralPath, [string]$Encoding)
    process {
        $state = $global:LizhangSigningFixture
        $state.calls.Add('record')
        if ($state.scenario -eq 'record-failure') { throw '合成记录写入失败' }
        [IO.File]::WriteAllText($LiteralPath, [string]$Value, [Text.UTF8Encoding]::new($false))
    }
}
$renameMock = {
    param([string]$LiteralPath, [string]$Destination)
    $state = $global:LizhangSigningFixture
    $state.calls.Add('rename')
    if ($state.scenario -eq 'rename-failure') { throw '合成最终重命名失败' }
    [IO.File]::Move($LiteralPath, $Destination)
}
$implementations = @{ git = $gitMock; 'Set-Content' = $recordMock; 'Move-Item' = $renameMock }
$implementations[$tools.gradle] = $gradleMock
$implementations[$tools.analyzer] = $analyzerMock
$implementations[$tools.keytool] = $keytoolMock
$implementations[$tools.aligner] = $zipalignMock
$implementations[$tools.signer] = $signerMock
$results = [Collections.Generic.List[object]]::new()
try {
    foreach ($name in $mockNames) { Set-Item -LiteralPath ('Function:script:' + $name) -Value $implementations[$name] }
    $cases = @(
        @{ name='合成流程通过并写成功记录'; scenario=''; accepted=$true },
        @{ name='拒绝不完整指纹'; scenario=''; error='SHA-256'; parameter='fingerprint' },
        @{ name='拒绝非法版本代码'; scenario=''; error='版本代码'; parameter='version' },
        @{ name='拒绝超过Android上限的版本代码'; scenario=''; error='版本代码'; parameter='oversize-version' },
        @{ name='拒绝空白密钥别名'; scenario=''; error='别名不能为空'; parameter='blank-alias' },
        @{ name='缺少检查工具立即停止'; scenario=''; error='缺少签名检查工具'; parameter='missing-analyzer' },
        @{ name='缺少keytool立即停止'; scenario=''; error='缺少 JDK'; parameter='missing-keytool' },
        @{ name='拒绝调试别名'; scenario=''; error='调试身份'; parameter='alias' },
        @{ name='拒绝仓库内密钥'; scenario=''; error='仓库外'; parameter='key-in-repo' },
        @{ name='拒绝既有输出目录'; scenario=''; error='禁止覆盖'; parameter='existing-output' },
        @{ name='拒绝仓库内输出'; scenario=''; error='Git 仓库外'; parameter='output-in-repo' },
        @{ name='拒绝经目录联接访问仓库内密钥'; scenario=''; error='目录联接'; parameter='key-junction' },
        @{ name='拒绝经目录联接的输出目录'; scenario=''; error='目录联接'; parameter='output-junction' },
        @{ name='Git命令失败立即停止'; scenario='git-failure'; error='干净工作树' },
        @{ name='拒绝未提交源码'; scenario='dirty-source'; error='干净工作树' },
        @{ name='正式门禁失败前不读证书'; scenario='gate-failure'; error='正式发布批准' },
        @{ name='批准证书重复不能签名'; scenario=''; error='记录重复'; parameter='duplicate-approval' },
        @{ name='拒绝候选输入'; scenario=''; error='版本不符合'; parameter='candidate' },
        @{ name='拒绝错误包名'; scenario='wrong-package'; error='版本不符合' },
        @{ name='拒绝错误版本代码'; scenario='wrong-code'; error='版本不符合' },
        @{ name='拒绝可调试输入'; scenario='debuggable'; error='仍可调试' },
        @{ name='分析工具失败立即停止'; scenario='analyzer-failure'; error='APK 检查失败' },
        @{ name='拒绝测试清单组件'; scenario='test-host'; error='测试宿主' },
        @{ name='拒绝额外联网权限'; scenario='extra-permission'; error='未审查权限' },
        @{ name='拒绝测试DEX'; scenario='test-dex'; error='DEX' },
        @{ name='拒绝陈旧内容绑定'; scenario=''; error='编译时法律资源'; parameter='stale' },
        @{ name='拒绝已有签名材料'; scenario=''; error='已有签名材料'; parameter='existing-signature' },
        @{ name='证书读取失败不签名'; scenario='certificate-read-failure'; error='授权证书读取失败' },
        @{ name='拒绝调试公共证书'; scenario='debug-certificate'; error='调试证书' },
        @{ name='证书指纹不符不签名'; scenario='wrong-certificate'; error='公共证书' },
        @{ name='签前输入改变不签名'; scenario='changed-input'; error='APK 已改变' },
        @{ name='对齐失败不签名'; scenario='alignment-failure'; error='对齐失败' },
        @{ name='对齐检查失败立即停止'; scenario='alignment-check-failure'; error='对齐验证失败' },
        @{ name='签名工具失败不写成功记录'; scenario='sign-failure'; error='签名失败' },
        @{ name='签后验签失败不写成功记录'; scenario='signature-check-failure'; error='签名验证失败' },
        @{ name='签后证书不符不写成功记录'; scenario='wrong-final-certificate'; error='签名证书' },
        @{ name='签后缺少v2不写成功记录'; scenario='missing-v2'; error='v2 签名验证' },
        @{ name='签名期间源码提交改变禁止交付'; scenario='changed-commit'; error='源码提交发生变化' },
        @{ name='记录写入失败没有最终名APK'; scenario='record-failure'; error='记录写入失败' },
        @{ name='最终重命名失败没有最终名APK'; scenario='rename-failure'; error='最终重命名失败' }
    )
    $index = 0
    foreach ($case in $cases) {
        $index++
        $state = $global:LizhangSigningFixture
        $state.scenario = $case.scenario; $state.calls.Clear(); $state.signed = $false; $state.version = '1.0.0'; $state.input = $unsigned
        Write-Fixture $approvalPath $approvalText
        $arguments = @{ KeyStorePath=$keyPath; KeyAlias='fixture'; ExpectedCertificateSha256=('a' * 64); ExpectedVersionCode=24;
            OutputDirectory=(Join-Path $fixtureRoot "output-$index"); BuildToolsDirectory=$fixtureTools; ApkAnalyzerPath=$tools.analyzer; KeyToolPath=$tools.keytool; ApkPath=$unsigned }
        switch ($case.parameter) {
            'fingerprint' { $arguments.ExpectedCertificateSha256='a' }
            'version' { $arguments.ExpectedVersionCode=0 }
            'oversize-version' { $arguments.ExpectedVersionCode=2100000001 }
            'blank-alias' { $arguments.KeyAlias='  ' }
            'missing-analyzer' { $arguments.ApkAnalyzerPath=Join-Path $fixtureTools 'missing-fixture.bat' }
            'missing-keytool' { $arguments.KeyToolPath='lizhang-absent-fixture-keytool' }
            'key-junction' {
                Write-Fixture (Join-Path $fixtureRepo 'identity.fixture') '合成路径输入'
                $link = Join-Path $fixtureRoot 'key-junction'
                New-Item -ItemType Junction -Path $link -Target $fixtureRepo | Out-Null
                $arguments.KeyStorePath=Join-Path $link 'identity.fixture'
            }
            'output-junction' {
                $link = Join-Path $fixtureRoot 'output-junction'
                New-Item -ItemType Junction -Path $link -Target $fixtureTools | Out-Null
                $arguments.OutputDirectory=Join-Path $link 'new-output'
            }
            'alias' { $arguments.KeyAlias='androiddebugkey' }
            'key-in-repo' { $arguments.KeyStorePath=Join-Path $fixtureRepo 'identity.fixture'; Write-Fixture $arguments.KeyStorePath '合成路径输入' }
            'existing-output' { New-Item -ItemType Directory -Path $arguments.OutputDirectory | Out-Null }
            'output-in-repo' { $arguments.OutputDirectory=Join-Path $fixtureRepo 'signing-output' }
            'duplicate-approval' { Write-Fixture $approvalPath ($approvalText + "signing_certificate_sha256=$('a' * 64)`n") }
            'candidate' { $state.version='1.0.0-rc4' }
        }
        New-FixtureZip -VersionName $state.version -Official ($case.parameter -ne 'candidate') -Signature ($case.parameter -eq 'existing-signature') -Stale ($case.parameter -eq 'stale')
        $accepted = $false; $reason = ''
        try { $actual = & (Join-Path $fixtureScripts 'sign-release.ps1') @arguments; $accepted = $true }
        catch { $reason = $_.Exception.Message }
        if ($accepted -ne [bool]$case.accepted -or (!$accepted -and $reason -notlike ('*' + $case.error + '*'))) {
            throw "签名脚本回归失败：$($case.name)；接受=$accepted；原因=$reason"
        }
        $recordPath = Join-Path $arguments.OutputDirectory 'signing-record.json'
        if ($case.scenario -ne 'rename-failure' -and (Test-Path -LiteralPath $recordPath) -ne $accepted) { throw "成功记录状态错误：$($case.name)" }
        if (!$accepted -and $case.scenario -notin @('sign-failure','signature-check-failure','wrong-final-certificate','missing-v2','changed-commit','record-failure','rename-failure') -and 'sign' -in $state.calls) { throw "失败后仍进入签名：$($case.name)" }
        $finalApk = Join-Path $arguments.OutputDirectory 'Lizhang-1.0.0-release.apk'
        if ((Test-Path -LiteralPath $finalApk) -ne $accepted) { throw "最终名 APK 状态错误：$($case.name)" }
        if ($case.scenario -eq 'gate-failure' -and 'keytool' -in $state.calls) { throw '门禁拒绝后仍访问证书。' }
        if ($accepted -and (!$actual.signature_identity_checked -or !$actual.build_content_binding_verified)) { throw '签后检查结果不完整。' }
        $results.Add([PSCustomObject]@{ name=$case.name; passed=$true; expected_accepted=[bool]$case.accepted; reason=$reason; calls=@($state.calls) })
    }
} finally {
    foreach ($name in $mockNames) {
        if ($priorFunctions[$name]) { Set-Item -LiteralPath ('Function:script:' + $name) -Value $priorFunctions[$name] }
        else { Remove-Item -LiteralPath ('Function:' + $name) -ErrorAction Stop }
    }
    $global:LizhangSigningFixture = $priorFixtureState
    $global:LASTEXITCODE = $priorExitCode
}
# mock 不能留在运行会话；防止后续真实 Git 或报告写入被合成工具污染。
foreach ($name in $mockNames) {
    $restored = Get-Item -LiteralPath ('Function:' + $name) -ErrorAction SilentlyContinue
    if (!$priorFunctions[$name] -and $restored) { throw "mock 函数未清理：$name" }
    if ($priorFunctions[$name] -and (!$restored -or $restored.ScriptBlock.ToString() -ne $priorFunctions[$name].ToString())) {
        throw "原命令函数未恢复：$name"
    }
}
$results.Add([PSCustomObject]@{ name='全部mock函数恢复原状'; passed=$true; expected_accepted=$false })
# 独立 PowerShell 进程验证生产入口的异常确实以非零退出；无 mock，也不接触正式资料。
$hostPath = (Get-Process -Id $PID).Path
& $hostPath -NoProfile -File (Join-Path $fixtureScripts 'sign-release.ps1') -KeyStorePath $keyPath -KeyAlias fixture -ExpectedCertificateSha256 invalid -ExpectedVersionCode 24 -OutputDirectory (Join-Path $fixtureRoot 'process-output') *> (Join-Path $fixtureRoot 'nonzero-exit.txt')
if ($LASTEXITCODE -eq 0) { throw '非法输入未以非零进程状态退出。' }
$results.Add([PSCustomObject]@{ name='非法输入的独立进程以非零退出'; passed=$true; expected_accepted=$false; exit_code=$LASTEXITCODE })
New-Item -ItemType Directory -Path $ReportDirectory -Force | Out-Null
$report = [PSCustomObject]@{ synthetic=$true; actual_signing=$false; installable_apk_built=$false; private_key_read=$false; case_count=$results.Count; passed_count=$results.Count; fixture_directory=$fixtureRoot; cases=@($results) }
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $ReportDirectory 'signing-script-results.json') -Encoding utf8
$report | Select-Object synthetic, actual_signing, installable_apk_built, private_key_read, case_count, passed_count
