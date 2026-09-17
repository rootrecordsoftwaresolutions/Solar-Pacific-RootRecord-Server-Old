#!/usr/bin/env python3
"""
Ava Ivy Solar Status Monitor
Polls https://rootrecord.info/ava/status/solar and prints a clean live summary.
"""

import time
import re
import sys
from datetime import datetime

try:
    import requests
    from bs4 import BeautifulSoup
except ImportError:
    print("Installing required packages...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "requests", "beautifulsoup4", "-q"])
    import requests
    from bs4 import BeautifulSoup

URL = "https://rootrecord.info/ava/status/solar"
REFRESH_SECONDS = 45

def fetch_stats():
    try:
        r = requests.get(URL, timeout=15, headers={
            "User-Agent": "AvaIvy-SolarMonitor/1.0"
        })
        r.raise_for_status()
        text = r.text

        # Extract key numbers with simple regex (page is somewhat static in source)
        def grab(patterns, default="—"):
            for p in patterns:
                m = re.search(p, text, re.I)
                if m:
                    return m.group(1).strip()
            return default

        battery = grab([
            r'Battery now["\s:>]+(\d+%)',
            r'(\d+%)\s*avg day',
            r'battery\s+(\d+%)',
            r'>\s*(\d+%)\s*<.*avg day'
        ])

        solar = grab([
            r'Solar Input.*?(\d+\s*W)',
            r'panel in.*?(\d+\s*W)',
            r'Current input.*?(\d+\s*W)',
            r'(\d+\s*W)\s*panel'
        ])

        load = grab([
            r'Load Out.*?(\d+\s*W)',
            r'Load now.*?(\d+\s*W)',
            r'(\d+\s*W)\s*day avg'
        ])

        energy = grab([
            r'Energy Today.*?([\d.]+\s*kWh)',
            r'Energy today.*?([\d.]+\s*kWh)',
            r'([\d.]+\s*kWh est\.)'
        ])

        eco = "LIVE" if "EcoFlow live" in text or "EcoFlow Pack</td><td>LIVE" in text else "—"
        host = "Online" if "host online" in text.lower() else "—"

        # Mood / bonus
        bonus = grab([r'Gaming Bonus.*?([\d.]+×)', r'([\d.]+×)\s*connected'])
        weather = grab([r'(\d+°F\s*/\s*\d+°C)'])

        return {
            "battery": battery,
            "solar": solar,
            "load": load,
            "energy": energy,
            "eco": eco,
            "host": host,
            "bonus": bonus,
            "weather": weather,
            "ok": True
        }
    except Exception as e:
        return {"ok": False, "error": str(e)}

def clear():
    print("\033[2J\033[H", end="")  # clear screen

def display(stats):
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S HST")
    print("=" * 52)
    print(f"  AVA IVY  ·  SOLAR ROOT SERVER  ·  {now}")
    print("=" * 52)
    if not stats.get("ok"):
        print(f"  ERROR: {stats.get('error')}")
        print("=" * 52)
        return

    print(f"  Battery     :  {stats['battery']}")
    print(f"  Solar Input :  {stats['solar']}")
    print(f"  Load Out    :  {stats['load']}")
    print(f"  Energy Today:  {stats['energy']}")
    print(f"  EcoFlow     :  {stats['eco']}")
    print(f"  Host        :  {stats['host']}")
    print(f"  Gaming Bonus:  {stats['bonus']}")
    print(f"  Weather     :  {stats['weather']}")
    print("-" * 52)
    print("  Source: https://rootrecord.info/ava/status/solar")
    print(f"  Refresh every {REFRESH_SECONDS}s  ·  Ctrl+C to stop")
    print("=" * 52)

def main():
    print("Starting Ava Ivy Solar Monitor...")
    try:
        while True:
            stats = fetch_stats()
            clear()
            display(stats)
            time.sleep(REFRESH_SECONDS)
    except KeyboardInterrupt:
        print("\nMonitor stopped.")

if __name__ == "__main__":
    main()
