# Flip public DNS between Cloudflare Tunnel (local) and Worker/Pages (cloudflare backup).
# Requires CLOUDFLARE_API_TOKEN + CLOUDFLARE_ZONE_ID in environment / workspace .env.
# Safe default: staging hostnames only until -AllowProduction is passed.

param(
    [string]$ConfigPath = "",
    [ValidateSet("local", "cloudflare")][string]$Preference = "local",
    [string]$Reason = "manual",
    [switch]$AllowProduction,
    [switch]$WhatIf,
    [switch]$Force,
    [switch]$SkipPreOnlineGate
)

$ErrorActionPreference = "Stop"
$EdgeRoot = $PSScriptRoot
if (-not $ConfigPath) { $ConfigPath = Join-Path $EdgeRoot "config.json" }
. (Join-Path $EdgeRoot "lib\Preference.ps1")
$cfg = Get-LocalEdgeConfig -ConfigPath $ConfigPath

function Import-WorkspaceEnv {
    $envPath = Join-Path $cfg.workspaceRoot ".env"
    if (-not (Test-Path $envPath)) { return }
    Get-Content $envPath | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $i = $line.IndexOf("=")
        if ($i -lt 1) { return }
        $k = $line.Substring(0, $i).Trim()
        $v = $line.Substring($i + 1).Trim()
        if ($k -and $v) { Set-Item -Path "Env:$k" -Value $v }
    }
}

Import-WorkspaceEnv
$token = $env:CLOUDFLARE_API_TOKEN
$zoneId = $env:CLOUDFLARE_ZONE_ID
$tunnelId = $env:ROOTMC_TUNNEL_ID

if ($Preference -eq "local" -and -not $SkipPreOnlineGate -and -not $Force) {
    $readyFile = Join-Path $cfg.stateDir "preonline-ready.json"
    $ready = $false
    if (Test-Path -LiteralPath $readyFile) {
        try {
            $rj = Get-Content -LiteralPath $readyFile -Raw | ConvertFrom-Json
            $ready = [bool]$rj.ready
            $ageH = ((Get-Date).ToUniversalTime() - [datetime]$rj.at).TotalHours
            if ($ageH -gt 6) { $ready = $false }
        } catch { $ready = $false }
    }
    if (-not $ready) {
        Write-Host "Pre-online gate not ready. Running Assert-PreOnlineSync.ps1 ..."
        & (Join-Path $EdgeRoot "Assert-PreOnlineSync.ps1") -ConfigPath $ConfigPath
        if ($LASTEXITCODE -ne 0) {
            throw "Pre-online sync gate failed. Fix issues or pass -Force / -SkipPreOnlineGate."
        }
    }
}
if (-not $token -or -not $zoneId) {
    Write-Warning "CLOUDFLARE_API_TOKEN / CLOUDFLARE_ZONE_ID missing - recording preference only (no DNS change)."
    $pref = New-PreferenceObject -Preference $Preference -Reason $Reason -LocalHealth "unknown" -ScheduleAllowsLocal $true
    Write-PreferenceState -StateDir $cfg.stateDir -PreferenceObject $pref | Out-Null
    $log = Join-Path $cfg.stateDir "cutover-log.jsonl"
    Add-Content -LiteralPath $log -Value ((@{ at = (Get-Date).ToUniversalTime().ToString("o"); preference = $Preference; reason = $Reason; dns = $false } | ConvertTo-Json -Compress))
    return
}

$hosts = if ($AllowProduction -or -not [bool]$cfg.useStagingHostnamesFirst) {
    $cfg.hostnames
} else {
    $cfg.stagingHostnames
}

$tunnelTarget = if ($tunnelId) { "$tunnelId.cfargotunnel.com" } else { $env:ROOTMC_TUNNEL_CNAME }
if ($Preference -eq "local" -and -not $tunnelTarget) {
    throw "Set ROOTMC_TUNNEL_ID or ROOTMC_TUNNEL_CNAME for local cutover DNS target."
}

# Worker/Pages targets when falling back to Cloudflare
$cfTargets = @{
    api  = @{ type = "AAAA"; content = "100::"; proxied = $true; comment = "workers_route_placeholder_use_custom_domain" }
}

function Get-DnsRecords([string]$Name) {
    $uri = "https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records?name=$Name"
    return Invoke-RestMethod -Uri $uri -Headers @{ Authorization = "Bearer $token" } -Method Get
}

function Set-Cname([string]$Name, [string]$Content) {
    $existing = Get-DnsRecords -Name $Name
    $bodyObj = @{
        type    = "CNAME"
        name    = $Name
        content = $Content
        proxied = $true
        ttl     = 1
        comment = "rootmc-local-edge:$Preference"
    }
    $body = $bodyObj | ConvertTo-Json -Compress
    if ($WhatIf) {
        Write-Host "WhatIf: upsert CNAME $Name -> $Content"
        return
    }
    if ($existing.result -and $existing.result.Count -gt 0) {
        $id = $existing.result[0].id
        Invoke-RestMethod -Method Put -Uri "https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records/$id" `
            -Headers @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" } `
            -Body $body | Out-Null
    } else {
        Invoke-RestMethod -Method Post -Uri "https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records" `
            -Headers @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" } `
            -Body $body | Out-Null
    }
    Write-Host "DNS CNAME $Name -> $Content"
}

# For cloudflare preference: do not delete Worker custom domains automatically.
# Staging: point CNAMEs at workers.dev or leave unchanged; production reattach is documented in RUNBOOK.
if ($Preference -eq "local") {
    foreach ($prop in $hosts.PSObject.Properties) {
        $name = [string]$prop.Value
        Set-Cname -Name $name -Content $tunnelTarget
    }
} else {
    Write-Host "preference=cloudflare: DNS left to Worker/Pages custom domains. Restore via Cloudflare dashboard or RUNBOOK if staging CNAMEs were pointed at tunnel."
    if (-not $AllowProduction) {
        foreach ($prop in $hosts.PSObject.Properties) {
            Write-Host "Staging hostname $($prop.Value) - remove tunnel CNAME manually if needed."
        }
    }
}

$pref = New-PreferenceObject -Preference $Preference -Reason $Reason -LocalHealth "unknown" -ScheduleAllowsLocal $true
Write-PreferenceState -StateDir $cfg.stateDir -PreferenceObject $pref | Out-Null
$log = Join-Path $cfg.stateDir "cutover-log.jsonl"
Add-Content -LiteralPath $log -Value ((@{
    at = (Get-Date).ToUniversalTime().ToString("o")
    preference = $Preference
    reason = $Reason
    dns = $true
    allow_production = [bool]$AllowProduction
} | ConvertTo-Json -Compress))

Write-Host "Cutover recorded preference=$Preference reason=$Reason"
