# Attach slack.rootmc.net to Pages rootmc-web and point DNS at the project.
# Redirect logic: Web Files/rootmc-web/functions/_middleware.ts -> rootmcworkspace.slack.com
#
# Usage:
#   powershell -File scripts/setup-slack-subdomain.ps1
#   powershell -File scripts/setup-slack-subdomain.ps1 -WhatIf

param(
    [string]$ProjectName = "rootmc-web",
    [string]$Domain = "slack.rootmc.net",
    [string]$SlackWorkspace = "rootmcworkspace.slack.com",
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $workspaceRoot "scripts\load-rootmc-env.ps1")

$token = $env:CLOUDFLARE_API_TOKEN
$accountId = $env:CLOUDFLARE_ACCOUNT_ID
$zoneId = $env:CLOUDFLARE_ZONE_ID

if (-not $token -or $token.Length -lt 20) { throw "Set CLOUDFLARE_API_TOKEN in RootMC Workspace\.env" }
if (-not $accountId) { throw "Set CLOUDFLARE_ACCOUNT_ID in RootMC Workspace\.env" }
if (-not $zoneId) { throw "Set CLOUDFLARE_ZONE_ID (rootmc.net zone) in RootMC Workspace\.env" }

$headers = @{
    Authorization = "Bearer $token"
    "Content-Type" = "application/json"
}

function Invoke-CfApi {
    param([string]$Method, [string]$Uri, [object]$Body = $null)
    $params = @{
        Method  = $Method
        Uri     = $Uri
        Headers = $headers
    }
    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Compress)
    }
    $res = Invoke-RestMethod @params
    if (-not $res.success) {
        throw "Cloudflare API failed: $($res.errors | ConvertTo-Json -Compress)"
    }
    return $res.result
}

Write-Host "Fetching Pages project $ProjectName ..."
$project = Invoke-CfApi -Method Get -Uri "https://api.cloudflare.com/client/v4/accounts/$accountId/pages/projects/$ProjectName"
$pagesTarget = [string]$project.subdomain
if ($pagesTarget -notmatch '\.pages\.dev$') {
    $pagesTarget = "$pagesTarget.pages.dev"
}
Write-Host "Pages target: $pagesTarget"

Write-Host "Adding custom domain $Domain to Pages ..."
if ($WhatIf) {
    Write-Host "WhatIf: POST pages domain $Domain"
} elseif ($project.domains -contains $Domain) {
    Write-Host "Pages domain already registered: $Domain"
} else {
    try {
        Invoke-CfApi -Method Post -Uri "https://api.cloudflare.com/client/v4/accounts/$accountId/pages/projects/$ProjectName/domains" -Body @{ name = $Domain } | Out-Null
        Write-Host "Pages domain registered: $Domain"
    } catch {
        Write-Host "Pages domain add skipped (may already exist): $($_.Exception.Message)"
    }
}

Write-Host "Upserting DNS CNAME slack -> $pagesTarget ..."
$dnsName = if ($Domain -match "^([^.]+)\.") { $Matches[1] } else { $Domain }
$existing = Invoke-RestMethod -Method Get -Uri "https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records?type=CNAME&name=$Domain" -Headers @{ Authorization = "Bearer $token" }
$bodyObj = @{
    type    = "CNAME"
    name    = $dnsName
    content = $pagesTarget
    proxied = $true
    ttl     = 1
    comment = "rootmc-slack-admin-redirect"
}
$body = $bodyObj | ConvertTo-Json -Compress

if ($WhatIf) {
    Write-Host "WhatIf: upsert CNAME $Domain -> $pagesTarget"
} elseif ($existing.result -and $existing.result.Count -gt 0) {
    $id = $existing.result[0].id
    Invoke-RestMethod -Method Put -Uri "https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records/$id" -Headers $headers -Body $body | Out-Null
    Write-Host "Updated DNS CNAME $Domain -> $pagesTarget"
} else {
    Invoke-RestMethod -Method Post -Uri "https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records" -Headers $headers -Body $body | Out-Null
    Write-Host "Created DNS CNAME $Domain -> $pagesTarget"
}

Write-Host ""
Write-Host "Done."
Write-Host "  https://$Domain/  ->  https://$SlackWorkspace/  (after Pages deploy)"
Write-Host "Deploy middleware: cd `"Web Files\rootmc-web`"; powershell -File deploy.ps1"
