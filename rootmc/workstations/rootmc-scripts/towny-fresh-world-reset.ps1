# Towny fresh map: clear claims + spawns; keep towns/nations/residents/banks.
# Run from PC with server stopped or after (then /townyadmin reload database).
#
#   powershell -File scripts\towny-fresh-world-reset.ps1 -DryRun
#   powershell -File scripts\towny-fresh-world-reset.ps1

param([switch]$DryRun)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
$handoff = Join-Path $PWD "Server Handoffs\2. RootMC - Towny"
$dbPath = Join-Path $handoff "plugins\Towny\settings\database.yml"
if (-not (Test-Path $dbPath)) { throw "database.yml not found" }
$yaml = Get-Content $dbPath -Raw
function Get-DbVal($k) {
    if ($yaml -match "(?m)^\s*" + [regex]::Escape($k) + ":\s*'?([^'\r\n]+)'?\s*`$") { return $Matches[1].Trim() }
    return $null
}
$dryPy = if ($DryRun) { "True" } else { "False" }
$py = @"
import pymysql, sys
dry = $dryPy
conn = pymysql.connect(
    host="$($(Get-DbVal hostname))",
    port=int("$($(Get-DbVal port))" or 3306),
    user="$($(Get-DbVal username))",
    password="$($(Get-DbVal password))",
    database="$($(Get-DbVal dbname))",
    charset="utf8mb4",
    autocommit=False,
)
cur = conn.cursor()
actions = []

cur.execute("SELECT COUNT(*) FROM TOWNY_TOWNBLOCKS")
actions.append(("townblocks cleared", cur.fetchone()[0]))
if not dry:
    cur.execute("DELETE FROM TOWNY_TOWNBLOCKS")

for col in ("homeblock", "spawn", "outpostSpawns"):
    cur.execute(f"UPDATE TOWNY_TOWNS SET `{col}`='' WHERE `{col}` IS NOT NULL AND `{col}` != ''")
    actions.append((f"TOWNS.{col} cleared", cur.rowcount))

cur.execute("UPDATE TOWNY_NATIONS SET nationSpawn='' WHERE nationSpawn IS NOT NULL AND nationSpawn != ''")
actions.append(("NATIONS.nationSpawn cleared", cur.rowcount))

cur.execute("DELETE FROM TOWNY_WORLDS WHERE name='world-old'")
actions.append(("TOWNY_WORLDS world-old removed", cur.rowcount))

for table in ("root_homes",):
    cur.execute(f"DELETE FROM `{table}` WHERE world_name IN ('world-old','world')")
    actions.append((f"{table} stale homes removed", cur.rowcount))

if not dry:
    conn.commit()
conn.close()
for label, n in actions:
    prefix = "[dry-run] " if dry else ""
    print(f"{prefix}{label}: {n}")
print("Done." if not dry else "Dry run complete.")
"@
$tmp = Join-Path $env:TEMP "towny-fresh-world.py"
Set-Content $tmp $py -Encoding UTF8
python -c "import pymysql" 2>$null; if ($LASTEXITCODE -ne 0) { pip install pymysql -q }
python $tmp
Remove-Item $tmp -Force -ErrorAction SilentlyContinue
