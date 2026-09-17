# Shared preference helpers for local-edge scripts.

function Get-LocalEdgeConfig {
    param([string]$ConfigPath)
    if (-not $ConfigPath) {
        $ConfigPath = Join-Path (Split-Path $PSScriptRoot -Parent) "config.json"
    }
    if (-not (Test-Path -LiteralPath $ConfigPath)) { throw "Missing config: $ConfigPath" }
    return Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
}

function Get-HstNow {
    try {
        return [System.TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, "Hawaiian Standard Time")
    } catch {
        return [DateTime]::UtcNow.AddHours(-10)
    }
}

function Test-ScheduleAllowsLocal {
    param(
        $Config,
        $HstNow = $null
    )
    if ($null -eq $HstNow) { $HstNow = Get-HstNow }
    if ($HstNow -isnot [DateTime]) { $HstNow = Get-HstNow }
    $windows = @($Config.scheduleWindowsHst)
    if ($windows.Count -eq 0) { return $true }
    $dow = [int]$HstNow.DayOfWeek  # 0=Sun .. 6=Sat
    $mins = $HstNow.Hour * 60 + $HstNow.Minute
    foreach ($w in $windows) {
        $days = @($w.days)
        if ($days -notcontains $dow) { continue }
        $startParts = ([string]$w.start).Split(":")
        $endParts = ([string]$w.end).Split(":")
        $startM = [int]$startParts[0] * 60 + [int]$startParts[1]
        $endM = [int]$endParts[0] * 60 + [int]$endParts[1]
        if ($mins -ge $startM -and $mins -le $endM) { return $true }
    }
    return $false
}

function New-PreferenceObject {
    param(
        [ValidateSet("local", "cloudflare")][string]$Preference,
        [string]$Reason,
        [string]$LocalHealth,
        [bool]$ScheduleAllowsLocal,
        [hashtable]$Surfaces = $null,
        [string]$SyncStatus = $null
    )
    if (-not $Surfaces) {
        $Surfaces = @{ api = $Preference; api2 = $Preference; site = $Preference; map = $Preference }
    }
    $obj = [ordered]@{
        preference            = $Preference
        active_provider       = $(if ($Preference -eq "local") { "solar" } else { "cloudflare" })
        reason                = $Reason
        checked_at            = [DateTime]::UtcNow.ToString("o")
        local_health          = $LocalHealth
        schedule_allows_local = $ScheduleAllowsLocal
        surfaces              = $Surfaces
    }
    if ($null -ne $SyncStatus) { $obj.sync_status = $SyncStatus }
    $nodeUser = Get-NodeDiscordUsername -StateDir $script:__prefStateDir
    if ($Preference -eq "local" -and $nodeUser) {
        $obj.node_discord_username = $nodeUser
    } else {
        $obj.node_discord_username = $null
    }
    return $obj
}

function Get-NodeDiscordUsername {
    param([string]$StateDir)
    $candidates = @()
    if ($StateDir) {
        $candidates += (Join-Path $StateDir "node_operator.json")
    }
    $candidates += (Join-Path $env:LOCALAPPDATA "RootMC\root-core-node\state\node_operator.json")
    $candidates += (Join-Path $env:LOCALAPPDATA "RootMC\local-edge\state\node_operator.json")
    foreach ($path in $candidates) {
        if (-not (Test-Path -LiteralPath $path)) { continue }
        try {
            $j = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
            $name = [string]($j.discord_username)
            if ([string]::IsNullOrWhiteSpace($name)) { $name = [string]($j.discord_global_name) }
            if (-not [string]::IsNullOrWhiteSpace($name)) { return $name.Trim() }
        } catch { }
    }
    return $null
}

function Get-LatestD1SyncUtc {
    param([string]$StateDir)
    if (-not $StateDir -or -not (Test-Path -LiteralPath $StateDir)) { return $null }
    $markers = @(
        "last-d1-sync-rootmc.json",
        "last-d1-sync-g2.json",
        "last-d1-sync-webstat.json",
        "last-d1-bootstrap.json",
        "last-sync-heartbeat.json"
    )
    $latest = $null
    foreach ($name in $markers) {
        $path = Join-Path $StateDir $name
        if (-not (Test-Path -LiteralPath $path)) { continue }
        $t = $null
        try {
            $j = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
            if ($j.at) { $t = [DateTime]::Parse([string]$j.at).ToUniversalTime() }
        } catch { }
        if (-not $t) { $t = (Get-Item -LiteralPath $path).LastWriteTimeUtc }
        if (-not $latest -or $t -gt $latest) { $latest = $t }
    }
    return $latest
}

function Test-LocalSyncFresh {
    param(
        $Config,
        [string]$StateDir = $null
    )
    if (-not $StateDir) { $StateDir = [string]$Config.stateDir }
    $maxMin = 45
    try {
        if ($null -ne $Config.maxSyncAgeMinutes) { $maxMin = [int]$Config.maxSyncAgeMinutes }
    } catch { }
    if ($maxMin -lt 1) { $maxMin = 45 }

    $latest = Get-LatestD1SyncUtc -StateDir $StateDir
    if (-not $latest) {
        return [pscustomobject]@{ Fresh = $false; Reason = "no_sync_marker"; AgeMinutes = $null; MaxMinutes = $maxMin }
    }
    $age = ((Get-Date).ToUniversalTime() - $latest).TotalMinutes
    if ($age -gt $maxMin) {
        return [pscustomobject]@{ Fresh = $false; Reason = "sync_stale"; AgeMinutes = [Math]::Round($age, 1); MaxMinutes = $maxMin }
    }
    return [pscustomobject]@{ Fresh = $true; Reason = "sync_ok"; AgeMinutes = [Math]::Round($age, 1); MaxMinutes = $maxMin }
}

