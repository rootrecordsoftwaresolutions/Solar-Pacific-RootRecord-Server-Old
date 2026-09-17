# Install Root-Core-Node.exe as the Windows logon script runner.
# Replaces the legacy PowerShell "RootMC Dev Workstation" / dev-edge-console scheduled task.

param(
    [string]$ExePath = "",
    [string]$OutDir = "",
    [string]$InstallDir = "",
    [switch]$SkipPublish,
    [switch]$StartNow
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$desktopTest = Join-Path ([Environment]::GetFolderPath("Desktop")) "RootMC test server"
if (-not $OutDir) {
    $OutDir = Join-Path $desktopTest "Root-Core-Node"
}
if (-not $InstallDir) {
    # Prefer Desktop test server folder (SSD + co-located with Paper DEV).
    $InstallDir = Join-Path $desktopTest "Root-Core-Node"
    if (-not (Test-Path -LiteralPath $desktopTest)) {
        $InstallDir = Join-Path $env:LOCALAPPDATA "RootMC\root-core-node"
    }
}

Write-Host "Root-Core-Node install"
Write-Host "  Project: $ProjectRoot"
Write-Host "  Install: $InstallDir"

if (-not $SkipPublish) {
    & (Join-Path $ProjectRoot "publish-root-core-node.ps1") -OutDir $OutDir
}

if (-not $ExePath) {
    $ExePath = Join-Path $OutDir "Root-Core-Node.exe"
}
if (-not (Test-Path -LiteralPath $ExePath)) {
    throw "Missing exe: $ExePath (publish first or pass -ExePath)"
}

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $InstallDir "state") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $InstallDir "logs") | Out-Null

# Migrate session/state from old LocalAppData install if present
$legacy = Join-Path $env:LOCALAPPDATA "RootMC\root-core-node"
if ((Test-Path -LiteralPath $legacy) -and ($legacy -ne $InstallDir)) {
    foreach ($name in @("state", "config.json")) {
        $src = Join-Path $legacy $name
        $dst = Join-Path $InstallDir $name
        if ((Test-Path -LiteralPath $src) -and -not (Test-Path -LiteralPath $dst)) {
            Copy-Item -LiteralPath $src -Destination $dst -Recurse -Force
            Write-Host "  Migrated $name from LocalAppData"
        }
    }
}
$installedExe = Join-Path $InstallDir "Root-Core-Node.exe"
if ([IO.Path]::GetFullPath($ExePath) -ne [IO.Path]::GetFullPath($installedExe)) {
    Copy-Item -LiteralPath $ExePath -Destination $installedExe -Force
} else {
    Write-Host "  Publish output already at install path - skip copy"
}
$configSrc = Join-Path $ProjectRoot "config.json"
if (-not (Test-Path $configSrc)) { $configSrc = Join-Path $OutDir "config.json" }
if (Test-Path $configSrc) {
    Copy-Item -LiteralPath $configSrc -Destination (Join-Path $InstallDir "config.json") -Force
}
$installScript = Join-Path $ProjectRoot "install-root-core-node.ps1"
if (Test-Path $installScript) {
    Copy-Item -LiteralPath $installScript -Destination (Join-Path $InstallDir "install-root-core-node.ps1") -Force
}

# Keep presence scripts available for Node to launch headless
$dwSrc = Join-Path (Split-Path $ProjectRoot -Parent) "dev-workstation"
$dwDst = Join-Path $env:LOCALAPPDATA "RootMC\dev-workstation"
if (Test-Path $dwSrc) {
    New-Item -ItemType Directory -Force -Path $dwDst | Out-Null
    foreach ($name in @("dev-workstation-startup.ps1", "config.json", "host-metrics.ps1")) {
        $s = Join-Path $dwSrc $name
        if (Test-Path $s) {
            Copy-Item -LiteralPath $s -Destination (Join-Path $dwDst $name) -Force
            if ($name -like "*.ps1") {
                $utf8Bom = New-Object System.Text.UTF8Encoding $true
                $text = [System.IO.File]::ReadAllText($s)
                [System.IO.File]::WriteAllText((Join-Path $dwDst $name), $text, $utf8Bom)
            }
        }
    }
}

# Remove legacy PowerShell edge-console launcher
Unregister-ScheduledTask -TaskName "RootMC Dev Workstation" -Confirm:$false -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName "RootMC-LocalEdge" -Confirm:$false -ErrorAction SilentlyContinue

$startupDir = [Environment]::GetFolderPath("Startup")
foreach ($legacy in @(
    (Join-Path $startupDir "RootMC Dev Workstation.lnk"),
    (Join-Path $env:ProgramData "Microsoft\Windows\Start Menu\Programs\StartUp\RootMC Dev Workstation.lnk")
)) {
    if (Test-Path -LiteralPath $legacy) {
        try { Remove-Item -LiteralPath $legacy -Force -ErrorAction Stop } catch {
            Write-Warning "Could not remove $legacy"
        }
    }
}

$taskName = "RootMC Root-Core-Node"
Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
$action = New-ScheduledTaskAction -Execute $installedExe -Argument "--autostart" -WorkingDirectory $InstallDir
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$trigger.Delay = "PT30S"
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 2)
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal `
    -Description "Root-Core-Node local-edge script runner + tray UI (replaces PowerShell edge console)" | Out-Null

$lnk = Join-Path $startupDir "Root-Core-Node.lnk"
$ws = New-Object -ComObject WScript.Shell
$s = $ws.CreateShortcut($lnk)
$s.TargetPath = $installedExe
$s.Arguments = "--autostart"
$s.WorkingDirectory = $InstallDir
$s.Description = "RootMC Root-Core-Node (local-edge script runner)"
$s.Save()

# Desktop shortcut (next to Paper test server)
$deskLnk = Join-Path ([Environment]::GetFolderPath("Desktop")) "Root-Core-Node.lnk"
$ds = $ws.CreateShortcut($deskLnk)
$ds.TargetPath = $installedExe
$ds.WorkingDirectory = $InstallDir
$ds.Description = "Root-Core-Node - RootMC Network"
$ds.Save()

Write-Host ""
Write-Host "Installed: $installedExe"
Write-Host "Scheduled task: $taskName (At logon, --autostart)"
Write-Host "Startup shortcut: $lnk"
Write-Host "Desktop shortcut: $deskLnk"
Write-Host "Legacy PowerShell RootMC Dev Workstation task removed."

if ($StartNow) {
    Start-Process -FilePath $installedExe -ArgumentList "--autostart" -WorkingDirectory $InstallDir
    Write-Host "Started Root-Core-Node now."
}
