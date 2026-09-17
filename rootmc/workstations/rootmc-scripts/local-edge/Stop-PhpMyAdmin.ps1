# Stop phpMyAdmin PHP built-in server.

$ErrorActionPreference = "Continue"
$InstallRoot = Join-Path $env:LOCALAPPDATA "RootMC\phpmyadmin"
$pidFile = Join-Path $InstallRoot "phpmyadmin.pid"
if (-not (Test-Path $pidFile)) {
    Write-Host "No phpMyAdmin pid file."
    exit 0
}
$id = [int](Get-Content $pidFile -Raw)
if ($id -gt 0) {
    try {
        Stop-Process -Id $id -Force -ErrorAction Stop
        Write-Host "Stopped phpMyAdmin pid=$id"
    } catch {
        Write-Host "Already stopped."
    }
}
Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
