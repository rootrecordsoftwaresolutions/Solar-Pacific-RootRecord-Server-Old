#Requires -Version 5.1
<#
.SYNOPSIS
  Stage, push, and release every RootMC plugin under github.com/RootRecord/<name>.

.PARAMETER StageOnly
  Only copy sources + generate docs into github-plugin-repos/staging.

.PARAMETER PushRepos
  Create/push GitHub repos from staging (requires gh as RootRecord).

.PARAMETER BuildJars
  Build all plugin jars via monorepo Gradle.

.PARAMETER CreateReleases
  Create GitHub Releases with jar assets.

.PARAMETER WriteIndex
  Write REPO-INDEX.md.

.PARAMETER All
  Run Stage + Push + Build + Releases + Index.

.PARAMETER Force
  Overwrite staging README/docs; recreate release if tag exists (delete+create).

.PARAMETER Plugin
  Limit to one plugin folder name (e.g. root-core).
#>
param(
    [switch]$StageOnly,
    [switch]$PushRepos,
    [switch]$BuildJars,
    [switch]$CreateReleases,
    [switch]$WriteIndex,
    [switch]$All,
    [switch]$Force,
    [string]$Plugin = ""
)

$ErrorActionPreference = "Stop"
$WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$MinecraftRoot = Join-Path $WorkspaceRoot "Plugin Building\Minecraft"
$PluginsRoot = Join-Path $MinecraftRoot "plugins"
$StagingRoot = Join-Path $WorkspaceRoot "github-plugin-repos\staging"
$Owner = "RootRecord"

if (-not ($StageOnly -or $PushRepos -or $BuildJars -or $CreateReleases -or $WriteIndex -or $All)) {
    $All = $true
}
if ($All) {
    $StageOnly = $true
    $PushRepos = $true
    $BuildJars = $true
    $CreateReleases = $true
    $WriteIndex = $true
}

function Get-PluginFolders {
    Get-ChildItem -LiteralPath $PluginsRoot -Directory |
        Where-Object { -not $_.Name.StartsWith(".") } |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "build.gradle.kts") } |
        Where-Object { if ($Plugin) { $_.Name -eq $Plugin } else { $true } } |
        Sort-Object Name
}

function Get-GradleVersion([string]$PluginDir) {
    $bg = Join-Path $PluginDir "build.gradle.kts"
    $text = Get-Content -LiteralPath $bg -Raw
    if ($text -match 'version\s*=\s*"([^"]+)"') { return $Matches[1] }
    return "0.0.0"
}

