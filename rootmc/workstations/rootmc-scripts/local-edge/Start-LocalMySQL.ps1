# Start RootMC local MySQL if not already listening.

$ErrorActionPreference = "Continue"
$port = 3307
$startPs1 = "D:\.mysql-config\Start-RootMcMySQL.ps1"
if (Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue) {
    Write-Host "MySQL already listening on $port"
    exit 0
}
if (Test-Path $startPs1) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $startPs1
    Write-Host "Started via $startPs1"
    exit 0
}
$install = Join-Path $PSScriptRoot "Install-LocalMySQL.ps1"
if (Test-Path $install) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $install
    exit $LASTEXITCODE
}
Write-Warning "No MySQL start script found."
exit 1
