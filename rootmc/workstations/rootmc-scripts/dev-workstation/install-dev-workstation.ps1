# Install / refresh RootMC workstation helpers under AppData.
# Canonical logon runner is Root-Core-Node.exe â€” see scripts\root-core-node\install-root-core-node.ps1.
# This script only syncs presence scripts + secrets used by the Node.

$ErrorActionPreference = "Stop"
$SourceDir = $PSScriptRoot
$TargetDir = Join-Path $env:LOCALAPPDATA "RootMC\dev-workstation"

New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null

foreach ($name in @(
    "dev-workstation-startup.ps1",
    "config.json",
    "host-metrics.ps1",
    "dev-edge-console.ps1"
)) {
    $src = Join-Path $SourceDir $name
    if (-not (Test-Path -LiteralPath $src)) {
        if ($name -eq "dev-edge-console.ps1") { continue }
        throw "Missing source: $src"
    }
    Copy-Item -LiteralPath $src -Destination (Join-Path $TargetDir $name) -Force
    if ($name -like "*.ps1") {
        $utf8Bom = New-Object System.Text.UTF8Encoding $true
        $text = [System.IO.File]::ReadAllText($src)
        [System.IO.File]::WriteAllText((Join-Path $TargetDir $name), $text, $utf8Bom)
    }
}

$envKeys = @("DISCORD_ROOTMC_BOT_TOKEN", "ROOTMC_DEV_WORKSTATION_KEY", "ROOTMC_EDGE_SIGNING_KEY", "ROOTMC_TUNNEL_ID", "ROOTMC_TUNNEL_HEALTH_URL", "CLOUDFLARE_API_TOKEN", "CLOUDFLARE_ZONE_ID")
$targetEnv = Join-Path $TargetDir ".env"
$found = @{}

function Read-EnvKeys([string]$Path) {
    if (-not (Test-Path $Path)) { return }
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $p = $line.IndexOf("=")
        if ($p -gt 0) {
            $k = $line.Substring(0, $p).Trim()
            $v = $line.Substring($p + 1).Trim()
            if ($envKeys -contains $k -and $v) { $script:found[$k] = $v }
        }
    }
}

Read-EnvKeys $targetEnv
foreach ($drive in @("D")) {
    $driveRoot = "${drive}:\"
    if (-not (Test-Path -LiteralPath $driveRoot)) { continue }
    $candidate = Join-Path $driveRoot "RootMC Workspace\.env"
    if (Test-Path -LiteralPath $candidate) {
        Read-EnvKeys $candidate
        break
    }
}
$parentEnv = Join-Path (Split-Path $SourceDir -Parent) "..\.env"
$parentEnv = [System.IO.Path]::GetFullPath($parentEnv)
Read-EnvKeys $parentEnv

if ($found.Count -eq 0) {
    Write-Warning "No Discord/API keys found - add DISCORD_ROOTMC_BOT_TOKEN and ROOTMC_DEV_WORKSTATION_KEY to $targetEnv"
} else {
    if (-not $found.ContainsKey("ROOTMC_DEV_WORKSTATION_KEY")) {
        $bytes = New-Object byte[] 32
        [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
        $found["ROOTMC_DEV_WORKSTATION_KEY"] = [Convert]::ToBase64String($bytes).TrimEnd("=").Replace("+", "x").Replace("/", "y")
        Write-Host "Generated ROOTMC_DEV_WORKSTATION_KEY (add same value to D:\.1 Work Stations\RootMC\.env and wrangler secret put on rootmc-api)."
    }
    $lines = @("# RootMC dev workstation / local-edge local secrets")
    foreach ($key in $envKeys) {
        if ($found.ContainsKey($key)) { $lines += "$key=$($found[$key])" }
    }
    Set-Content -LiteralPath $targetEnv -Value $lines -Encoding UTF8
    Write-Host ("Wrote {0} ({1} keys)." -f $targetEnv, $found.Count)
}

# Remove legacy PowerShell edge-console logon task â€” Root-Core-Node owns autostart now.
Unregister-ScheduledTask -TaskName "RootMC Dev Workstation" -Confirm:$false -ErrorAction SilentlyContinue
$startupDir = [Environment]::GetFolderPath("Startup")
$legacyLnk = Join-Path $startupDir "RootMC Dev Workstation.lnk"
if (Test-Path -LiteralPath $legacyLnk) {
    try { Remove-Item -LiteralPath $legacyLnk -Force } catch { }
}

$nodeInstall = Join-Path (Split-Path $SourceDir -Parent) "root-core-node\install-root-core-node.ps1"
Write-Host ""
Write-Host "Synced presence helpers: $TargetDir"
Write-Host "Logon runner: install Root-Core-Node via:"
Write-Host "  powershell -File `"$nodeInstall`" -StartNow"
if (Test-Path $nodeInstall) {
    & $nodeInstall -SkipPublish
}
