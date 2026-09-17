# Move RootMC local Paper test server to Desktop (SSD) and leave a junction
# at the old workspace path so existing scripts keep working.
#
# Usage:
#   powershell -File scripts\Move-TestServerToDesktop.ps1

param(
    [string]$WorkspaceRoot = "D:\.1 Work Stations\RootMC",
    [string]$DesktopName = "RootMC test server"
)

$ErrorActionPreference = "Stop"

$src = Join-Path $WorkspaceRoot "test server"
$dst = Join-Path ([Environment]::GetFolderPath("Desktop")) $DesktopName

if (-not (Test-Path -LiteralPath $src)) {
    if (Test-Path -LiteralPath $dst) {
        Write-Host "Already on Desktop: $dst"
        # Ensure junction exists at workspace path
        if (-not (Test-Path -LiteralPath $src)) {
            cmd /c mklink /J "$src" "$dst" | Out-Host
        }
        exit 0
    }
    throw "Source missing: $src"
}

# If src is already a junction to dst, done
$item = Get-Item -LiteralPath $src -Force
if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
    Write-Host "Workspace path is already a reparse point: $src"
    Write-Host "Target desktop folder: $dst"
    if (-not (Test-Path -LiteralPath $dst)) {
        throw "Junction exists but desktop target missing: $dst"
    }
    exit 0
}

if (Test-Path -LiteralPath $dst) {
    throw "Desktop target already exists (move manually or delete): $dst"
}

Write-Host "Copying -> $dst"
New-Item -ItemType Directory -Path $dst -Force | Out-Null
robocopy $src $dst /E /COPY:DAT /R:2 /W:2 /NFL /NDL /NJH /NJS /nc /ns /np | Out-Null
$rc = $LASTEXITCODE
if ($rc -ge 8) { throw "robocopy failed exit=$rc" }

$backup = Join-Path $WorkspaceRoot ("test server.bak-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
Write-Host "Renaming old folder -> $backup"
Rename-Item -LiteralPath $src -NewName (Split-Path $backup -Leaf)

Write-Host "Creating junction: $src => $dst"
cmd /c mklink /J "$src" "$dst" | Out-Host

# Marker file on Desktop for operators
@(
    "RootMC local Paper test server (moved to Desktop for SSD I/O)."
    "Workspace junction: $src"
    "Start: start.bat"
    "Boot once: powershell -File .\boot-once.ps1"
    "Sync jars: powershell -File .\sync-plugins.ps1"
) | Set-Content -LiteralPath (Join-Path $dst "DESKTOP-LOCATION.txt") -Encoding UTF8

Write-Host "OK. Desktop: $dst"
Write-Host "Junction: $src"
Write-Host "Backup (safe to delete after verify): $backup"
