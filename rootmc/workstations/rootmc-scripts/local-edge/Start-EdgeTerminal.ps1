# Local edge terminal status UI (console + tiny HTTP /status for operators).

param(
    [string]$ConfigPath = "",
    [int]$HttpPort = 18792,
    [switch]$ConsoleOnly = $true
)

$ErrorActionPreference = "Continue"
$EdgeRoot = $PSScriptRoot
if (-not $ConfigPath) { $ConfigPath = Join-Path $EdgeRoot "config.json" }
. (Join-Path $EdgeRoot "lib\Preference.ps1")

try { $Host.UI.RawUI.WindowTitle = "RootMC Local Edge" } catch { }

function Get-CacheStats([string]$cacheDir) {
    $p = Join-Path $cacheDir "_stats.json"
    if (-not (Test-Path $p)) {
        return @{ hits = 0; misses = 0; bytes = 0; entries = 0; oldest = $null }
    }
    return Get-Content $p -Raw | ConvertFrom-Json
}

function Get-StatusObject($cfg) {
    $pref = Read-PreferenceState -StateDir $cfg.stateDir
    if (-not $pref) {
        $pref = [pscustomobject]@{
            preference            = "unknown"
            reason                = "no_state"
            local_health          = "unknown"
            schedule_allows_local = $false
        }
    }
    $cache = Get-CacheStats $cfg.cacheDir
    $hitRate = 0
    $total = [int]$cache.hits + [int]$cache.misses
    if ($total -gt 0) {
        $hitRate = [math]::Round(100.0 * [int]$cache.hits / $total, 1)
    }
    $forceFile = Join-Path $cfg.stateDir "force_mode.txt"
    return [ordered]@{
        service    = "rootmc-local-edge-terminal"
        preference = $pref
        cache      = @{
            hits     = $cache.hits
            misses   = $cache.misses
            hit_rate = $hitRate
            bytes    = $cache.bytes
            entries  = $cache.entries
            oldest   = $cache.oldest
        }
        ports      = $cfg.ports
        checked_at = [DateTime]::UtcNow.ToString("o")
        force_file = $forceFile
        controls   = @{
            force_local      = "Set-Content force_mode.txt local"
            force_cloudflare = "Set-Content force_mode.txt cloudflare"
            auto             = "Set-Content force_mode.txt auto"
        }
    }
}

function Write-ConsoleStatus($status) {
    Clear-Host
    Write-Host "=== RootMC Local Edge Terminal ===" -ForegroundColor Cyan
    Write-Host ("Preference : {0} ({1})" -f $status.preference.preference, $status.preference.reason)
    Write-Host ("Health     : {0}" -f $status.preference.local_health)
    Write-Host ("Schedule   : allows_local={0}" -f $status.preference.schedule_allows_local)
    Write-Host ("Cache      : hits={0} misses={1} hit_rate={2}% bytes={3}" -f `
            $status.cache.hits, $status.cache.misses, $status.cache.hit_rate, $status.cache.bytes)
    Write-Host ("Oldest     : {0}" -f $status.cache.oldest)
    Write-Host ""
    Write-Host ("Gateway    : http://127.0.0.1:{0}/__edge/health" -f $status.ports.gateway)
    Write-Host ("Site       : http://127.0.0.1:{0}/" -f $status.ports.site)
    Write-Host ("API        : http://127.0.0.1:{0}/health" -f $status.ports.api)
    Write-Host ""
    Write-Host ("Force: write local|cloudflare|auto to {0}" -f $status.force_file)
    Write-Host ("JSON status: http://127.0.0.1:{0}/__edge/status" -f $status.ports.gateway)
    Write-Host "Ctrl+C to stop this window (use Stop-LocalEdge.ps1 to stop backends)."
}

$listener = $null
if (-not $ConsoleOnly) {
    try {
        $listener = New-Object System.Net.HttpListener
        $listener.Prefixes.Add(("http://127.0.0.1:{0}/" -f $HttpPort))
        $listener.Start()
    } catch {
        Write-Warning ("HTTP terminal bind failed: {0} - console only" -f $_.Exception.Message)
        $listener = $null
    }
}

while ($true) {
    $cfg = Get-LocalEdgeConfig -ConfigPath $ConfigPath
    $status = Get-StatusObject $cfg
    Write-ConsoleStatus $status

    if ($listener) {
        $deadline = [DateTime]::UtcNow.AddSeconds(3)
        while (([DateTime]::UtcNow -lt $deadline) -and $listener.IsListening) {
            if (-not $listener.Pending) {
                Start-Sleep -Milliseconds 100
                continue
            }
            $ctx = $listener.GetContext()
            $path = $ctx.Request.Url.AbsolutePath
            $resp = $ctx.Response
            try {
                if (($path -eq "/status") -or ($path -eq "/")) {
                    $json = ($status | ConvertTo-Json -Depth 8)
                    $bytes = [Text.Encoding]::UTF8.GetBytes($json)
                    $resp.ContentType = "application/json; charset=utf-8"
                    $resp.Headers["Cache-Control"] = "no-store"
                    $resp.StatusCode = 200
                    $resp.OutputStream.Write($bytes, 0, $bytes.Length)
                } elseif (($path -eq "/force/local") -or ($path -eq "/force/cloudflare") -or ($path -eq "/force/auto")) {
                    $mode = $path.Split("/")[-1]
                    Set-Content -LiteralPath (Join-Path $cfg.stateDir "force_mode.txt") -Value $mode -Encoding UTF8
                    $payload = @{ ok = $true; mode = $mode } | ConvertTo-Json
                    $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
                    $resp.ContentType = "application/json; charset=utf-8"
                    $resp.StatusCode = 200
                    $resp.OutputStream.Write($bytes, 0, $bytes.Length)
                } else {
                    $resp.StatusCode = 404
                }
            } finally {
                $resp.Close()
            }
            $status = Get-StatusObject $cfg
        }
    } else {
        Start-Sleep -Seconds 3
    }
}