function Parse-PluginYml([string]$Path) {
    $result = [ordered]@{
        name        = ""
        description = ""
        author      = ""
        website     = "https://rootmc.net"
        apiVersion  = ""
        main        = ""
        depend      = @()
        softdepend  = @()
        commands    = @()
        permissions = @()
    }
    if (-not (Test-Path -LiteralPath $Path)) { return $result }

    $lines = Get-Content -LiteralPath $Path
    $section = "root"
    $currentCmd = $null
    $currentPerm = $null

    foreach ($raw in $lines) {
        $line = $raw
        if ($line -match '^\s*#') { continue }
        if ($line -match '^\s*$') { continue }

        if ($line -match '^commands:\s*$') { $section = "commands"; $currentCmd = $null; continue }
        if ($line -match '^permissions:\s*$') { $section = "permissions"; $currentPerm = $null; continue }
        if ($line -match '^(depend|softdepend|loadbefore|loadafter|libraries|provides):') {
            if ($section -notin @("commands", "permissions")) { $section = "root" }
        }
        if ($line -match '^(name|version|main|api-version|description|author|website|folia-supported):' -and $line -notmatch '^\s') {
            $section = "root"
        }

        if ($section -eq "root") {
            if ($line -match '^name:\s*(.+)$') { $result.name = $Matches[1].Trim().Trim("'").Trim('"'); continue }
            if ($line -match '^description:\s*(.+)$') { $result.description = $Matches[1].Trim().Trim("'").Trim('"'); continue }
            if ($line -match '^author:\s*(.+)$') { $result.author = $Matches[1].Trim().Trim("'").Trim('"'); continue }
            if ($line -match '^website:\s*(.+)$') { $result.website = $Matches[1].Trim().Trim("'").Trim('"'); continue }
            if ($line -match '^api-version:\s*(.+)$') { $result.apiVersion = $Matches[1].Trim().Trim("'").Trim('"'); continue }
            if ($line -match '^main:\s*(.+)$') { $result.main = $Matches[1].Trim().Trim("'").Trim('"'); continue }
            if ($line -match '^depend:\s*\[(.*)\]\s*$') {
                $result.depend = @($Matches[1].Split(",") | ForEach-Object { $_.Trim().Trim("'").Trim('"') } | Where-Object { $_ })
                continue
            }
            if ($line -match '^softdepend:\s*\[(.*)\]\s*$') {
                $result.softdepend = @($Matches[1].Split(",") | ForEach-Object { $_.Trim().Trim("'").Trim('"') } | Where-Object { $_ })
                continue
            }
        }

        if ($section -eq "commands") {
            if ($line -match '^  ([A-Za-z0-9_-]+):\s*$') {
                $currentCmd = [ordered]@{ name = $Matches[1]; description = ""; usage = ""; permission = ""; aliases = "" }
                $result.commands += $currentCmd
                continue
            }
            if ($null -ne $currentCmd) {
                if ($line -match '^\s+description:\s*(.+)$') { $currentCmd.description = $Matches[1].Trim().Trim("'").Trim('"'); continue }
                if ($line -match '^\s+usage:\s*(.+)$') { $currentCmd.usage = $Matches[1].Trim().Trim("'").Trim('"'); continue }
                if ($line -match '^\s+permission:\s*(.+)$') { $currentCmd.permission = $Matches[1].Trim().Trim("'").Trim('"'); continue }
                if ($line -match '^\s+aliases:\s*(.+)$') { $currentCmd.aliases = $Matches[1].Trim(); continue }
            }
        }

        if ($section -eq "permissions") {
            if ($line -match '^  ([A-Za-z0-9_.*-]+):\s*$') {
                $currentPerm = [ordered]@{ name = $Matches[1]; description = ""; default = "" }
                $result.permissions += $currentPerm
                continue
            }
            if ($null -ne $currentPerm) {
                if ($line -match '^\s+description:\s*(.+)$') { $currentPerm.description = $Matches[1].Trim().Trim("'").Trim('"'); continue }
                if ($line -match '^\s+default:\s*(.+)$') { $currentPerm.default = $Matches[1].Trim().Trim("'").Trim('"'); continue }
            }
        }
    }
    return $result
}

function Get-LinksMarkdown([string]$FolderName, [string]$BukkitName) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("## Links")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("| Resource | URL |")
    [void]$sb.AppendLine("|----------|-----|")
    [void]$sb.AppendLine("| Website | https://rootmc.net |")
    [void]$sb.AppendLine("| Plugin catalog | https://rootmc.net/plugins/ |")
    [void]$sb.AppendLine("| This plugin page | https://rootmc.net/plugins/$FolderName/ |")
    [void]$sb.AppendLine("| Suite wiki | https://rootmc.net/wiki/plugins/ |")
    [void]$sb.AppendLine("| Player wiki | https://rootmc.net/wiki/player/ |")
    [void]$sb.AppendLine("| Constitution | https://rootmc.net/wiki/constitution/ |")
    [void]$sb.AppendLine("| Economy guide | https://rootmc.net/wiki/economy/ |")
    [void]$sb.AppendLine("| Developer keys | https://rootmc.net/developer/keys/ |")
    [void]$sb.AppendLine("| Manifest | https://rootmc.net/plugins/manifest.json |")
    [void]$sb.AppendLine("| Play | ``play.rootmc.net`` |")
    [void]$sb.AppendLine("| Live map | https://map.rootmc.net |")
    [void]$sb.AppendLine("| API | https://api.rootmc.net |")
    [void]$sb.AppendLine("| Discord | https://discord.gg/rFFQYrNaqS |")
    [void]$sb.AppendLine("| GitHub (this repo) | https://github.com/$Owner/$FolderName |")
    [void]$sb.AppendLine("| Releases (version notes) | https://github.com/$Owner/$FolderName/releases |")
    [void]$sb.AppendLine("| BuiltByBit (paid jars) | https://builtbybit.com/ (listing coming soon) |")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("**Discord:** RootMC community - join for support, announcements, and governance: https://discord.gg/rFFQYrNaqS")
    return $sb.ToString()
}

