#Requires -Version 5.1
<#
.SYNOPSIS
  Save Slack Incoming Webhook for #server-reports (Discord report copies) and optionally deploy API.

.EXAMPLE
  powershell -File scripts\setup-slack-server-reports.ps1 -WebhookUrl "https://hooks.slack.com/services/..."
  powershell -File scripts\setup-slack-server-reports.ps1 -WebhookUrl "..." -Deploy
#>
param(
    [string]$WebhookUrl = "",
    [string]$ChannelId = "C0BLY49H13M",
    [switch]$SkipBrowser,
    [switch]$Deploy
)

$ErrorActionPreference = "Stop"
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$envFile = Join-Path $workspaceRoot ".env"

$installUrl = "https://rootmcworkspace.slack.com/apps/A0F7XDUUB-incoming-webhooks?tab=settings&next_id=0"
$legacyUrl = "https://rootmcworkspace.slack.com/services/new/incoming-webhook?channel=$ChannelId"

if (-not $WebhookUrl) {
    if (-not $SkipBrowser) {
        Write-Host "Opening Slack Incoming Webhooks installer..."
        Write-Host "  1) Add Incoming WebHooks (or Add New Webhook)"
        Write-Host "  2) Choose channel #server-reports"
        Write-Host "  3) Copy the Webhook URL"
        Start-Process $installUrl
        Start-Sleep -Seconds 1
        Start-Process $legacyUrl
    }
    Write-Host ""
    Write-Host "Paste the Incoming Webhook URL for #server-reports (https://hooks.slack.com/services/...)"
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
        $lines += "# Slack Incoming Webhook -> #server-reports ($ChannelId) - Discord report copies"
        $lines += $entry
        $lines += "SLACK_SERVER_REPORTS_CHANNEL_ID=$ChannelId"
    }
    Set-Content -LiteralPath $Path -Value $lines -Encoding UTF8
}

Upsert-EnvLine -Path $envFile -Key "SLACK_SERVER_REPORTS_WEBHOOK_URL" -Value $WebhookUrl
Upsert-EnvLine -Path $envFile -Key "SLACK_SERVER_REPORTS_CHANNEL_ID" -Value $ChannelId
$env:SLACK_SERVER_REPORTS_WEBHOOK_URL = $WebhookUrl
$env:SLACK_SERVER_REPORTS_CHANNEL_ID = $ChannelId

Write-Host "Saved SLACK_SERVER_REPORTS_WEBHOOK_URL to $envFile"

if ($Deploy) {
    & (Join-Path $workspaceRoot "Web Files\rootmc-api\deploy.ps1")
}
