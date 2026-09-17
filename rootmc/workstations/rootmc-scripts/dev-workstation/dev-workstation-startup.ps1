# RootMC dev workstation presence - runs from local AppData (drive optional).
# Posts start/shutdown to Discord; heartbeats api.rootmc.net for hourly cron line.

param(
    [string]$Status = "Online",
    [string]$StatusLine = "",
    [string]$ConfigPath = "",
    [switch]$SkipMutex,
    [switch]$Headless
)

$ErrorActionPreference = "Stop"
$InstallRoot = $PSScriptRoot

# Single-instance guard: Startup shortcut + scheduled task can both fire at logon.
if (-not $SkipMutex) {
    $script:InstanceMutex = New-Object System.Threading.Mutex($false, "Global\RootMcDevWorkstationPresence")
    if (-not $script:InstanceMutex.WaitOne(0)) {
        Write-Host "Another dev-workstation presence instance is already running - exiting."
        exit 0
    }
} else {
    $script:InstanceMutex = $null
}
if (-not $ConfigPath) { $ConfigPath = Join-Path $InstallRoot "config.json" }
if (-not (Test-Path $ConfigPath)) { throw "Missing config: $ConfigPath" }

$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
$driveLetter = [string]$config.workstationDrive
if (-not $driveLetter) { $driveLetter = "D" }
$driveLetter = $driveLetter.TrimEnd(':').ToUpperInvariant()
$workspaceFolder = [string]$config.workspaceFolderName
if (-not $workspaceFolder) { $workspaceFolder = "RootMC Workspace" }
$channelId = [string]$config.discordChannelId
if (-not $channelId) { $channelId = "1529414449088172064" }
$apiBase = [string]$config.apiBase
if (-not $apiBase) { $apiBase = "https://api.rootmc.net" }
$heartbeatMinutes = [int]$config.heartbeatMinutes
if ($heartbeatMinutes -lt 1) { $heartbeatMinutes = 5 }
$workstationId = [string]$config.workstationId
if (-not $workstationId) { $workstationId = "primary" }
# API host-metrics + presence only accept primary | laptop.
if ($workstationId -ne "primary" -and $workstationId -ne "laptop") { $workstationId = "primary" }
$machineLabel = [string]$config.machineLabel
if (-not $machineLabel) {
    if ($workstationId -eq "laptop") { $machineLabel = "Dev laptop" }
    else { $machineLabel = "Dev station" }
}

function Import-DotEnvFile([string]$LiteralPath) {
    if (-not (Test-Path -LiteralPath $LiteralPath)) { return }
    Get-Content -LiteralPath $LiteralPath | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $p = $line.IndexOf("=")
        if ($p -gt 0) {
            $k = $line.Substring(0, $p).Trim()
            $v = $line.Substring($p + 1).Trim()
            if ($k -and $v) { Set-Item -Path "Env:$k" -Value $v }
        }
    }
}

function Sync-EnvFromWorkstationDrive {
    $driveRoot = "${driveLetter}:\"
    if (-not (Test-Path $driveRoot)) { return }
    $remoteEnv = Join-Path $driveRoot "$workspaceFolder\.env"
    if (-not (Test-Path $remoteEnv)) { return }
    $localEnv = Join-Path $InstallRoot ".env"
    $keys = @("DISCORD_ROOTMC_BOT_TOKEN", "ROOTMC_DEV_WORKSTATION_KEY", "SLACK_SERVER_LOGS_WEBHOOK_URL")
    $remote = @{}
    Get-Content -LiteralPath $remoteEnv | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $p = $line.IndexOf("=")
        if ($p -gt 0) {
            $k = $line.Substring(0, $p).Trim()
            $v = $line.Substring($p + 1).Trim()
            if ($k -and $v) { $remote[$k] = $v }
        }
    }
    $lines = @()
    if (Test-Path $localEnv) { $lines = @(Get-Content -LiteralPath $localEnv) }
    foreach ($key in $keys) {
        if (-not $remote.ContainsKey($key)) { continue }
        $entry = "$key=$($remote[$key])"
        $replaced = $false
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -match "^\s*$([regex]::Escape($key))=") {
                $lines[$i] = $entry
                $replaced = $true
                break
            }
        }
        if (-not $replaced) { $lines += $entry }
    }
    if ($lines.Count -gt 0) {
        Set-Content -LiteralPath $localEnv -Value $lines -Encoding UTF8
    }
}

