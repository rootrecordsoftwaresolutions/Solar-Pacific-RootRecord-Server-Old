# Preference health loop — writes current_connection_preference.json and mirrors to CF when possible.
# Local preference requires healthy edge + fresh D1 sync markers. Stale/failed sync → Cloudflare.
# Discord "Dev connection" posts only on preference flips (local after sync, or CF fallback).
# Cutover on flip is OPT-IN via -EnableAutoCutover (avoids surprise DNS changes at logon).

param(
    [string]$ConfigPath = "",
    [switch]$Once,
    [switch]$EnableAutoCutover
)

$ErrorActionPreference = "Continue"
$EdgeRoot = $PSScriptRoot
if (-not $ConfigPath) { $ConfigPath = Join-Path $EdgeRoot "config.json" }
. (Join-Path $EdgeRoot "lib\Preference.ps1")

function Test-UrlOk([string]$Url, [int]$TimeoutSec = 3) {
    try {
        $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSec
        return ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500)
    } catch {
        return $false
    }
}

function Import-EdgeEnv {
    $wsEnv = Join-Path ((Get-LocalEdgeConfig -ConfigPath $ConfigPath).workspaceRoot) ".env"
    if (-not (Test-Path -LiteralPath $wsEnv)) { return }
    Get-Content -LiteralPath $wsEnv | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $p = $line.IndexOf("=")
        if ($p -gt 0) {
            $k = $line.Substring(0, $p).Trim()
            $v = $line.Substring($p + 1).Trim()
            if ($k -and $v -and -not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($k))) { return }
            if ($k -and $v) { Set-Item -Path "Env:$k" -Value $v }
        }
    }
}

function Invoke-D1SyncCycle {
    param($Config, [string]$Direction)
    $syncScript = Join-Path $EdgeRoot "Sync-D1Replica.ps1"
    if (-not (Test-Path -LiteralPath $syncScript)) { return $false }
    try {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $syncScript `
            -Direction $Direction -Database all -ConfigPath $ConfigPath
        if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) { return $false }
        @{
            at        = (Get-Date).ToUniversalTime().ToString("o")
            direction = $Direction
            ok        = $true
        } | ConvertTo-Json | Set-Content (Join-Path $Config.stateDir "last-sync-heartbeat.json") -Encoding UTF8
        return $true
    } catch {
        Write-Warning "D1 sync ($Direction) failed: $($_.Exception.Message)"
        @{
            at        = (Get-Date).ToUniversalTime().ToString("o")
            direction = $Direction
            ok        = $false
            error     = $_.Exception.Message
        } | ConvertTo-Json | Set-Content (Join-Path $Config.stateDir "last-sync-failure.json") -Encoding UTF8
        return $false
    }
}

function Publish-ConnectionDiscord {
    param($Config, $Pref, [string]$Previous)
    if ($Previous -eq [string]$Pref.preference) { return }
    $channel = [string]$Config.discordConnectionChannelId
    if (-not $channel) { $channel = "1529414449088172064" }
    $stamp = (Get-Date).ToString("yyyy-MM-dd HH:mm") + " HST"
    try {
        $hst = [System.TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, "Hawaiian Standard Time")
        $stamp = $hst.ToString("yyyy-MM-dd HH:mm") + " HST"
    } catch { }

    if ($Pref.preference -eq "local") {
        $msg = @(
            "**Dev connection: RootMC Network · online**",
            "Databases synced; RootMC Network preferred.",
            "Reason: $($Pref.reason) · sync: $($Pref.sync_status)",
            $stamp
        ) -join "`n"
    } else {
        $msg = @(
            "**Dev connection: Cloudflare fallback**",
            "RootMC Network not preferred - traffic stays on Cloudflare.",
            "Reason: $($Pref.reason) · sync: $($Pref.sync_status)",
            $stamp
        ) -join "`n"
    }
    [void](Send-DevConnectionDiscord -Message $msg -ChannelId $channel)
}

Import-EdgeEnv
$failStreak = 0
$forceFile = $null
$lastSyncAttempt = [DateTime]::MinValue
$discordStatePath = $null