function Send-DevConnectionDiscord {
    param(
        [string]$Message,
        [string]$ChannelId = "1529414449088172064"
    )
    # Prefer Slack #server-logs (Incoming Webhook) — no Worker.
    $slackHelper = Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) "lib\Send-SlackServerLogs.ps1"
    if (-not (Test-Path $slackHelper)) {
        $slackHelper = "D:\.1 Work Stations\RootMC\scripts\lib\Send-SlackServerLogs.ps1"
    }
    if (Test-Path $slackHelper) {
        . $slackHelper
        if (Send-SlackServerLogs -Message $Message) { return $true }
    }

    $token = $env:DISCORD_ROOTMC_BOT_TOKEN
    if (-not $token) { return $false }
    if ($Message.Length -gt 2000) { $Message = $Message.Substring(0, 2000) }
    $body = @{ content = $Message } | ConvertTo-Json -Compress
    $headers = @{
        Authorization = "Bot $token"
        "Content-Type" = "application/json"
        "User-Agent"   = "RootMC/local-edge-preference"
    }
    try {
        Invoke-RestMethod -Uri "https://discord.com/api/v10/channels/$ChannelId/messages" `
            -Method POST -Headers $headers -Body $body -TimeoutSec 15 | Out-Null
        return $true
    } catch {
        Write-Warning "Discord connection post failed: $($_.Exception.Message)"
        return $false
    }
}

function Read-PreferenceState {
    param([string]$StateDir)
    $path = Join-Path $StateDir "current_connection_preference.json"
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    return Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
}

function Write-PreferenceState {
    param([string]$StateDir, $PreferenceObject)
    if (-not (Test-Path -LiteralPath $StateDir)) {
        New-Item -ItemType Directory -Path $StateDir -Force | Out-Null
    }
    $path = Join-Path $StateDir "current_connection_preference.json"
    $json = ($PreferenceObject | ConvertTo-Json -Depth 6)
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($path, $json, $utf8NoBom)
    return $path
}

function Resolve-ConnectionPreference {
    param(
        $Config,
        [string]$LocalHealth,
        [int]$FailStreak = 0,
        [string]$ForceMode = $null,
        $SyncFresh = $null
    )
    $script:__prefStateDir = [string]$Config.stateDir
    $mode = [string]$Config.preferenceMode
    if ($ForceMode) { $mode = $ForceMode }
    $scheduleOk = Test-ScheduleAllowsLocal -Config $Config
    $threshold = [int]$Config.healthFailThreshold
    if ($threshold -lt 1) { $threshold = 3 }
    if ($null -eq $SyncFresh) {
        $SyncFresh = Test-LocalSyncFresh -Config $Config
    }
    $syncStatus = "$($SyncFresh.Reason)$(if ($null -ne $SyncFresh.AgeMinutes) { " age=$($SyncFresh.AgeMinutes)m" })"

    if ($mode -eq "local") {
        # Forced RootMC Network still cannot claim Solar when the local edge is down.
        if ($LocalHealth -ne "ok" -or $FailStreak -ge $threshold) {
            return New-PreferenceObject -Preference cloudflare -Reason "forced_local_but_unhealthy" -LocalHealth $LocalHealth -ScheduleAllowsLocal $scheduleOk -SyncStatus $syncStatus
        }
        return New-PreferenceObject -Preference local -Reason "forced_local" -LocalHealth $LocalHealth -ScheduleAllowsLocal $scheduleOk -SyncStatus $syncStatus
    }
    if ($mode -eq "cloudflare") {
        return New-PreferenceObject -Preference cloudflare -Reason "forced_cloudflare" -LocalHealth $LocalHealth -ScheduleAllowsLocal $scheduleOk -SyncStatus $syncStatus
    }

    # auto — local only when healthy AND D1 sync markers are fresh
    if (-not $scheduleOk) {
        return New-PreferenceObject -Preference cloudflare -Reason "outside_schedule_window" -LocalHealth $LocalHealth -ScheduleAllowsLocal $false -SyncStatus $syncStatus
    }
    if ($LocalHealth -ne "ok" -or $FailStreak -ge $threshold) {
        return New-PreferenceObject -Preference cloudflare -Reason "local_health_failed" -LocalHealth $LocalHealth -ScheduleAllowsLocal $scheduleOk -SyncStatus $syncStatus
    }
    if (-not $SyncFresh.Fresh) {
        return New-PreferenceObject -Preference cloudflare -Reason $SyncFresh.Reason -LocalHealth $LocalHealth -ScheduleAllowsLocal $scheduleOk -SyncStatus $syncStatus
    }
    return New-PreferenceObject -Preference local -Reason "health_and_sync_ok" -LocalHealth $LocalHealth -ScheduleAllowsLocal $scheduleOk -SyncStatus $syncStatus
}
