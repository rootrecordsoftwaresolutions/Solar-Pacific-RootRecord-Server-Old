#Requires -Version 5.1
<#
.SYNOPSIS
  Post text (and optional file tail) to Slack #server-logs via Incoming Webhook.
  No Cloudflare Worker - direct HTTPS to hooks.slack.com.
#>
function Get-RootMcSlackServerLogsWebhook {
    $candidates = @(
        [string]$env:SLACK_SERVER_LOGS_WEBHOOK_URL,
        [string]$env:SLACK_ROOTMC_SERVER_LOGS_WEBHOOK_URL
    )
    foreach ($c in $candidates) {
        if ($c -and $c -match '^https://hooks\.slack\.com/services/') { return $c.Trim() }
    }
    return ""
}

function Send-SlackServerLogs {
    param(
        [Parameter(Mandatory = $true)][string]$Message,
        [string]$WebhookUrl = "",
        [string]$FilePath = ""
    )
    if (-not $WebhookUrl) { $WebhookUrl = Get-RootMcSlackServerLogsWebhook }
    if (-not $WebhookUrl) {
        Write-Warning "SLACK_SERVER_LOGS_WEBHOOK_URL not set - skip Slack server-logs post."
        return $false
    }

    $text = [string]$Message
    if ($FilePath -and (Test-Path -LiteralPath $FilePath)) {
        $raw = [System.IO.File]::ReadAllText($FilePath)
        # Incoming webhooks are text-only; keep a useful tail under Slack's practical limit.
        $max = 35000
        if ($raw.Length -gt $max) {
            $raw = $raw.Substring($raw.Length - $max)
            $text = "$text`n_(truncated to last $max chars)_"
        }
        $fence = '```'
        $text = "$text`n$fence$raw$fence"
    }
    if ($text.Length -gt 39000) { $text = $text.Substring(0, 39000) }

    $body = @{ text = $text } | ConvertTo-Json -Compress
    try {
        Invoke-RestMethod -Uri $WebhookUrl -Method POST -ContentType 'application/json' -Body $body -TimeoutSec 30 | Out-Null
        return $true
    } catch {
        $err = $_.Exception.Message
        Write-Warning ("Slack server-logs post failed: {0}" -f $err)
        return $false
    }
}
