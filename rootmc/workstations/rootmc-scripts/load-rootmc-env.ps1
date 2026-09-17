# Dot-source from RootMC deploy scripts:
#   . (Join-Path $workspaceRoot "scripts\load-rootmc-env.ps1")
#
# Prefers layout-relative secrets, then E: credentials (pit-stop / canonical),
# then D: twin (may disappear during SSD flash). Never prints secret values.

if (-not (Get-Command Import-DotEnvFile -ErrorAction SilentlyContinue)) {
    function Import-DotEnvFile([string]$LiteralPath, [switch]$SkipCloudflareKeys) {
        if (-not $LiteralPath -or -not (Test-Path -LiteralPath $LiteralPath)) { return }
        Get-Content -LiteralPath $LiteralPath | ForEach-Object {
            $line = $_.Trim()
            if (-not $line -or $line.StartsWith("#")) { return }
            $p = $line.IndexOf("=")
            if ($p -gt 0) {
                $k = $line.Substring(0, $p).Trim()
                $v = $line.Substring($p + 1).Trim()
                if ($SkipCloudflareKeys -and $k -match '^CLOUDFLARE_|^ROOTMC_CLOUDFLARE_') { return }
                if ($k -and $v) { Set-Item -Path "Env:$k" -Value $v }
            }
        }
    }
}

$script:WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$script:WorkspaceEnv = Join-Path $script:WorkspaceRoot ".env"
$script:WorkstationCredentials = Join-Path (Split-Path $script:WorkspaceRoot -Parent) ".credentials\.env"
$script:ProjectsCredentials = Join-Path (Split-Path $script:WorkspaceRoot -Parent) "credentials.env"

# Drive-letter credentials: E first (Ubuntu pit-stop source), workspace letter, then D twin.
$script:DriveCredentialCandidates = [System.Collections.Generic.List[string]]::new()
foreach ($letter in @("E", $null, "D")) {
    if (-not $letter) {
        if ($script:WorkspaceRoot -match '^([A-Za-z]):') {
            $letter = $Matches[1].ToUpperInvariant()
        } else { continue }
    }
    $cand = "${letter}:\.credentials\.env"
    if (-not $script:DriveCredentialCandidates.Contains($cand)) {
        [void]$script:DriveCredentialCandidates.Add($cand)
    }
}

# Broad consolidated credentials first (fill gaps), then workspace .env overrides.
foreach ($driveCred in $script:DriveCredentialCandidates) {
    Import-DotEnvFile $driveCred -SkipCloudflareKeys
}
Import-DotEnvFile $script:WorkstationCredentials -SkipCloudflareKeys
Import-DotEnvFile $script:ProjectsCredentials -SkipCloudflareKeys
Import-DotEnvFile $script:WorkspaceEnv

Remove-Item Env:CLOUDFLARE_API_KEY -ErrorAction SilentlyContinue
Remove-Item Env:CLOUDFLARE_EMAIL -ErrorAction SilentlyContinue
Remove-Item Env:CLOUDFLARE_GLOBAL_API_KEY -ErrorAction SilentlyContinue
