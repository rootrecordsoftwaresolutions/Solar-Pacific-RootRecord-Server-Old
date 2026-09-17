# FreeLTC one-file XMRig installer for Windows
# Downloads official XMRig, asks for LTC address once, creates config + start script
# Referral code: U-80KT6Z (lower fees on unMineable)

$ErrorActionPreference = "Stop"
$REF = "U-80KT6Z"
$POOL = "rx.unmineable.com:3333"
$INSTALL_DIR = Join-Path $env:USERPROFILE "FreeLTC-XMRig"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  FreeLTC XMRig Installer (Windows)" -ForegroundColor Cyan
Write-Host "  Pool: unMineable  |  Coin: LTC" -ForegroundColor Cyan
Write-Host "  Referral: $REF (0.75% fee)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Ask for LTC address
$LTC = Read-Host "Enter your Litecoin (LTC) wallet address"
$LTC = $LTC.Trim()
if ([string]::IsNullOrWhiteSpace($LTC)) {
    Write-Host "No address entered. Exiting." -ForegroundColor Red
    exit 1
}

$WORKER = Read-Host "Worker name (press Enter for 'pc1')"
if ([string]::IsNullOrWhiteSpace($WORKER)) { $WORKER = "pc1" }

# Create install directory
if (-not (Test-Path $INSTALL_DIR)) {
    New-Item -ItemType Directory -Path $INSTALL_DIR | Out-Null
}
Set-Location $INSTALL_DIR

Write-Host ""
Write-Host "Downloading latest XMRig..." -ForegroundColor Yellow

# Get latest release asset URL (Windows x64)
$api = "https://api.github.com/repos/xmrig/xmrig/releases/latest"
$release = Invoke-RestMethod -Uri $api -Headers @{ "User-Agent" = "FreeLTC-Installer" }
$asset = $release.assets | Where-Object { $_.name -match "xmrig-.*-msvc-win64\.zip$" } | Select-Object -First 1

if (-not $asset) {
    Write-Host "Could not find Windows XMRig release. Check https://github.com/xmrig/xmrig/releases" -ForegroundColor Red
    exit 1
}

$zipPath = Join-Path $INSTALL_DIR "xmrig.zip"
Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $zipPath -UseBasicParsing

Write-Host "Extracting..." -ForegroundColor Yellow
Expand-Archive -Path $zipPath -DestinationPath $INSTALL_DIR -Force
Remove-Item $zipPath -Force

# Find the extracted folder (usually xmrig-6.xx.x)
$xmrigFolder = Get-ChildItem -Directory | Where-Object { $_.Name -match "^xmrig-" } | Select-Object -First 1
if ($xmrigFolder) {
    # Move contents up
    Get-ChildItem -Path $xmrigFolder.FullName | Move-Item -Destination $INSTALL_DIR -Force
    Remove-Item $xmrigFolder.FullName -Recurse -Force
}

$xmrigExe = Join-Path $INSTALL_DIR "xmrig.exe"
if (-not (Test-Path $xmrigExe)) {
    Write-Host "xmrig.exe not found after extract. Aborting." -ForegroundColor Red
    exit 1
}

# Create config.json
$user = "LTC:${LTC}.${WORKER}#${REF}"
$config = @"
{
    "autosave": true,
    "cpu": true,
    "opencl": false,
    "cuda": false,
    "pools": [
        {
            "url": "$POOL",
            "user": "$user",
            "pass": "x",
            "keepalive": true,
            "tls": false
        }
    ]
}
"@
$configPath = Join-Path $INSTALL_DIR "config.json"
Set-Content -Path $configPath -Value $config -Encoding UTF8

# Create start.bat
$startBat = @"
@echo off
cd /d "%~dp0"
echo Starting FreeLTC miner (XMRig + unMineable)...
echo Address: $LTC
echo Worker:  $WORKER
echo Referral: $REF
echo.
xmrig.exe
pause
"@
Set-Content -Path (Join-Path $INSTALL_DIR "start.bat") -Value $startBat -Encoding ASCII

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Installation complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Folder: $INSTALL_DIR"
Write-Host "Run:    start.bat   (double-click it)"
Write-Host ""
Write-Host "If Windows Defender deletes xmrig.exe, add an exclusion for this folder."
Write-Host "Check your balance at: https://unmineable.com  (search your LTC address)"
Write-Host ""

$run = Read-Host "Start mining now? (Y/n)"
if ($run -eq "" -or $run -match "^[Yy]") {
    Start-Process -FilePath $xmrigExe -WorkingDirectory $INSTALL_DIR
}
