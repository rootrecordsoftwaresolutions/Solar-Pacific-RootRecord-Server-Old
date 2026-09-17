# Towny fresh-map reset: unclaim all town blocks and refund each town's claim value to its mayor wallet.
# Uses TOWNY_TOWNBLOCKS.price sum per town, then clears claims/spawns.
#
#   powershell -File scripts\towny-unclaim-refund-mayors.ps1 -DryRun
#   powershell -File scripts\towny-unclaim-refund-mayors.ps1

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
import pymysql
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

cur.execute("""
    SELECT
      t.name AS town_name,
      t.mayor AS mayor_name,
      r.uuid AS mayor_uuid,
      COUNT(tb.town) AS block_count,
      COALESCE(SUM(COALESCE(tb.price, 0)), 0) AS refund_gold
    FROM TOWNY_TOWNS t
    LEFT JOIN TOWNY_TOWNBLOCKS tb ON tb.town = t.name
    LEFT JOIN TOWNY_RESIDENTS r ON r.name = t.mayor
    GROUP BY t.name, t.mayor, r.uuid
    HAVING block_count > 0
    ORDER BY block_count DESC, t.name ASC
""")
refund_rows = cur.fetchall()
actions.append(("towns with claims", len(refund_rows)))
actions.append(("townblocks to clear", int(sum(r[3] for r in refund_rows))))
actions.append(("total refund gold", float(sum(r[4] for r in refund_rows))))

print("Refund plan (town -> mayor):")
for town_name, mayor_name, mayor_uuid, block_count, refund_gold in refund_rows:
    mayor_uuid = mayor_uuid or ""
    print(f" - {town_name}: {block_count} block(s), {refund_gold:.2f} G -> {mayor_name} ({mayor_uuid})")

for town_name, mayor_name, mayor_uuid, block_count, refund_gold in refund_rows:
    if not mayor_uuid:
        continue
    cur.execute("SELECT balance FROM root_economy_balances WHERE minecraft_uuid=%s", (mayor_uuid,))
    row = cur.fetchone()
    if row is None:
        if not dry:
            cur.execute(
                "INSERT INTO root_economy_balances (minecraft_uuid, minecraft_username, balance) VALUES (%s,%s,%s)",
                (mayor_uuid, mayor_name, float(refund_gold)),
            )
    else:
        if not dry:
            cur.execute(
                "UPDATE root_economy_balances SET balance = balance + %s, updated_at = CURRENT_TIMESTAMP WHERE minecraft_uuid=%s",
                (float(refund_gold), mayor_uuid),
            )
    if not dry and refund_gold > 0:
        cur.execute(
            "INSERT INTO root_treasury_ledger (entry_type, amount, from_uuid, to_uuid, details) VALUES (%s,%s,%s,%s,%s)",
            ("towny_unclaim_refund", float(refund_gold), None, mayor_uuid, f"Towny unclaim refund for {town_name} ({block_count} blocks)"),
        )

cur.execute("SELECT COUNT(*) FROM TOWNY_TOWNBLOCKS")
actions.append(("townblocks before delete", cur.fetchone()[0]))
if not dry:
    cur.execute("DELETE FROM TOWNY_TOWNBLOCKS")
actions.append(("townblocks deleted", cur.rowcount if not dry else 0))

for col in ("homeblock", "spawn", "outpostSpawns"):
    if not dry:
        cur.execute(f"UPDATE TOWNY_TOWNS SET `{col}`='' WHERE `{col}` IS NOT NULL AND `{col}` != ''")
        actions.append((f"TOWNS.{col} cleared", cur.rowcount))
    else:
        actions.append((f"[dry-run] TOWNS.{col} clear", 0))

if not dry:
    cur.execute("UPDATE TOWNY_NATIONS SET nationSpawn='' WHERE nationSpawn IS NOT NULL AND nationSpawn != ''")
    actions.append(("NATIONS.nationSpawn cleared", cur.rowcount))
else:
    actions.append(("[dry-run] NATIONS.nationSpawn clear", 0))

if not dry:
    conn.commit()
else:
    conn.rollback()
conn.close()

print("\\nSummary:")
for label, n in actions:
    print(f" - {label}: {n}")
print("Done." if not dry else "Dry run complete.")
"@
$tmp = Join-Path $env:TEMP "towny-unclaim-refund-mayors.py"
Set-Content $tmp $py -Encoding UTF8
python -c "import pymysql" 2>$null; if ($LASTEXITCODE -ne 0) { pip install pymysql -q }
python $tmp
Remove-Item $tmp -Force -ErrorAction SilentlyContinue
