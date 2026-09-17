# Register logon task to start local-edge (optional).

param(
    [string]$ConfigPath = "",
    [switch]$Unregister
)

$ErrorActionPreference = "Stop"
$EdgeRoot = $PSScriptRoot
$taskName = "RootMC-LocalEdge"
if ($Unregister) {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
    Write-Host "Unregistered $taskName"
    return
}

$start = Join-Path $EdgeRoot "Start-LocalEdge.ps1"
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$start`" -ConfigPath `"$ConfigPath`""
$trigger = New-ScheduledTaskTrigger -AtLogOn
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Description "RootMC local-edge primary stack" -Force | Out-Null
Write-Host "Registered $taskName"
