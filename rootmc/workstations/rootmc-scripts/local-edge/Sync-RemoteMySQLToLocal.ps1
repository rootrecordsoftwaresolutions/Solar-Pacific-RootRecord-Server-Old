# Pull full MySQL snapshots from Claims + Towny Shockbyte hosts into local :3307.
# Creates/replaces local schemas: rootmc_claims, rootmc_towny
# ROOTMC DEV schema (rootmc_dev) is separate â€” use Ensure-RootMcDevMysql.ps1.
# Does NOT change live server database.yml / peer configs.
#
# Usage:
#   powershell -File Sync-RemoteMySQLToLocal.ps1
#   powershell -File Sync-RemoteMySQLToLocal.ps1 -ClaimsOnly
#   powershell -File Sync-RemoteMySQLToLocal.ps1 -TownyOnly

param(
    [switch]$ClaimsOnly,
    [switch]$TownyOnly,
    [string]$WorkspaceRoot = "D:\.1 Work Stations\RootMC",
    [string]$MysqlBin = "C:\mysql84\bin",
    [string]$DumpDir = ""
)

$ErrorActionPreference = "Stop"

function Read-DatabaseYml {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { throw "Missing $Path" }
    $map = @{}
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        if ($line -match '^(host|port|database|username|password)\s*:\s*(.+)$') {
            $k = $Matches[1]
            $v = $Matches[2].Trim().Trim('"').Trim("'")
            $map[$k] = $v
        }
    }
    foreach ($req in @("host", "port", "database", "username", "password")) {
        if (-not $map[$req]) { throw "database.yml missing $req in $Path" }
    }
    return [pscustomobject]$map
}

function Read-LocalMysqlCreds {
    $boot = Join-Path $WorkspaceRoot "scripts\local-edge\state\mysql-bootstrap.env"
    if (-not (Test-Path -LiteralPath $boot)) {
        throw "Missing $boot - run Install-LocalMySQL.ps1 first"
    }
    $map = @{}
    Get-Content -LiteralPath $boot | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $i = $line.IndexOf("=")
        if ($i -lt 1) { return }
        $map[$line.Substring(0, $i).Trim()] = $line.Substring($i + 1).Trim()
    }
    return [pscustomobject]@{
        Host = if ($map.ROOTMC_LOCAL_MYSQL_HOST) { $map.ROOTMC_LOCAL_MYSQL_HOST } else { "127.0.0.1" }
        Port = if ($map.ROOTMC_LOCAL_MYSQL_PORT) { [int]$map.ROOTMC_LOCAL_MYSQL_PORT } else { 3307 }
        User = "root"
        Password = $map.ROOTMC_LOCAL_MYSQL_ROOT_PASSWORD
    }
}

function New-MysqlCnf {
    param($HostName, $Port, $User, $Password, $Suffix)
    $path = Join-Path $env:TEMP ("rootmc-mysql-" + $Suffix + ".cnf")
    $safePass = ($Password -replace '\\', '\\') -replace '"', '\"'
    @(
        "[client]"
        "host=$HostName"
        "port=$Port"
        "user=$User"
        "password=$safePass"
        "protocol=TCP"
    ) | Set-Content -LiteralPath $path -Encoding ASCII
    return $path
}

