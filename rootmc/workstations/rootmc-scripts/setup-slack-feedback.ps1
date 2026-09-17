#Requires -Version 5.1
<#
.SYNOPSIS
  Save Slack Incoming Webhook for #feedback (in-game /feedback) and optionally deploy API.

.EXAMPLE
  powershell -File scripts\setup-slack-feedback.ps1 -WebhookUrl "https://hooks.slack.com/services/..."
  powershell -File scripts\setup-slack-feedback.ps1 -WebhookUrl "..." -Deploy
#>
param(
    [string]$WebhookUrl = "",
    [string]$ChannelId = "C0BLMGBVAMD",
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
        Write-Host "  2) Choose channel #feedback"
        Write-Host "  3) Copy the Webhook URL"
        Start-Process $installUrl
        Start-Sleep -Seconds 1
        Start-Process $legacyUrl
    }
    Write-Host ""
    Write-Host "Paste the Incoming Webhook URL for #feedback (https://hooks.slack.com/services/...)"
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
        $lines += "# Slack Incoming Webhook -> #feedback ($ChannelId) - in-game /feedback"
        $lines += $entry
    }
    Set-Content -LiteralPath $Path -Value $lines -Encoding UTF8
}

Upsert-EnvLine -Path $envFile -Key "SLACK_FEEDBACK_WEBHOOK_URL" -Value $WebhookUrl
Upsert-EnvLine -Path $envFile -Key "SLACK_FEEDBACK_CHANNEL_ID" -Value $ChannelId

$payload = @{ text = "RootMC in-game /feedback is wired to <#$ChannelId> via Incoming Webhook (api.rootmc.net)." } | ConvertTo-Json -Compress
try {
    $resp = Invoke-WebRequest -Method Post -Uri $WebhookUrl -ContentType "application/json; charset=utf-8" -Body $payload -UseBasicParsing
    $body = [string]$resp.Content
    if ($body.Trim() -ne "ok") {
        Write-Warning "Webhook responded: $body"
    } else {
        Write-Host "Test message posted to #feedback."
    }
} catch {
    throw "Webhook saved but test post failed: $_"
}

Write-Host "Saved SLACK_FEEDBACK_WEBHOOK_URL to $envFile"

if ($Deploy) {
    Write-Host "Deploying rootmc-api (uploads Slack webhook secret)..."
    & powershell -NoProfile -File (Join-Path $workspaceRoot "Web Files\rootmc-api\deploy.ps1")
}
