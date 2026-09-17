# Sync BlueMap / R2 assets between local map persist and remote R2 bucket rootmc-bluemap.

param(
    [ValidateSet("push", "pull")][string]$Direction = "push",
    [string]$ConfigPath = "",
    [string]$Prefix = "",
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"
$EdgeRoot = $PSScriptRoot
if (-not $ConfigPath) { $ConfigPath = Join-Path $EdgeRoot "config.json" }
. (Join-Path $EdgeRoot "lib\Preference.ps1")
$cfg = Get-LocalEdgeConfig -ConfigPath $ConfigPath
$ws = $cfg.workspaceRoot
$mapDir = Join-Path $ws "Web Files\rootmc-minecraft-map"
$localAssets = Join-Path $cfg.persistDir "map-assets"
if (-not (Test-Path $localAssets)) { New-Item -ItemType Directory -Path $localAssets -Force | Out-Null }

Push-Location $mapDir
try {
    if ($Direction -eq "pull") {
        Write-Host "PULL R2 rootmc-bluemap -> $localAssets (manual: use wrangler r2 object get / dashboard sync)"
        Write-Host "Example: npx wrangler r2 object get rootmc-bluemap/<key> --file <path>"
        if ($WhatIf) { return }
        # List is expensive; document + optional single prefix sync via rclone if configured.
        $rclone = Get-Command rclone -ErrorAction SilentlyContinue
        if ($rclone -and $env:ROOTMC_R2_RCLONE_REMOTE) {
            $dest = $localAssets
            $src = "$($env:ROOTMC_R2_RCLONE_REMOTE):rootmc-bluemap/$Prefix"
            & $rclone.Source sync $src $dest
        } else {
            Write-Warning "Set ROOTMC_R2_RCLONE_REMOTE or copy objects manually. Local cache dir ready: $localAssets"
        }
    } else {
        Write-Host "PUSH $localAssets -> R2 rootmc-bluemap"
        $rclone = Get-Command rclone -ErrorAction SilentlyContinue
        if ($rclone -and $env:ROOTMC_R2_RCLONE_REMOTE) {
            if ($WhatIf) { Write-Host "WhatIf rclone sync"; return }
            & $rclone.Source sync $localAssets "$($env:ROOTMC_R2_RCLONE_REMOTE):rootmc-bluemap/$Prefix"
        } else {
            Write-Warning "Install rclone + ROOTMC_R2_RCLONE_REMOTE for bulk map sync. Gateway still caches map tiles on disk under $($cfg.cacheDir)."
        }
    }
} finally {
    Pop-Location
}

@{
    at = (Get-Date).ToUniversalTime().ToString("o")
    direction = $Direction
    local = $localAssets
} | ConvertTo-Json | Set-Content (Join-Path $cfg.stateDir "last-map-sync.json") -Encoding UTF8
