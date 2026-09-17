# Start RootMC local-edge stack (api, api2, map, site, gateway, preference loop).
# Does NOT change production DNS. Use Invoke-EdgeCutover.ps1 for that.

param(
    [string]$ConfigPath = "",
    [switch]$SkipWrangler,
    [switch]$SkipTunnel,
    [switch]$SkipTerminal
)

$ErrorActionPreference = "Stop"
$EdgeRoot = $PSScriptRoot
if (-not $ConfigPath) { $ConfigPath = Join-Path $EdgeRoot "config.json" }

. (Join-Path $EdgeRoot "lib\Preference.ps1")
$cfg = Get-LocalEdgeConfig -ConfigPath $ConfigPath

foreach ($d in @($cfg.persistDir, $cfg.cacheDir, $cfg.stateDir, (Join-Path $EdgeRoot "logs"))) {
    if (-not (Test-Path -LiteralPath $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null }
}

$stateDir = [string]$cfg.stateDir
$pidFile = Join-Path $stateDir "pids.json"
$logDir = Join-Path $EdgeRoot "logs"
$ws = [string]$cfg.workspaceRoot

function Import-WorkspaceEnv {
    $envPath = Join-Path $ws ".env"
    if (-not (Test-Path -LiteralPath $envPath)) { return }
    Get-Content -LiteralPath $envPath | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $i = $line.IndexOf("=")
        if ($i -lt 1) { return }
        $k = $line.Substring(0, $i).Trim()
        $v = $line.Substring($i + 1).Trim()
        if ($k -and $v -and -not [Environment]::GetEnvironmentVariable($k)) {
            Set-Item -Path "Env:$k" -Value $v
        }
    }
}

function Set-HyperdriveLocalFromDatabaseYml {
    param([string]$YmlPath)
    if ($env:CLOUDFLARE_HYPERDRIVE_LOCAL_CONNECTION_STRING_ROOTMC_MYSQL) { return $true }
    if (-not (Test-Path -LiteralPath $YmlPath)) { return $false }
    $hostName = $null; $port = "3306"; $db = $null; $user = $null; $pass = $null
    Get-Content -LiteralPath $YmlPath | ForEach-Object {
        if ($_ -match '^\s*host:\s*(.+)\s*$') { $hostName = $Matches[1].Trim().Trim('"') }
        if ($_ -match '^\s*port:\s*(\d+)\s*$') { $port = $Matches[1] }
        if ($_ -match '^\s*database:\s*(.+)\s*$') { $db = $Matches[1].Trim().Trim('"') }
        if ($_ -match '^\s*username:\s*(.+)\s*$') { $user = $Matches[1].Trim().Trim('"') }
        if ($_ -match '^\s*password:\s*"?([^"]+)"?\s*$') { $pass = $Matches[1] }
    }
    if (-not ($hostName -and $db -and $user -and $pass)) { return $false }
    $eu = [Uri]::EscapeDataString($user)
    $ep = [Uri]::EscapeDataString($pass)
    $conn = "mysql://${eu}:${ep}@${hostName}:${port}/${db}"
    $env:CLOUDFLARE_HYPERDRIVE_LOCAL_CONNECTION_STRING_ROOTMC_MYSQL = $conn
    Write-Host "  Hyperdrive local MySQL string loaded from database.yml ($hostName/$db)"
    return $true
}

Import-WorkspaceEnv
$townyDb = Join-Path $ws "2. RootMC - Towny\plugins\RootMC\database.yml"
$claimsDb = Join-Path $ws "1. RootMC - Claims\plugins\RootMC\database.yml"
if (-not (Set-HyperdriveLocalFromDatabaseYml $townyDb)) {
    [void](Set-HyperdriveLocalFromDatabaseYml $claimsDb)
}
if (-not $env:CLOUDFLARE_HYPERDRIVE_LOCAL_CONNECTION_STRING_ROOTMC_MYSQL) {
    Write-Warning "No Hyperdrive local connection string - wrangler may refuse to start. Set CLOUDFLARE_HYPERDRIVE_LOCAL_CONNECTION_STRING_ROOTMC_MYSQL or ensure database.yml is present."
}

