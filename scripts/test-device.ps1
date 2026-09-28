param([switch]$SkipBuild, [switch]$InputOnly, [string]$AdbPath)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $SkipBuild) {
  & (Join-Path $PSScriptRoot 'build-windows.ps1')
  & (Join-Path $PSScriptRoot 'build-android.ps1') -DeviceTests
}
if (-not $AdbPath) { $AdbPath = Join-Path $repoRoot '.tools\android-sdk\platform-tools\adb.exe' }
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'Pass -AdbPath or bootstrap the Android SDK.' }
$serial = & $AdbPath -d get-serialno
if ($LASTEXITCODE -ne 0 -or -not $serial -or $serial -eq 'unknown') { throw 'Connect one authorized USB phone.' }
$mappings = & $AdbPath -d reverse --list
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect reverse mappings' }
$previousMapping = $null
foreach ($line in $mappings) {
  if ($line -match '^\S+\s+tcp:27184\s+(\S+)') { $previousMapping = $Matches[1] }
}
$outputRoot = Join-Path $repoRoot 'build\device-test'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$tracePath = Join-Path $outputRoot 'input-trace.txt'
$portReservation = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$portReservation.Start()
$port = $portReservation.LocalEndpoint.Port
$portReservation.Stop()
$arguments = @('--dry-run', '--port', "$port", '--trace', "`"$tracePath`"")
$testHost = Start-Process -FilePath (Join-Path $repoRoot 'dist\InFalsusTouchHost.exe') -ArgumentList $arguments -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $outputRoot 'host-stdout.txt') -RedirectStandardError (Join-Path $outputRoot 'host-stderr.txt')
try {
  & $AdbPath -d reverse tcp:27184 "tcp:$port"
  if ($LASTEXITCODE -ne 0) { throw 'Could not set up test USB reverse' }
  & $AdbPath -d install -r (Join-Path $repoRoot 'dist\InFalsusTouch.apk')
  if ($LASTEXITCODE -ne 0) { throw 'App installation failed' }
  & $AdbPath -d install -r -t (Join-Path $repoRoot 'android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
  if ($LASTEXITCODE -ne 0) { throw 'Instrumentation installation failed' }
  $testClasses = 'dev.cedarflake.ift.MotionEventDeviceTest,dev.cedarflake.ift.UsbTransportDeviceTest,dev.cedarflake.ift.SettingsDeviceTest'
  $expectedTests = 12
  if ($InputOnly) {
    $testClasses = 'dev.cedarflake.ift.MotionEventDeviceTest,dev.cedarflake.ift.UsbTransportDeviceTest,dev.cedarflake.ift.SettingsDeviceTest#settingsPersistAcrossStoreInstancesAndRecoverFromCorruption,dev.cedarflake.ift.SettingsDeviceTest#legacyFieldModeMigratesWithoutResettingOtherPreferences'
    $expectedTests = 7
  }
  $result = & $AdbPath -d shell am instrument -w -r -e usbHost true -e class $testClasses dev.cedarflake.infalsustouch.test/androidx.test.runner.AndroidJUnitRunner
  $instrumentExit = $LASTEXITCODE
  $result | Set-Content -LiteralPath (Join-Path $outputRoot 'instrumentation.txt') -Encoding UTF8
  $result | Write-Output
  if ($instrumentExit -ne 0 -or -not ($result -match [regex]::Escape("OK ($expectedTests tests)")) -or ($result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed')) {
    throw "Device instrumentation did not pass all $expectedTests tests"
  }
  $deadline = [DateTime]::UtcNow.AddSeconds(2)
  do {
    $trace = @(Get-Content -LiteralPath $tracePath)
    $releases = @($trace | Where-Object { $_ -match '^UP ' }).Count
    if ($releases -ge 6) { break }
    Start-Sleep -Milliseconds 20
  } while ([DateTime]::UtcNow -lt $deadline)
  foreach ($lane in 1..6) {
    if ($trace -notcontains "DOWN $lane" -or $trace -notcontains "UP $lane") { throw "USB lane $lane did not complete its hold/release" }
  }
  if ($trace -notcontains 'ABS 640 360') { throw 'USB Field mapping missing from host trace' }
  if ($trace -notcontains 'REL 1280' -or $trace -notcontains 'REL -320') { throw 'USB relative Field displacement was changed' }
  Write-Output 'PASS: physical USB transport, six-key hold, Field and disconnect release verified in dry-run mode.'
} finally {
  if ($previousMapping) {
    & $AdbPath -d reverse tcp:27184 $previousMapping
  } else {
    & $AdbPath -d reverse --remove tcp:27184
  }
  if (-not $testHost.HasExited) { Stop-Process -Id $testHost.Id }
  $testHost.Dispose()
}
