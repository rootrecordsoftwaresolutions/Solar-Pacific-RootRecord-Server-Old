# Start PHP built-in server for phpMyAdmin on 127.0.0.1 only.

param([int]$HttpPort = 8080)

$ErrorActionPreference = "Stop"
$InstallRoot = Join-Path $env:LOCALAPPDATA "RootMC\phpmyadmin"
$metaPath = Join-Path $InstallRoot "install.json"
$pidFile = Join-Path $InstallRoot "phpmyadmin.pid"

if (-not (Test-Path $metaPath)) {
    Write-Host "Not installed - running Install-PhpMyAdmin.ps1..."
    & (Join-Path $PSScriptRoot "Install-PhpMyAdmin.ps1") -HttpPort $HttpPort
}

$meta = Get-Content $metaPath -Raw | ConvertFrom-Json
$phpExe = [string]$meta.phpExe
$www = [string]$meta.www
if (-not (Test-Path $phpExe)) { throw "php.exe missing - re-run Install-PhpMyAdmin.ps1" }

if (Test-Path $pidFile) {
    $old = 0
    try { $old = [int]((Get-Content $pidFile -Raw).Trim()) } catch { $old = 0 }
    if ($old -gt 0) {
        $alive = Get-Process -Id $old -ErrorAction SilentlyContinue
        if ($alive) {
            Write-Host "phpMyAdmin already running pid=$old - http://127.0.0.1:$HttpPort"
            return
        }
    }
}

$proc = Start-Process -FilePath $phpExe -ArgumentList @(
    "-S", "127.0.0.1:$HttpPort", "-t", $www
) -WorkingDirectory $www -WindowStyle Hidden -PassThru
Set-Content -LiteralPath $pidFile -Value $proc.Id -Encoding ASCII
Write-Host "phpMyAdmin listening http://127.0.0.1:$HttpPort (pid $($proc.Id))"
