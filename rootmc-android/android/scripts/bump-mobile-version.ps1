# Shared "bump patch version" helper used by every Mobile/<app>/bump-and-build-release.bat.
#
# Behaviour (single rule for every RootRecord app, matches Google Play "Release N (MAJOR.MINOR.N)"):
#   * Read current versionCode from the Android Gradle build file (Groovy `versionCode 16` or
#     Kotlin DSL `versionCode = 16`).
#   * Increment versionCode by 1.
#   * Keep MAJOR.MINOR from the existing versionName, replace PATCH with the new versionCode, e.g.
#       1.0.16 (code 16) -> 1.0.17 (code 17)
#       0.1.3  (code 5)  -> 0.1.6  (code 6)     ← one-time alignment when PATCH != versionCode
#   * Write the new versionCode + versionName back into the Gradle file.
#   * If -WrapperPackageJson / -WebPackageJson are given, surgically replace their `"version": "..."`
#     field with the new versionName (so About pages and `cap sync` agree). Kilauea (native Kotlin
#     only) calls this without the package.json paths.
#
# Output: prints the new versionName to stdout (single line, no decoration). The .bat captures it
# into %VER% and uses it as the artifact filename suffix. Everything diagnostic goes to stderr via
# Write-Host so it does NOT contaminate the .bat's `for /f` capture.

param(
    [string]$WrapperPackageJson,
    [string]$WebPackageJson,
    [Parameter(Mandatory = $true)][string]$BuildGradle
)

$ErrorActionPreference = 'Stop'

# Write UTF-8 WITHOUT a BOM. Windows PowerShell 5.1's `Set-Content -Encoding UTF8` writes a BOM,
# which breaks JSON parsers (webpack/react-scripts choke with "Unexpected token '\ufeff'" when
# package.json starts with one). Use .NET directly so behaviour is identical on PS 5.1 and PS 7+.
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
function Write-Utf8NoBom([string]$Path, [string]$Content) {
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

if (-not (Test-Path -LiteralPath $BuildGradle)) {
    throw "bump-mobile-version.ps1: BuildGradle not found at $BuildGradle"
}

# Auto-detect Groovy (.gradle) vs Kotlin DSL (.gradle.kts). The "=" is optional in Groovy and
# required in Kotlin DSL; matching both with a single regex keeps a single code path.
$isKts = $BuildGradle.ToLower().EndsWith('.kts')
$codePattern = if ($isKts) { 'versionCode\s*=\s*\d+' }       else { 'versionCode\s+\d+' }
$namePattern = if ($isKts) { 'versionName\s*=\s*"[^"]+"' }   else { 'versionName\s+"[^"]+"' }
$codeReadPattern = if ($isKts) { 'versionCode\s*=\s*(\d+)' } else { 'versionCode\s+(\d+)' }
$nameReadPattern = if ($isKts) { 'versionName\s*=\s*"([^"]+)"' } else { 'versionName\s+"([^"]+)"' }

$gradleText = Get-Content -Raw -LiteralPath $BuildGradle

$codeMatch = [regex]::Match($gradleText, $codeReadPattern)
$nameMatch = [regex]::Match($gradleText, $nameReadPattern)
if (-not $codeMatch.Success) { throw "bump-mobile-version.ps1: could not find versionCode in $BuildGradle" }
if (-not $nameMatch.Success) { throw "bump-mobile-version.ps1: could not find versionName in $BuildGradle" }

$oldCode = [int]$codeMatch.Groups[1].Value
$oldName = $nameMatch.Groups[1].Value
$newCode = $oldCode + 1

# Preserve MAJOR.MINOR; replace PATCH with newCode so Play's "Release N (M.M.N)" alignment holds.
$nameParts = @($oldName -split '\.')
while ($nameParts.Count -lt 3) { $nameParts += '0' }
$major = $nameParts[0]
$minor = $nameParts[1]
$newName = "$major.$minor.$newCode"

Write-Host "bump-mobile-version: $oldName (code $oldCode) -> $newName (code $newCode)"

# Write back to gradle. The new values are computed from -read- patterns above; the -replace uses
# the broader writeback patterns so spacing / quoting style of the original file is preserved
# (Groovy `versionCode 17` vs KTS `versionCode = 17`).
$newCodeLine = if ($isKts) { "versionCode = $newCode" }       else { "versionCode $newCode" }
$newNameLine = if ($isKts) { "versionName = `"$newName`"" }   else { "versionName `"$newName`"" }
$gradleText = [regex]::Replace($gradleText, $codePattern, $newCodeLine)
$gradleText = [regex]::Replace($gradleText, $namePattern, $newNameLine)
Write-Utf8NoBom $BuildGradle $gradleText

# Capacitor apps: mirror the version into the wrapper + web package.json so the About page and
# `cap sync` see the same value. Surgical regex so nothing else in the JSON formatting moves.
# Use the BOM-less writer above — JSON parsers (webpack/react-scripts) reject a leading BOM.
foreach ($pkg in @($WrapperPackageJson, $WebPackageJson)) {
    if ([string]::IsNullOrWhiteSpace($pkg)) { continue }
    if (-not (Test-Path -LiteralPath $pkg)) {
        throw "bump-mobile-version.ps1: package.json not found at $pkg"
    }
    $pkgText = (Get-Content -Raw -LiteralPath $pkg) -replace '"version"\s*:\s*"[^"]+"', "`"version`": `"$newName`""
    # Defensive: also strip any pre-existing BOM the source file might have carried in.
    if ($pkgText.Length -gt 0 -and $pkgText[0] -eq [char]0xFEFF) {
        $pkgText = $pkgText.Substring(1)
    }
    Write-Utf8NoBom $pkg $pkgText
}

# Single-line stdout for the .bat caller.
Write-Output $newName
