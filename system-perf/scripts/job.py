"""System performance cron — CPU, RAM, battery, disk, uptime snapshot."""

from __future__ import annotations

import logging
from datetime import datetime, timezone

log = logging.getLogger("ava.cron.system_perf")


async def run():
    from apps.core import config
    from apps.core.host_metrics import host_disks, npu_present, snapshot

    now = datetime.now(timezone.utc)
    row = snapshot(home=config.AVA_HOME)
    disks = host_disks()
    disk_lines = []
    for d in disks:
        disk_lines.append(
            f"- {d.get('label') or d['mount']}: {d['pct']}% used ({d['used_gb']} / {d['total_gb']} GB)"
        )
    if not disk_lines:
        disk_lines = ["- (no disk sample)"]

    batt = row.get("battery_pct")
    plugged = row.get("battery_plugged")
    if batt is None:
        batt_line = "Host charge: not sampled"
    else:
        ac = " on AC" if plugged else " on battery"
        batt_line = f"Host charge: {batt}%{ac}"

    temp = row.get("temp_c")
    temp_src = row.get("temp_src") or ""
    if temp is None:
        temp_line = "Temp: not sampled"
    else:
        src = f" ({temp_src})" if temp_src else ""
        temp_line = f"Temp: {temp}°C{src}"

    gpu = row.get("gpu_name") or "not sampled"
    gpu_pct = row.get("gpu_pct")
    if gpu_pct is not None:
        gpu_line = f"iGPU: {gpu} — {gpu_pct}%"
    else:
        gpu_line = f"iGPU: {gpu} — load not sampled"
    npu = row.get("npu_pct")
    if npu is not None:
        npu_line = f"NPU: {npu}% (present={bool(npu_present())})"
    elif npu_present():
        npu_line = (
            "NPU: present — load not sampled "
            "(stock Ollama GGUF stays on the 840M; XDNA needs Ryzen AI userspace)"
        )
    else:
        npu_line = "NPU: not found"
    up = int(row.get("uptime_s") or 0)

    content = (
        f"# System Performance — {now.strftime('%Y-%m-%dT%H')}\n\n"
        f"Host: HI Pacific Solar Root Server\n"
        f"CPU: {row.get('cpu_pct')}%\n"
        f"RAM: {row.get('mem_pct')}% used "
        f"({row.get('mem_used_gb')} / {row.get('mem_total_gb')} GB)\n"
        f"{batt_line}\n"
        f"{temp_line}\n"
        f"{gpu_line}\n"
        f"{npu_line}\n"
        f"Uptime: {up // 3600}h {(up % 3600) // 60}m\n"
        f"\nDisks:\n"
        + "\n".join(disk_lines)
        + "\n"
    )

    report_path = config.REPORTS_DIR / f"system-performance-{now.strftime('%Y-%m-%dT%H')}.md"
    report_path.write_text(content, encoding="utf-8")
    try:
        from apps.core.services import discord, reports

        channel_id = config.DISCORD_CHANNELS.get("ava_home")
        if channel_id and reports.discord_delivery_is_new("system", channel_id, content):
            posted = await discord.post_message(channel_id, content[:1900])
            if posted:
                reports.mark_discord_delivery("system", channel_id, content)
                log.info("System performance posted to Discord channel=%s", channel_id)
        else:
            log.debug("System performance unchanged; Discord post skipped")
    except Exception as e:
        log.warning("System performance Discord post failed: %s", type(e).__name__)
    try:
        from apps.core.crons.since_last_fire.solar_weather import record_host_sample
        record_host_sample(force=True)
    except Exception as e:
        log.debug("host sample from system_perf skipped: %s", e)
    log.info(
        "System performance written: %s  cpu=%s%%  ram=%s%%  batt=%s",
        report_path.name, row.get("cpu_pct"), row.get("mem_pct"), batt,
    )
    try:
        from pathlib import Path
        from zoneinfo import ZoneInfo

        from apps.core.host_metrics import host_drives_spoken
        from apps.core.services import db_facts
        from apps.council.report_cast import notify_report
        from apps.voice.speakable import spoken_clock
        from apps.voice.speakers import publish_current, speak_report

        hst = datetime.now(ZoneInfo("Pacific/Honolulu"))
        spoken = (
            f"Host desk at {spoken_clock(hst.hour, hst.minute)} Hawaiian Standard Time. "
            f"{db_facts.host_line()} {host_drives_spoken()}"
        ).strip()
        dest = config.GENERATED_DIR / f"system-performance-{hst.strftime('%Y%m%d-%H%M')}.wav"
        built = speak_report("system", spoken, dest)
        wav = None
        if built.get("ok") and dest.is_file():
            publish_current(
                dest,
                config.GENERATED_DIR / "system-performance-current.wav",
                config.GENERATED_DIR / "hourly-system-current.wav",
                Path(config.AUDIO_CURRENT_DIR) / "system-performance-current.wav",
            )
            wav = dest
        notify_report("system", transcript=content, audio=wav)
    except Exception as e:
        log.debug("system performance telegram skipped: %s", e)
