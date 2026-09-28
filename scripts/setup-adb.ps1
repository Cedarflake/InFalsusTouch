param([string]$Serial, [string]$AdbPath)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $AdbPath) {
  $command = Get-Command adb -ErrorAction SilentlyContinue
  $AdbPath = if ($command) { $command.Source } else { Join-Path $repoRoot '.tools\android-sdk\platform-tools\adb.exe' }
}
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'ADB not found. Install Android platform-tools or pass -AdbPath.' }
$devices = & $AdbPath devices -l
if ($LASTEXITCODE -ne 0) { throw 'adb devices failed' }
$usbSerial = & $AdbPath -d get-serialno
if ($LASTEXITCODE -ne 0 -or -not $usbSerial -or $usbSerial -eq 'unknown') {
  $devices | Write-Output
  throw 'Connect and authorize exactly one USB phone. Wireless ADB is not selected.'
}
if ($Serial -and $Serial -ne $usbSerial) { throw 'The requested serial is not the connected USB phone.' }
$Serial = $usbSerial
foreach ($port in @(27183, 27184)) {
  & $AdbPath -s $Serial reverse "tcp:$port" "tcp:$port"
  if ($LASTEXITCODE -ne 0) { throw "adb reverse failed for port $port" }
}
& $AdbPath -s $Serial reverse --list
if ($LASTEXITCODE -ne 0) { throw 'Could not verify reverse mappings' }
