# Ensure every Work Stations .env on D: also exists on E: (copy, never move).
# Does NOT print secret values.
#
# Usage:
#   powershell -File "D:\.1 Work Stations\RootMC\scripts\ensure-env-on-e.ps1"
$ErrorActionPreference = "Continue"
$srcRoot = "D:\.1 Work Stations"
$dstRoot = "E:\.1 Work Stations"
$logDir = "E:\windows backup\logs"
New-Item -ItemType Directory -Force -Path $dstRoot, $logDir | Out-Null
$log = Join-Path $logDir "ensure-env-on-e-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"

function W([string]$m) {
  $line = "$(Get-Date -Format 'HH:mm:ss')  $m"
  Write-Host $line
  Add-Content -LiteralPath $log -Value $line -Encoding utf8
}

if (-not (Test-Path -LiteralPath $srcRoot)) { throw "Missing $srcRoot" }

W "START ensure-env-on-e (copy only; D: intact)"
$files = Get-ChildItem -LiteralPath $srcRoot -Recurse -Force -File -EA SilentlyContinue |
  Where-Object {
    (
      $_.Name -eq '.env' -or
      $_.Name -like '.env.*' -or
      $_.Name -eq 'solana.env' -or
      ($_.Name -like '*.env' -and $_.Name -notlike '*.env.example')
    ) -and
    $_.FullName -notmatch '\\node_modules\\|\\\.git\\|\\halted-development\\'
  }

$copied = 0
$same = 0
foreach ($f in $files) {
  $rel = $f.FullName.Substring($srcRoot.Length).TrimStart('\')
  $dest = Join-Path $dstRoot $rel
  $destDir = Split-Path $dest -Parent
  New-Item -ItemType Directory -Force -Path $destDir | Out-Null
  $need = $true
  if (Test-Path -LiteralPath $dest) {
    $d = Get-Item -LiteralPath $dest
    if ($d.Length -eq $f.Length -and $d.LastWriteTimeUtc -ge $f.LastWriteTimeUtc.AddSeconds(-2)) {
      $need = $false
      $same++
    }
  }
  if ($need) {
    Copy-Item -LiteralPath $f.FullName -Destination $dest -Force
    $copied++
    W "COPY $rel ($($f.Length) bytes)"
  }
}

# Manifest of paths only (no values)
$manifest = Join-Path $dstRoot "RootMC\Server Handoffs\Ava Ivy\notes\env-paths-on-e.txt"
$manifestDir = Split-Path $manifest -Parent
New-Item -ItemType Directory -Force -Path $manifestDir | Out-Null
@(
  "Generated $(Get-Date -Format o)",
  "Source $srcRoot -> $dstRoot (copy, never move)",
  ""
) + @(
  $files | ForEach-Object {
    $rel = $_.FullName.Substring($srcRoot.Length).TrimStart('\')
    $onE = Test-Path (Join-Path $dstRoot $rel)
    "{0}`tbytes={1}`tonE={2}" -f $rel, $_.Length, $onE
  }
) | Set-Content -LiteralPath $manifest -Encoding utf8

W "DONE copied=$copied alreadySame=$same total=$($files.Count)"
W "Manifest $manifest"
W "D: and originals untouched."

# Also stage a sealed bundle folder on E for device install (still copies)
$bundle = "E:\windows backup\env-bundle"
New-Item -ItemType Directory -Force -Path $bundle | Out-Null
$priority = @(
  ".credentials\.env",
  ".credentials\solana.env",
  "RootMC\.env",
  "RootMC\Plugin Building\Minecraft\.env"
)
foreach ($rel in $priority) {
  $s = Join-Path $srcRoot $rel
  if (Test-Path -LiteralPath $s) {
    $d = Join-Path $bundle ($rel -replace '\\', '__')
    Copy-Item -LiteralPath $s -Destination $d -Force
    W "BUNDLE $($rel) -> env-bundle"
  }
}
@"
Env bundle for Ubuntu device copy.
On Linux after mount:
  sudo bash "/mnt/e/.1 Work Stations/RootMC/scripts/sync-env-to-device.sh"
Or copy from: /mnt/e/windows backup/env-bundle/
Source trees on E stay intact.
"@ | Set-Content (Join-Path $bundle "README.txt") -Encoding utf8

W "Bundle $bundle"
