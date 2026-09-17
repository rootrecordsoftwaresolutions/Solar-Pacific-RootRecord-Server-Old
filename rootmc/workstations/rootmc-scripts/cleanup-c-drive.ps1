# Safe C: cleanup — temp, package caches, browser/app caches. No docs/source deleted.
$ErrorActionPreference = 'SilentlyContinue'

$before = (Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='C:'").FreeSpace
Write-Host ("BEFORE_FREE_GB={0:N2}" -f ($before / 1GB))

function Clear-DirContents([string]$path) {
  if (-not (Test-Path $path)) { return }
  Get-ChildItem $path -Force | ForEach-Object {
    Remove-Item $_.FullName -Recurse -Force
  }
  Write-Host "Cleared: $path"
}

Clear-DirContents "$env:LOCALAPPDATA\Temp"
Clear-DirContents "C:\Windows\Temp"
Clear-DirContents "$env:USERPROFILE\.gradle\caches"
Clear-DirContents "$env:USERPROFILE\.gradle\daemon"
Clear-DirContents "$env:LOCALAPPDATA\npm-cache"
Clear-DirContents "$env:LOCALAPPDATA\NuGet\v3-cache"
Clear-DirContents "$env:LOCALAPPDATA\D3DSCache"
Clear-DirContents "$env:LOCALAPPDATA\CrashDumps"
Clear-DirContents "$env:LOCALAPPDATA\Microsoft\Windows\INetCache"
Clear-DirContents "C:\Windows\SoftwareDistribution\Download"

foreach ($name in @('Cache', 'Code Cache', 'GPUCache')) {
  Clear-DirContents "$env:LOCALAPPDATA\Discord\$name"
  Clear-DirContents "$env:APPDATA\Cursor\$name"
}
Clear-DirContents "$env:APPDATA\Cursor\CachedData"

Clear-DirContents "$env:LOCALAPPDATA\BraveSoftware\Brave-Browser\User Data\Default\Cache"
Clear-DirContents "$env:LOCALAPPDATA\BraveSoftware\Brave-Browser\User Data\Default\Code Cache"
Clear-DirContents "$env:LOCALAPPDATA\Microsoft\Edge\User Data\Default\Cache"
Clear-DirContents "$env:LOCALAPPDATA\Microsoft\Edge\User Data\Default\Code Cache"

if (Get-Command npm -ErrorAction SilentlyContinue) {
  npm cache clean --force 2>$null
  Write-Host "npm cache clean --force"
}
if (Get-Command pnpm -ErrorAction SilentlyContinue) {
  pnpm store prune 2>$null
  Write-Host "pnpm store prune"
}

Clear-RecycleBin -Force
Write-Host "Recycle Bin emptied"

$after = (Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='C:'").FreeSpace
Write-Host ("AFTER_FREE_GB={0:N2}" -f ($after / 1GB))
Write-Host ("FREED_GB={0:N2}" -f (($after - $before) / 1GB))

Write-Host ""
Write-Host "Leftover heavy folders (review manually):"
@(
  "$env:LOCALAPPDATA\Programs",
  "$env:LOCALAPPDATA\pnpm",
  "$env:LOCALAPPDATA\BraveSoftware",
  "$env:LOCALAPPDATA\Packages",
  "$env:LOCALAPPDATA\Discord",
  "$env:USERPROFILE\Downloads"
) | ForEach-Object {
  if (Test-Path $_) {
    $sum = (Get-ChildItem $_ -Recurse -Force -File -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum
    Write-Host ("{0,8:N2} GB  {1}" -f ($sum / 1GB), $_)
  }
}
