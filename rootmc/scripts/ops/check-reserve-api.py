#!/usr/bin/env python3
import json
import urllib.request

with urllib.request.urlopen("https://api.rootmc.net/api/rootmc/treasury/rootmc/reserve") as resp:
    r = json.load(resp)

print("balance:", r["balance"])
print("grant_mtd:", r["month"]["by_type"]["GRANT"])
print("towny_sink_mtd:", r["month"]["by_type"]["TOWNY_SINK"])
print("claims_mtd:", r["towny_intake_mtd"]["claims"])
print("grant_all:", r["all_time"]["by_type"]["GRANT"])
july = [d for d in r["flow_daily"] if d["day"] == "2026-07-01"]
if july:
    print("july1_flow:", july[0])
