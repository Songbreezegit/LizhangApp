# 礼账 0.9.6 专项设备验证：每次执行单独留存，不重试、不覆盖历史证据。
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^emulator-[0-9]+$')][string]$Serial,
    [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$ExpectedAvd,
    [Parameter(Mandatory = $true)][ValidateRange(26, 100)][int]$RequiredApi,
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]{0,100}$')][string]$RunId,
    [string[]]$Classes = @(),
    [string]$TestFilter = '',
    [hashtable]$InstrumentationArguments = @{},
    [switch]$Install,
    [ValidateSet('', '0', '1')][string]$AnimationScale = '',
    [string]$AdbPath = 'D:\Android\Sdk\platform-tools\adb.exe',
    [string]$OutputRoot = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (!$OutputRoot) { $OutputRoot = Join-Path $repoRoot 'build\v096-api36-repair' }
$outputBase = [IO.Path]::GetFullPath($OutputRoot)
$runDirectory = [IO.Path]::GetFullPath((Join-Path $outputBase $RunId))
if (!$runDirectory.StartsWith($outputBase.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) { throw '运行目录越过证据根目录。' }
if (Test-Path -LiteralPath $runDirectory) { throw "运行编号已存在，禁止覆盖：$runDirectory" }
if ($Classes.Count -gt 0 -and $TestFilter) { throw 'Classes 和 TestFilter 只能选择一个。' }
foreach ($key in $InstrumentationArguments.Keys) {
    if ($key -isnot [string] -or [string]::IsNullOrWhiteSpace($key) -or $key -notmatch '^[A-Za-z][A-Za-z0-9_.-]*$') {
        throw 'InstrumentationArguments 的键必须是非空参数名，只能使用字母、数字、下划线、点或连字符。'
    }
    if ($key -in @('class', 'repairRunId', 'v096RunId', 'motion')) {
        throw "参数 $key 由验证脚本维护；class 使用 Classes/TestFilter，motion 使用 AnimationScale 并按实际设备状态生成。"
    }
    if ($null -eq $InstrumentationArguments[$key]) { throw "InstrumentationArguments 参数 $key 的值不能为 null。" }
}
if (!(Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw "找不到 adb：$AdbPath" }
[IO.Directory]::CreateDirectory($outputBase) | Out-Null
New-Item -ItemType Directory -Path $runDirectory -ErrorAction Stop | Out-Null
$utf8 = New-Object Text.UTF8Encoding($false)
$manifestPath = Join-Path $runDirectory 'manifest.json'
$commandPath = Join-Path $runDirectory 'commands.jsonl'
$runner = 'com.yangsong.lizhang.test/androidx.test.runner.AndroidJUnitRunner'
$animationKeys = @('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale')
$originalScales = @{}
$restoreNeeded = $false
$manifest = [ordered]@{
    runId = $RunId; serial = $Serial; expectedAvd = $ExpectedAvd; requiredApi = $RequiredApi
    startedAtUtc = [DateTimeOffset]::UtcNow.ToString('o'); completedAtUtc = $null
    status = '执行中'; requestedAnimationScale = $AnimationScale
    instrumentationArguments = $InstrumentationArguments
    configuration = [ordered]@{}; code = [ordered]@{}; apk = @(); attempts = @(); errors = @()
    originalRegressionCount = 67
    # 仅为默认完整计划的数量映射；指定类/方法与实际完成数量均以 stdout 为准。
    expectedExpandedCount = 80
    privacyScope = '仅本任务专属空白模拟器、测试画面和非敏感外观状态；不读取联系人、金额或备份。'
}

function Write-Text([string]$Path, [string]$Value) {
    [IO.File]::WriteAllText($Path, $Value, $utf8)
}
function Save-Manifest {
    Write-Text $manifestPath ($manifest | ConvertTo-Json -Depth 16)
}
function Get-TextHash([string]$Value) {
    $digest = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($digest.ComputeHash($utf8.GetBytes($Value)))).Replace('-', '').ToLowerInvariant() }
    finally { $digest.Dispose() }
}
function Invoke-AdbRaw([string[]]$Arguments, [string]$OutputFile = '') {
    $started = [DateTimeOffset]::UtcNow
    # 参数数组直接交给 adb，不拼接可执行的宿主 shell 字符串。
    $savedErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lines = @(& $AdbPath '-s' $Serial @Arguments 2>&1)
        $nativeExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedErrorPreference }
    $body = ($lines | ForEach-Object { $_.ToString() }) -join "`n"
    $record = [ordered]@{
        startedAtUtc = $started.ToString('o'); endedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
        executable = $AdbPath; arguments = @('-s', $Serial) + $Arguments
        exitCode = $nativeExitCode; outputFile = $OutputFile
    }
    [IO.File]::AppendAllText($commandPath, ($record | ConvertTo-Json -Depth 5 -Compress) + "`n", $utf8)
    if ($OutputFile) { Write-Text $OutputFile $body }
    return [pscustomobject]@{ Text = $body; ExitCode = $nativeExitCode }
}
function Assert-DeviceOwner {
    $name = Invoke-AdbRaw @('emu', 'avd', 'name')
    $names = @($name.Text -split '\r?\n' | ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -ne 'OK' })
    if ($name.ExitCode -ne 0 -or $names.Count -ne 1 -or $names[0] -cne $ExpectedAvd) {
        throw "设备归属不符，要求 $ExpectedAvd，实际：$($name.Text)"
    }
    $api = Invoke-AdbRaw @('shell', 'getprop', 'ro.build.version.sdk')
    if ($api.ExitCode -ne 0 -or $api.Text.Trim() -ne "$RequiredApi") {
        throw "设备 API 不符，要求 $RequiredApi，实际：$($api.Text)"
    }
}
function Invoke-OwnedAdb([string[]]$Arguments, [string]$OutputFile = '') {
    Assert-DeviceOwner
    return Invoke-AdbRaw $Arguments $OutputFile
}
function Read-DeviceValue([string[]]$Arguments) {
    $value = Invoke-OwnedAdb $Arguments
    if ($value.ExitCode -ne 0) { throw "读取设备配置失败：$($Arguments -join ' ')；$($value.Text)" }
    return $value.Text.Trim()
}
function Set-AnimationScales([hashtable]$Values) {
    foreach ($key in $animationKeys) {
        $value = "$($Values[$key])"
        $arguments = if ($value -eq 'null') { @('shell', 'settings', 'delete', 'global', $key) }
            else { @('shell', 'settings', 'put', 'global', $key, $value) }
        $result = Invoke-OwnedAdb $arguments
        if ($result.ExitCode -ne 0) { throw "修改本任务设备动画比例失败：$key；$($result.Text)" }
    }
}
function Capture-Diagnostics([string]$Destination, [string]$Phase) {
    # 限定诊断和系统异常标签，避免收集业务内容；不清空设备 logcat。
    $outputFile = Join-Path $Destination "logcat-$Phase.txt"
    $result = Invoke-OwnedAdb @('logcat', '-d', '-v', 'threadtime',
        '礼账主题诊断:D', '礼账启动诊断:D', 'AndroidRuntime:E', 'ActivityManager:I',
        'WindowManager:I', 'Choreographer:I', 'OpenGLRenderer:I', 'TestRunner:I', '*:S') $outputFile
    if ($result.ExitCode -ne 0) { throw "读取诊断日志失败：$($result.Text)" }
}
function Capture-Failure([string]$Destination, [string]$AttemptId) {
    $remoteStem = "/sdcard/Android/data/com.yangsong.lizhang/files/repair-failure-$RunId-$AttemptId"
    foreach ($capture in @(
        @{ Name = 'failure-screen.png'; Remote = "$remoteStem.png"; Args = @('shell', 'screencap', '-p', "$remoteStem.png") },
        @{ Name = 'failure-hierarchy.xml'; Remote = "$remoteStem.xml"; Args = @('shell', 'uiautomator', 'dump', "$remoteStem.xml") }
    )) {
        $result = Invoke-OwnedAdb $capture.Args (Join-Path $Destination ($capture.Name + '.capture.log'))
        if ($result.ExitCode -eq 0) {
            $pullLog = Join-Path $Destination ($capture.Name + '.pull.log')
            $pull = Invoke-OwnedAdb @('pull', $capture.Remote, (Join-Path $Destination $capture.Name)) $pullLog
            if ($pull.ExitCode -ne 0) { throw "失败证据复制失败：$($capture.Name)；$($pull.Text)" }
        } else { throw "失败证据采集失败：$($capture.Name)；$($result.Text)" }
    }
}
function Pull-TestEvidence([string]$Destination, [string]$EvidenceRunId) {
    $pullResults = @()
    # 仅复制测试代码明确创建的证据目录；不遍历或复制数据库、偏好与备份。
    foreach ($folder in @('transition-evidence', 'startup-v096-real', 'startup-v096',
        'navigation-v096', 'navigation-pages-v096', 'guide-v096', 'press-v096', 'v096-api36-repair')) {
        $remote = "/sdcard/Android/data/com.yangsong.lizhang/files/$folder"
        $local = $Destination
        # 启动测试按运行编号独立保存，只复制当前轮，避免重复复制先前冷启动画面。
        if ($folder -in @('startup-v096-real', 'startup-v096')) {
            $remote += "/$EvidenceRunId"
            $local = Join-Path $Destination $folder
        }
        $exists = Invoke-OwnedAdb @('shell', 'test', '-d', $remote)
        if ($exists.ExitCode -ne 0) {
            $pullResults += @{ directory = $folder; status = '未生成'; checkExitCode = $exists.ExitCode }
            continue
        }
        [IO.Directory]::CreateDirectory($local) | Out-Null
        $result = Invoke-OwnedAdb @('pull', $remote, $local) (Join-Path $Destination "pull-$folder.log")
        $pullResults += @{ directory = $folder; remote = $remote; status = $(if ($result.ExitCode -eq 0) { '已保存' } else { '复制受阻' }); exitCode = $result.ExitCode }
    }
    return $pullResults
}

