# Fix GitHub repo descriptions: strip non-ASCII / mojibake dashes
$ErrorActionPreference = "Continue"
$pluginsRoot = "D:\.1 Work Stations\RootMC\Plugin Building\Minecraft\plugins"

function Get-AsciiDesc([string]$pluginDir) {
    $yml = Join-Path $pluginDir "src\main\resources\plugin.yml"
    $desc = ""
    if (Test-Path -LiteralPath $yml) {
        foreach ($line in Get-Content -LiteralPath $yml -Encoding UTF8) {
            if ($line -match "^description:\s*(.+)$") {
                $desc = $Matches[1].Trim().Trim("'").Trim('"')
                break
            }
        }
    }
    if (-not $desc) { $desc = "RootMC plugin: $(Split-Path $pluginDir -Leaf)" }

    # Unicode dashes / ellipsis -> ASCII hyphen
    $desc = $desc.Replace([char]0x2014, "-")  # em dash
    $desc = $desc.Replace([char]0x2013, "-")  # en dash
    $desc = $desc.Replace([char]0x2212, "-")  # minus
    $desc = $desc.Replace([string][char]0x2026, "...") # ellipsis
    $desc = $desc.Replace([char]0x2018, "'").Replace([char]0x2019, "'")
    $desc = $desc.Replace([char]0x201C, '"').Replace([char]0x201D, '"')

    # Drop any remaining non-ASCII (covers mojibake like a-circumflex sequences)
    $chars = New-Object System.Text.StringBuilder
    foreach ($ch in $desc.ToCharArray()) {
        $code = [int]$ch
        if ($code -ge 32 -and $code -le 126) {
            [void]$chars.Append($ch)
        } elseif ($code -eq 9 -or $code -eq 10 -or $code -eq 13) {
            [void]$chars.Append(" ")
        } else {
            [void]$chars.Append("-")
        }
    }
    $desc = $chars.ToString()
    while ($desc.Contains("--")) { $desc = $desc.Replace("--", "-") }
    $desc = ($desc -replace "\s+-\s+", " - ").Trim()
    $desc = ($desc -replace "\s{2,}", " ").Trim().Trim("-").Trim()
    if ($desc.Length -gt 350) { $desc = $desc.Substring(0, 347) + "..." }
    return $desc
}

$fixed = 0
$fail = @()
Get-ChildItem -LiteralPath $pluginsRoot -Directory | ForEach-Object {
    $name = $_.Name
    $newDesc = Get-AsciiDesc $_.FullName
    Write-Host "FIX $name => $newDesc"
    gh repo edit "RootRecord/$name" --description $newDesc | Out-Null
    if ($LASTEXITCODE -eq 0) { $fixed++ } else { $fail += $name }
}
Write-Host "fixed=$fixed fail=$($fail.Count) $($fail -join ',')"
Write-Host "--- verify ---"
gh api repos/RootRecord/roothelp --jq .description
gh api repos/RootRecord/root-haste --jq .description
gh api repos/RootRecord/root-ops --jq .description