# Seed wrangler local secrets so Discord link (complete-by-code) works without CF Workers.
function Sync-WranglerDevVars {
    param([string]$ApiDir)
    $devVars = Join-Path $ApiDir ".dev.vars"
    $keys = @(
        "DISCORD_ROOTMC_BOT_TOKEN",
        "DISCORD_ROOTMC_CLIENT_SECRET",
        "JWT_SECRET",
        "ROOTMC_EDGE_SIGNING_KEY",
        "ROOTMC_INTERNAL_API_KEY",
        "ROOTMC_DEV_WORKSTATION_KEY"
    )
    $map = @{}
    if (Test-Path -LiteralPath $devVars) {
        Get-Content -LiteralPath $devVars | ForEach-Object {
            $line = $_.Trim()
            if (-not $line -or $line.StartsWith("#")) { return }
            $i = $line.IndexOf("=")
            if ($i -lt 1) { return }
            $map[$line.Substring(0, $i).Trim()] = $line.Substring($i + 1).Trim()
        }
    }
    $changed = $false
    foreach ($k in $keys) {
        $v = [Environment]::GetEnvironmentVariable($k)
        if ($v -and $map[$k] -ne $v) { $map[$k] = $v; $changed = $true }
    }
    # Prefer staging public URLs when tunnel hostnames are used; else local site for verify links.
    if (-not $map.ContainsKey("SITE_URL") -or -not $map["SITE_URL"]) {
        $map["SITE_URL"] = if ($env:ROOTMC_SITE_URL) { $env:ROOTMC_SITE_URL } else { "http://127.0.0.1:$($cfg.ports.site)" }
        $changed = $true
    }
    if (-not $map.ContainsKey("DISCORD_ROOTMC_OAUTH_REDIRECT_URI") -or -not $map["DISCORD_ROOTMC_OAUTH_REDIRECT_URI"]) {
        $map["DISCORD_ROOTMC_OAUTH_REDIRECT_URI"] = if ($env:DISCORD_ROOTMC_OAUTH_REDIRECT_URI) {
            $env:DISCORD_ROOTMC_OAUTH_REDIRECT_URI
        } else {
            "https://api-local.rootmc.net/v1/discord/rootmc/callback"
        }
        $changed = $true
    }
    if ($changed -or -not (Test-Path -LiteralPath $devVars)) {
        $lines = @("# Auto-synced from workspace .env by Start-LocalEdge.ps1 - do not commit")
        foreach ($k in ($map.Keys | Sort-Object)) {
            $lines += "$k=$($map[$k])"
        }
        Set-Content -LiteralPath $devVars -Value $lines -Encoding UTF8
        Write-Host "  Synced wrangler .dev.vars ($ApiDir) for Discord/local link"
    }
}
Sync-WranglerDevVars -ApiDir (Join-Path $ws "Web Files\rootmc-api")