function Get-CommandsMarkdown($meta) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("# Commands and permissions")
    [void]$sb.AppendLine()
    if ($meta.commands.Count -eq 0) {
        [void]$sb.AppendLine("_No commands declared in ``plugin.yml``._")
    } else {
        [void]$sb.AppendLine("## Commands")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("| Command | Description | Permission | Usage |")
        [void]$sb.AppendLine("|---------|-------------|------------|-------|")
        foreach ($c in $meta.commands) {
            $cmd = "/" + $c.name
            $desc = ($c.description -replace '\|', '/').Trim()
            $perm = ($c.permission -replace '\|', '/').Trim()
            $usage = ($c.usage -replace '\|', '/').Trim()
            if (-not $usage) { $usage = $cmd }
            [void]$sb.AppendLine("| ``$cmd`` | $desc | ``$perm`` | ``$usage`` |")
        }
    }
    [void]$sb.AppendLine()
    if ($meta.permissions.Count -eq 0) {
        [void]$sb.AppendLine("## Permissions")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("_No permissions declared in ``plugin.yml``._")
    } else {
        [void]$sb.AppendLine("## Permissions")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("| Permission | Description | Default |")
        [void]$sb.AppendLine("|------------|-------------|---------|")
        foreach ($p in $meta.permissions) {
            $desc = ($p.description -replace '\|', '/').Trim()
            [void]$sb.AppendLine("| ``$($p.name)`` | $desc | ``$($p.default)`` |")
        }
    }
    return $sb.ToString()
}