Sync-EnvFromWorkstationDrive
Import-DotEnvFile (Join-Path $InstallRoot ".env")

$botToken = [string]$env:DISCORD_ROOTMC_BOT_TOKEN
$botToken = $botToken -replace '^(?i)bot\s+', ''
$devKey = [string]$env:ROOTMC_DEV_WORKSTATION_KEY
if (-not $botToken -or $botToken.Length -lt 40) {
    throw "DISCORD_ROOTMC_BOT_TOKEN missing in $InstallRoot\.env (sync from D: drive or run install-dev-workstation.ps1)."
}
if (-not $devKey -or $devKey.Length -lt 16) {
    throw "ROOTMC_DEV_WORKSTATION_KEY missing in $InstallRoot\.env"
}

function Get-HstNow {
    [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, "Hawaiian Standard Time")
}

function Format-HstStamp {
    $hst = Get-HstNow
    @{
        Date = $hst.ToString("dddd, MMMM d, yyyy")
        Time = $hst.ToString("h:mm tt") + " HST"
    }
}

function Get-WorkstationDriveStatus {
    $driveRoot = "${driveLetter}:\"
    $connected = Test-Path $driveRoot
    $workspacePath = Join-Path $driveRoot $workspaceFolder
    $workspaceAvailable = $connected -and (Test-Path $workspacePath)
    $driveLabel = if ($connected) { "${driveLetter}: connected" } else { "${driveLetter}: disconnected" }
    $workspaceLabel = if ($workspaceAvailable) { "available" } else { "not available" }
    @{
        Connected          = $connected
        WorkspaceAvailable = $workspaceAvailable
        DriveLine          = "**Workstation drive (${driveLetter}:):** $driveLabel"
        WorkspaceLine      = "**RootMC workspace:** $workspaceLabel"
        Summary            = "${driveLetter}:$(if ($connected) { 'on' } else { 'off' })"
    }
}

function Get-PresenceStatusLine {
    $drive = Get-WorkstationDriveStatus
    $custom = [string]$StatusLine
    if ($custom) { return $custom }
    "${machineLabel}: $Status | drive $($drive.Summary)"
}

function Send-DiscordPost([string]$Message) {
    $slackHelper = Join-Path (Split-Path $InstallRoot -Parent) "scripts\lib\Send-SlackServerLogs.ps1"
    if (-not (Test-Path $slackHelper)) {
        $slackHelper = "D:\.1 Work Stations\RootMC\scripts\lib\Send-SlackServerLogs.ps1"
    }
    if (Test-Path $slackHelper) {
        . $slackHelper
        if (Send-SlackServerLogs -Message $Message) { return }
    }

    if (-not $channelId) {
        throw "No Slack webhook (SLACK_SERVER_LOGS_WEBHOOK_URL) and no discordChannelId configured."
    }
    $content = $Message
    if ($content.Length -gt 2000) { $content = $content.Substring(0, 2000) }
    $body = @{ content = $content } | ConvertTo-Json -Compress
    $headers = @{
        Authorization = "Bot $botToken"
        "Content-Type" = "application/json"
        "User-Agent"   = "RootMC/dev-workstation-local"
    }
    Invoke-RestMethod -Uri "https://discord.com/api/v10/channels/$channelId/messages" `
        -Method POST -Headers $headers -Body $body | Out-Null
}

function Invoke-DevWorkstationApi([string]$Path, [string]$Method = "POST", [string]$Body = $null) {
    $uri = "$($apiBase.TrimEnd('/'))/api$Path"
    $headers = @{
        Authorization = "Bearer $devKey"
        Accept        = "application/json"
    }
    if ($Body) {
        Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers -ContentType "application/json" -Body $Body | Out-Null
    } else {
        Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers | Out-Null
    }
}

function Wait-BootReady {
    $maxSec = 120
    $deadline = (Get-Date).AddSeconds($maxSec)
    while ((Get-Date) -lt $deadline) {
        $drive = Get-WorkstationDriveStatus
        $net = Test-Connection -ComputerName "1.1.1.1" -Count 1 -Quiet -ErrorAction SilentlyContinue
        if ($net -and ($drive.Connected -or (Test-Path (Join-Path $InstallRoot ".env")))) { return }
        Start-Sleep -Seconds 5
    }
    Write-Warning "Boot wait timed out after ${maxSec}s - starting anyway."
}

function Invoke-WithRetry([scriptblock]$Action, [int]$Attempts = 6, [int]$DelaySec = 10) {
    for ($i = 1; $i -le $Attempts; $i++) {
        try {
            & $Action
            return
        } catch {
            if ($i -ge $Attempts) { throw }
            Write-Warning "Attempt $i failed: $($_.Exception.Message) - retry in ${DelaySec}s"
            Start-Sleep -Seconds $DelaySec
        }
    }
}

$script:ShutdownSent = $false

function Send-StartupPost {
    $stamp = Format-HstStamp
    $drive = Get-WorkstationDriveStatus
    $msg = @(
        "**$machineLabel powered on** — starting local edge / syncing databases…",
        "Dev connection **online** posts after D1 sync is fresh and the edge is healthy.",
        "**Date:** $($stamp.Date)",
        "**Time:** $($stamp.Time)",
        $drive.DriveLine,
        $drive.WorkspaceLine
    ) -join "`n"
    Send-DiscordPost $msg
}

