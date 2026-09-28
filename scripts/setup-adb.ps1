param([string]$Serial, [string]$AdbPath, [switch]$AllDevices, [switch]$Install)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $AdbPath) {
  $command = Get-Command adb -ErrorAction SilentlyContinue
  $AdbPath = if ($command) { $command.Source } else { Join-Path $repoRoot '.tools\android-sdk\platform-tools\adb.exe' }
}
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'ADB not found. Install Android platform-tools or pass -AdbPath.' }
$devices = & $AdbPath devices -l
if ($LASTEXITCODE -ne 0) { throw 'adb devices failed' }
if ($Serial -and $AllDevices) { throw 'Choose -Serial or -AllDevices, not both.' }
$usbDevices = @()
$usbHardware = @(Get-CimInstance Win32_PnPEntity -Filter "PNPDeviceID LIKE 'USB%'" | Select-Object -ExpandProperty PNPDeviceID)
foreach ($line in $devices) {
  if ($line -notmatch '^(\S+)\s+device\s') { continue }
  $candidate = $Matches[1]
  $devicePath = & $AdbPath -s $candidate get-devpath
  $hasUsbHardware = @($usbHardware | Where-Object { $_.EndsWith(('\' + $candidate), [StringComparison]::OrdinalIgnoreCase) }).Count -gt 0
  if (($LASTEXITCODE -eq 0 -and $devicePath -match '^usb:') -or $hasUsbHardware) { $usbDevices += $candidate }
}
if ($Serial) {
  if ($Serial -notin $usbDevices) { throw 'The requested serial is not an authorized USB device.' }
  $selectedDevices = @($Serial)
} elseif ($AllDevices) {
  $selectedDevices = $usbDevices
} elseif ($usbDevices.Count -eq 1) {
  $selectedDevices = $usbDevices
} else {
  $devices | Write-Output
  throw 'Connect an authorized USB phone. With multiple phones, choose -AllDevices or -Serial.'
}
if ($selectedDevices.Count -lt 1 -or $selectedDevices.Count -gt 7) { throw 'Select between one and seven USB devices.' }
foreach ($deviceSerial in $selectedDevices) {
  foreach ($port in @(27183, 27184)) {
    & $AdbPath -s $deviceSerial reverse "tcp:$port" "tcp:$port"
    if ($LASTEXITCODE -ne 0) { throw "adb reverse failed for $deviceSerial on port $port" }
  }
  if ($Install) {
    & $AdbPath -s $deviceSerial install -r (Join-Path $repoRoot 'dist\InFalsusTouch.apk')
    if ($LASTEXITCODE -ne 0) { throw "App installation failed for $deviceSerial" }
  }
  Write-Output "USB ready: $deviceSerial"
  & $AdbPath -s $deviceSerial reverse --list
  if ($LASTEXITCODE -ne 0) { throw 'Could not verify reverse mappings' }
}
