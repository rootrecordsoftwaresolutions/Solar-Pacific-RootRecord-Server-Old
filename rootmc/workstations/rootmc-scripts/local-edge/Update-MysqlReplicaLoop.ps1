# Continuous Claims/Towny Shockbyte -> local MySQL replica for Root-Core-Node / local-edge.
# Full DB copy each cycle (mcMMO, votes, playtime, economy, shops, Towny tables, etc.).
#
# Usage:
#   powershell -File Update-MysqlReplicaLoop.ps1
#   powershell -File Update-MysqlReplicaLoop.ps1 -Once
#   powershell -File Update-MysqlReplicaLoop.ps1 -IntervalMinutes 10

param(
    [string]$WorkspaceRoot = "D:\.1 Work Stations\RootMC",
    [int]$IntervalMinutes = 15,
    [switch]$Once
)

$ErrorActionPreference = "Continue"
$EdgeRoot = $PSScriptRoot
$syncScript = Join-Path $EdgeRoot "Sync-RemoteMySQLToLocal.ps1"
$stateDir = Join-Path $env:LOCALAPPDATA "RootMC\local-edge\state"
$lockFile = Join-Path $stateDir "mysql_replica.lock"
$statusFile = Join-Path $stateDir "mysql_replica_status.json"
New-Item -ItemType Directory -Force -Path $stateDir | Out-Null

if ($IntervalMinutes -lt 5) { $IntervalMinutes = 5 }

function Write-ReplicaStatus {
    param([hashtable]$Obj)
    $Obj.checked_at = [DateTime]::UtcNow.ToString("o")
    ($Obj | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $statusFile -Encoding utf8
}

function Invoke-ReplicaCycle {
    if (-not (Test-Path -LiteralPath $syncScript)) {
        Write-ReplicaStatus @{
            ok = $false
            error = "missing Sync-RemoteMySQLToLocal.ps1"
            running = $false
        }
        Write-Warning "Missing $syncScript"
        return $false
    }

    if (Test-Path -LiteralPath $lockFile) {
        try {
            $age = (Get-Date) - (Get-Item -LiteralPath $lockFile).LastWriteTime
            if ($age.TotalMinutes -lt ($IntervalMinutes + 30)) {
                Write-Host "Replica already running (lock age $([int]$age.TotalMinutes)m) - skip"
                return $false
            }
            Remove-Item -LiteralPath $lockFile -Force -ErrorAction SilentlyContinue
        } catch { }
    }

    Set-Content -LiteralPath $lockFile -Value (Get-Date).ToString("o") -Encoding ascii
    Write-ReplicaStatus @{
        ok = $true
        running = $true
        phase = "sync"
        message = "Pulling Claims + Towny (full DB incl. mcMMO/votes/playtime/economy)..."
    }
    Write-Host ("[{0}] MySQL replica sync starting..." -f (Get-Date -Format "HH:mm:ss"))

    try {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $syncScript -WorkspaceRoot $WorkspaceRoot
        $code = $LASTEXITCODE
        if ($null -eq $code) { $code = 0 }
        if ($code -ne 0) {
            Write-ReplicaStatus @{
                ok = $false
                running = $false
                phase = "failed"
                exit_code = $code
                message = "Sync-RemoteMySQLToLocal exited $code"
            }
            Write-Warning "Replica sync failed exit=$code"
            return $false
        }
        # Sync script also writes detailed status; mark loop idle
        if (Test-Path -LiteralPath $statusFile) {
            try {
                $j = Get-Content -LiteralPath $statusFile -Raw | ConvertFrom-Json
                $j | Add-Member -NotePropertyName running -NotePropertyValue $false -Force
                $j | Add-Member -NotePropertyName phase -NotePropertyValue "idle" -Force
                ($j | ConvertTo-Json -Depth 8) | Set-Content -LiteralPath $statusFile -Encoding utf8
            } catch { }
        }
        Write-Host ("[{0}] MySQL replica sync OK" -f (Get-Date -Format "HH:mm:ss"))
        return $true
    } catch {
        Write-ReplicaStatus @{
            ok = $false
            running = $false
            phase = "error"
            message = $_.Exception.Message
        }
        Write-Warning $_
        return $false
    } finally {
        Remove-Item -LiteralPath $lockFile -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "RootMC MySQL replica loop interval=${IntervalMinutes}m Once=$Once"
Write-Host "Status: $statusFile"

while ($true) {
    [void](Invoke-ReplicaCycle)
    if ($Once) { break }
    Start-Sleep -Seconds ($IntervalMinutes * 60)
}