function Send-ShutdownPost {
    if ($script:ShutdownSent) { return }
    $script:ShutdownSent = $true
    $stamp = Format-HstStamp
    $drive = Get-WorkstationDriveStatus
    $msg = @(
        "**$machineLabel powered off**",
        "**Date:** $($stamp.Date)",
        "**Time:** $($stamp.Time)",
        $drive.DriveLine
    ) -join "`n"
    try { Send-DiscordPost $msg } catch { Write-Warning $_ }
}

function Send-Heartbeat {
    $payload = @{
        workstation_id = $workstationId
        status_line    = Get-PresenceStatusLine
    }
    $json = $payload | ConvertTo-Json -Compress
    Invoke-DevWorkstationApi -Path "/rootmc/dev-workstation/heartbeat" -Body $json
}

function Send-ShutdownSignal {
    $payload = @{ workstation_id = $workstationId } | ConvertTo-Json -Compress
    try { Invoke-DevWorkstationApi -Path "/rootmc/dev-workstation/shutdown" -Body $payload } catch { Write-Warning $_ }
}

$cancelHandler = [System.ConsoleCancelEventHandler]{
    param($sender, $e)
    $e.Cancel = $true
    throw [System.OperationCanceledException]::new("Dev workstation startup stopped.")
}

. (Join-Path $PSScriptRoot "host-metrics.ps1")

Wait-BootReady
Invoke-WithRetry { Send-StartupPost }
Invoke-WithRetry { Send-Heartbeat }

$drive = Get-WorkstationDriveStatus
if (-not $Headless) {
    Write-Host "$machineLabel presence ($InstallRoot)"
    Write-Host "  Drive ${driveLetter}: $(if ($drive.Connected) { 'connected' } else { 'disconnected' })"
    Write-Host "  Metrics: 60 samples/minute posted to D1"
    Write-Host "  Presence heartbeat every $heartbeatMinutes min - Ctrl+C to stop."
}

$metricSamples = New-Object System.Collections.Generic.List[object]
$currentMinute = (Get-Date).Date.AddHours((Get-Date).Hour).AddMinutes((Get-Date).Minute)
$nextHeartbeat = (Get-Date).AddMinutes($heartbeatMinutes)

[Console]::Add_CancelKeyPress($cancelHandler)
try {
    while ($true) {
        $metricSamples.Add((Get-HostMetricsSample)) | Out-Null
        $now = Get-Date
        $minute = $now.Date.AddHours($now.Hour).AddMinutes($now.Minute)
        if ($minute -ne $currentMinute -and $metricSamples.Count -gt 0) {
            Send-HostMetricsMinute -Samples $metricSamples.ToArray() -MinuteStart $currentMinute `
                -WorkstationId $workstationId -ApiBase $apiBase -DevKey $devKey
            $metricSamples.Clear()
            $currentMinute = $minute
        } elseif ($metricSamples.Count -ge 60) {
            Send-HostMetricsMinute -Samples $metricSamples.ToArray() -MinuteStart $currentMinute `
                -WorkstationId $workstationId -ApiBase $apiBase -DevKey $devKey
            $metricSamples.Clear()
            $currentMinute = $minute
        }
        if ($now -ge $nextHeartbeat) {
            Send-Heartbeat
            $nextHeartbeat = $now.AddMinutes($heartbeatMinutes)
        }
        Start-Sleep -Seconds 1
    }
} catch [System.OperationCanceledException] {
    # Ctrl+C
} finally {
    [Console]::Remove_CancelKeyPress($cancelHandler)
    Send-ShutdownPost
    Send-ShutdownSignal
}