# 保留原交付 17 批、67 项的顺序与历史数量；Native 类现新增 3 项。
# 末尾追加外观异常 6 项、启动窗口异常 3 项和返回中断 1 项，默认完整映射为 80 项。
# expectedOriginalCount / expectedAdditionalCount 只描述映射，不替代 stdout 实际统计。
$plan = @()
if ($TestFilter) { $plan = @(@{ filter = $TestFilter; name = '指定用例'; arguments = @(); expectedOriginalCount = $null }) }
elseif ($Classes.Count -gt 0) {
    foreach ($class in $Classes) { $plan += @{ filter = $class; name = $class; arguments = @(); expectedOriginalCount = $null } }
} else {
    foreach ($entry in @(
        @('flow.NotchedBottomNavInstrumentedTest', 9), @('flow.NavigationPageMotionInstrumentedTest', 2),
        @('onboarding.FeatureGuideUiInstrumentedTest', 10), @('flow.PressFeedbackInstrumentedTest', 8),
        @('flow.FrostedGlassInstrumentedTest', 5), @('flow.GlassUiInstrumentedTest', 8),
        @('flow.SettingsAndYearMenuInstrumentedTest', 6), @('flow.GiftSaveFlowInstrumentedTest', 4),
        @('flow.AppearanceTransitionInstrumentedTest', 3), @('flow.NativeTransitionRecordingTest', 3)
    )) { $plan += @{ filter = "com.yangsong.lizhang.$($entry[0])"; name = $entry[0]; arguments = @(); expectedOriginalCount = $entry[1] } }
    $plan += @{ filter = 'com.yangsong.lizhang.flow.StartupRealFrameInstrumentedTest'; name = '启动-real'; arguments = @('-e', 'startupScenario', 'real'); expectedOriginalCount = 1 }
    foreach ($scenario in @('launcher', 'notification', 'interrupt', 'background', 'animation-disabled')) {
        $startupScenario = if ($scenario -eq 'animation-disabled') { 'launcher' } else { $scenario }
        $plan += @{ filter = 'com.yangsong.lizhang.flow.StartupAnimationInstrumentedTest'; name = "启动-$scenario"
            arguments = @('-e', 'startupScenario', $startupScenario); expectedOriginalCount = 1; disableAnimations = ($scenario -eq 'animation-disabled') }
    }
    $plan += @{ filter = 'com.yangsong.lizhang.flow.ExpandedLanguageInstrumentedTest,com.yangsong.lizhang.flow.LocalizationInstrumentedTest'
        name = '语言回归'; arguments = @(); expectedOriginalCount = 3 }
    $plan += @{ filter = 'com.yangsong.lizhang.flow.AppearanceFailureInstrumentedTest'
        name = '外观异常路径'; arguments = @(); expectedOriginalCount = 0; expectedAdditionalCount = 6 }
    $plan += @{ filter = 'com.yangsong.lizhang.flow.StartupWindowGateInstrumentedTest'
        name = '启动窗口异常路径'; arguments = @(); expectedOriginalCount = 0; expectedAdditionalCount = 3 }
    $plan += @{ filter = 'com.yangsong.lizhang.flow.StartupAnimationInstrumentedTest'
        name = '启动-back'; arguments = @('-e', 'startupScenario', 'back'); expectedOriginalCount = 0; expectedAdditionalCount = 1 }
}
$manifest.plan = $plan
Save-Manifest

