param([switch]$CoreOnly, [switch]$SkipDistCopy)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$cmakeCommand = Get-Command cmake -ErrorAction SilentlyContinue
if ($cmakeCommand) {
  $cmakePath = $cmakeCommand.Source
} else {
  $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
  if (-not (Test-Path -LiteralPath $vswhere)) { throw 'Install Visual Studio 2022 C++ Build Tools and CMake.' }
  $vsPath = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
  $cmakePath = Join-Path $vsPath 'Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe'
}
$ctestPath = Join-Path (Split-Path -Parent $cmakePath) 'ctest.exe'
Push-Location $repoRoot
try {
  $hostEnabled = if ($CoreOnly) { 'OFF' } else { 'ON' }
  & $cmakePath --preset windows "-DIFT_BUILD_HOST=$hostEnabled"
  if ($LASTEXITCODE -ne 0) { throw 'CMake configuration failed' }
  & $cmakePath --build --preset windows
  if ($LASTEXITCODE -ne 0) { throw 'C++ build failed' }
  & $ctestPath --preset windows
  if ($LASTEXITCODE -ne 0) { throw 'C++ tests failed' }
  if (-not $CoreOnly -and -not $SkipDistCopy) {
    New-Item -ItemType Directory -Force -Path (Join-Path $repoRoot 'dist') | Out-Null
    Copy-Item -LiteralPath 'build\windows\windows\Release\InFalsusTouchHost.exe' -Destination 'dist\InFalsusTouchHost.exe'
  }
} finally {
  Pop-Location
}
