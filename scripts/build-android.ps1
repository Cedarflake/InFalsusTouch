param([switch]$SkipLint, [switch]$DeviceTests)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$env:GRADLE_USER_HOME = Join-Path $repoRoot '.cache\gradle'
$env:ANDROID_USER_HOME = Join-Path $repoRoot '.cache\android'
$javaSocketDir = Join-Path $repoRoot '.cache\java-tmp'
New-Item -ItemType Directory -Force -Path $javaSocketDir | Out-Null
$env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS `"-Djdk.net.unixdomain.tmpdir=$javaSocketDir`"".Trim()
$gradle = Join-Path $repoRoot '.tools\gradle-8.11.1\bin\gradle.bat'
if (-not (Test-Path -LiteralPath $gradle)) { $gradle = Join-Path $repoRoot 'android\gradlew.bat' }
$tasks = @(':touch:test', ':transport:test', ':app:assembleDebug')
$hostExecutable = Join-Path $repoRoot 'build\windows\windows\Release\InFalsusTouchHost.exe'
if (Test-Path -LiteralPath $hostExecutable) { $env:IFT_HOST_EXE = $hostExecutable }
if (-not $SkipLint) { $tasks += ':app:lintDebug' }
if ($DeviceTests) { $tasks += ':app:assembleDebugAndroidTest' }
Push-Location (Join-Path $repoRoot 'android')
try {
  $ErrorActionPreference = 'Continue'
  & $gradle --no-daemon --console=plain @tasks
  $buildExit = $LASTEXITCODE
  $ErrorActionPreference = 'Stop'
  if ($buildExit -ne 0) { throw 'Android build or verification failed' }
  $dist = Join-Path $repoRoot 'dist'
  New-Item -ItemType Directory -Force -Path $dist | Out-Null
  Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination (Join-Path $dist 'InFalsusTouch.apk')
} finally {
  Pop-Location
}