# Idempotent: if a prior stack is still alive, reuse it.
function Test-PortListen([int]$Port) {
    try {
        $c = New-Object Net.Sockets.TcpClient
        $iar = $c.BeginConnect("127.0.0.1", $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(400)
        if (-not $ok) { try { $c.Close() } catch {}; return $false }
        $c.EndConnect($iar)
        $c.Close()
        return $true
    } catch { return $false }
}

$portsBusy = @([int]$cfg.ports.api, [int]$cfg.ports.gateway) | Where-Object { Test-PortListen $_ }

if (Test-Path -LiteralPath $pidFile) {
    $existing = @(Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json)
    $alive = @()
    foreach ($p in $existing) {
        try {
            $proc = Get-Process -Id ([int]$p.pid) -ErrorAction Stop
            if ($proc) { $alive += $p }
        } catch { }
    }
    if ($alive.Count -gt 0 -or $portsBusy.Count -ge 2) {
        Write-Host "Local edge already running (pids=$($alive.Count) busyPorts=$($portsBusy -join ',')) - skipping restart."
        if (-not $SkipTerminal) {
            $terminal = Join-Path $EdgeRoot "Start-EdgeTerminal.ps1"
            Start-Process -FilePath "powershell.exe" -WorkingDirectory $EdgeRoot -ArgumentList @(
                "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $terminal,
                "-ConfigPath", $ConfigPath, "-HttpPort", "$($cfg.ports.terminalApi)"
            ) -WindowStyle Normal
        }
        exit 0
    }
} elseif ($portsBusy.Count -ge 2) {
    Write-Host "Local edge ports already listening ($($portsBusy -join ',')) - skipping restart (stale pids.json)."
    exit 0
}

function Start-LoggedProcess {
    param(
        [string]$Name,
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory
    )
    $outLog = Join-Path $logDir "$Name.out.log"
    $errLog = Join-Path $logDir "$Name.err.log"
    # Quote each arg so paths with spaces survive Start-Process.
    $argLine = ($ArgumentList | ForEach-Object {
        $a = [string]$_
        if ($a -match '[\s"]') { '"' + ($a.Replace('"', '\"')) + '"' } else { $a }
    }) -join " "
    $p = Start-Process -FilePath $FilePath -ArgumentList $argLine `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -PassThru -WindowStyle Hidden
    return @{ name = $Name; pid = $p.Id; log = $outLog }
}

$procs = @()

# Seed preference local while starting
$seed = Resolve-ConnectionPreference -Config $cfg -LocalHealth "starting" -FailStreak 0
Write-PreferenceState -StateDir $stateDir -PreferenceObject $seed | Out-Null

if (-not $SkipWrangler) {
    $apiDir = Join-Path $ws "Web Files\rootmc-api"
    $mapDir = Join-Path $ws "Web Files\rootmc-minecraft-map"
    $persist = [string]$cfg.persistDir

    $npxPrefix = @()
    $npxCmd = Get-Command npx.cmd -ErrorAction SilentlyContinue
    if (-not $npxCmd) { $npxCmd = Get-Command npx -ErrorAction SilentlyContinue }
    if (-not $npxCmd) { throw "npx not found on PATH" }
    $npx = $npxCmd.Source
    if ($npx -notmatch '\.(cmd|bat|exe)$') {
        $npxCmd2 = Get-Command npx.cmd -ErrorAction SilentlyContinue
        if ($npxCmd2) { $npx = $npxCmd2.Source }
        else {
            $npx = "cmd.exe"
            $npxPrefix = @("/c", "npx")
        }
    }

    $procs += Start-LoggedProcess -Name "api" -FilePath $npx -WorkingDirectory $apiDir -ArgumentList ($npxPrefix + @(
        "wrangler", "dev", "--local", "--persist-to", (Join-Path $persist "api"),
        "--ip", "127.0.0.1", "--port", "$($cfg.ports.api)"
    ))
    # Gen 2 / api2 retired — Claims uses main api (rootmc).
    $procs += Start-LoggedProcess -Name "map" -FilePath $npx -WorkingDirectory $mapDir -ArgumentList ($npxPrefix + @(
        "wrangler", "dev", "--local", "--persist-to", (Join-Path $persist "map"),
        "--ip", "127.0.0.1", "--port", "$($cfg.ports.map)"
    ))
}

$nodeCmd = Get-Command node -ErrorAction SilentlyContinue
if (-not $nodeCmd) { throw "node not found on PATH" }
$node = $nodeCmd.Source

$env:ROOTMC_EDGE_CONFIG = $ConfigPath
$procs += Start-LoggedProcess -Name "gateway" -FilePath $node -WorkingDirectory (Join-Path $EdgeRoot "gateway") -ArgumentList @("server.mjs")
$procs += Start-LoggedProcess -Name "site" -FilePath $node -WorkingDirectory $EdgeRoot -ArgumentList @("site-server.mjs")
$procs += Start-LoggedProcess -Name "discord-link-bot" -FilePath $node -WorkingDirectory $EdgeRoot -ArgumentList @("discord-link-bot.mjs")

$prefLoop = Join-Path $EdgeRoot "Update-PreferenceLoop.ps1"
$procs += Start-LoggedProcess -Name "preference" -FilePath "powershell.exe" -WorkingDirectory $EdgeRoot -ArgumentList @(
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $prefLoop, "-ConfigPath", $ConfigPath
)

$mysqlLoop = Join-Path $EdgeRoot "Update-MysqlReplicaLoop.ps1"
$mysqlInterval = 15
try {
    if ($null -ne $cfg.mysqlReplicaIntervalMinutes) { $mysqlInterval = [int]$cfg.mysqlReplicaIntervalMinutes }
} catch { }
if ($mysqlInterval -lt 5) { $mysqlInterval = 5 }
$procs += Start-LoggedProcess -Name "mysql-replica" -FilePath "powershell.exe" -WorkingDirectory $EdgeRoot -ArgumentList @(
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $mysqlLoop,
    "-WorkspaceRoot", $ws, "-IntervalMinutes", "$mysqlInterval"
)
Write-Host "MySQL replica loop every ${mysqlInterval}m (Claims+Towny full DB -> local :3307)"

if (-not $SkipTerminal) {
    $terminal = Join-Path $EdgeRoot "Start-EdgeTerminal.ps1"
    # Visible operator window (not redirected / hidden)
    $tp = Start-Process -FilePath "powershell.exe" -WorkingDirectory $EdgeRoot -ArgumentList @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-NoExit", "-File", $terminal,
        "-ConfigPath", $ConfigPath, "-HttpPort", "$($cfg.ports.terminalApi)"
    ) -PassThru -WindowStyle Normal
    $procs += @{ name = "terminal"; pid = $tp.Id; log = "" }
}

if (-not $SkipTunnel) {
    $cloudflared = Get-Command cloudflared -ErrorAction SilentlyContinue
    $tunnelCfg = [string]$cfg.tunnelConfigPath
    $tunnelReady = $false
    if ($cloudflared -and (Test-Path -LiteralPath $tunnelCfg)) {
        $raw = Get-Content -LiteralPath $tunnelCfg -Raw
        if ($raw -notmatch 'REPLACE_WITH_TUNNEL_ID') { $tunnelReady = $true }
    }
    if ($tunnelReady) {
        $procs += Start-LoggedProcess -Name "tunnel" -FilePath $cloudflared.Source -WorkingDirectory $EdgeRoot -ArgumentList @(
            "tunnel", "--config", $tunnelCfg, "run"
        )
    } else {
        Write-Warning "Tunnel skipped (cloudflared missing, or config.yml still has REPLACE_WITH_TUNNEL_ID). See cloudflared/README.md"
    }
}

($procs | ConvertTo-Json -Depth 4) | Set-Content -LiteralPath $pidFile -Encoding UTF8
Write-Host "Local edge started. PIDs -> $pidFile"
Write-Host "Gateway: http://127.0.0.1:$($cfg.ports.gateway)/__edge/health"
Write-Host "Site:    http://127.0.0.1:$($cfg.ports.site)/"
Write-Host "API:     http://127.0.0.1:$($cfg.ports.api)/health"
Write-Host "Terminal status: http://127.0.0.1:$($cfg.ports.terminalApi)/status"
