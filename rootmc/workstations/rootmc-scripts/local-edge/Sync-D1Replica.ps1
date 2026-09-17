# Push local D1 → remote (warm backup) or pull remote → local (catch-up after CF-primary window).

param(
    [ValidateSet("push", "pull")][string]$Direction = "push",
    [ValidateSet("api", "api2", "webstat", "all")][string]$Database = "all",
    [string]$ConfigPath = "",
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"
$EdgeRoot = $PSScriptRoot
if (-not $ConfigPath) { $ConfigPath = Join-Path $EdgeRoot "config.json" }
. (Join-Path $EdgeRoot "lib\Preference.ps1")
$cfg = Get-LocalEdgeConfig -ConfigPath $ConfigPath
$ws = $cfg.workspaceRoot
$dumpDir = Join-Path $cfg.persistDir "sync"
if (-not (Test-Path $dumpDir)) { New-Item -ItemType Directory -Path $dumpDir -Force | Out-Null }

# Prefer workspace API token over wrangler OAuth (wrong account).
$envPath = Join-Path $ws ".env"
if (Test-Path $envPath) {
    Get-Content $envPath | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $i = $line.IndexOf("=")
        if ($i -lt 1) { return }
        $k = $line.Substring(0, $i).Trim()
        $v = $line.Substring($i + 1).Trim()
        if ($k -and $v -and ($k -eq "CLOUDFLARE_API_TOKEN" -or $k -eq "CLOUDFLARE_ACCOUNT_ID" -or $k -like "CLOUDFLARE_*")) {
            Set-Item -Path "Env:$k" -Value $v
        }
    }
}
Remove-Item Env:CLOUDFLARE_API_KEY -ErrorAction SilentlyContinue

$careTables = @(
    "rootmc_treasury_ledger",
    "rootmc_gold_transfers",
    "rootmc_pending_gold_transfers"
)

# wrangler 4.102+ removed --persist-to from `d1 export` (still on `d1 execute` / `dev`).
# Point the project's default .wrangler/state at our edge persist dir so --local export works.
function Ensure-LocalWranglerState {
    param([string]$WorkDir, [string]$PersistDir)
    if (-not (Test-Path -LiteralPath $PersistDir)) {
        New-Item -ItemType Directory -Path $PersistDir -Force | Out-Null
    }
    $wranglerDir = Join-Path $WorkDir ".wrangler"
    $statePath = Join-Path $wranglerDir "state"
    New-Item -ItemType Directory -Path $wranglerDir -Force | Out-Null
    if (Test-Path -LiteralPath $statePath) {
        $item = Get-Item -LiteralPath $statePath -Force
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            return
        }
        # Real folder — leave alone if it already has v3 data; otherwise replace with junction.
        $hasV3 = Test-Path -LiteralPath (Join-Path $statePath "v3")
        if ($hasV3) { return }
        Remove-Item -LiteralPath $statePath -Recurse -Force
    }
    cmd /c mklink /J "$statePath" "$PersistDir" | Out-Null
}

function Sync-One {
    param(
        [string]$WorkDir,
        [string]$DbName,
        [string]$PersistSub,
        [string]$Label
    )
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $file = Join-Path $dumpDir "$Label-$Direction-$stamp.sql"
    $persistDir = Join-Path $cfg.persistDir $PersistSub
    Push-Location $WorkDir
    try {
        if ($Direction -eq "push") {
            Write-Host "PUSH local $DbName -> remote ($file)"
            if ($WhatIf) { return }
            Ensure-LocalWranglerState -WorkDir $WorkDir -PersistDir $persistDir
            # No --persist-to on export (wrangler 4.102+); uses .wrangler/state junction above.
            npx wrangler d1 export $DbName --local --output $file
            if (-not (Test-Path -LiteralPath $file) -or (Get-Item -LiteralPath $file).Length -lt 20) {
                throw "d1 export produced no/empty file: $file"
            }
            Write-Warning "CARE: treasury/ledger tables ($($careTables -join ', ')) - review before remote import on production."
            npx wrangler d1 execute $DbName --remote --file $file
        } else {
            Write-Host "PULL remote $DbName -> local ($file)"
            if ($WhatIf) { return }
            npx wrangler d1 export $DbName --remote --output $file
            if (-not (Test-Path -LiteralPath $file) -or (Get-Item -LiteralPath $file).Length -lt 20) {
                throw "d1 export produced no/empty file: $file"
            }
            npx wrangler d1 execute $DbName --local --persist-to $persistDir --file $file
        }
        $state = @{
            at        = (Get-Date).ToUniversalTime().ToString("o")
            direction = $Direction
            database  = $DbName
            file      = $file
        }
        $state | ConvertTo-Json | Set-Content (Join-Path $cfg.stateDir "last-d1-sync-$Label.json") -Encoding UTF8
    } finally {
        Pop-Location
    }
}

if ($Database -eq "api" -or $Database -eq "all") {
    Sync-One -WorkDir (Join-Path $ws "Web Files\rootmc-api") -DbName "rootmc" -PersistSub "api" -Label "rootmc"
}
if ($Database -eq "webstat" -or $Database -eq "all") {
    Sync-One -WorkDir (Join-Path $ws "Web Files\rootmc-api") -DbName "rootmc-webstat" -PersistSub "api" -Label "webstat"
}
if ($Database -eq "api2" -or $Database -eq "all") {
    # Gen 2 / rootmc-g2 retired.
    if ($Database -eq "api2") {
        Write-Host "Skip: api2/rootmc-g2 retired."
    }
}

Write-Host "D1 sync $Direction done."
