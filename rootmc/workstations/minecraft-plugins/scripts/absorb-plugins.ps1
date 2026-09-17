# Absorb retired plugin sources into host jars (host-plugin pattern).
# Usage: run from workspace; idempotent if dest packages already exist.

$ErrorActionPreference = "Stop"
$minecraft = "D:\.1 Work Stations\RootMC\Plugin Building\Minecraft\plugins"
$gen2 = "D:\.1 Work Stations\RootMC\gen2-27\Plugin Building\Minecraft\plugins"

function Copy-JavaTree($srcPlugin, $dstPlugin) {
    $srcJava = Join-Path $minecraft "$srcPlugin\src\main\java"
    $dstJava = Join-Path $minecraft "$dstPlugin\src\main\java"
    if (-not (Test-Path $srcJava)) { throw "Missing $srcJava" }
    Copy-Item -Recurse -Force (Join-Path $srcJava "*") $dstJava
    $srcRes = Join-Path $minecraft "$srcPlugin\src\main\resources"
    $dstRes = Join-Path $minecraft "$dstPlugin\src\main\resources"
    Get-ChildItem $srcRes -File | Where-Object { $_.Name -ne "plugin.yml" } | ForEach-Object {
        Copy-Item -Force $_.FullName (Join-Path $dstRes $_.Name)
    }
    Write-Output "Copied $srcPlugin -> $dstPlugin"
}

function Convert-MainToFeature($javaFile, $className) {
    $text = Get-Content -Raw $javaFile
    if ($text -notmatch "extends JavaPlugin") {
        Write-Output "Skip (already converted?): $javaFile"
        return
    }
    $text = $text -replace "extends JavaPlugin", ""
    # Insert host field after class opening brace (first { after class)
    $pattern = "(public final class $className\s*\{)"
    $replacement = @"
public final class $className {
    private final org.bukkit.plugin.java.JavaPlugin host;

    public $className(org.bukkit.plugin.java.JavaPlugin host) {
        this.host = host;
    }

    public org.bukkit.plugin.java.JavaPlugin host() { return host; }
    public org.bukkit.plugin.Plugin getPlugin() { return host; }
    public java.util.logging.Logger getLogger() { return host.getLogger(); }
    public org.bukkit.Server getServer() { return host.getServer(); }
    public java.io.File getDataFolder() { return host.getDataFolder(); }
    public org.bukkit.command.PluginCommand getCommand(String name) { return host.getCommand(name); }
    public org.bukkit.plugin.PluginDescriptionFile getDescription() { return host.getDescription(); }
    public java.io.InputStream getResource(String path) { return host.getResource(path); }
    public void saveResource(String path, boolean replace) { host.saveResource(path, replace); }
    public org.bukkit.scheduler.BukkitScheduler getScheduler() { return host.getServer().getScheduler(); }
"@
    if ($text -match $pattern) {
        $text = [regex]::Replace($text, $pattern, [System.Text.RegularExpressions.MatchEvaluator]{
            param($m)
            return $replacement
        }, 1)
    }
    # Remove @Override onEnable/onDisable that conflict conceptually — keep method names enable()/disable()
    $text = $text -replace "@Override\s+public void onEnable\(\)", "public void enable()"
    $text = $text -replace "@Override\s+public void onDisable\(\)", "public void disable()"
    # registerEvents(this) where this was Plugin — use host
    $text = $text -replace "registerEvents\(([^,]+),\s*this\)", "registerEvents(`$1, host)"
    $text = $text -replace "runTask\(this,", "runTask(host,"
    $text = $text -replace "runTaskAsynchronously\(this,", "runTaskAsynchronously(host,"
    $text = $text -replace "runTaskLater\(this,", "runTaskLater(host,"
    $text = $text -replace "runTaskTimer\(this,", "runTaskTimer(host,"
    $text = $text -replace "runTaskLaterAsynchronously\(this,", "runTaskLaterAsynchronously(host,"
    $text = $text -replace "runTaskTimerAsynchronously\(this,", "runTaskTimerAsynchronously(host,"
    $text = $text -replace "ServicesManager\(\)\.register\(([^)]+),\s*this,", "ServicesManager().register(`$1, host,"
    $text = $text -replace "getPluginManager\(\)\.registerEvents\(([^,]+),\s*this\)", "getPluginManager().registerEvents(`$1, host)"
    Set-Content -Path $javaFile -Value $text -NoNewline
    Write-Output "Converted $className"
}

# Phase 2: Activity -> Times
Copy-JavaTree "root-activity" "root-times"
Convert-MainToFeature (Join-Path $minecraft "root-times\src\main\java\com\rootrecord\minecraft\rootactivity\RootActivityPlugin.java") "RootActivityPlugin"

# Phase 3: Spawn/Banner/Potions -> Essentials
foreach ($p in @(@{src='root-spawn'; cls='RootSpawnPlugin'}, @{src='root-banner'; cls='RootBannerPlugin'}, @{src='root-potions'; cls='RootPotionsPlugin'})) {
    Copy-JavaTree $p.src "root-essentials"
    $main = Get-ChildItem (Join-Path $minecraft "root-essentials\src\main\java") -Recurse -Filter "$($p.cls).java" | Select-Object -First 1
    Convert-MainToFeature $main.FullName $p.cls
}

# Phase 4 econ
foreach ($p in @(
    @{src='rootmc-shops'; cls='RootMcShopsPlugin'},
    @{src='root-bonds'; cls='RootBondsPlugin'},
    @{src='root-upkeep'; cls='RootUpkeepPlugin'},
    @{src='root-loans'; cls='RootLoansPlugin'},
    @{src='root-tokens'; cls='RootTokensPlugin'}
)) {
    Copy-JavaTree $p.src "root-essentials"
    $main = Get-ChildItem (Join-Path $minecraft "root-essentials\src\main\java") -Recurse -Filter "$($p.cls).java" | Select-Object -First 1
    if ($main) { Convert-MainToFeature $main.FullName $p.cls }
}

# Phase 5: create root-play skeleton + copy ranks/rewards/help/road
$play = Join-Path $minecraft "root-play"
if (-not (Test-Path $play)) {
    New-Item -ItemType Directory -Path "$play\src\main\java","$play\src\main\resources" -Force | Out-Null
}
@"
plugins { java }
version = "1.0.0"
repositories { maven("https://jitpack.io") }
dependencies {
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("net.luckperms:api:5.4")
}
tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}
"@ | Set-Content "$play\build.gradle.kts"

