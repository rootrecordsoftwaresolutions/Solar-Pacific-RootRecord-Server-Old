# Publish portable Root-Core-Node.exe (self-contained win-x64).

param(
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
if (-not $OutDir) {
    $OutDir = Join-Path ([Environment]::GetFolderPath("Desktop")) "RootMC test server\Root-Core-Node"
}
$dotnet = Get-Command dotnet -ErrorAction SilentlyContinue
if (-not $dotnet) {
    # Common install locations after winget
    $candidates = @(
        "$env:ProgramFiles\dotnet\dotnet.exe",
        "$env:LOCALAPPDATA\Microsoft\dotnet\dotnet.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { $dotnet = @{ Source = $c }; break }
    }
}
if (-not $dotnet) { throw "dotnet SDK not found. Install Microsoft.DotNet.SDK.8" }

$exe = if ($dotnet.Source) { $dotnet.Source } else { "dotnet" }
Push-Location $ProjectRoot
try {
    & $exe publish -c Release -r win-x64 --self-contained true `
        -p:PublishSingleFile=true `
        -p:IncludeNativeLibrariesForSelfExtract=true `
        -o $OutDir
} finally {
    Pop-Location
}

Copy-Item (Join-Path $ProjectRoot "config.json") (Join-Path $OutDir "config.json") -Force
$installPs1 = Join-Path $ProjectRoot "install-root-core-node.ps1"
if (Test-Path $installPs1) {
    Copy-Item $installPs1 (Join-Path $OutDir "install-root-core-node.ps1") -Force
}
# Junction/copy scripts pointers — document relative paths in README
$readme = @"
# Root-Core-Node — RootMC Network

Portable RootMC Network hub monitor/control panel + local-edge script runner.

1. Edit ``config.json`` — set ``workspaceRoot`` / ``edgeRoot`` for this device.
2. Install logon autostart:
   ``powershell -File install-root-core-node.ps1 -SkipPublish -ExePath .\Root-Core-Node.exe -StartNow``
3. Or run ``Root-Core-Node.exe`` / ``Root-Core-Node.exe --autostart``
4. On launch it starts Start-LocalEdge (SkipTerminal) + presence when ``autoStartEdgeOnLaunch`` is true.

phpMyAdmin: ``powershell -File <workspace>\scripts\local-edge\Install-PhpMyAdmin.ps1`` then Start from panel.
"@
Set-Content (Join-Path $OutDir "README.txt") $readme -Encoding UTF8
Write-Host "Published: $OutDir\Root-Core-Node.exe"
