# Download portable PHP + phpMyAdmin (or Adminer) and configure for local MySQL :3307.
# No Docker required. Serves http://127.0.0.1:8080

param(
    [string]$InstallRoot = "$env:LOCALAPPDATA\RootMC\phpmyadmin",
    [int]$HttpPort = 8080
)

$ErrorActionPreference = "Stop"
$EdgeRoot = $PSScriptRoot
$BootstrapEnv = Join-Path $EdgeRoot "state\mysql-bootstrap.env"
$WorkspaceEnv = "D:\.1 Work Stations\RootMC\.env"

function Read-EnvMap([string]$Path) {
    $map = @{}
    if (-not (Test-Path $Path)) { return $map }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $i = $line.IndexOf("=")
        if ($i -lt 1) { return }
        $map[$line.Substring(0, $i).Trim()] = $line.Substring($i + 1).Trim()
    }
    return $map
}

$cred = Read-EnvMap $BootstrapEnv
if ($cred.Count -eq 0) { $cred = Read-EnvMap $WorkspaceEnv }

$port = if ($cred.ROOTMC_LOCAL_MYSQL_PORT) { [int]$cred.ROOTMC_LOCAL_MYSQL_PORT } else { 3307 }
$user = if ($cred.ROOTMC_LOCAL_MYSQL_USER) { $cred.ROOTMC_LOCAL_MYSQL_USER } else { "root" }
$pass = if ($cred.ROOTMC_LOCAL_MYSQL_PASSWORD) { $cred.ROOTMC_LOCAL_MYSQL_PASSWORD }
         elseif ($cred.ROOTMC_LOCAL_MYSQL_ROOT_PASSWORD) { $cred.ROOTMC_LOCAL_MYSQL_ROOT_PASSWORD }
         else { "" }

New-Item -ItemType Directory -Force -Path $InstallRoot | Out-Null
$phpDir = Join-Path $InstallRoot "php"
$pmaDir = Join-Path $InstallRoot "www"
$phpZip = Join-Path $InstallRoot "php.zip"
$pmaZip = Join-Path $InstallRoot "pma.zip"

if (-not (Test-Path (Join-Path $phpDir "php.exe"))) {
    Write-Host "Downloading PHP 8.3 Win x64 NTS..."
    $phpUrl = "https://windows.php.net/downloads/releases/php-8.3.22-nts-Win32-vs16-x64.zip"
    try {
        Invoke-WebRequest -Uri $phpUrl -OutFile $phpZip -UseBasicParsing
    } catch {
        # Fallback: latest known path may move â€” try archives
        $phpUrl = "https://windows.php.net/downloads/releases/archives/php-8.3.14-nts-Win32-vs16-x64.zip"
        Invoke-WebRequest -Uri $phpUrl -OutFile $phpZip -UseBasicParsing
    }
    Expand-Archive -Path $phpZip -DestinationPath $phpDir -Force
}

$phpExe = Join-Path $phpDir "php.exe"
if (-not (Test-Path $phpExe)) { throw "php.exe missing after extract" }

# Enable mysqli in php.ini
$iniProd = Join-Path $phpDir "php.ini-production"
$ini = Join-Path $phpDir "php.ini"
if (-not (Test-Path $ini) -and (Test-Path $iniProd)) { Copy-Item $iniProd $ini }
if (Test-Path $ini) {
    $raw = Get-Content $ini -Raw
    $raw = $raw -replace ';extension=mysqli', 'extension=mysqli'
    $raw = $raw -replace ';extension=pdo_mysql', 'extension=pdo_mysql'
    $raw = $raw -replace ';extension_dir = "ext"', 'extension_dir = "ext"'
    Set-Content $ini $raw -Encoding ASCII
}

if (-not (Test-Path (Join-Path $pmaDir "index.php"))) {
    Write-Host "Downloading phpMyAdmin..."
    $pmaUrl = "https://files.phpmyadmin.net/phpMyAdmin/5.2.2/phpMyAdmin-5.2.2-english.zip"
    Invoke-WebRequest -Uri $pmaUrl -OutFile $pmaZip -UseBasicParsing
    $tmp = Join-Path $InstallRoot "pma-extract"
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive -Path $pmaZip -DestinationPath $tmp -Force
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    if (Test-Path $pmaDir) { Remove-Item $pmaDir -Recurse -Force }
    Move-Item $inner.FullName $pmaDir
}

$config = @"
<?php
`$cfg['blowfish_secret'] = 'RootMcSolarPhpMyAdminSecret32ch';
`$i = 0;
`$i++;
`$cfg['Servers'][`$i]['auth_type'] = 'config';
`$cfg['Servers'][`$i]['host'] = '127.0.0.1';
`$cfg['Servers'][`$i]['port'] = '$port';
`$cfg['Servers'][`$i]['user'] = '$user';
`$cfg['Servers'][`$i]['password'] = '$pass';
`$cfg['Servers'][`$i]['Compress'] = false;
`$cfg['Servers'][`$i]['AllowNoPassword'] = false;
`$cfg['DefaultLang'] = 'en';
`$cfg['ServerDefault'] = 1;
`$cfg['UploadDir'] = '';
`$cfg['SaveDir'] = '';
"@
Set-Content -Path (Join-Path $pmaDir "config.inc.php") -Value $config -Encoding UTF8

$meta = @{
    installRoot = $InstallRoot
    phpExe      = $phpExe
    www         = $pmaDir
    httpPort    = $HttpPort
    mysqlPort   = $port
} | ConvertTo-Json
Set-Content (Join-Path $InstallRoot "install.json") $meta -Encoding UTF8

Write-Host "phpMyAdmin installed at $pmaDir"
Write-Host "Run Start-PhpMyAdmin.ps1 then open http://127.0.0.1:$HttpPort"
