# RootMC auto-start console: local-edge stack + visible edge terminal + workstation presence.
# Installed by install-dev-workstation.ps1 as the logon scheduled-task window.

param(
    [string]$Status = "Online",
    [string]$StatusLine = "",
    [string]$ConfigPath = ""
)

$ErrorActionPreference = "Stop"
$InstallRoot = $PSScriptRoot

$script:InstanceMutex = New-Object System.Threading.Mutex($false, "Global\RootMcDevWorkstationPresence")
if (-not $script:InstanceMutex.WaitOne(0)) {
    Write-Host "Another RootMC edge console is already running - exiting."
    exit 0
}

try { $Host.UI.RawUI.WindowTitle = "RootMC Local Edge" } catch { }

if (-not $ConfigPath) { $ConfigPath = Join-Path $InstallRoot "config.json" }
if (-not (Test-Path $ConfigPath)) { throw "Missing config: $ConfigPath" }

$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
$driveLetter = [string]$config.workstationDrive
if (-not $driveLetter) { $driveLetter = "D" }
$driveLetter = $driveLetter.TrimEnd(':').ToUpperInvariant()
$workspaceFolder = [string]$config.workspaceFolderName
if (-not $workspaceFolder) { $workspaceFolder = "RootMC Workspace" }

function Import-DotEnvFile([string]$LiteralPath) {
    if (-not (Test-Path -LiteralPath $LiteralPath)) { return }
    Get-Content -LiteralPath $LiteralPath | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $p = $line.IndexOf("=")
        if ($p -gt 0) {
            $k = $line.Substring(0, $p).Trim()
            $v = $line.Substring($p + 1).Trim()
            if ($k -and $v) { Set-Item -Path "Env:$k" -Value $v }
        }
    }
}

function Resolve-WorkspaceRoot {
    $driveRoot = "${driveLetter}:\"
    if (Test-Path $driveRoot) {
        $candidate = Join-Path $driveRoot $workspaceFolder
        if (Test-Path $candidate) { return $candidate }
    }
    # Fallback: AppData install is under ...\RootMC\dev-workstation â€” walk up if D: missing
    $fallback = "D:\.1 Work Stations\RootMC"
    if (Test-Path $fallback) { return $fallback }
    return $null
}

Import-DotEnvFile (Join-Path $InstallRoot ".env")
$workspaceRoot = Resolve-WorkspaceRoot
if ($workspaceRoot) {
    Import-DotEnvFile (Join-Path $workspaceRoot ".env")
}

$edgeRoot = if ($workspaceRoot) { Join-Path $workspaceRoot "scripts\local-edge" } else { $null }
$edgeConfig = if ($edgeRoot) { Join-Path $edgeRoot "config.json" } else { $null }
$edgeStarted = $false

Write-Host "RootMC Local Edge console"
Write-Host "  Install: $InstallRoot"
Write-Host "  Workspace: $(if ($workspaceRoot) { $workspaceRoot } else { '(not found)' })"

if ($edgeRoot -and (Test-Path (Join-Path $edgeRoot "Start-LocalEdge.ps1"))) {
    Write-Host "  Starting local-edge stack (api/api2/map/site/gateway)..."
    try {
        $skipTunnel = -not (Get-Command cloudflared -ErrorAction SilentlyContinue)
        $args = @(
            "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", (Join-Path $edgeRoot "Start-LocalEdge.ps1"),
            "-ConfigPath", $edgeConfig,
            "-SkipTerminal"
        )
        if ($skipTunnel) { $args += "-SkipTunnel" }
        & powershell.exe @args
        if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) {
            Write-Warning "Start-LocalEdge exited with code $LASTEXITCODE"
        } else {
            $edgeStarted = $true
            Write-Host "  Local-edge start requested."
        }
    } catch {
        Write-Warning "Local-edge start failed: $_"
    }
} else {
    Write-Warning "scripts\local-edge not found on workspace drive - terminal-only mode."
}

# Presence + Discord still run from original startup script in a background process (SkipMutex).
$presenceScript = Join-Path $InstallRoot "dev-workstation-startup.ps1"
$presenceProc = $null
if (Test-Path $presenceScript) {
    Write-Host "  Starting presence heartbeat (background)..."
    # Start-Process rejects empty ArgumentList entries (PS 5.1) â€” omit blank -StatusLine.
    $presenceArgs = @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
        "-File", $presenceScript,
        "-Status", $Status,
        "-ConfigPath", $ConfigPath,
        "-SkipMutex",
        "-Headless"
    )
    if (-not [string]::IsNullOrEmpty($StatusLine)) {
        $presenceArgs += @("-StatusLine", $StatusLine)
    }
    $presenceProc = Start-Process -FilePath "powershell.exe" -WindowStyle Hidden -PassThru -ArgumentList $presenceArgs
}

$terminalScript = if ($edgeRoot) { Join-Path $edgeRoot "Start-EdgeTerminal.ps1" } else { $null }
$cancelHandler = [System.ConsoleCancelEventHandler]{
    param($sender, $e)
    $e.Cancel = $true
    throw [System.OperationCanceledException]::new("Edge console stopped.")
}
[Console]::Add_CancelKeyPress($cancelHandler)

try {
    if ($terminalScript -and (Test-Path $terminalScript)) {
        Write-Host "  Opening edge terminal UI (Ctrl+C stops console)..."
        Start-Sleep -Seconds 2
        & $terminalScript -ConfigPath $edgeConfig -HttpPort 18792
    } else {
        Write-Host "No edge terminal script - waiting. Ctrl+C to exit."
        while ($true) { Start-Sleep -Seconds 60 }
    }
} catch [System.OperationCanceledException] {
    Write-Host "Shutting down..."
} finally {
    [Console]::Remove_CancelKeyPress($cancelHandler)
    if ($presenceProc -and -not $presenceProc.HasExited) {
        try {
            Stop-Process -Id $presenceProc.Id -Force -ErrorAction SilentlyContinue
        } catch { }
    }
    if ($edgeStarted -and $edgeRoot -and (Test-Path (Join-Path $edgeRoot "Stop-LocalEdge.ps1"))) {
        Write-Host "Stopping local-edge stack..."
        try {
            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $edgeRoot "Stop-LocalEdge.ps1") -ConfigPath $edgeConfig
        } catch {
            Write-Warning $_
        }
    }
    try { if ($script:InstanceMutex) { $script:InstanceMutex.ReleaseMutex() } } catch { }
}
