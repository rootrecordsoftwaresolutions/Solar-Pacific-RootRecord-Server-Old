# Create Cloudflare Tunnel via API (no interactive cloudflared login) and wire local-edge config.
param(
    [string]$TunnelName = "rootmc-local-edge",
    [string]$ConfigPath = ""
)

$ErrorActionPreference = "Stop"
$EdgeRoot = $PSScriptRoot
if (-not $ConfigPath) { $ConfigPath = Join-Path $EdgeRoot "config.json" }
. (Join-Path $EdgeRoot "lib\Preference.ps1")
$cfg = Get-LocalEdgeConfig -ConfigPath $ConfigPath
$ws = [string]$cfg.workspaceRoot

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
        if ($k -and $v) { Set-Item -Path "Env:$k" -Value $v }
    }
}

function Set-EnvFileKey([string]$Path, [string]$Key, [string]$Value) {
    $lines = @()
    if (Test-Path $Path) { $lines = @(Get-Content $Path) }
    $found = $false
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($line in $lines) {
        if ($line -match ("^\s*" + [regex]::Escape($Key) + "\s*=")) {
            $found = $true
            [void]$out.Add("$Key=$Value")
        } else {
            [void]$out.Add($line)
        }
    }
    if (-not $found) { [void]$out.Add("$Key=$Value") }
    Set-Content -LiteralPath $Path -Value $out.ToArray() -Encoding UTF8
}

Import-WorkspaceEnv
$token = $env:CLOUDFLARE_API_TOKEN
$accountId = $env:CLOUDFLARE_ACCOUNT_ID
$zoneId = $env:CLOUDFLARE_ZONE_ID
if (-not $token -or -not $accountId) {
    throw "Need CLOUDFLARE_API_TOKEN and CLOUDFLARE_ACCOUNT_ID in .env"
}

$headers = @{
    Authorization = "Bearer $token"
    "Content-Type" = "application/json"
}
$base = "https://api.cloudflare.com/client/v4/accounts/$accountId/cfd_tunnel"

$list = Invoke-RestMethod -Uri "$base`?name=$TunnelName&is_deleted=false" -Headers $headers -Method Get
$tunnelId = $null
$tunnelSecret = $null
$tunnelToken = $null

if ($list.result -and $list.result.Count -gt 0) {
    $tunnelId = [string]$list.result[0].id
    Write-Host "Found existing tunnel $TunnelName id=$tunnelId"
    $tokRes = Invoke-RestMethod -Uri "$base/$tunnelId/token" -Headers $headers -Method Get
    if (-not $tokRes.success) {
        throw ("Failed to get tunnel token: " + ($tokRes | ConvertTo-Json -Compress))
    }
    $tunnelToken = [string]$tokRes.result
} else {
    Write-Host "Creating tunnel $TunnelName ..."
    $rng = [byte[]]::new(32)
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($rng)
    $tunnelSecret = [Convert]::ToBase64String($rng)
    $body = (@{
        name = $TunnelName
        config_src = "local"
        tunnel_secret = $tunnelSecret
    } | ConvertTo-Json -Compress)
    $created = Invoke-RestMethod -Uri $base -Headers $headers -Method Post -Body $body
    if (-not $created.success) {
        throw ("Create tunnel failed: " + ($created | ConvertTo-Json -Compress))
    }
    $tunnelId = [string]$created.result.id
    Write-Host "Created tunnel id=$tunnelId"
}

$credDir = Join-Path $env:USERPROFILE ".cloudflared"
New-Item -ItemType Directory -Path $credDir -Force | Out-Null
$credFile = Join-Path $credDir ($tunnelId + ".json")
$tokenFile = Join-Path $credDir ($tunnelId + ".token")

if ($tunnelSecret) {
    $json = '{"AccountTag":"' + $accountId + '","TunnelSecret":"' + $tunnelSecret + '","TunnelID":"' + $tunnelId + '"}'
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($credFile, $json, $utf8)
} elseif ($tunnelToken) {
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($tokenFile, $tunnelToken, $utf8)
    Write-Host "Stored tunnel token for cloudflared run --token"
}

$tunnelCfgPath = [string]$cfg.tunnelConfigPath
$cfgLines = @(
    "# Auto-written by Provision-CloudflareTunnel.ps1",
    ("tunnel: " + $tunnelId),
    ("credentials-file: " + $credFile),
    "",
    "ingress:",
    "  - hostname: ava.rootmc.net",
    "    service: http://127.0.0.1:8787",
    "  - hostname: api-local.rootmc.net",
    "    service: http://127.0.0.1:8791",
    "  - hostname: api2-local.rootmc.net",
    "    service: http://127.0.0.1:8791",
    "  - hostname: map-local.rootmc.net",
    "    service: http://127.0.0.1:8791",
    "  - hostname: site-local.rootmc.net",
    "    service: http://127.0.0.1:8790",
    "  - service: http_status:404"
)
Set-Content -LiteralPath $tunnelCfgPath -Value $cfgLines -Encoding UTF8
Write-Host "Wrote $tunnelCfgPath"

$envFile = Join-Path $ws ".env"
Set-EnvFileKey -Path $envFile -Key "ROOTMC_TUNNEL_ID" -Value $tunnelId
$env:ROOTMC_TUNNEL_ID = $tunnelId

if ($zoneId) {
    $target = $tunnelId + ".cfargotunnel.com"
    $names = @(
        "ava.rootmc.net",
        "api-local.rootmc.net",
        "api2-local.rootmc.net",
        "map-local.rootmc.net",
        "site-local.rootmc.net"
    )
    foreach ($name in $names) {
        $existing = Invoke-RestMethod -Uri ("https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records?name=$name") -Headers $headers -Method Get
        $bodyObj = (@{
            type = "CNAME"
            name = $name
            content = $target
            proxied = $true
            ttl = 1
            comment = "rootmc-local-edge"
        } | ConvertTo-Json -Compress)
        if ($existing.result -and $existing.result.Count -gt 0) {
            $id = $existing.result[0].id
            Invoke-RestMethod -Method Put -Uri ("https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records/$id") -Headers $headers -Body $bodyObj | Out-Null
            Write-Host "Updated DNS $name -> $target"
        } else {
            Invoke-RestMethod -Method Post -Uri ("https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records") -Headers $headers -Body $bodyObj | Out-Null
            Write-Host "Created DNS $name -> $target"
        }
    }
} else {
    Write-Warning "CLOUDFLARE_ZONE_ID missing - tunnel created but staging DNS not set."
}

$tokenOut = Join-Path $cfg.stateDir "tunnel-run.json"
$runInfo = @{
    at = (Get-Date).ToUniversalTime().ToString("o")
    tunnelId = $tunnelId
    credentialsFile = $credFile
    hasSecretJson = (Test-Path -LiteralPath $credFile)
    tokenFile = $(if (Test-Path -LiteralPath $tokenFile) { $tokenFile } else { $null })
}
$runInfo | ConvertTo-Json | Set-Content -LiteralPath $tokenOut -Encoding UTF8

Write-Host "Tunnel provisioned: $tunnelId"
