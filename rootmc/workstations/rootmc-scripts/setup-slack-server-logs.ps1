#Requires -Version 5.1
<#
.SYNOPSIS
  One-time setup: create Slack Incoming Webhook for #server-logs and save it locally.

  No Cloudflare Worker. Opens Slack's Incoming Webhooks installer for RootMC,
  then waits for you to paste the hooks.slack.com URL (or pass -WebhookUrl).

.EXAMPLE
  powershell -File scripts\setup-slack-server-logs.ps1
  powershell -File scripts\setup-slack-server-logs.ps1 -WebhookUrl "https://hooks.slack.com/services/..."
#>
param(
    [string]$WebhookUrl = "",
    [string]$ChannelId = "C0BMX0QKSTS",
    [switch]$SkipBrowser
)

$ErrorActionPreference = "Stop"
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$envFile = Join-Path $workspaceRoot ".env"
$handoffs = @(
    (Join-Path $workspaceRoot "Server Handoffs\1. RootMC - Claims\plugins\RootMC\cloud.yml"),
    (Join-Path $workspaceRoot "Server Handoffs\2. RootMC - Towny\plugins\RootMC\cloud.yml"),
    (Join-Path $workspaceRoot "Server Handoffs\3. RootMC - Test Server\plugins\RootMC\cloud.yml")
)

$installUrl = "https://rootmcworkspace.slack.com/apps/A0F7XDUUB-incoming-webhooks?tab=settings&next_id=0"
# Deep link that preselects channel when legacy flow is available:
$legacyUrl = "https://rootmcworkspace.slack.com/services/new/incoming-webhook?channel=$ChannelId"

if (-not $WebhookUrl) {
    if (-not $SkipBrowser) {
        Write-Host "Opening Slack Incoming Webhooks installer..."
        Write-Host "  1) Add Incoming WebHooks to RootMC"
        Write-Host "  2) Choose channel #server-logs"
        Write-Host "  3) Copy the Webhook URL"
        Start-Process $installUrl
        Start-Sleep -Seconds 1
        Start-Process $legacyUrl
    }
    Write-Host ""
    Write-Host "Paste the Incoming Webhook URL for #server-logs (https://hooks.slack.com/services/...)"
    $WebhookUrl = (Read-Host "Webhook URL").Trim()
}

if ($WebhookUrl -notmatch '^https://hooks\.slack\.com/services/') {
    throw "Expected a hooks.slack.com Incoming Webhook URL."
}

function Upsert-EnvLine([string]$Path, [string]$Key, [string]$Value) {
    $lines = @()
    if (Test-Path -LiteralPath $Path) { $lines = @(Get-Content -LiteralPath $Path) }
    $entry = "$Key=$Value"
    $replaced = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^\s*$([regex]::Escape($Key))=") {
            $lines[$i] = $entry
            $replaced = $true
            break
        }
    }
    if (-not $replaced) {
        if ($lines.Count -gt 0 -and $lines[-1] -ne "") { $lines += "" }
        $lines += "# Slack Incoming Webhook → #server-logs ($ChannelId)"
        $lines += $entry
    }
    Set-Content -LiteralPath $Path -Value $lines -Encoding UTF8
}

function Set-CloudWebhook([string]$Path, [string]$Url) {
    if (-not (Test-Path -LiteralPath $Path)) { return }
    $raw = Get-Content -LiteralPath $Path -Raw
    if ($raw -match '(?m)^(\s*server-logs-webhook:\s*).*$') {
        $raw = [regex]::Replace($raw, '(?m)^(\s*server-logs-webhook:\s*).*$', "`${1}`"$Url`"")
    } elseif ($raw -match '(?m)^slack:') {
        $raw = $raw -replace '(?m)^(slack:\s*\r?\n)', "`$1  server-logs-webhook: `"$Url`"`r`n"
    } else {
        $raw = $raw.TrimEnd() + "`r`n`r`nslack:`r`n  server-logs-channel: `"$ChannelId`"`r`n  server-logs-webhook: `"$Url`"`r`n"
    }
    # Keep Discord server-logs blank so Slack is preferred.
    $raw = [regex]::Replace($raw, '(?m)^(\s*server-logs:\s*).*$', '${1}""')
    Set-Content -LiteralPath $Path -Value $raw -NoNewline -Encoding UTF8
}

Upsert-EnvLine -Path $envFile -Key "SLACK_SERVER_LOGS_WEBHOOK_URL" -Value $WebhookUrl
Upsert-EnvLine -Path $envFile -Key "SLACK_SERVER_LOGS_CHANNEL_ID" -Value $ChannelId
foreach ($h in $handoffs) { Set-CloudWebhook -Path $h -Url $WebhookUrl }

. (Join-Path $PSScriptRoot "lib\Send-SlackServerLogs.ps1")
$env:SLACK_SERVER_LOGS_WEBHOOK_URL = $WebhookUrl
$ok = Send-SlackServerLogs -Message ("RootMC server logs are now wired to <#{0}> via Incoming Webhook (no Worker)." -f $ChannelId) -WebhookUrl $WebhookUrl
if (-not $ok) { throw "Webhook saved but test post failed." }

Write-Host ""
Write-Host "Saved SLACK_SERVER_LOGS_WEBHOOK_URL to $envFile"
Write-Host "Updated handoff cloud.yml slack.server-logs-webhook"
Write-Host "Test message posted to #server-logs."
Write-Host "Rebuild/upload root-discord so Paper hourly logs use Slack."
