# Bootstrap local D1 persist from remote Cloudflare D1 (one-time / refresh).
# Uses wrangler d1 export → import into --persist-to directory via local execute.

param(
    [ValidateSet("api", "api2", "webstat", "all")][string]$Database = "all",
    [string]$ConfigPath = ""
)

$ErrorActionPreference = "Stop"
$EdgeRoot = $PSScriptRoot
if (-not $ConfigPath) { $ConfigPath = Join-Path $EdgeRoot "config.json" }
. (Join-Path $EdgeRoot "lib\Preference.ps1")
$cfg = Get-LocalEdgeConfig -ConfigPath $ConfigPath
$ws = $cfg.workspaceRoot
$dumpDir = Join-Path $cfg.persistDir "dumps"
if (-not (Test-Path $dumpDir)) { New-Item -ItemType Directory -Path $dumpDir -Force | Out-Null }

function Export-RemoteD1 {
    param([string]$WorkDir, [string]$BindingName, [string]$OutFile)
    Push-Location $WorkDir
    try {
        Write-Host "Exporting remote D1 binding=$BindingName -> $OutFile"
        npx wrangler d1 export $BindingName --remote --output $OutFile
    } finally {
        Pop-Location
    }
}

if ($Database -eq "api" -or $Database -eq "all") {
    $out = Join-Path $dumpDir "rootmc-remote.sql"
    Export-RemoteD1 -WorkDir (Join-Path $ws "Web Files\rootmc-api") -BindingName "rootmc" -OutFile $out
    Write-Host "Imported later via: npx wrangler d1 execute rootmc --local --persist-to `"$($cfg.persistDir)\api`" --file `"$out`""
    Push-Location (Join-Path $ws "Web Files\rootmc-api")
    try {
        npx wrangler d1 execute rootmc --local --persist-to (Join-Path $cfg.persistDir "api") --file $out
    } finally { Pop-Location }
}

if ($Database -eq "webstat" -or $Database -eq "all") {
    $out = Join-Path $dumpDir "rootmc-webstat-remote.sql"
    Export-RemoteD1 -WorkDir (Join-Path $ws "Web Files\rootmc-api") -BindingName "rootmc-webstat" -OutFile $out
    Push-Location (Join-Path $ws "Web Files\rootmc-api")
    try {
        npx wrangler d1 execute rootmc-webstat --local --persist-to (Join-Path $cfg.persistDir "api") --file $out
    } finally { Pop-Location }
}

if ($Database -eq "api2" -or $Database -eq "all") {
    # Gen 2 / rootmc-g2 retired (Claims uses main rootmc D1).
    if ($Database -eq "api2") {
        Write-Host "Skip: api2/rootmc-g2 retired."
    }
}

Write-Host "Bootstrap complete under $($cfg.persistDir)"
