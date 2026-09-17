# Stop processes recorded by Start-LocalEdge.ps1

param([string]$ConfigPath = "")

$ErrorActionPreference = "Continue"
$EdgeRoot = $PSScriptRoot
if (-not $ConfigPath) { $ConfigPath = Join-Path $EdgeRoot "config.json" }
. (Join-Path $EdgeRoot "lib\Preference.ps1")
$cfg = Get-LocalEdgeConfig -ConfigPath $ConfigPath
$pidFile = Join-Path $cfg.stateDir "pids.json"

if (-not (Test-Path -LiteralPath $pidFile)) {
    Write-Host "No pid file at $pidFile"
    exit 0
}

$procs = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
foreach ($p in @($procs)) {
    $id = [int]$p.pid
    if ($id -le 0) { continue }
    try {
        Stop-Process -Id $id -Force -ErrorAction Stop
        Write-Host "Stopped $($p.name) pid=$id"
    } catch {
        Write-Host "Already gone $($p.name) pid=$id"
    }
}

Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
Write-Host "Local edge stopped."
