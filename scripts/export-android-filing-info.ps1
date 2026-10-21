# 只导出公开证书；不构建 APK、不读取私钥、不修改密钥库或批准资料。
[CmdletBinding(DefaultParameterSetName = 'KeyStore')]
param(
    [Parameter(ParameterSetName = 'KeyStore')]
    [string]$KeyStorePath = $env:LIZHANG_SIGNING_STORE_FILE,
    [string]$KeyAlias = $env:LIZHANG_SIGNING_KEY_ALIAS,
    [Parameter(Mandatory = $true, ParameterSetName = 'PublicCertificate')]
    [string]$PublicCertificatePath,
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$outputDirectory = Join-Path $repoRoot 'build/filing'
$reportPath = Join-Path $outputDirectory 'android-filing-info.txt'

if (!$JdkHome) {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) { $JdkHome = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent }
}
$java = if ($JdkHome) { Join-Path $JdkHome 'bin/java.exe' } else { '' }
$keytool = if ($JdkHome) { Join-Path $JdkHome 'bin/keytool.exe' } else { '' }
if (!$java -or !(Test-Path -LiteralPath $java -PathType Leaf) -or
    !$keytool -or !(Test-Path -LiteralPath $keytool -PathType Leaf)) {
    throw '需要本机 JDK 17 或更新版本，请用 -JdkHome 指定 JDK 目录。'
}
# 防止把用户输入拼接进命令行；Windows 路径和别名不接受引号或换行。
function Assert-NativeArgument([string]$Value) {
    if ($Value -match '["\r\n]') { throw '路径或别名不能包含双引号或换行。' }
}
if (!$KeyAlias) { $KeyAlias = Read-Host '正式签名 Key Alias（别名，不是密码）' }
if ([string]::IsNullOrWhiteSpace($KeyAlias)) { throw '必须提供明确的证书别名。' }
Assert-NativeArgument $KeyAlias
if ($KeyAlias -ieq 'androiddebugkey') { throw '不得使用 Android 默认 Debug 签名作为正式备案身份。' }

# 固定输出到 Git 忽略目录；先验证忽略状态，避免意外把报告作为源文件提交。
$ignoredReport = & git -C $repoRoot check-ignore -- $reportPath 2>$null
if ($LASTEXITCODE -ne 0 -or !$ignoredReport) { throw 'build/filing 必须被 Git 忽略，停止导出。' }
[IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
$runId = [Guid]::NewGuid().ToString('N')
$tempCertificate = Join-Path $outputDirectory "$runId.cer"
$tempReport = Join-Path $outputDirectory "$runId.txt"
try {
    if ($PSCmdlet.ParameterSetName -eq 'KeyStore') {
        if (!$KeyStorePath) { $KeyStorePath = Read-Host '已选定的正式密钥库完整路径（仓库之外）' }
        Assert-NativeArgument $KeyStorePath
        try {
            $keyStoreFile = Get-Item -LiteralPath $KeyStorePath
        } catch {
            throw '正式密钥库路径无效或不可读，请在本机核对；不输出实际路径。'
        }
        if ($keyStoreFile.PSIsContainer -or $keyStoreFile.Name -ieq 'debug.keystore') {
            throw '需要仓库之外的正式密钥库文件，禁止使用 Debug 密钥。'
        }
        try {
            $ancestor = $keyStoreFile.Directory
            while ($null -ne $ancestor) {
                if (Test-Path -LiteralPath (Join-Path $ancestor.FullName '.git')) {
                    throw '密钥库位置不符合要求。'
                }
                $ancestor = $ancestor.Parent
            }
        } catch {
            throw '无法确认密钥库位于 Git 仓库之外，请在本机核对私有目录。'
        }
        # 隐藏输入；仅传给 keytool 子进程环境，不写密码文件、不放入命令行或父进程环境。
        $secret = Read-Host '密钥库密码（仅本机隐藏输入；不需要 Key 密码）' -AsSecureString
        if ($secret.Length -eq 0) { $secret.Dispose(); throw '未输入密码，停止；不进行猜测或重试。' }
        $start = New-Object Diagnostics.ProcessStartInfo
        $start.FileName = $keytool
        $start.UseShellExecute = $false
        $start.CreateNoWindow = $true
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        $start.Arguments = '-exportcert -keystore "{0}" -alias "{1}" -file "{2}" -storepass:env LIZHANG_FILING_STORE_PASSWORD' -f $keyStoreFile.FullName, $KeyAlias, $tempCertificate
        $buffer = [IntPtr]::Zero
        $process = $null
        try {
            $buffer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
            $start.EnvironmentVariables['LIZHANG_FILING_STORE_PASSWORD'] = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($buffer)
            $process = New-Object Diagnostics.Process
            $process.StartInfo = $start
            [void]$process.Start()
            # 同时排空两个管道，底层输出不进入日志。
            $stdout = $process.StandardOutput.ReadToEndAsync()
            $stderr = $process.StandardError.ReadToEndAsync()
            $process.WaitForExit()
            [void]$stdout.GetAwaiter().GetResult()
            [void]$stderr.GetAwaiter().GetResult()
            if ($process.ExitCode -ne 0) { throw '公开证书导出失败；请在本机核对路径、别名和密码，不自动重试。' }
        } catch {
            throw '公开证书导出失败；请在本机核对 JDK、路径、别名和密码，不自动重试。'
        } finally {
            $start.EnvironmentVariables.Remove('LIZHANG_FILING_STORE_PASSWORD')
            if ($buffer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($buffer) }
            $secret.Dispose()
            if ($null -ne $process) { $process.Dispose() }
        }
        $certificatePath = $tempCertificate
    } else {
        # 可接受本人已通过 keytool 或 Android Studio 导出的单张公开 X.509 证书。
        try {
            $certificatePath = (Get-Item -LiteralPath $PublicCertificatePath).FullName
        } catch {
            throw '公开证书路径无效或不可读，请在本机核对。'
        }
        if ([IO.Path]::GetExtension($certificatePath) -notin '.cer', '.crt', '.der', '.pem') {
            throw '仅接受公开 X.509 证书文件，不接受 JKS、PKCS12、私钥或 APK。'
        }
    }
    & $java '-Dfile.encoding=UTF-8' '--source' '17' (Join-Path $PSScriptRoot 'AndroidFilingInfo.java') $certificatePath $KeyAlias $tempReport
    if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $tempReport -PathType Leaf)) {
        throw '公开证书解析失败；已有报告未更新，不得将旧报告视为本次结果。'
    }
    Move-Item -LiteralPath $tempReport -Destination $reportPath -Force
    Write-Host "公开备案信息已保存到：$reportPath"
    Get-Content -LiteralPath $reportPath -Encoding UTF8
} finally {
    # 仅清理本次生成的两个公开临时文件，不触碰密钥库或历史资料。
    foreach ($temporaryFile in @($tempCertificate, $tempReport)) {
        if (Test-Path -LiteralPath $temporaryFile -PathType Leaf) { Remove-Item -LiteralPath $temporaryFile -Force }
    }
}