function Get-ReadmeMarkdown([string]$FolderName, [string]$Version, $meta, [bool]$IsCommon) {
    $bukkit = if ($meta.name) { $meta.name } else { $FolderName }
    $desc = if ($meta.description) { $meta.description } else { "RootMC plugin module $FolderName." }
    $author = if ($meta.author) { $meta.author } else { "Root Record" }
    $api = if ($meta.apiVersion) { $meta.apiVersion } else { "26.1" }
    $dependLine = if ($meta.depend.Count) { ($meta.depend -join ", ") } else { "_none_" }
    $softLine = if ($meta.softdepend.Count) { ($meta.softdepend -join ", ") } else { "_none_" }
    $sb = New-Object System.Text.StringBuilder

    if ($IsCommon) {
        [void]$sb.AppendLine("# rootrecord-common")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("Shared Java library for RootMC Paper plugins (not a Bukkit plugin - do **not** drop this jar into ``plugins/`` alone).")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("**Version:** ``$Version``")
        [void]$sb.AppendLine("**Group:** ``com.rootrecord.minecraft``")
        [void]$sb.AppendLine("**Author:** $author")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("## What it is")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("``rootrecord-common`` is compiled into each RootMC plugin via the monorepo Gradle graph:")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine('```kotlin')
        [void]$sb.AppendLine('implementation(project(":plugins:rootrecord-common"))')
        [void]$sb.AppendLine('```')
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("Standalone plugin repositories publish sources for transparency. **Builds** are produced from the RootMC Plugin Building monorepo.")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("## Paid download")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("**Jars are sold on [BuiltByBit](https://builtbybit.com/) (listing coming soon).**")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("This GitHub repo is the public explainer (install notes, commands, links) for discovery. It is **not** a free jar download mirror.")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("## Install / use")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("1. Do not install this artifact as a server plugin.")
        [void]$sb.AppendLine("2. Feature plugins depend on it at compile time through the monorepo.")
        [void]$sb.AppendLine("3. Licensed builds ship with paid plugins from BuiltByBit when listed.")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine((Get-LinksMarkdown $FolderName $bukkit))
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("## License")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("Copyright Root Record. All rights reserved. Public docs are for discovery; redistribution of binaries or source for commercial use requires Root Record permission.")
        return $sb.ToString()
    }

    [void]$sb.AppendLine("# $bukkit")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine($desc)
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("| Field | Value |")
    [void]$sb.AppendLine("|-------|-------|")
    [void]$sb.AppendLine("| **Folder / artifact** | ``$FolderName`` |")
    [void]$sb.AppendLine("| **Version** | ``$Version`` |")
    [void]$sb.AppendLine("| **Bukkit name** | ``$bukkit`` |")
    [void]$sb.AppendLine("| **Paper API** | ``$api`` |")
    [void]$sb.AppendLine("| **Author** | $author |")
    [void]$sb.AppendLine("| **Website** | $($meta.website) |")
    [void]$sb.AppendLine("| **Main class** | ``$($meta.main)`` |")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("## Paid download")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("**Get the jar on [BuiltByBit](https://builtbybit.com/) - listing coming soon.**")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("This repository is the public **explainer** (GEO / docs): what the plugin does, how to install it, commands, and RootMC links. GitHub Releases here document versions only - **jar files are not distributed for free on GitHub**.")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("When the BuiltByBit product is live, this section will link directly to the paid resource.")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("## Install")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("1. Purchase / download ``$FolderName-$Version.jar`` from BuiltByBit (coming soon) or your licensed RootMC distribution channel.")
    [void]$sb.AppendLine("2. Install **[Root-Core](https://github.com/$Owner/root-core)** first when required (license/cloud spine for the suite).")
    [void]$sb.AppendLine("3. Remove any older ``$FolderName-*.jar`` from ``plugins/``.")
    [void]$sb.AppendLine("4. Drop the new jar into ``plugins/`` and restart (or use Root-Core suite updater when this plugin is on your licensed manifest).")
    [void]$sb.AppendLine("5. Shared config and secrets live under ``plugins/RootMC/`` (not a per-plugin data folder unless documented otherwise).")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("### Dependencies")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("| Type | Plugins |")
    [void]$sb.AppendLine("|------|---------|")
    [void]$sb.AppendLine("| Hard depend | $dependLine |")
    [void]$sb.AppendLine("| Soft depend | $softLine |")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("## Configuration")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("Most RootMC plugins store operator YAML under ``plugins/RootMC/``. After first boot, check that folder for new keys. Never commit live ``cloud.yml`` / database passwords to git.")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("## Build (monorepo)")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("Primary compilation is the private RootMC Gradle workspace. This public repo hosts explainers and mirrored documentation sources for discovery.")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("This module depends on ``rootrecord-common`` inside the monorepo.")
    [void]$sb.AppendLine()

    if ($meta.commands.Count -gt 0) {
        [void]$sb.AppendLine("## Commands (summary)")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("| Command | Description |")
        [void]$sb.AppendLine("|---------|-------------|")
        $n = 0
        foreach ($c in $meta.commands) {
            if ($n -ge 25) {
                [void]$sb.AppendLine()
                [void]$sb.AppendLine("_...and more - see [docs/COMMANDS.md](docs/COMMANDS.md)._")
                break
            }
            $cdesc = ($c.description -replace '\|', '/').Trim()
            [void]$sb.AppendLine("| ``/$($c.name)`` | $cdesc |")
            $n++
        }
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("Full command and permission tables: [docs/COMMANDS.md](docs/COMMANDS.md).")
    } else {
        [void]$sb.AppendLine("## Commands")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("See [docs/COMMANDS.md](docs/COMMANDS.md) (none declared in ``plugin.yml``, or runtime-only).")
    }
    [void]$sb.AppendLine()
    [void]$sb.AppendLine((Get-LinksMarkdown $FolderName $bukkit))
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("## Documentation in this repo")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("- [docs/COMMANDS.md](docs/COMMANDS.md) - commands and permissions from ``plugin.yml``")
    [void]$sb.AppendLine("- [docs/LINKS.md](docs/LINKS.md) - canonical RootMC web and Discord links")
    [void]$sb.AppendLine("- [CHANGELOG.md](CHANGELOG.md) - version history seed")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("## License")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("Copyright Root Record. All rights reserved. Public docs are for discovery; jars are distributed via BuiltByBit / licensed channels only.")
    return $sb.ToString()
}

