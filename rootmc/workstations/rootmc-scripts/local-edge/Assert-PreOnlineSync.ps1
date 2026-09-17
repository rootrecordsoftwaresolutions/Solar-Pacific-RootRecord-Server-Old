# Gate public cutover: sync D1 + verify local edge + Discord link bot + peer Official configs.
# Exit 0 only when ready for tunnel-primary public use. Use -Apply to pull D1 + start missing pieces.

param(
    [string]$ConfigPath = "",
    [switch]$Apply,
    [switch]$SkipD1Pull,
    [switch]$AllowStaleD1,
    [int]$MaxD1AgeHours = 24
)

$ErrorActionPreference = "Stop"
$EdgeRoot = $PSScriptRoot
if (-not $ConfigPath) { $ConfigPath = Join-Path $EdgeRoot "config.json" }
. (Join-Path $EdgeRoot "lib\Preference.ps1")
$cfg = Get-LocalEdgeConfig -ConfigPath $ConfigPath
$ws = [string]$cfg.workspaceRoot
$stateDir = [string]$cfg.stateDir
$fail = @()
$ok = @()

function Test-HttpOk([string]$Url, [int]$TimeoutSec = 5) {
    try {
        $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSec
        return ($r.StatusCode -ge 200 -and $r.StatusCode -lt 300)
    } catch { return $false }
}

function Import-WorkspaceEnv {
    $envPath = Join-Path $ws ".env"
    if (-not (Test-Path $envPath)) { return }
    Get-Content $envPath | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $i = $line.IndexOf("=")
        if ($i -lt 1) { return }
        $k = $line.Substring(0, $i).Trim()
        $v = $line.Substring($i + 1).Trim()
        if ($k -and $v -and -not [Environment]::GetEnvironmentVariable($k)) {
            Set-Item -Path "Env:$k" -Value $v
        }
    }
}

Import-WorkspaceEnv

# --- Local edge health ---
$apiOk = Test-HttpOk "http://127.0.0.1:$($cfg.ports.api)/health"
$gwOk = Test-HttpOk "http://127.0.0.1:$($cfg.ports.gateway)/__edge/health"
$siteOk = Test-HttpOk "http://127.0.0.1:$($cfg.ports.site)/"
if ($Apply -and (-not $apiOk -or -not $gwOk)) {
    Write-Host "Starting local edge..."
    & (Join-Path $EdgeRoot "Start-LocalEdge.ps1") -ConfigPath $ConfigPath -SkipTunnel
    Start-Sleep -Seconds 8
    $apiOk = Test-HttpOk "http://127.0.0.1:$($cfg.ports.api)/health"
    $gwOk = Test-HttpOk "http://127.0.0.1:$($cfg.ports.gateway)/__edge/health"
    $siteOk = Test-HttpOk "http://127.0.0.1:$($cfg.ports.site)/"
}
if ($apiOk) { $ok += "local API :$($cfg.ports.api) healthy" } else { $fail += "local API not healthy" }
if ($gwOk) { $ok += "gateway healthy" } else { $fail += "gateway not healthy" }
if ($siteOk) { $ok += "site healthy" } else { $fail += "site not healthy" }

