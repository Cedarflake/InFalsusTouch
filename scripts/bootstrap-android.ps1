param([switch]$AcceptAndroidSdkLicense)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path $repoRoot '.tools'
$sdkRoot = Join-Path $toolsRoot 'android-sdk'
$env:GRADLE_USER_HOME = Join-Path $repoRoot '.cache\gradle'
$env:ANDROID_USER_HOME = Join-Path $repoRoot '.cache\android'
New-Item -ItemType Directory -Force -Path $toolsRoot, $env:GRADLE_USER_HOME, $env:ANDROID_USER_HOME | Out-Null
$javaSocketDir = Join-Path $repoRoot '.cache\java-tmp'
New-Item -ItemType Directory -Force -Path $javaSocketDir | Out-Null
$env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS `"-Djdk.net.unixdomain.tmpdir=$javaSocketDir`"".Trim()

function Invoke-CheckedNative {
  param([scriptblock]$Command)
  $previousPreference = $ErrorActionPreference
  try {
    $ErrorActionPreference = 'Continue'
    & $Command
    $nativeExit = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousPreference
  }
  if ($nativeExit -ne 0) { throw "Native command failed with exit code $nativeExit" }
}

function Get-VerifiedArchive {
  param([string]$Url, [string]$Path, [string]$Hash, [string]$Algorithm = 'SHA256')
  if (-not (Test-Path -LiteralPath $Path)) {
    Invoke-CheckedNative { & curl.exe --silent --show-error --fail --location --retry 2 --connect-timeout 20 --output $Path $Url }
  }
  if ((Get-FileHash -LiteralPath $Path -Algorithm $Algorithm).Hash -ne $Hash) {
    throw "Checksum mismatch; remove and re-download the invalid archive: $Path"
  }
}

$gradleVersion = '8.11.1'
$gradleRoot = Join-Path $toolsRoot "gradle-$gradleVersion"
$gradleArchive = Join-Path $toolsRoot "gradle-$gradleVersion-bin.zip"
Get-VerifiedArchive "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" $gradleArchive 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6'
if (-not (Test-Path -LiteralPath $gradleRoot)) {
  Expand-Archive -LiteralPath $gradleArchive -DestinationPath $toolsRoot
}

$wrapperProject = Join-Path $toolsRoot 'wrapper-project'
New-Item -ItemType Directory -Force -Path $wrapperProject | Out-Null
Set-Content -LiteralPath (Join-Path $wrapperProject 'settings.gradle') -Value "rootProject.name = 'wrapper-bootstrap'" -Encoding ASCII
Invoke-CheckedNative { & "$gradleRoot\bin\gradle.bat" -p $wrapperProject --no-daemon --max-workers=2 wrapper --gradle-version $gradleVersion --distribution-type bin --no-validate-url --gradle-distribution-sha256-sum 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6' }
Copy-Item -LiteralPath "$wrapperProject\gradlew", "$wrapperProject\gradlew.bat" -Destination "$repoRoot\android"
New-Item -ItemType Directory -Force -Path "$repoRoot\android\gradle\wrapper" | Out-Null
Copy-Item -Path "$wrapperProject\gradle\wrapper\*" -Destination "$repoRoot\android\gradle\wrapper"

if (-not $AcceptAndroidSdkLicense) {
  throw 'SDK setup requires -AcceptAndroidSdkLicense after reviewing https://developer.android.com/studio#terms-and-conditions'
}
$commandTools = Join-Path $toolsRoot 'commandlinetools-win-13114758.zip'
Get-VerifiedArchive 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' $commandTools '54a582f3bf73e04253602f2d1c80bd5868aac115' 'SHA1'
$sdkManager = Join-Path $sdkRoot 'cmdline-tools\19.0\bin\sdkmanager.bat'
if (-not (Test-Path -LiteralPath $sdkManager)) {
  $commandToolsRoot = Join-Path $sdkRoot 'cmdline-tools'
  New-Item -ItemType Directory -Force -Path $commandToolsRoot | Out-Null
  Expand-Archive -LiteralPath $commandTools -DestinationPath $commandToolsRoot
  $unpackedTools = [IO.Path]::GetFullPath((Join-Path $commandToolsRoot 'cmdline-tools'))
  if (-not $unpackedTools.StartsWith([IO.Path]::GetFullPath($toolsRoot) + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Unexpected SDK extraction path'
  }
  Rename-Item -LiteralPath $unpackedTools -NewName '19.0'
}
Invoke-CheckedNative { 1..30 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$sdkRoot" --licenses }
$packageFile = Join-Path $toolsRoot 'sdk-packages.txt'
Set-Content -LiteralPath $packageFile -Value @('platform-tools', 'platforms;android-35', 'build-tools;35.0.0') -Encoding ASCII
Invoke-CheckedNative { & $sdkManager "--sdk_root=$sdkRoot" "--package_file=$packageFile" }
$sdkProperty = $sdkRoot.Replace('\', '/')
Set-Content -LiteralPath "$repoRoot\android\local.properties" -Value "sdk.dir=$sdkProperty" -Encoding ASCII
Write-Output "Android toolchain ready at $sdkRoot"