foreach ($p in @(
    @{src='root-ranks'; cls='RootRanksPlugin'},
    @{src='root-rewards'; cls='RootRewardsPlugin'},
    @{src='roothelp'; cls='RootHelpPlugin'}
)) {
    Copy-JavaTree $p.src "root-play"
    $main = Get-ChildItem (Join-Path $minecraft "root-play\src\main\java") -Recurse -Filter "$($p.cls).java" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($main) { Convert-MainToFeature $main.FullName $p.cls }
}

# Road from gen2
$roadSrc = Join-Path $gen2 "root-road\src\main\java"
$roadDst = Join-Path $minecraft "root-play\src\main\java"
if (Test-Path $roadSrc) {
    Copy-Item -Recurse -Force (Join-Path $roadSrc "*") $roadDst
    Get-ChildItem (Join-Path $gen2 "root-road\src\main\resources") -File | Where-Object { $_.Name -ne "plugin.yml" } | ForEach-Object {
        Copy-Item -Force $_.FullName (Join-Path $minecraft "root-play\src\main\resources\$($_.Name)")
    }
    $roadMain = Get-ChildItem $roadDst -Recurse -Filter "RootRoadPlugin.java" | Select-Object -First 1
    if ($roadMain) { Convert-MainToFeature $roadMain.FullName "RootRoadPlugin" }
    Write-Output "Copied root-road from gen2"
}

# Phase 6: root-ops
$ops = Join-Path $minecraft "root-ops"
if (-not (Test-Path $ops)) {
    New-Item -ItemType Directory -Path "$ops\src\main\java","$ops\src\main\resources" -Force | Out-Null
}
@"
plugins { java }
version = "1.0.0"
repositories { maven("https://jitpack.io") }
dependencies {
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
}
tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}
"@ | Set-Content "$ops\build.gradle.kts"

foreach ($p in @(
    @{src='root-admin'; cls='RootAdminPlugin'},
    @{src='root-restart'; cls='RootRestartPlugin'},
    @{src='root-announcer'; cls='RootAnnouncerPlugin'},
    @{src='root-mapper'; cls='RootMapperPlugin'}
)) {
    Copy-JavaTree $p.src "root-ops"
    $main = Get-ChildItem (Join-Path $minecraft "root-ops\src\main\java") -Recurse -Filter "$($p.cls).java" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($main) { Convert-MainToFeature $main.FullName $p.cls }
}

Write-Output "ABSORB COPY DONE"
