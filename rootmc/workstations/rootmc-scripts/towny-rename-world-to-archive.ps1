# Retarget Towny MySQL claims: "world" -> "world-old" after folder rename.
# Run from YOUR PC (not the game server). MySQL is on Shockbyte.
#
#   powershell -File scripts\towny-rename-world-to-archive.ps1 -DryRun
#   powershell -File scripts\towny-rename-world-to-archive.ps1
#
# Or paste Server Handoffs\2. RootMC - Towny\TOWNY-WORLD-RENAME.sql into phpMyAdmin.

param(
    [string]$HandoffRoot = "",
    [string]$FromWorld = "world",
    [string]$ToWorld = "world-old",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Find-HandoffRoot {
    param([string]$Explicit)
    if ($Explicit -and (Test-Path $Explicit)) { return (Resolve-Path $Explicit).Path }
    $candidates = @(
        (Join-Path $env:USERPROFILE "Desktop\Projects\RootMC Workspace\Server Handoffs\2. RootMC - Towny"),
        (Join-Path $env:USERPROFILE "Desktop\RootMC - Current")
    )
    foreach ($c in $candidates) {
        if (Test-Path (Join-Path $c "plugins\Towny\settings\database.yml")) {
            return (Resolve-Path $c).Path
        }
    }
    throw "Towny database.yml not found. Pass -HandoffRoot."
}

function Read-TownyDatabaseYaml([string]$Path) {
    $yaml = Get-Content -LiteralPath $Path -Raw
    $out = @{}
    foreach ($key in @("hostname", "port", "dbname", "table_prefix", "username", "password")) {
        $pattern = "(?m)^\s*" + [regex]::Escape($key) + ":\s*'?([^'\r\n]+)'?\s*`$"
        if ($yaml -match $pattern) {
            $out[$key] = $Matches[1].Trim()
        }
    }
    if (-not $out.hostname -or -not $out.dbname) { throw "Could not parse Towny database.yml at $Path" }
    return $out
}

$handoff = Find-HandoffRoot $HandoffRoot
$dbPath = Join-Path $handoff "plugins\Towny\settings\database.yml"
$db = Read-TownyDatabaseYaml $dbPath
$dryPy = if ($DryRun) { "True" } else { "False" }

$py = @"
import sys
import pymysql

cfg = {
    "host": "$($db.hostname)",
    "port": int("$($db.port)" or 3306),
    "user": "$($db.username)",
    "password": "$($db.password)",
    "database": "$($db.dbname)",
    "charset": "utf8mb4",
    "autocommit": False,
}
src = "$FromWorld"
dst = "$ToWorld"
dry = $dryPy

conn = pymysql.connect(**cfg)
cur = conn.cursor()
cur.execute("SHOW TABLES")
tables = {r[0].upper(): r[0] for r in cur.fetchall()}

def pick(*candidates):
    for c in candidates:
        if c.upper() in tables:
            return tables[c.upper()]
    raise SystemExit(f"Missing Towny table (tried {candidates})")

tb = pick("TOWNY_TOWNBLOCKS", "towny_townblocks")
tw = pick("TOWNY_WORLDS", "towny_worlds")
tt = pick("TOWNY_TOWNS", "towny_towns")

def count_tb(world):
    cur.execute(f"SELECT COUNT(*) FROM `{tb}` WHERE world=%s", (world,))
    return cur.fetchone()[0]

before_src = count_tb(src)
before_dst = count_tb(dst)
print(f"Townblocks: {src}={before_src}, {dst}={before_dst}")

if before_src == 0:
    print(f"No townblocks on '{src}' â€” already migrated or empty.")
    conn.close()
    sys.exit(0)

if dry:
    print(f"[dry-run] Would move {before_src} rows in {tb}")
    print(f"[dry-run] Would patch {tw} and {tt} spawn/home fields")
    conn.close()
    sys.exit(0)

cur.execute(f"UPDATE `{tb}` SET world=%s WHERE world=%s", (dst, src))
print(f"Updated townblocks: {cur.rowcount}")

cur.execute(f"SELECT COUNT(*) FROM `{tw}` WHERE name=%s", (dst,))
has_dst = cur.fetchone()[0] > 0
cur.execute(f"SELECT COUNT(*) FROM `{tw}` WHERE name=%s", (src,))
has_src = cur.fetchone()[0] > 0
if has_src and not has_dst:
    cur.execute(f"UPDATE `{tw}` SET name=%s WHERE name=%s", (dst, src))
    print(f"Renamed WORLD row: {cur.rowcount}")
elif has_src and has_dst:
    cur.execute(f"DELETE FROM `{tw}` WHERE name=%s", (src,))
    print(f"Removed duplicate WORLD row for {src}: {cur.rowcount}")

for col in ("homeblock", "spawn", "outpostSpawns", "jailSpawns"):
    cur.execute(f"SHOW COLUMNS FROM `{tt}` LIKE %s", (col,))
    if not cur.fetchone():
        continue
    cur.execute(
        f"UPDATE `{tt}` SET `{col}`=REPLACE(`{col}`, %s, %s) WHERE `{col}` LIKE CONCAT(%s, '#%%')",
        (src + "#", dst + "#", src),
    )
    if cur.rowcount:
        print(f"Patched TOWNS.{col}: {cur.rowcount}")

conn.commit()
print(f"Done. Townblocks: {src}={count_tb(src)}, {dst}={count_tb(dst)}")
conn.close()
"@

$tmp = Join-Path $env:TEMP "towny-rename-world.py"
Set-Content -LiteralPath $tmp -Value $py -Encoding UTF8
python -c "import pymysql" 2>$null
if ($LASTEXITCODE -ne 0) { pip install pymysql -q }
python $tmp
if ($LASTEXITCODE -ne 0) { throw "Towny world rename failed" }
Remove-Item $tmp -Force -ErrorAction SilentlyContinue

Write-Host "Towny MySQL migration complete. Start server, then: /townyadmin reload"