try {
    Assert-DeviceOwner
    Push-Location $repoRoot
    try {
        $sha = @(& git rev-parse HEAD 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw '无法取得代码 SHA。' }
        $dirty = @(& git status --porcelain=v1 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw '无法取得工作区状态。' }
        $diff = @(& git diff HEAD --binary 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw '无法取得当前差异。' }
        $manifest.code = @{ sha = $sha.Trim(); status = $dirty; trackedDiffSha256 = Get-TextHash $diff
            diffHashEncoding = 'git diff HEAD --binary 的 stdout 以 LF 连接后按 UTF-8 计算；不保存差异内容。' }
        $untrackedSource = @()
        foreach ($path in @(& git ls-files --others --exclude-standard)) {
            if ($path -match '\.(kt|kts|ps1|md|xml)$') {
                $untrackedSource += @{ path = $path; sha256 = (Get-FileHash -LiteralPath (Join-Path $repoRoot $path) -Algorithm SHA256).Hash.ToLowerInvariant() }
            }
        }
        $manifest.code.untrackedSourceHashes = $untrackedSource
    } finally { Pop-Location }

    foreach ($apkRelative in @('app\build\outputs\apk\debug\app-debug.apk', 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')) {
        $apkPath = Join-Path $repoRoot $apkRelative
        if (!(Test-Path -LiteralPath $apkPath -PathType Leaf)) { throw "APK 不存在，执行受阻：$apkPath" }
        $manifest.apk += @{ path = $apkPath; sha256 = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant(); bytes = (Get-Item -LiteralPath $apkPath).Length }
        if ($Install) {
            $result = Invoke-OwnedAdb @('install', '-r', '-t', $apkPath) (Join-Path $runDirectory ("install-$($manifest.apk.Count).log"))
            if ($result.ExitCode -ne 0 -or $result.Text -notmatch '(?m)^Success\s*$') { throw "安装失败：$($result.Text)" }
        }
    }
    $manifest.configuration.api = Read-DeviceValue @('shell', 'getprop', 'ro.build.version.sdk')
    $manifest.configuration.fingerprint = Read-DeviceValue @('shell', 'getprop', 'ro.build.fingerprint')
    $manifest.configuration.image = Read-DeviceValue @('shell', 'getprop', 'ro.build.description')
    $manifest.configuration.resolution = Read-DeviceValue @('shell', 'wm', 'size')
    $manifest.configuration.density = Read-DeviceValue @('shell', 'wm', 'density')
    $manifest.configuration.fontScale = Read-DeviceValue @('shell', 'settings', 'get', 'system', 'font_scale')
    $manifest.configuration.systemLanguage = Read-DeviceValue @('shell', 'getprop', 'persist.sys.locale')
    $manifest.configuration.systemLocalesSetting = Read-DeviceValue @('shell', 'settings', 'get', 'system', 'system_locales')
    if ($RequiredApi -ge 33) {
        $locales = Invoke-OwnedAdb @('shell', 'cmd', 'locale', 'get-app-locales', 'com.yangsong.lizhang', '--user', '0')
        $manifest.configuration.applicationLocales = @{ text = $locales.Text; exitCode = $locales.ExitCode }
    }
    $package = Invoke-OwnedAdb @('shell', 'dumpsys', 'package', 'com.yangsong.lizhang')
    if ($package.ExitCode -ne 0) { throw '无法读取已安装应用版本。' }
    $manifest.configuration.applicationVersion = @($package.Text -split '\r?\n' | Where-Object { $_ -match '^\s*version(Code|Name)=' } | ForEach-Object { $_.Trim() })
    if ($package.Text -notmatch '(?m)^\s*versionCode=16\s' -or $package.Text -notmatch '(?m)^\s*versionName=0\.9\.6\s*$') {
        throw '已安装应用不是 0.9.6 / 16，执行受阻。'
    }
    $manifest.configuration.installedApkHashes = @()
    for ($apkIndex = 0; $apkIndex -lt 2; $apkIndex++) {
        $packageId = if ($apkIndex -eq 0) { 'com.yangsong.lizhang' } else { 'com.yangsong.lizhang.test' }
        $packagePaths = Read-DeviceValue @('shell', 'pm', 'path', $packageId)
        $basePath = @($packagePaths -split '\r?\n' | Where-Object { $_ -match '^package:.*\/base\.apk$' })
        if ($basePath.Count -ne 1) { throw "无法唯一确定已安装 APK：$packageId；$packagePaths" }
        $deviceApk = $basePath[0].Substring(8)
        $deviceHash = Read-DeviceValue @('shell', 'sha256sum', $deviceApk)
        if ($deviceHash -notmatch '^([a-fA-F0-9]{64})\s') { throw "无法校验已安装 APK：$packageId" }
        $installedHash = $Matches[1].ToLowerInvariant()
        $manifest.configuration.installedApkHashes += @{ package = $packageId; sha256 = $installedHash }
        if ($installedHash -ne $manifest.apk[$apkIndex].sha256) { throw "设备 APK 与本次构建不一致：$packageId" }
    }
    foreach ($key in $animationKeys) { $originalScales[$key] = Read-DeviceValue @('shell', 'settings', 'get', 'global', $key) }
    $manifest.configuration.originalAnimationScales = $originalScales.Clone()
    if ($AnimationScale) {
        $restoreNeeded = $true
        $requested = @{}; foreach ($key in $animationKeys) { $requested[$key] = $AnimationScale }
        Set-AnimationScales $requested
    }
    $baseScales = @{}; foreach ($key in $animationKeys) { $baseScales[$key] = Read-DeviceValue @('shell', 'settings', 'get', 'global', $key) }
    $manifest.configuration.executionAnimationScales = $baseScales.Clone()
    Save-Manifest

    for ($index = 0; $index -lt $plan.Count; $index++) {
        $item = $plan[$index]
        $attemptId = '{0:D2}' -f ($index + 1)
        $destination = Join-Path $runDirectory "attempt-$attemptId"
        New-Item -ItemType Directory -Path $destination | Out-Null
        $attempt = [ordered]@{ id = $attemptId; name = $item.name; filter = $item.filter
            startedAtUtc = [DateTimeOffset]::UtcNow.ToString('o'); endedAtUtc = $null
            status = '执行中'; testsRun = $null; failures = $null; expectedOriginalCount = $item.expectedOriginalCount
            exitCode = $null; evidenceDirectory = $destination; evidence = @(); diagnosticErrors = @() }
        $manifest.attempts += $attempt
        Save-Manifest
        try {
            Assert-DeviceOwner
            if ($item.disableAnimations) {
                $restoreNeeded = $true
                $zeroScales = @{}; foreach ($key in $animationKeys) { $zeroScales[$key] = '0' }
                Set-AnimationScales $zeroScales
            }
            $attempt.animationScales = @{}; foreach ($key in $animationKeys) { $attempt.animationScales[$key] = Read-DeviceValue @('shell', 'settings', 'get', 'global', $key) }
            Capture-Diagnostics $destination 'before'
            $motion = if ($attempt.animationScales.animator_duration_scale -eq '0') { 'disabled' } else { 'enabled' }
            $testArguments = [ordered]@{ class = $item.filter; repairRunId = "$RunId-$attemptId"
                v096RunId = "$RunId-$attemptId"; motion = $motion }
            for ($argumentIndex = 0; $argumentIndex -lt $item.arguments.Count; $argumentIndex += 3) {
                $testArguments[$item.arguments[$argumentIndex + 1]] = $item.arguments[$argumentIndex + 2]
            }
            # 例如 themeLocator=legacy/precise；每轮实际参数完整写入清单。
            foreach ($key in ($InstrumentationArguments.Keys | Sort-Object)) { $testArguments[$key] = [string]$InstrumentationArguments[$key] }
            $arguments = @('shell', 'am', 'instrument', '-w', '-r')
            foreach ($entry in $testArguments.GetEnumerator()) { $arguments += @('-e', [string]$entry.Key, [string]$entry.Value) }
            $arguments += @($runner)
            $attempt.instrumentationArguments = $testArguments
            $attempt.command = @{ executable = $AdbPath; arguments = @('-s', $Serial) + $arguments }
            Save-Manifest
            $result = Invoke-OwnedAdb $arguments (Join-Path $destination 'instrumentation.txt')
            $attempt.exitCode = $result.ExitCode
            if ($result.ExitCode -eq 0 -and $result.Text -match 'OK \((\d+) tests?\)' -and
                $result.Text -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_ABORTED') {
                $attempt.status = '通过'; $attempt.testsRun = [int]$Matches[1]; $attempt.failures = 0
            } elseif ($result.Text -match 'Tests run: (\d+),\s+Failures: (\d+)') {
                $attempt.status = '失败'; $attempt.testsRun = [int]$Matches[1]; $attempt.failures = [int]$Matches[2]
            } else { $attempt.status = '执行受阻' }
        } catch {
            $attempt.status = '执行受阻'; $attempt.diagnosticErrors += $_.Exception.Message
        } finally {
            try { Capture-Diagnostics $destination 'after' } catch { $attempt.diagnosticErrors += $_.Exception.Message }
            if ($attempt.status -ne '通过') {
                try { Capture-Failure $destination $attemptId } catch { $attempt.diagnosticErrors += $_.Exception.Message }
            }
            try {
                $attempt.evidence = @(Pull-TestEvidence $destination "$RunId-$attemptId")
                foreach ($copy in @($attempt.evidence | Where-Object { $_.status -eq '复制受阻' })) {
                    $attempt.diagnosticErrors += "测试证据复制受阻：$($copy.directory)"
                }
            } catch { $attempt.diagnosticErrors += $_.Exception.Message }
            if ($item.disableAnimations) {
                try { Set-AnimationScales $baseScales } catch { $attempt.diagnosticErrors += $_.Exception.Message }
            }
            $attempt.testResult = $attempt.status
            if ($attempt.status -eq '通过' -and $attempt.diagnosticErrors.Count -gt 0) { $attempt.status = '执行受阻' }
            $attempt.endedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
            Save-Manifest
        }
        Write-Output "$RunId $attemptId $($item.name)：$($attempt.status)，运行 $($attempt.testsRun)，失败 $($attempt.failures)"
        # 断连、崩溃或工具错误不包装成失败断言，也不自动重试。
        if ($attempt.status -eq '执行受阻') { break }
    }
    $manifest.status = if (@($manifest.attempts | Where-Object { $_.status -eq '执行受阻' }).Count -gt 0) { '执行受阻' }
        elseif (@($manifest.attempts | Where-Object { $_.status -eq '失败' }).Count -gt 0) { '失败' }
        elseif ($manifest.attempts.Count -eq $plan.Count) { '通过' } else { '未完成' }
} catch {
    $manifest.status = '执行受阻'; $manifest.errors += $_.Exception.Message
    Write-Warning $_.Exception.Message
} finally {
    if ($restoreNeeded -and $originalScales.Count -eq 3) {
        try { Set-AnimationScales $originalScales; $manifest.configuration.animationRestore = '已恢复原值' }
        catch { $manifest.errors += "动画比例恢复受阻：$($_.Exception.Message)"; $manifest.configuration.animationRestore = '执行受阻'; $manifest.status = '执行受阻' }
    }
    $manifest.completedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
    Save-Manifest
}
Write-Output "证据清单：$manifestPath；结果：$($manifest.status)"
if ($manifest.status -ne '通过') { exit 1 }