# --- Discord link bot (Gateway - no CF Workers) ---
$botStatus = Join-Path $stateDir "discord-link-bot.json"
$botConnected = $false
if (Test-Path $botStatus) {
    try {
        $bj = Get-Content $botStatus -Raw | ConvertFrom-Json
        $botConnected = [bool]$bj.connected
    } catch { }
}
if ($Apply -and -not $botConnected) {
    Write-Host "Starting discord-link-bot..."
    $node = (Get-Command node).Source
    $p = Start-Process -FilePath $node -WorkingDirectory $EdgeRoot -ArgumentList @("discord-link-bot.mjs") `
        -RedirectStandardOutput (Join-Path $EdgeRoot "logs\discord-link-bot.out.log") `
        -RedirectStandardError (Join-Path $EdgeRoot "logs\discord-link-bot.err.log") `
        -PassThru -WindowStyle Hidden
    $pidFile = Join-Path $stateDir "pids.json"
    $list = @()
    if (Test-Path $pidFile) {
        try { $list = @(Get-Content $pidFile -Raw | ConvertFrom-Json) } catch { $list = @() }
    }
    $list += @{ name = "discord-link-bot"; pid = $p.Id; log = (Join-Path $EdgeRoot "logs\discord-link-bot.out.log") }
    ($list | ConvertTo-Json -Depth 4) | Set-Content $pidFile -Encoding UTF8
    Start-Sleep -Seconds 4
    if (Test-Path $botStatus) {
        try {
            $bj = Get-Content $botStatus -Raw | ConvertFrom-Json
            $botConnected = [bool]$bj.connected
        } catch { }
    }
}
if ($botConnected) { $ok += "discord-link-bot Gateway connected" } else { $fail += "discord-link-bot not connected (DM link path)" }

# --- Peer Official configs ---
foreach ($pair in @(
    @{ host = "Claims"; path = (Join-Path $ws "1. RootMC - Claims\plugins\RootMC\rootmc-official.yml") },
    @{ host = "Towny"; path = (Join-Path $ws "2. RootMC - Towny\plugins\RootMC\rootmc-official.yml") }
)) {
    if (-not (Test-Path $pair.path)) { $fail += "$($pair.host) rootmc-official.yml missing"; continue }
    $raw = Get-Content $pair.path -Raw
    if ($raw -notmatch '(?m)^enabled:\s*true') { $fail += "$($pair.host) Official enabled:false" }
    elseif ($raw -notmatch '(?m)^  host:\s*".+"') { $fail += "$($pair.host) Official peer host empty" }
    elseif ($raw -notmatch '(?m)^    playtime:\s*true') { $fail += "$($pair.host) Official playtime sync off" }
    elseif ($raw -notmatch '(?m)^    votes:\s*true') { $fail += "$($pair.host) Official votes sync off" }
    else { $ok += "$($pair.host) Official peer playtime/votes on" }
}

# --- D1 freshness ---
$syncMarkers = @(
    (Join-Path $stateDir "last-d1-sync-rootmc.json"),
    (Join-Path $stateDir "last-d1-bootstrap.json")
)
$latest = $null
foreach ($f in $syncMarkers) {
    if (Test-Path $f) {
        $t = (Get-Item $f).LastWriteTimeUtc
        if (-not $latest -or $t -gt $latest) { $latest = $t }
    }
}
# Also treat persist dir mtime as weak signal
$persistApi = Join-Path $cfg.persistDir "api"
if (Test-Path $persistApi) {
    $t = (Get-Item $persistApi).LastWriteTimeUtc
    if (-not $latest -or $t -gt $latest) { $latest = $t }
}

if ($Apply -and -not $SkipD1Pull -and (-not $latest -or ((Get-Date).ToUniversalTime() - $latest).TotalHours -gt $MaxD1AgeHours)) {
    Write-Host "Pulling remote D1 → local (catch-up before public)..."
    try {
        & (Join-Path $EdgeRoot "Sync-D1Replica.ps1") -Direction pull -Database all -ConfigPath $ConfigPath
        @{ at = (Get-Date).ToUniversalTime().ToString("o"); action = "preonline-pull" } |
            ConvertTo-Json | Set-Content (Join-Path $stateDir "last-d1-bootstrap.json") -Encoding UTF8
        $latest = (Get-Date).ToUniversalTime()
        $ok += "D1 pull completed"
    } catch {
        $fail += "D1 pull failed: $($_.Exception.Message)"
    }
}

if ($latest) {
    $ageH = ((Get-Date).ToUniversalTime() - $latest).TotalHours
    if ($ageH -le $MaxD1AgeHours -or $AllowStaleD1) {
        $ok += ("D1 sync marker age {0:N1}h" -f $ageH)
    } else {
        $fail += ("D1 sync stale ({0:N1}h > {1}h). Run Sync-D1Replica -Direction pull or -AllowStaleD1" -f $ageH, $MaxD1AgeHours)
    }
} elseif ($AllowStaleD1) {
    $ok += "D1 age unknown (-AllowStaleD1)"
} else {
    $fail += "No D1 sync marker - run Bootstrap-LocalD1.ps1 or Sync-D1Replica -Direction pull"
}

# --- Tunnel config ---
$tunnelCfg = [string]$cfg.tunnelConfigPath
if ((Test-Path $tunnelCfg) -and ((Get-Content $tunnelCfg -Raw) -notmatch "REPLACE_WITH_TUNNEL_ID")) {
    $ok += "tunnel config has real id"
} else {
    $fail += "tunnel config still REPLACE_WITH_TUNNEL_ID - see cloudflared/README.md"
}

# --- Result ---
Write-Host ""
Write-Host "=== Pre-online sync gate ==="
foreach ($x in $ok) { Write-Host "  OK  $x" }
foreach ($x in $fail) { Write-Host "  FAIL $x" }

$ready = ($fail.Count -eq 0)
$report = @{
    at    = (Get-Date).ToUniversalTime().ToString("o")
    ready = $ready
    ok    = $ok
    fail  = $fail
}
$report | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $stateDir "preonline-ready.json") -Encoding UTF8

if (-not $ready) {
    Write-Host ""
    Write-Host "NOT READY for public cutover. Fix FAILs, then re-run."
    Write-Host "When ready: Invoke-EdgeCutover.ps1 -Preference local [-AllowProduction]"
    exit 1
}

Write-Host ""
Write-Host "READY - data synced enough for tunnel-primary public cutover."
Write-Host "Next: start tunnel (Start-LocalEdge without -SkipTunnel), then Invoke-EdgeCutover.ps1"
exit 0