function Sync-OneRemote {
    param(
        [string]$Label,
        [string]$LocalSchema,
        [string]$DatabaseYmlPath,
        $LocalCred,
        [string]$OutDir
    )
    $remote = Read-DatabaseYml -Path $DatabaseYmlPath
    Write-Host ""
    Write-Host "=== $Label ===" -ForegroundColor Cyan
    Write-Host "Remote: $($remote.host):$($remote.port) / $($remote.database)"
    Write-Host "Local schema: $LocalSchema"

    $remoteCnf = New-MysqlCnf -HostName $remote.host -Port $remote.port -User $remote.username -Password $remote.password -Suffix "remote-$Label"
    $localCnf = New-MysqlCnf -HostName $LocalCred.Host -Port $LocalCred.Port -User $LocalCred.User -Password $LocalCred.Password -Suffix "local-$Label"
    $dumpFile = Join-Path $OutDir ("$LocalSchema-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".sql")
    $mysqlPath = Join-Path $MysqlBin "mysql.exe"
    $dumpPath = Join-Path $MysqlBin "mysqldump.exe"

    try {
        Write-Host "Dumping remote (can take several minutes)..."
        $errDump = Join-Path $OutDir "$LocalSchema-dump.err.log"
        $dumpCmd = "`"$dumpPath`" --defaults-extra-file=`"$remoteCnf`" --single-transaction --quick --routines --triggers --events --set-gtid-purged=OFF --column-statistics=0 --hex-blob --no-tablespaces `"$($remote.database)`" > `"$dumpFile`" 2> `"$errDump`""
        cmd.exe /c $dumpCmd
        if ($LASTEXITCODE -ne 0) {
            Get-Content $errDump -ErrorAction SilentlyContinue | Select-Object -Last 20
            throw "mysqldump failed exit=$LASTEXITCODE for $Label"
        }
        if (-not (Test-Path $dumpFile) -or (Get-Item $dumpFile).Length -lt 100) {
            Get-Content $errDump -ErrorAction SilentlyContinue | Select-Object -Last 20
            throw "mysqldump produced empty file for $Label"
        }
        $sizeMb = [Math]::Round((Get-Item $dumpFile).Length / 1MB, 2)
        Write-Host ("Dump OK: {0} ({1} MB)" -f $dumpFile, $sizeMb)

        Write-Host "Recreating local schema $LocalSchema..."
        & $mysqlPath --defaults-extra-file=$localCnf -e "DROP DATABASE IF EXISTS ``$LocalSchema``; CREATE DATABASE ``$LocalSchema`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
        if ($LASTEXITCODE -ne 0) { throw "Failed to recreate local schema $LocalSchema" }

        # phpMyAdmin logs in as rootmc_edge â€” grant after each recreate (DROP removes schema ACLs).
        $edgeUser = "rootmc_edge"
        & $mysqlPath --defaults-extra-file=$localCnf -e @"
GRANT ALL PRIVILEGES ON ``$LocalSchema``.* TO '$edgeUser'@'127.0.0.1';
GRANT ALL PRIVILEGES ON ``$LocalSchema``.* TO '$edgeUser'@'localhost';
GRANT ALL PRIVILEGES ON ``rootmc_network``.* TO '$edgeUser'@'127.0.0.1';
GRANT ALL PRIVILEGES ON ``rootmc_network``.* TO '$edgeUser'@'localhost';
FLUSH PRIVILEGES;
"@

        Write-Host "Importing into local :$($LocalCred.Port)/$LocalSchema..."
        $importErr = Join-Path $OutDir "$LocalSchema-import.err.log"
        $importCmd = "`"$mysqlPath`" --defaults-extra-file=`"$localCnf`" `"$LocalSchema`" < `"$dumpFile`" 2> `"$importErr`""
        cmd.exe /c $importCmd
        if ($LASTEXITCODE -ne 0) {
            Get-Content $importErr -ErrorAction SilentlyContinue | Select-Object -Last 30
            throw "mysql import failed exit=$LASTEXITCODE for $Label"
        }

        $tables = & $mysqlPath --defaults-extra-file=$localCnf -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$LocalSchema';"
        Write-Host ("Import OK - {0} tables={1}" -f $LocalSchema, $tables) -ForegroundColor Green

        $serverId = switch ($Label) {
            "claims" { "4963895e-0964-48b8-81b7-1f40a966e8be" }
            "towny" { "15bbc057-4f8b-4761-abdb-7b7e4d9c7512" }
            default { $null }
        }
        if ($serverId) {
            $remoteHost = [string]$remote.host
            & $mysqlPath --defaults-extra-file=$localCnf -e @"
CREATE DATABASE IF NOT EXISTS rootmc_network;
CREATE TABLE IF NOT EXISTS rootmc_network.rootmc_network_servers (
  server_id varchar(64) NOT NULL PRIMARY KEY,
  display_name varchar(128) DEFAULT NULL,
  host_mysql_host varchar(255) DEFAULT NULL,
  last_snapshot_at datetime DEFAULT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
UPDATE rootmc_network.rootmc_network_servers
SET last_snapshot_at = UTC_TIMESTAMP(),
    updated_at = UTC_TIMESTAMP(),
    host_mysql_host = '$remoteHost'
WHERE server_id = '$serverId';
"@
        }

        return [pscustomobject]@{
            Label  = $Label
            Schema = $LocalSchema
            Tables = $tables
            Dump   = $dumpFile
            SizeMb = $sizeMb
        }
    } finally {
        Remove-Item $remoteCnf, $localCnf -Force -ErrorAction SilentlyContinue
    }
}

$mysqlExe = Join-Path $MysqlBin "mysql.exe"
$dumpExe = Join-Path $MysqlBin "mysqldump.exe"
if (-not (Test-Path $mysqlExe)) { throw "mysql.exe not found: $mysqlExe" }
if (-not (Test-Path $dumpExe)) { throw "mysqldump.exe not found: $dumpExe" }

$local = Read-LocalMysqlCreds
if (-not $local.Password) { throw "ROOTMC_LOCAL_MYSQL_ROOT_PASSWORD missing" }

try {
    $c = New-Object Net.Sockets.TcpClient
    $c.Connect($local.Host, [int]$local.Port)
    $c.Close()
} catch {
    throw "Local MySQL not reachable on $($local.Host):$($local.Port) - Start MySQL from Root-Core-Node first"
}

if (-not $DumpDir) {
    $DumpDir = Join-Path $env:LOCALAPPDATA "RootMC\local-edge\mysql-snapshots"
}
New-Item -ItemType Directory -Force -Path $DumpDir | Out-Null

$doClaims = -not $TownyOnly
$doTowny = -not $ClaimsOnly
$results = @()

if ($doClaims) {
    $results += Sync-OneRemote -Label "claims" -LocalSchema "rootmc_claims" `
        -DatabaseYmlPath (Join-Path $WorkspaceRoot "1. RootMC - Claims\plugins\RootMC\database.yml") `
        -LocalCred $local -OutDir $DumpDir
}
if ($doTowny) {
    $results += Sync-OneRemote -Label "towny" -LocalSchema "rootmc_towny" `
        -DatabaseYmlPath (Join-Path $WorkspaceRoot "2. RootMC - Towny\plugins\RootMC\database.yml") `
        -LocalCred $local -OutDir $DumpDir
}

Write-Host ""
Write-Host "Done. Local schemas on :$($local.Port):" -ForegroundColor Green
$results | Format-Table Label, Schema, Tables, SizeMb -AutoSize
Write-Host "Dump files: $DumpDir"
Write-Host "Browse in phpMyAdmin: rootmc_claims / rootmc_towny"

# Dataset coverage report + status file for Root-Core-Node
$localCnfReport = New-MysqlCnf -HostName $local.Host -Port $local.Port -User $local.User -Password $local.Password -Suffix "report"
try {
    $coverage = @{}
    foreach ($schema in @("rootmc_claims", "rootmc_towny")) {
        $exists = & (Join-Path $MysqlBin "mysql.exe") --defaults-extra-file=$localCnfReport -N -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$schema';"
        if ("$exists" -ne "1") { continue }
        $patterns = @{
            mcmmo    = "table_name LIKE 'mcmmo_%'"
            votes    = "table_name LIKE '%vote%'"
            playtime = "table_name LIKE '%playtime%'"
            economy  = "(table_name LIKE '%econom%' OR table_name LIKE '%treasury%' OR table_name LIKE '%loan%' OR table_name LIKE '%wallet%' OR table_name LIKE '%shop%')"
            towny    = "table_name LIKE '%towny%'"
            rewards  = "table_name LIKE '%reward%'"
            players  = "(table_name LIKE '%player%' OR table_name LIKE '%rootstat%')"
        }
        $bucket = @{}
        foreach ($key in $patterns.Keys) {
            $n = & (Join-Path $MysqlBin "mysql.exe") --defaults-extra-file=$localCnfReport -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$schema' AND $($patterns[$key]);"
            $bucket[$key] = [int]$n
        }
        $total = & (Join-Path $MysqlBin "mysql.exe") --defaults-extra-file=$localCnfReport -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$schema';"
        $bucket["tables"] = [int]$total
        $coverage[$schema] = $bucket
        Write-Host ("Coverage {0}: tables={1} mcmmo={2} votes={3} playtime={4} economy/shops={5}" -f `
            $schema, $bucket.tables, $bucket.mcmmo, $bucket.votes, $bucket.playtime, $bucket.economy)
    }

    $statusDir = Join-Path $env:LOCALAPPDATA "RootMC\local-edge\state"
    New-Item -ItemType Directory -Force -Path $statusDir | Out-Null
    $status = [ordered]@{
        ok            = $true
        running       = $false
        phase         = "idle"
        message       = "Full Claims+Towny snapshot complete (mcMMO lives in rootmc_towny)"
        checked_at    = [DateTime]::UtcNow.ToString("o")
        local_port    = $local.Port
        schemas       = @($results | ForEach-Object { $_.Schema })
        results       = @($results | ForEach-Object {
            [ordered]@{
                label  = $_.Label
                schema = $_.Schema
                tables = $_.Tables
                size_mb = $_.SizeMb
            }
        })
        coverage      = $coverage
        next_hint     = "Automatic via Update-MysqlReplicaLoop.ps1 / Root-Core-Node"
    }
    ($status | ConvertTo-Json -Depth 8) | Set-Content -LiteralPath (Join-Path $statusDir "mysql_replica_status.json") -Encoding utf8
    Write-Host ("Status written: {0}" -f (Join-Path $statusDir "mysql_replica_status.json"))
} finally {
    Remove-Item $localCnfReport -Force -ErrorAction SilentlyContinue
}
