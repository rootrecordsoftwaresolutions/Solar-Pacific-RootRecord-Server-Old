# Create local MySQL schema + registry row for ROOTMC DEV (test server).
# Does not touch Claims/Towny Shockbyte DBs.
#
# Usage:
#   powershell -File scripts\local-edge\Ensure-RootMcDevMysql.ps1

param(
    [string]$WorkspaceRoot = "D:\.1 Work Stations\RootMC",
    [string]$TestServerRoot = "",
    [string]$MysqlBin = "C:\mysql84\bin",
    [string]$Schema = "rootmc_dev",
    [string]$DisplayName = "ROOTMC DEV portal",
    [string]$PlayitPublic = "147.185.221.21:41654",
    [string]$LocalBind = "127.0.0.1:25565"
)

$ErrorActionPreference = "Stop"

function Read-DotEnvMap {
    param([string]$Path)
    $map = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $map }
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $i = $line.IndexOf("=")
        if ($i -lt 1) { return }
        $map[$line.Substring(0, $i).Trim()] = $line.Substring($i + 1).Trim().Trim('"').Trim("'")
    }
    return $map
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

$envMap = Read-DotEnvMap (Join-Path $WorkspaceRoot ".env")
$hostName = if ($envMap["ROOTMC_LOCAL_MYSQL_HOST"]) { $envMap["ROOTMC_LOCAL_MYSQL_HOST"] } else { "127.0.0.1" }
$port = if ($envMap["ROOTMC_LOCAL_MYSQL_PORT"]) { [int]$envMap["ROOTMC_LOCAL_MYSQL_PORT"] } else { 3307 }
$rootPass = $envMap["ROOTMC_LOCAL_MYSQL_ROOT_PASSWORD"]
$edgeUser = if ($envMap["ROOTMC_LOCAL_MYSQL_USER"]) { $envMap["ROOTMC_LOCAL_MYSQL_USER"] } else { "rootmc_edge" }
$edgePass = $envMap["ROOTMC_LOCAL_MYSQL_PASSWORD"]
if (-not $rootPass) { throw "ROOTMC_LOCAL_MYSQL_ROOT_PASSWORD missing in .env" }
if (-not $edgePass) { throw "ROOTMC_LOCAL_MYSQL_PASSWORD missing in .env" }

$mysql = Join-Path $MysqlBin "mysql.exe"
if (-not (Test-Path -LiteralPath $mysql)) { throw "mysql.exe not found: $mysql" }

try {
    $c = New-Object Net.Sockets.TcpClient
    $c.Connect($hostName, $port)
    $c.Close()
} catch {
    throw "Local MySQL not reachable on ${hostName}:${port}"
}

if (-not $TestServerRoot) {
    $desktopCandidate = Join-Path ([Environment]::GetFolderPath("Desktop")) "RootMC test server"
    $workspaceCandidate = Join-Path $WorkspaceRoot "test server"
    if (Test-Path -LiteralPath $desktopCandidate) { $TestServerRoot = $desktopCandidate }
    elseif (Test-Path -LiteralPath $workspaceCandidate) { $TestServerRoot = $workspaceCandidate }
    else { $TestServerRoot = $desktopCandidate }
}
$identityDir = Join-Path $TestServerRoot "plugins\RootMC"
New-Item -ItemType Directory -Path $identityDir -Force | Out-Null
$identityPath = Join-Path $identityDir ".dev-server-identity.env"
$serverId = $null
$serverSecret = $null
if (Test-Path -LiteralPath $identityPath) {
    $idMap = Read-DotEnvMap $identityPath
    $serverId = $idMap["ROOTMC_DEV_SERVER_ID"]
    $serverSecret = $idMap["ROOTMC_DEV_SERVER_SECRET"]
}
if (-not $serverId) { $serverId = [guid]::NewGuid().ToString() }
if (-not $serverSecret) {
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] 32
    $rng.GetBytes($bytes)
    $serverSecret = -join ($bytes | ForEach-Object { $_.ToString("x2") })
}