function Get-LicenseText {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("Copyright (c) Root Record. All rights reserved.")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("This source code and associated documentation files are published for")
    [void]$sb.AppendLine("transparency and operator reference for the RootMC / Root Record Minecraft")
    [void]$sb.AppendLine("plugin suite.")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("No license is granted to use, copy, modify, merge, publish, distribute,")
    [void]$sb.AppendLine("sublicense, and/or sell copies of the Software, except as Root Record may")
    [void]$sb.AppendLine("expressly permit in writing or via a separate agreement.")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.')
    return $sb.ToString()
}

function Get-GitignoreText {
    $sb = New-Object System.Text.StringBuilder
    foreach ($line in @(
        ".gradle/", "build/", "out/", "bin/", ".idea/", "*.iml", "*.ipr", "*.iws",
        ".classpath", ".project", ".settings/", "*.class", "*.log", ".DS_Store",
        "Thumbs.db", "local.properties", ".env", "*.jks", "*.keystore", ".publish-meta.json"
    )) {
        [void]$sb.AppendLine($line)
    }
    return $sb.ToString()
}

function Stage-OnePlugin($dir) {
    $name = $dir.Name
    $dest = Join-Path $StagingRoot $name
    Write-Host "STAGE $name -> $dest"

    New-Item -ItemType Directory -Force -Path $dest | Out-Null

    # Copy source tree (exclude build outputs)
    $robolog = Join-Path $env:TEMP "rr-stage-$name.log"
    & robocopy $dir.FullName $dest /E /XD build .gradle out bin .idea /XF *.class /NFL /NDL /NJH /NJS /nc /ns /np | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy failed for $name (code $LASTEXITCODE)" }

    $version = Get-GradleVersion $dir.FullName
    $ymlPath = Join-Path $dir.FullName "src\main\resources\plugin.yml"
    $meta = Parse-PluginYml $ymlPath
    $isCommon = ($name -eq "rootrecord-common")

    $docsDir = Join-Path $dest "docs"
    New-Item -ItemType Directory -Force -Path $docsDir | Out-Null

    $readmePath = Join-Path $dest "README.md"
    if ($Force -or -not (Test-Path $readmePath) -or (Get-Item $readmePath).Length -lt 500) {
        # Always regenerate for publish consistency (plan: extensive docs)
        Set-Content -LiteralPath $readmePath -Value (Get-ReadmeMarkdown $name $version $meta $isCommon) -Encoding UTF8
    } else {
        # Still regenerate - plan wants extensive generated docs on every stage
        Set-Content -LiteralPath $readmePath -Value (Get-ReadmeMarkdown $name $version $meta $isCommon) -Encoding UTF8
    }

    Set-Content -LiteralPath (Join-Path $docsDir "COMMANDS.md") -Value (Get-CommandsMarkdown $meta) -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $docsDir "LINKS.md") -Value (Get-LinksMarkdown $name $(if ($meta.name) { $meta.name } else { $name })) -Encoding UTF8

    $bukkit = if ($meta.name) { $meta.name } else { $name }
    $csb = New-Object System.Text.StringBuilder
    [void]$csb.AppendLine("# Changelog")
    [void]$csb.AppendLine()
    [void]$csb.AppendLine("## $version")
    [void]$csb.AppendLine()
    [void]$csb.AppendLine("- Published GitHub source repository and release for **$bukkit** (``$name``).")
    [void]$csb.AppendLine("- Artifact: ``$name-$version.jar``")
    [void]$csb.AppendLine("- Catalog: https://rootmc.net/plugins/")
    [void]$csb.AppendLine("- Discord: https://discord.gg/rFFQYrNaqS")
    Set-Content -LiteralPath (Join-Path $dest "CHANGELOG.md") -Value $csb.ToString() -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $dest "LICENSE") -Value (Get-LicenseText) -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $dest ".gitignore") -Value (Get-GitignoreText) -Encoding UTF8

    # Meta for later steps
    $metaOut = [ordered]@{
        folder      = $name
        version     = $version
        bukkitName  = $bukkit
        description = $meta.description
        isCommon    = $isCommon
        hasPluginYml = [bool](Test-Path -LiteralPath $ymlPath)
    }
    ($metaOut | ConvertTo-Json) | Set-Content -LiteralPath (Join-Path $dest ".publish-meta.json") -Encoding UTF8
}

