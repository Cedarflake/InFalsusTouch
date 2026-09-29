param([string]$AdbDirectory)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$versionLine = Get-Content -LiteralPath (Join-Path $repoRoot 'pubspec.yaml') | Where-Object { $_ -match '^version: ' }
if ($versionLine -notmatch '^version: ([0-9]+\.[0-9]+\.[0-9]+(?:-[a-z0-9.]+)?)\+([0-9]+)$') { throw 'Invalid release version in pubspec.yaml' }
$version = $Matches[1]
$versionCode = $Matches[2]
$apk = Join-Path $repoRoot 'dist\InFalsusTouch-release.apk'
$hostBinary = Join-Path $repoRoot 'build\windows\windows\Release\InFalsusTouchHost.exe'
$license = Join-Path $repoRoot 'LICENSE'
$sdkLine = Get-Content -LiteralPath (Join-Path $repoRoot 'android\local.properties') | Where-Object { $_ -match '^sdk.dir=' }
if (-not $sdkLine) { throw 'Configure sdk.dir in android/local.properties' }
$sdkRoot = $sdkLine.Substring(8).Replace('\\', '\').Replace('\:', ':')
$buildTools = Join-Path $sdkRoot 'build-tools\35.0.0'
if (-not $AdbDirectory) { $AdbDirectory = Join-Path $sdkRoot 'platform-tools' }
$adbFiles = @('adb.exe', 'AdbWinApi.dll', 'AdbWinUsbApi.dll', 'NOTICE.txt')
foreach ($path in @($apk, $hostBinary, $license, (Join-Path $buildTools 'apksigner.bat'), (Join-Path $buildTools 'aapt.exe'))) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required build artifact or tool is missing: $path" }
}
foreach ($name in $adbFiles) {
  if (-not (Test-Path -LiteralPath (Join-Path $AdbDirectory $name) -PathType Leaf)) { throw "Missing platform-tools file: $name" }
}

$ErrorActionPreference = 'Continue'
$signature = & (Join-Path $buildTools 'apksigner.bat') verify --verbose --print-certs $apk 2>&1
$signatureExit = $LASTEXITCODE
$badging = & (Join-Path $buildTools 'aapt.exe') dump badging $apk 2>&1
$badgingExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($signatureExit -ne 0) { throw "APK signature verification failed: $signature" }
if ($signature -match 'CN=Android Debug') { throw 'A release must not use an Android debug signing certificate' }
if ($badgingExit -ne 0) { throw "Cannot inspect APK: $badging" }
$metadata = $badging -join "`n"
$expected = "package: name='dev.cedarflake.infalsustouch' versionCode='$versionCode' versionName='$version'"
if (-not $metadata.Contains($expected) -or $metadata -match 'application-debuggable') { throw 'APK version, application ID or release mode is incorrect' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($apk)
try {
  $names = @($archive.Entries | ForEach-Object { $_.FullName })
  foreach ($abi in @('arm64-v8a', 'armeabi-v7a', 'x86_64')) {
    if ($names -notcontains "lib/$abi/libapp.so") { throw "Missing release AOT code for $abi" }
  }
  if ($names -contains 'assets/flutter_assets/kernel_blob.bin') { throw 'A debug Dart kernel is present in the release APK' }
} finally { $archive.Dispose() }

$binary = [IO.BinaryReader]::new([IO.File]::OpenRead($hostBinary))
try {
  if ($binary.ReadUInt16() -ne 0x5A4D) { throw 'Invalid Host executable' }
  $binary.BaseStream.Position = 0x3C
  $peOffset = $binary.ReadUInt32()
  $binary.BaseStream.Position = $peOffset
  if ($binary.ReadUInt32() -ne 0x4550 -or $binary.ReadUInt16() -ne 0x8664) { throw 'Host must be a Windows x64 PE executable' }
} finally { $binary.Dispose() }

$output = Join-Path $repoRoot "dist\releases\v$version"
if (Test-Path -LiteralPath $output) { throw "Release output already exists: $output" }
$stage = Join-Path $repoRoot ("build\release-package-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $output, $stage, (Join-Path $stage 'platform-tools') -Force | Out-Null
Copy-Item -LiteralPath $hostBinary -Destination (Join-Path $stage 'InFalsusTouchHost.exe')
Copy-Item -LiteralPath $license -Destination (Join-Path $stage 'LICENSE')
foreach ($name in $adbFiles) {
  Copy-Item -LiteralPath (Join-Path $AdbDirectory $name) -Destination (Join-Path $stage 'platform-tools')
}
$readme = [IO.File]::ReadAllText((Join-Path $repoRoot 'README.md'))
foreach ($relative in @('docs/SETTINGS.md', 'docs/DEVELOPMENT.md', 'ARCHITECTURE.md')) {
  $readme = $readme.Replace("($relative)", "(https://github.com/Cedarflake/InFalsusTouch/blob/v$version/$relative)")
}
$readme = $readme.Replace('(docs/assets/gameplay.gif)', "(https://raw.githubusercontent.com/Cedarflake/InFalsusTouch/v$version/docs/assets/gameplay.gif)")
[IO.File]::WriteAllText((Join-Path $stage 'README.md'), $readme, [Text.UTF8Encoding]::new($false))
$apkName = "InFalsusTouch-v$version-android.apk"
$zipName = "InFalsusTouch-v$version-windows-x64.zip"
Copy-Item -LiteralPath $apk -Destination (Join-Path $output $apkName)
$bundle = [IO.Compression.ZipFile]::Open((Join-Path $output $zipName), [IO.Compression.ZipArchiveMode]::Create)
try {
  $bundleFiles = @('InFalsusTouchHost.exe', 'README.md', 'LICENSE') + @($adbFiles | ForEach-Object { "platform-tools/$_" })
  foreach ($relative in $bundleFiles) {
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($bundle, (Join-Path $stage $relative), $relative,
      [IO.Compression.CompressionLevel]::Optimal) | Out-Null
  }
} finally { $bundle.Dispose() }
$checksums = foreach ($name in @($apkName, $zipName)) {
  $hash = (Get-FileHash -LiteralPath (Join-Path $output $name) -Algorithm SHA256).Hash.ToLowerInvariant()
  "$hash  $name"
}
[IO.File]::WriteAllLines((Join-Path $output 'SHA256SUMS.txt'), $checksums, [Text.UTF8Encoding]::new($false))
$signature | Where-Object { $_ -match 'certificate SHA-256 digest' }
Get-ChildItem -LiteralPath $output | Select-Object Name, Length
