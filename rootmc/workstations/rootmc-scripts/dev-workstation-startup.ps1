# Installed copy lives in %LOCALAPPDATA%\RootMC\dev-workstation\ (see install-dev-workstation.ps1).
# This file is the repo source — run install to deploy locally + Startup shortcut.
#
# Usage:
#   powershell -File scripts\dev-workstation\install-dev-workstation.ps1
#   scripts\install-dev-workstation.bat

$local = Join-Path $env:LOCALAPPDATA "RootMC\dev-workstation\dev-workstation-startup.ps1"
if (Test-Path $local) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $local @args
} else {
    Write-Error "Not installed locally. Run: powershell -File `"$PSScriptRoot\dev-workstation\install-dev-workstation.ps1`""
    exit 1
}