function Push-OneRepo($name) {
    $dest = Join-Path $StagingRoot $name
    if (-not (Test-Path -LiteralPath $dest)) { throw "Missing staging for $name" }

    Push-Location $dest
    try {
        $meta = Get-Content -LiteralPath (Join-Path $dest ".publish-meta.json") -Raw | ConvertFrom-Json
        $desc = [string]$meta.description
        if (-not $desc) { $desc = "RootMC plugin: $name" }
        # GitHub description must be ASCII-safe (UTF-8 fancy dashes become mojibake via some shells)
        foreach ($pair in @(
            @([char]0x2014, "-"), @([char]0x2013, "-"), @([char]0x2212, "-"),
            @([char]0x2018, "'"), @([char]0x2019, "'"),
            @([char]0x201C, '"'), @([char]0x201D, '"')
        )) { $desc = $desc.Replace($pair[0], $pair[1]) }
        $sbAscii = New-Object System.Text.StringBuilder
        foreach ($ch in $desc.ToCharArray()) {
            $code = [int]$ch
            if ($code -ge 32 -and $code -le 126) { [void]$sbAscii.Append($ch) }
            else { [void]$sbAscii.Append("-") }
        }
        $desc = $sbAscii.ToString()
        while ($desc.Contains("--")) { $desc = $desc.Replace("--", "-") }
        $desc = ($desc -replace "\s+-\s+", " - ").Trim().Trim("-").Trim()
        if ($desc.Length -gt 350) { $desc = $desc.Substring(0, 347) + "..." }

        if (-not (Test-Path -LiteralPath (Join-Path $dest ".git"))) {
            git init -b main | Out-Null
        }

        # Exclude .publish-meta.json from git (also listed in .gitignore)
        $gi = Get-Content -LiteralPath (Join-Path $dest ".gitignore") -Raw
        if ($gi -notmatch '\.publish-meta\.json') {
            Add-Content -LiteralPath (Join-Path $dest ".gitignore") -Value "`n.publish-meta.json`n"
        }

        git add -A
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $status = git status --porcelain
        if ($status) {
            git -c user.email="dev@rootrecord.info" -c user.name="RootRecord" commit -m "Publish $name $($meta.version) source and documentation."
            if ($LASTEXITCODE -ne 0) { throw "git commit failed for $name" }
        }
        $ErrorActionPreference = $prevEap

        $exists = $false
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        gh repo view "$Owner/$name" 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $exists = $true }
        $ErrorActionPreference = $prevEap

        if (-not $exists) {
            Write-Host "CREATE $Owner/$name"
            $ErrorActionPreference = "Continue"
            gh repo create "$Owner/$name" --public --source=. --remote=origin --push
            if ($LASTEXITCODE -ne 0) { throw "gh repo create failed for $name" }
            $ErrorActionPreference = $prevEap
        } else {
            Write-Host "PUSH existing $Owner/$name"
            $remotes = git remote
            if ($remotes -notcontains "origin") {
                git remote add origin "https://github.com/$Owner/$name.git"
            }
            $ErrorActionPreference = "Continue"
            git push -u origin HEAD:main
            if ($LASTEXITCODE -ne 0) { throw "git push failed for $name" }
            $ErrorActionPreference = $prevEap
        }

        $ErrorActionPreference = "Continue"
        gh repo edit "$Owner/$name" --add-topic minecraft --add-topic paper-plugin --add-topic rootmc 2>$null | Out-Null
        gh repo edit "$Owner/$name" --description "$desc" 2>$null | Out-Null
        gh repo edit "$Owner/$name" --homepage "https://rootmc.net/plugins/$name/" 2>$null | Out-Null
        $ErrorActionPreference = $prevEap
    }
    finally {
        Pop-Location
    }
}

