# 在新的独立 build/ 工程运行生产门禁的成功与拒绝分支，全为合成资料。
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$fixtureRoot = Join-Path $repoRoot ('build\rc3-check\gate-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'release'), (Join-Path $fixtureRoot 'app') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repoRoot 'release\legal-release.gradle') -Destination (Join-Path $fixtureRoot 'release\legal-release.gradle')
Copy-Item -LiteralPath (Join-Path $repoRoot 'release\unapproved-content-pattern.txt') -Destination (Join-Path $fixtureRoot 'release\unapproved-content-pattern.txt')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'fixtures\legal-gate-regression.gradle') -Destination (Join-Path $fixtureRoot 'app\build.gradle')
[IO.File]::WriteAllText((Join-Path $fixtureRoot 'settings.gradle'), "rootProject.name='合成法律门禁回归'`ninclude ':app'`n", [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $fixtureRoot 'gradle.properties'), "org.gradle.jvmargs=-Dfile.encoding=UTF-8`n", [Text.UTF8Encoding]::new($false))
& (Join-Path $repoRoot 'gradlew.bat') -p $fixtureRoot --no-daemon --console=plain --max-workers=1 '-PofficialVersionCode=24' :app:gateRegression
if ($LASTEXITCODE -ne 0) { throw "生产门禁的合成回归失败：$fixtureRoot" }
[PSCustomObject]@{ fixture = $fixtureRoot; report = (Join-Path $fixtureRoot 'gate-results.json'); synthetic = $true; official_apk_built = $false }