while ($true) {
    $cfg = Get-LocalEdgeConfig -ConfigPath $ConfigPath
    $discordStatePath = Join-Path $cfg.stateDir "last_discord_connection_preference.txt"
    $forceFile = Join-Path $cfg.stateDir "force_mode.txt"
    $forceMode = $null
    if (Test-Path -LiteralPath $forceFile) {
        $forceMode = (Get-Content -LiteralPath $forceFile -Raw).Trim().ToLowerInvariant()
        if ($forceMode -notin @("local", "cloudflare", "auto")) { $forceMode = $null }
        if ($forceMode -eq "auto") { $forceMode = $null }
    }

    $apiOk = Test-UrlOk "http://127.0.0.1:$($cfg.ports.api)/health"
    $gwOk = Test-UrlOk "http://127.0.0.1:$($cfg.ports.gateway)/__edge/health"
    $siteOk = Test-UrlOk "http://127.0.0.1:$($cfg.ports.site)/"
    $localHealth = if ($apiOk -and $gwOk) { "ok" } else { "failed" }
    if ($localHealth -eq "ok") { $failStreak = 0 } else { $failStreak++ }

    # Keep D1 in sync while the edge is up; stale/failed sync forces Cloudflare preference.
    $syncInterval = 300
    try {
        if ($null -ne $cfg.syncIntervalSeconds) { $syncInterval = [int]$cfg.syncIntervalSeconds }
    } catch { }
    if ($syncInterval -lt 60) { $syncInterval = 60 }
    $syncFresh = Test-LocalSyncFresh -Config $cfg
    $needSync = (-not $syncFresh.Fresh) -or (((Get-Date) - $lastSyncAttempt).TotalSeconds -ge $syncInterval)

    # Publish health immediately so the terminal is not stuck on a pre-sync "failed"
    # snapshot while a long wrangler D1 pull/push runs.
    if ($needSync -and $localHealth -eq "ok") {
        $interim = Resolve-ConnectionPreference -Config $cfg -LocalHealth $localHealth -FailStreak $failStreak -SyncFresh $syncFresh
        $interim | Add-Member -NotePropertyName fail_streak -NotePropertyValue $failStreak -Force
        $interim | Add-Member -NotePropertyName source -NotePropertyValue "local_edge" -Force
        $interim | Add-Member -NotePropertyName sync_phase -NotePropertyValue "in_progress" -Force
        [void](Write-PreferenceState -StateDir $cfg.stateDir -PreferenceObject $interim)
    }

    if ($needSync -and $localHealth -eq "ok") {
        $lastSyncAttempt = Get-Date
        # Catch-up pull when markers missing/stale; otherwise push local → remote warm backup.
        $dir = if (-not $syncFresh.Fresh) { "pull" } else { "push" }
        [void](Invoke-D1SyncCycle -Config $cfg -Direction $dir)
        $syncFresh = Test-LocalSyncFresh -Config $cfg
    } elseif ($needSync -and $localHealth -ne "ok") {
        # Edge down — do not claim sync freshness; preference will fall back to CF.
        $lastSyncAttempt = Get-Date
    }

    $modeArg = if ($forceMode) { $forceMode } else { $null }
    if ($forceMode) {
        $cfgClone = $cfg | ConvertTo-Json -Depth 8 | ConvertFrom-Json
        $cfgClone.preferenceMode = $forceMode
        $pref = Resolve-ConnectionPreference -Config $cfgClone -LocalHealth $localHealth -FailStreak $failStreak -SyncFresh $syncFresh
    } else {
        $pref = Resolve-ConnectionPreference -Config $cfg -LocalHealth $localHealth -FailStreak $failStreak -SyncFresh $syncFresh
    }

    $pref.surfaces = [ordered]@{
        api  = $(if ($apiOk -and $pref.preference -eq "local") { "local" } else { $pref.preference })
        api2 = $pref.preference
        site = $(if ($siteOk -and $pref.preference -eq "local") { "local" } else { $pref.preference })
        map  = $pref.preference
        gateway = $(if ($gwOk) { "local" } else { "down" })
    }
    $pref | Add-Member -NotePropertyName fail_streak -NotePropertyValue $failStreak -Force
    # Ensure operator name is attached even when surfaces were overwritten above
    $nodeUser = Get-NodeDiscordUsername -StateDir $cfg.stateDir
    if ($pref.preference -eq "local" -and $nodeUser) {
        $pref | Add-Member -NotePropertyName node_discord_username -NotePropertyValue $nodeUser -Force
    } else {
        $pref | Add-Member -NotePropertyName node_discord_username -NotePropertyValue $null -Force
    }
    $pref | Add-Member -NotePropertyName active_provider -NotePropertyValue $(if ($pref.preference -eq "local") { "solar" } else { "cloudflare" }) -Force
    $pref | Add-Member -NotePropertyName source -NotePropertyValue "local_edge" -Force
    $path = Write-PreferenceState -StateDir $cfg.stateDir -PreferenceObject $pref

    # Discord only after preference actually flips (local = synced online; else CF fallback)
    $prevDiscord = if (Test-Path -LiteralPath $discordStatePath) {
        (Get-Content -LiteralPath $discordStatePath -Raw).Trim()
    } else { "" }
    if ($prevDiscord -ne [string]$pref.preference) {
        # Skip announcing cloudflare on first boot before we've ever been local (avoid noise).
        # Still announce first local, and any later fallback / re-local.
        $announce = $true
        if ([string]::IsNullOrWhiteSpace($prevDiscord) -and $pref.preference -eq "cloudflare") {
            $announce = $false
        }
        if ($announce) {
            Publish-ConnectionDiscord -Config $cfg -Pref $pref -Previous $prevDiscord
        }
        Set-Content -LiteralPath $discordStatePath -Value $pref.preference -Encoding UTF8
    }

    # Mirror preference into local API D1 (Paper polls api-local.rootmc.net → :8787)
    # and Cloudflare public API (api.rootmc.net) when workstation key is set.
    $devKey = $env:ROOTMC_DEV_WORKSTATION_KEY
    $body = ($pref | ConvertTo-Json -Depth 6 -Compress)
    $headers = @{
        Authorization  = "Bearer $devKey"
        "Content-Type" = "application/json"
    }
    $localApiBase = "http://127.0.0.1:$($cfg.ports.api)"
    if ($devKey) {
        try {
            Invoke-RestMethod -Method Put -Uri "$localApiBase/api/rootmc/connection-preference" `
                -Headers $headers -Body $body -TimeoutSec 8 | Out-Null
        } catch {
            # Local API may be restarting
        }
    }
    $cfBase = [string]$cfg.upstreams.cfApi
    if ($devKey -and $cfBase) {
        try {
            Invoke-RestMethod -Method Put -Uri "$cfBase/api/rootmc/connection-preference" `
                -Headers $headers -Body $body -TimeoutSec 8 | Out-Null
        } catch {
            # Expected while CF is 429 / offline
        }
    }

    # Optional cutover hook when preference flips (disabled by default — enable with -EnableAutoCutover)
    if ($EnableAutoCutover) {
        $lastPath = Join-Path $cfg.stateDir "last_cutover_preference.txt"
        $prev = if (Test-Path $lastPath) { (Get-Content $lastPath -Raw).Trim() } else { "" }
        if ($prev -and $prev -ne $pref.preference) {
            $cutover = Join-Path $EdgeRoot "Invoke-EdgeCutover.ps1"
            if (Test-Path $cutover) {
                try {
                    & $cutover -ConfigPath $ConfigPath -Preference $pref.preference -Reason $pref.reason
                } catch {
                    Write-Warning "Cutover failed: $_"
                }
            }
        }
        Set-Content -LiteralPath $lastPath -Value $pref.preference -Encoding UTF8
    }

    if ($Once) { Write-Host "Wrote $path preference=$($pref.preference) reason=$($pref.reason) sync=$($pref.sync_status)"; break }
    Start-Sleep -Seconds ([Math]::Max(5, [int]$cfg.healthIntervalSeconds))
}