function Find-Jar([string]$Name, [string]$Version) {
    $candidates = @(
        (Join-Path $PluginsRoot "$Name\build\libs\$Name-$Version.jar"),
        (Join-Path $WorkspaceRoot "Server Handoffs\2. RootMC - Towny\plugins\$Name-$Version.jar"),
        (Join-Path $WorkspaceRoot "Server Handoffs\1. RootMC - Claims\plugins\$Name-$Version.jar"),
        (Join-Path $WorkspaceRoot "Web Files\rootmc-web\public\plugins\$Name-$Version.jar"),
        (Join-Path $MinecraftRoot "out\$Name-$Version.jar")
    )
    foreach ($c in $candidates) {
        if (Test-Path -LiteralPath $c) { return $c }
    }
    # Any versioned jar in build/libs
    $libs = Join-Path $PluginsRoot "$Name\build\libs"
    if (Test-Path $libs) {
        $hit = Get-ChildItem -LiteralPath $libs -Filter "$Name-*.jar" -File -EA SilentlyContinue |
            Where-Object { $_.Name -notmatch "(-plain|-sources|-javadoc)\.jar$" } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}

function Release-OnePlugin($dir) {
    $name = $dir.Name
    $version = Get-GradleVersion $dir.FullName
    $tag = "v$version"
    $ymlPath = Join-Path $dir.FullName "src\main\resources\plugin.yml"
    $meta = Parse-PluginYml $ymlPath
    $bukkit = if ($meta.name) { $meta.name } else { $name }
    $jar = Find-Jar $name $version

    if (-not $jar) {
        Write-Warning "No jar for $name $version - skip release"
        return $false
    }

    $jarLeaf = Split-Path $jar -Leaf
    $nsb = New-Object System.Text.StringBuilder
    [void]$nsb.AppendLine("## $bukkit $version")
    [void]$nsb.AppendLine()
    [void]$nsb.AppendLine([string]$meta.description)
    [void]$nsb.AppendLine()
    [void]$nsb.AppendLine("### Download")
    [void]$nsb.AppendLine()
    [void]$nsb.AppendLine("- **Paid jar:** [BuiltByBit](https://builtbybit.com/) (listing coming soon)")
    [void]$nsb.AppendLine("- This GitHub Release documents the version only - jar assets are not attached.")
    [void]$nsb.AppendLine("- Docs / Discord: https://rootmc.net/plugins/ · https://discord.gg/rFFQYrNaqS")
    [void]$nsb.AppendLine()
    [void]$nsb.AppendLine("### Install")
    [void]$nsb.AppendLine()
    [void]$nsb.AppendLine("1. Obtain ``$jarLeaf`` from BuiltByBit / your licensed channel.")
    [void]$nsb.AppendLine("2. Install Root-Core first when required.")
    [void]$nsb.AppendLine("3. Replace any older ``$name-*.jar`` in ``plugins/`` and restart.")
    [void]$nsb.AppendLine()
    [void]$nsb.AppendLine("See the repository README for the full explainer.")
    $notes = $nsb.ToString()
    $notesFile = Join-Path $env:TEMP "rr-release-notes-$name.md"
    Set-Content -LiteralPath $notesFile -Value $notes -Encoding UTF8

    # Prefer notes-only releases (no free jar attach) for BuiltByBit monetization.
    $attachJar = $false
    if ($env:ROOTMC_GH_ATTACH_JARS -eq "1") { $attachJar = $true }

    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    gh release view $tag --repo "$Owner/$name" 2>$null | Out-Null
    $releaseExists = ($LASTEXITCODE -eq 0)
    if ($releaseExists) {
        if ($Force) {
            Write-Host "DELETE release $Owner/$name $tag"
            gh release delete $tag --repo "$Owner/$name" --yes --cleanup-tag 2>$null | Out-Null
        } else {
            # Update notes; strip jar assets if present
            gh release edit $tag --repo "$Owner/$name" --notes-file $notesFile 2>$null | Out-Null
            $relJson = gh api "repos/$Owner/$name/releases/tags/$tag" 2>$null
            if ($LASTEXITCODE -eq 0 -and $relJson) {
                $rel = $relJson | ConvertFrom-Json
                foreach ($a in @($rel.assets)) {
                    if ($a.name -like "*.jar") {
                        Write-Host "DELETE asset $($a.name) on $Owner/$name $tag"
                        gh api -X DELETE "repos/$Owner/$name/releases/assets/$($a.id)" 2>$null | Out-Null
                    }
                }
            }
            Write-Host "UPDATED release notes / stripped jars $Owner/$name $tag"
            $ErrorActionPreference = $prevEap
            return $true
        }
    }

    Write-Host "RELEASE $Owner/$name $tag (notes-only)"
    if ($attachJar) {
        gh release create $tag --repo "$Owner/$name" --title "$bukkit $version" --notes-file $notesFile "$jar"
    } else {
        gh release create $tag --repo "$Owner/$name" --title "$bukkit $version" --notes-file $notesFile
    }
    $okRel = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $prevEap
    if (-not $okRel) {
        Write-Warning "release create failed for $name"
        return $false
    }
    return $true
}

# ---- main ----
Write-Host "Workspace: $WorkspaceRoot"
$folders = @(Get-PluginFolders)
Write-Host "Plugins: $($folders.Count)"

if ($StageOnly) {
    New-Item -ItemType Directory -Force -Path $StagingRoot | Out-Null
    foreach ($d in $folders) { Stage-OnePlugin $d }
    Write-Host "Staging complete."
}

if ($PushRepos) {
    # Prefer RootRecord account
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    gh auth status 2>&1 | Out-Host
    $ErrorActionPreference = $prevEap
    $pushOk = 0
    $pushFail = @()
    foreach ($d in $folders) {
        try {
            Push-OneRepo $d.Name
            $pushOk++
        } catch {
            Write-Warning "Push failed for $($d.Name): $_"
            $pushFail += $d.Name
        }
    }
    Write-Host "Push complete. ok=$pushOk fail=$($pushFail.Count) $($pushFail -join ',')"
}

if ($BuildJars) {
    Push-Location $MinecraftRoot
    try {
        Write-Host "Building all plugin jars..."
        $targets = ($folders | ForEach-Object { ":plugins:$($_.Name):jar" }) -join " "
        cmd /c "build-with-server-jdk.bat $targets"
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Full batch build exited $LASTEXITCODE - retrying via gradlew.bat"
            & .\gradlew.bat ($folders | ForEach-Object { ":plugins:$($_.Name):jar" }) --no-daemon
            if ($LASTEXITCODE -ne 0) { throw "Gradle jar build failed: $LASTEXITCODE" }
        }
    }
    finally {
        Pop-Location
    }
    Write-Host "Build complete."
}

