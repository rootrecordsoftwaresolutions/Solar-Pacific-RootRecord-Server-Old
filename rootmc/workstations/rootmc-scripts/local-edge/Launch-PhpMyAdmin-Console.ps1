# Console-attached phpMyAdmin for local RootMcMySQL (:3307).
# Close this window (or Ctrl+C) to stop the PHP server.

param([int]$HttpPort = 8080)

$ErrorActionPreference = "Stop"
$EdgeRoot = $PSScriptRoot
$InstallRoot = Join-Path $env:LOCALAPPDATA "RootMC\phpmyadmin"
$metaPath = Join-Path $InstallRoot "install.json"
$pidFile = Join-Path $InstallRoot "phpmyadmin.pid"

function Stop-ExistingPhpMyAdmin {
    if (Test-Path $pidFile) {
        $old = 0
        try { $old = [int]((Get-Content $pidFile -Raw).Trim()) } catch { $old = 0 }
        if ($old -gt 0) {
            Stop-Process -Id $old -Force -ErrorAction SilentlyContinue
        }
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    }
    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object {
            $_.Name -eq "php.exe" -and
            $_.CommandLine -match "-S\s+127\.0\.0\.1:$HttpPort"
        } |
        ForEach-Object {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        }
}

if (-not (Test-Path $metaPath)) {
    Write-Host "Not installed - running Install-PhpMyAdmin.ps1..."
    & (Join-Path $EdgeRoot "Install-PhpMyAdmin.ps1") -HttpPort $HttpPort
}

$meta = Get-Content $metaPath -Raw | ConvertFrom-Json
$phpExe = [string]$meta.phpExe
$www = [string]$meta.www
if (-not (Test-Path $phpExe)) { throw "php.exe missing - re-run Install-PhpMyAdmin.ps1" }
if (-not (Test-Path (Join-Path $www "index.php"))) { throw "phpMyAdmin www missing - re-run Install-PhpMyAdmin.ps1" }

$svc = Get-Service -Name "RootMcMySQL" -ErrorAction SilentlyContinue
if ($svc -and $svc.Status -ne "Running") {
    Write-Host "Starting RootMcMySQL..."
    Start-Service RootMcMySQL
    Start-Sleep -Seconds 2
}

Stop-ExistingPhpMyAdmin

$url = "http://127.0.0.1:$HttpPort/"
$host.ui.RawUI.WindowTitle = "RootMC phpMyAdmin - close to stop"
Write-Host "phpMyAdmin: $url"
Write-Host "MySQL:      127.0.0.1:3307 (RootMcMySQL)"
Write-Host "Close this window or press Ctrl+C to stop the server."
Write-Host ""

Start-Process $url

$phpProc = $null
try {
    $phpProc = Start-Process -FilePath $phpExe -ArgumentList @(
        "-S", "127.0.0.1:$HttpPort", "-t", $www
    ) -WorkingDirectory $www -NoNewWindow -PassThru
    Set-Content -LiteralPath $pidFile -Value $phpProc.Id -Encoding ASCII

    Wait-Process -Id $phpProc.Id
} finally {
    if ($phpProc -and -not $phpProc.HasExited) {
        Stop-Process -Id $phpProc.Id -Force -ErrorAction SilentlyContinue
    }
    Stop-ExistingPhpMyAdmin
    Write-Host "phpMyAdmin stopped."
}
