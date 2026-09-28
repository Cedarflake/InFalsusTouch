param(
  [switch]$SkipLint,
  [switch]$DeviceTests,
  [switch]$SkipFlutterChecks,
  [ValidateSet('debug', 'profile')][string]$Mode = 'debug'
)
$ErrorActionPreference = 'Stop'
$Mode = $Mode.ToLowerInvariant()
$repoRoot = Split-Path -Parent $PSScriptRoot
$flutterCommand = Get-Command flutter -ErrorAction Stop
$flutterSdk = Split-Path -Parent (Split-Path -Parent $flutterCommand.Source)
$localProperties = Join-Path $repoRoot 'android\local.properties'
if (-not (Test-Path -LiteralPath $localProperties)) { throw 'Configure sdk.dir in android/local.properties first' }
$properties = @(Get-Content -LiteralPath $localProperties | Where-Object { $_ -notmatch '^flutter.sdk=' })
$properties += 'flutter.sdk=' + $flutterSdk.Replace('\', '/')
[System.IO.File]::WriteAllLines($localProperties, $properties, [System.Text.UTF8Encoding]::new($false))
$env:GRADLE_USER_HOME = Join-Path $repoRoot '.cache\gradle'
$env:ANDROID_USER_HOME = Join-Path $repoRoot '.cache\android'
$javaSocketDir = Join-Path $repoRoot '.cache\java-tmp'
New-Item -ItemType Directory -Force -Path $javaSocketDir | Out-Null
Push-Location $repoRoot
try {
  & $flutterCommand.Source pub get --offline
  if ($LASTEXITCODE -ne 0) { throw 'Flutter dependencies are missing; run flutter pub get once with network access' }
  if (-not $SkipFlutterChecks) {
    & $flutterCommand.Source analyze --no-pub
    if ($LASTEXITCODE -ne 0) { throw 'Flutter analysis failed' }
    & $flutterCommand.Source test --no-pub --concurrency=1
    if ($LASTEXITCODE -ne 0) { throw 'Flutter tests failed' }
  }
} finally { Pop-Location }
$env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS `"-Djdk.net.unixdomain.tmpdir=$javaSocketDir`"".Trim()
$gradle = Join-Path $repoRoot '.tools\gradle-8.11.1\bin\gradle.bat'
if (-not (Test-Path -LiteralPath $gradle)) { $gradle = Join-Path $repoRoot 'android\gradlew.bat' }
$variant = if ($Mode -eq 'profile') { 'Profile' } else { 'Debug' }
$tasks = @(':settings:test', ':touch:test', ':transport:test', ':video:testDebugUnitTest', ":app:assemble$variant")
$hostExecutable = Join-Path $repoRoot 'build\windows\windows\Release\InFalsusTouchHost.exe'
if (Test-Path -LiteralPath $hostExecutable) { $env:IFT_HOST_EXE = $hostExecutable }
if (-not $SkipLint) { $tasks += ":app:lint$variant" }
if ($DeviceTests) { $tasks += ":app:assemble${variant}AndroidTest" }
Push-Location (Join-Path $repoRoot 'android')
try {
  $ErrorActionPreference = 'Continue'
  & $gradle --no-daemon --console=plain "-PiftTestBuildType=$Mode" @tasks
  $buildExit = $LASTEXITCODE
  $ErrorActionPreference = 'Stop'
  if ($buildExit -ne 0) { throw 'Android build or verification failed' }
  $dist = Join-Path $repoRoot 'dist'
  New-Item -ItemType Directory -Force -Path $dist | Out-Null
  $apkName = if ($Mode -eq 'profile') { 'InFalsusTouch-profile.apk' } else { 'InFalsusTouch.apk' }
  Copy-Item -LiteralPath "app\build\outputs\apk\$Mode\app-$Mode.apk" -Destination (Join-Path $dist $apkName)
} finally {
  Pop-Location
}