if ($CreateReleases) {
    $ok = 0; $fail = @()
    foreach ($d in $folders) {
        try {
            if (Release-OnePlugin $d) { $ok++ } else { $fail += $d.Name }
        } catch {
            Write-Warning "Release exception $($d.Name): $_"
            $fail += $d.Name
        }
    }
    Write-Host "Releases ok=$ok fail=$($fail.Count) $($fail -join ',')"
}

if ($WriteIndex) {
    $indexPath = Join-Path $WorkspaceRoot "github-plugin-repos\REPO-INDEX.md"
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("# RootMC plugin GitHub repositories")
    [void]$sb.AppendLine()
    $when = Get-Date -Format "yyyy-MM-dd HH:mm"
    [void]$sb.AppendLine("Owner: **$Owner** - Generated $when")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('| Plugin | Version | Repo | Release | Catalog |')
    [void]$sb.AppendLine('|--------|---------|------|---------|---------|')
    foreach ($d in $folders) {
        $v = Get-GradleVersion $d.FullName
        $n = $d.Name
        $row = '| `{0}` | `{1}` | https://github.com/{2}/{0} | https://github.com/{2}/{0}/releases/tag/v{1} | https://rootmc.net/plugins/{0}/ |' -f $n, $v, $Owner
        [void]$sb.AppendLine($row)
    }
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("## Links")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("- Discord: https://discord.gg/rFFQYrNaqS")
    [void]$sb.AppendLine("- Site: https://rootmc.net")
    [void]$sb.AppendLine("- Plugins: https://rootmc.net/plugins/")
    [void]$sb.AppendLine("- Manifest: https://rootmc.net/plugins/manifest.json")
    New-Item -ItemType Directory -Force -Path (Split-Path $indexPath) | Out-Null
    Set-Content -LiteralPath $indexPath -Value $sb.ToString() -Encoding UTF8
    Write-Host "Wrote $indexPath"
}

Write-Host "Done."