$cnf = New-MysqlCnf -HostName $hostName -Port $port -User "root" -Password $rootPass -Suffix "dev-ensure"
$escEdgePass = $edgePass.Replace("'", "''")
$sql = @"
CREATE DATABASE IF NOT EXISTS ``$Schema`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ``rootmc_network`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS rootmc_network.rootmc_network_servers (
  server_id varchar(64) NOT NULL PRIMARY KEY,
  display_name varchar(128) DEFAULT NULL,
  host_mysql_host varchar(255) DEFAULT NULL,
  last_snapshot_at datetime DEFAULT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS ``$Schema``.rootmc_dev_server (
  id tinyint NOT NULL PRIMARY KEY DEFAULT 1,
  server_id varchar(64) NOT NULL,
  display_name varchar(128) NOT NULL,
  playit_public varchar(64) NOT NULL,
  local_bind varchar(64) NOT NULL,
  schema_name varchar(64) NOT NULL,
  playtime_scope varchar(32) NOT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
INSERT INTO rootmc_network.rootmc_network_servers (server_id, display_name, host_mysql_host, last_snapshot_at)
VALUES ('$serverId', '$DisplayName', '$hostName', UTC_TIMESTAMP())
ON DUPLICATE KEY UPDATE
  display_name=VALUES(display_name),
  host_mysql_host=VALUES(host_mysql_host),
  last_snapshot_at=UTC_TIMESTAMP(),
  updated_at=UTC_TIMESTAMP();
INSERT INTO ``$Schema``.rootmc_dev_server
  (id, server_id, display_name, playit_public, local_bind, schema_name, playtime_scope)
VALUES
  (1, '$serverId', '$DisplayName', '$PlayitPublic', '$LocalBind', '$Schema', 'dev')
ON DUPLICATE KEY UPDATE
  server_id=VALUES(server_id),
  display_name=VALUES(display_name),
  playit_public=VALUES(playit_public),
  local_bind=VALUES(local_bind),
  schema_name=VALUES(schema_name),
  playtime_scope=VALUES(playtime_scope),
  updated_at=UTC_TIMESTAMP();
CREATE USER IF NOT EXISTS '$edgeUser'@'127.0.0.1' IDENTIFIED BY '$escEdgePass';
CREATE USER IF NOT EXISTS '$edgeUser'@'localhost' IDENTIFIED BY '$escEdgePass';
GRANT ALL PRIVILEGES ON ``$Schema``.* TO '$edgeUser'@'127.0.0.1';
GRANT ALL PRIVILEGES ON ``$Schema``.* TO '$edgeUser'@'localhost';
GRANT ALL PRIVILEGES ON ``rootmc_network``.* TO '$edgeUser'@'127.0.0.1';
GRANT ALL PRIVILEGES ON ``rootmc_network``.* TO '$edgeUser'@'localhost';
FLUSH PRIVILEGES;
SELECT CONCAT('schema=', schema_name) FROM information_schema.schemata WHERE schema_name='$Schema';
SELECT CONCAT('network=', server_id, ' / ', IFNULL(display_name,'')) FROM rootmc_network.rootmc_network_servers WHERE server_id='$serverId';
SELECT CONCAT('meta=', server_id, ' scope=', playtime_scope) FROM ``$Schema``.rootmc_dev_server WHERE id=1;
"@

try {
    & $mysql --defaults-extra-file=$cnf -e $sql
    if ($LASTEXITCODE -ne 0) { throw "mysql failed exit=$LASTEXITCODE" }
} finally {
    Remove-Item -LiteralPath $cnf -Force -ErrorAction SilentlyContinue
}

$metaDir = Split-Path -Parent $identityPath
New-Item -ItemType Directory -Force -Path $metaDir | Out-Null
@(
    "ROOTMC_DEV_SERVER_ID=$serverId"
    "ROOTMC_DEV_SERVER_SECRET=$serverSecret"
    "ROOTMC_DEV_MYSQL_DATABASE=$Schema"
    "ROOTMC_DEV_PLAYTIME_SCOPE=dev"
    "ROOTMC_DEV_PLAYIT_PUBLIC=$PlayitPublic"
) | Set-Content -LiteralPath $identityPath -Encoding ASCII

Write-Host "OK $Schema ready; identity written to $identityPath"
Write-Host "server-id=$serverId"
