# Materialize D:\.1 Work Stations\RootMC\emergent-repo\ for GitHub / Emergent AI.
# Re-run after workspace changes. Does not commit or push.
$ErrorActionPreference = "Stop"

$WorkspaceRoot = Split-Path $PSScriptRoot -Parent
$RepoRoot = Join-Path $WorkspaceRoot "emergent-repo"
$MonoShared = "D:\Web\cloudflare\shared"
$MonoAccount = "D:\Web\cloudflare\rootrecord-api-account"

function Copy-Tree {
    param(
        [string]$Source,
        [string]$Dest,
        [string[]]$ExcludeDirNames = @("node_modules", "build", "dist", ".gradle", ".wrangler", ".git")
    )
    if (-not (Test-Path $Source)) {
        throw "Missing source: $Source"
    }
    New-Item -ItemType Directory -Force -Path $Dest | Out-Null
    robocopy $Source $Dest /E /NFL /NDL /NJH /NJS /NC /NS /NP `
        /XD $ExcludeDirNames `
        /XF ".env" "credentials.env" "local.properties" "google-services.json" "*.jks" "*.keystore" `
        | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy failed ($LASTEXITCODE) for $Source" }
}

Write-Host "Exporting RootMC emergent repo -> $RepoRoot"

if (Test-Path $RepoRoot) {
    # Keep docs/scripts at repo root; refresh code trees only.
    foreach ($dir in @("web", "api", "android")) {
        $p = Join-Path $RepoRoot $dir
        if (Test-Path $p) { Remove-Item -Recurse -Force $p }
    }
} else {
    New-Item -ItemType Directory -Force -Path $RepoRoot | Out-Null
}

Copy-Tree (Join-Path $WorkspaceRoot "Web Files\rootmc-web") (Join-Path $RepoRoot "web")
Copy-Tree (Join-Path $WorkspaceRoot "Web Files\rootmc-api") (Join-Path $RepoRoot "api\rootmc-api")
Copy-Tree (Join-Path $WorkspaceRoot "Web Files\rootmc-realm-api") (Join-Path $RepoRoot "api\rootmc-realm-api")
Copy-Tree (Join-Path $WorkspaceRoot "Mobile App Files\rootmc-android") (Join-Path $RepoRoot "android") @(
    "node_modules", "build", "dist", ".gradle", ".wrangler", ".git", "app\build", ".idea"
)
Copy-Tree $MonoShared (Join-Path $RepoRoot "api\shared")
Copy-Tree $MonoAccount (Join-Path $RepoRoot "api\rootrecord-api-account") @(
    "node_modules", "build", "dist", ".wrangler", ".git"
)

# Strip web temp debug dumps if present.
Get-ChildItem (Join-Path $RepoRoot "web") -Filter "tmp-*.json" -ErrorAction SilentlyContinue |
    Remove-Item -Force

# Portable env loader for this repo layout.
$loadEnvSrc = Join-Path $RepoRoot "scripts\load-env.ps1"
if (-not (Test-Path (Split-Path $loadEnvSrc -Parent))) {
    New-Item -ItemType Directory -Force -Path (Split-Path $loadEnvSrc -Parent) | Out-Null
}

# Patch deploy scripts to use repo-root .env instead of canonical workstation paths.
$deployWeb = Join-Path $RepoRoot "web\deploy.ps1"
$deployApi = Join-Path $RepoRoot "api\rootmc-api\deploy.ps1"
$d1Apply = Join-Path $RepoRoot "api\rootmc-api\d1-apply-remote.ps1"

foreach ($file in @($deployWeb, $deployApi)) {
    if (-not (Test-Path $file)) { continue }
    $text = Get-Content -LiteralPath $file -Raw
    $text = $text -replace '\$workspaceRoot = "D:\\.1 Work Stations\\RootMC"', @'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot ".." )).Path
if ($PSScriptRoot -match "rootmc-api") {
    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\.." )).Path
}
'@
    $text = $text -replace '\$workspaceRoot = "C:\\Users\\rrdeveloper\\Desktop\\RootMC Workspace"', '$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot ".." )).Path'
    $text = $text -replace '\. \(Join-Path \$workspaceRoot "scripts\\load-rootmc-env\.ps1"\)', '. (Join-Path $repoRoot "scripts\load-env.ps1")'
    Set-Content -LiteralPath $file -Value $text -NoNewline
}

if (Test-Path $d1Apply) {
    $text = Get-Content -LiteralPath $d1Apply -Raw
    $text = $text -replace '\$workspaceRoot = "D:\\.1 Work Stations\\RootMC"\r?\n\. \(Join-Path \$workspaceRoot "scripts\\load-rootmc-env\.ps1"\)', '. (Join-Path $repoRoot "scripts\load-env.ps1")'
    $text = $text -replace 'C:\\Users\\store\\Desktop\\Projects\\cloudflare\\rootrecord-api-account\\migrations', '$migrationsDir'
    if ($text -notmatch '\$repoRoot = \(Resolve-Path') {
        $text = @"
`$repoRoot = (Resolve-Path (Join-Path `$PSScriptRoot "..\.." )).Path
`$migrationsDir = Join-Path `$repoRoot "api\rootrecord-api-account\migrations"
"@ + "`n" + $text
    }
    $text = $text -replace 'Set CLOUDFLARE_API_TOKEN in RootMC Workspace\\\.env', 'Set CLOUDFLARE_API_TOKEN in repo root .env'
    $text = $text -replace 'Set CLOUDFLARE_ACCOUNT_ID in RootMC Workspace\\\.env', 'Set CLOUDFLARE_ACCOUNT_ID in repo root .env'
    Set-Content -LiteralPath $d1Apply -Value $text -NoNewline
}

foreach ($file in @($deployWeb, $deployApi)) {
    if (-not (Test-Path $file)) { continue }
    $text = Get-Content -LiteralPath $file -Raw
    $text = $text -replace 'Set CLOUDFLARE_API_TOKEN in RootMC Workspace\\\.env', 'Set CLOUDFLARE_API_TOKEN in repo root .env'
    $text = $text -replace 'Set CLOUDFLARE_ACCOUNT_ID in RootMC Workspace\\\.env', 'Set CLOUDFLARE_ACCOUNT_ID in repo root .env'
    Set-Content -LiteralPath $file -Value $text -NoNewline
}

Write-Host "Done. Repo root: $RepoRoot"
Write-Host "Next: cd emergent-repo && git init && git add . && git commit -m 'Initial RootMC emergent export'"
