# Configure RootMC local MySQL: datadir under D:\.database\data, creds in Workspace .env.
# Note: MySQL InnoDB breaks when the datadir *leaf* path starts with '.' â€” use D:\.database\data.

$ErrorActionPreference = "Continue"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$DataRoot = "D:\.database"
$DataDir = Join-Path $DataRoot "data"
$WorkspaceRoot = "D:\.1 Work Stations\RootMC"
$BootstrapEnv = Join-Path $WorkspaceRoot "scripts\local-edge\state\mysql-bootstrap.env"
$MyIniDir = "D:\.mysql-config"
$MyIni = Join-Path $MyIniDir "my.ini"
$LogDir = Join-Path $WorkspaceRoot "scripts\local-edge\logs"
$Junction = "C:\mysql84"
$RealBase = "C:\Program Files\MySQL\MySQL Server 8.4"
New-Item -ItemType Directory -Force -Path $DataRoot, $DataDir, $LogDir, $MyIniDir | Out-Null

if (-not (Test-Path $Junction)) {
    cmd /c "mklink /J `"$Junction`" `"$RealBase`""
}

function Import-Bootstrap {
    if (-not (Test-Path $BootstrapEnv)) { throw "Missing $BootstrapEnv - regenerate credentials first" }
    $map = @{}
    Get-Content $BootstrapEnv | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $i = $line.IndexOf("=")
        if ($i -lt 1) { return }
        $map[$line.Substring(0, $i).Trim()] = $line.Substring($i + 1).Trim()
    }
    return $map
}

$mysqld = Join-Path $Junction "bin\mysqld.exe"
$mysqlCli = Join-Path $Junction "bin\mysql.exe"
if (-not (Test-Path $mysqld)) { throw "mysqld not found at $mysqld - install Oracle.MySQL first" }

$cred = Import-Bootstrap
$port = [int]$cred.ROOTMC_LOCAL_MYSQL_PORT
$rootPass = $cred.ROOTMC_LOCAL_MYSQL_ROOT_PASSWORD
$appUser = $cred.ROOTMC_LOCAL_MYSQL_USER
$appPass = $cred.ROOTMC_LOCAL_MYSQL_PASSWORD
$appDb = $cred.ROOTMC_LOCAL_MYSQL_DATABASE

@"
[mysqld]
basedir=C:/mysql84
datadir=D:/.database/data
port=$port
bind-address=127.0.0.1
mysqlx=0
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
innodb_buffer_pool_size=512M
max_connections=100
log-error=D:/.database/data/mysql-error.log
lc-messages-dir=C:/mysql84/share

[client]
port=$port
host=127.0.0.1
"@ | Set-Content -LiteralPath $MyIni -Encoding ASCII

if (-not (Test-Path (Join-Path $DataDir "mysql.ibd"))) {
    Write-Host "Initializing $DataDir ..."
    $initLog = Join-Path $LogDir "mysql-init.log"
    cmd /c "`"$mysqld`" --defaults-file=$MyIni --initialize-insecure --console > `"$initLog`" 2>&1"
    if (-not (Test-Path (Join-Path $DataDir "mysql.ibd"))) {
        Get-Content $initLog -Tail 30
        throw "initialize failed"
    }
}

function Test-Port([int]$P) {
    try { $c = New-Object Net.Sockets.TcpClient; $c.Connect("127.0.0.1", $P); $c.Close(); return $true } catch { return $false }
}

if (-not (Test-Port $port)) {
    Write-Host "Starting mysqld..."
    cmd /c "start `"RootMCMySQL`" /b `"$mysqld`" --defaults-file=$MyIni"
    foreach ($i in 1..20) { if (Test-Port $port) { break }; Start-Sleep 1 }
}
if (-not (Test-Port $port)) { throw "MySQL not listening on $port" }
Write-Host "MySQL up on 127.0.0.1:$port"

# Ensure .env keys present (idempotent)
$conn = "mysql://${appUser}:$([Uri]::EscapeDataString($appPass))@127.0.0.1:${port}/${appDb}"
$keys = [ordered]@{
    ROOTMC_LOCAL_MYSQL_HOST          = "127.0.0.1"
    ROOTMC_LOCAL_MYSQL_PORT          = "$port"
    ROOTMC_LOCAL_MYSQL_USER          = $appUser
    ROOTMC_LOCAL_MYSQL_PASSWORD      = $appPass
    ROOTMC_LOCAL_MYSQL_DATABASE      = $appDb
    ROOTMC_LOCAL_MYSQL_DATADIR       = $DataDir
    ROOTMC_LOCAL_MYSQL_ROOT_PASSWORD = $rootPass
    ROOTMC_LOCAL_MYSQL_URL           = $conn
    ROOTMC_LOCAL_MYSQL_MODE          = "process"
    CLOUDFLARE_HYPERDRIVE_LOCAL_CONNECTION_STRING_ROOTMC_MYSQL = $conn
}
$envPath = Join-Path $WorkspaceRoot ".env"
$lines = New-Object System.Collections.Generic.List[string]
if (Test-Path $envPath) { Get-Content $envPath | ForEach-Object { [void]$lines.Add($_) } }
foreach ($k in $keys.Keys) {
    $entry = "$k=$($keys[$k])"
    $found = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match ("^\s*" + [regex]::Escape($k) + "\s*=")) { $lines[$i] = $entry; $found = $true; break }
    }
    if (-not $found) { [void]$lines.Add($entry) }
}
Set-Content $envPath $lines.ToArray() -Encoding UTF8

$startPs1 = Join-Path $MyIniDir "Start-RootMcMySQL.ps1"
@"
if (Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue) { exit 0 }
Start-Process -FilePath '$mysqld' -ArgumentList '--defaults-file=$MyIni' -WindowStyle Hidden
"@ | Set-Content $startPs1 -Encoding UTF8

Unregister-ScheduledTask -TaskName "RootMC Local MySQL" -Confirm:$false -ErrorAction SilentlyContinue
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$startPs1`""
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
Register-ScheduledTask -TaskName "RootMC Local MySQL" -Action $action -Trigger $trigger `
    -Settings (New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable) `
    -Description "Start RootMC local MySQL (D:\.database\data)" | Out-Null

Write-Host "DONE - datadir=$DataDir user=$appUser db=$appDb env=$envPath"
