# Post to RootMC Discord via bot token in RootMC Workspace\.env
# Usage:
#   powershell -File scripts\discord-post.ps1 -Message "Dev workstation online"
#   powershell -File scripts\discord-post.ps1 -Channel updates -Message "Hello #updates"
param(
    [string]$Channel = "updates",
    [Parameter(Mandatory = $true)]
    [string]$Message,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$script = Join-Path $workspaceRoot "Web Files\rootmc-realm-api\scripts\post-discord-message.mjs"
$args = @("--channel", $Channel, "--message", $Message)
if ($DryRun) { $args += "--dry-run" }
& node $script @args
