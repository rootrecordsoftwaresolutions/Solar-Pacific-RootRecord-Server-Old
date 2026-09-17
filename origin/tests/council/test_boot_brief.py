from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from apps.council.boot_brief import (
    BOOT_ORIGIN,
    DESK_ORIGIN,
    current_slot,
    decide_boot_brief,
    is_clean_report_name,
    notify_generated_report,
    report_is_for_today,
    report_matches_slot,
    select_clean_reports,
)
from apps.core.crons.in_order_on_boot.boot_prelims import desk_report_kind

HST = ZoneInfo("Pacific/Honolulu")
MORNING = datetime(2026, 9, 15, 9, 0, tzinfo=HST)
AFTERNOON = datetime(2026, 9, 15, 13, 50, tzinfo=HST)


def test_catchup_origin_is_not_coming_online():
    low = DESK_ORIGIN.lower() + " " + BOOT_ORIGIN.lower()
    assert "coming online" not in low
    assert "just came up" not in low
    assert "just came back" not in low


def test_decide_skip_same_boot():
    assert decide_boot_brief("abc", "abc", 30) == "skip"


def test_decide_arm_when_never_run_and_uptime_long():
    assert decide_boot_brief("", "abc", 3600) == "arm"


def test_decide_run_fresh_boot_never_armed():
    assert decide_boot_brief("", "abc", 60) == "run"


def test_decide_run_after_reboot():
    assert decide_boot_brief("old", "new", 40) == "run"


def test_skip_churn_names():
    assert not is_clean_report_name("earthquake-hourly-current.md")
    assert not is_clean_report_name("system-performance-2026-09-15T13.md")
    assert not is_clean_report_name("nws-hawaii-counties-current.md")
    assert is_clean_report_name("morning-boot-current.md")
    assert is_clean_report_name("midday-boot-current.md")


def test_select_dedupes_identical_bodies(tmp_path: Path):
    body = (
        "This is the Ava Core Root Record morning status for September 15, 2026.\n\n"
        "End of status.\n" + ("x" * 40)
    )
    (tmp_path / "morning-boot-current.md").write_text(body)
    (tmp_path / "morning-report-current.md").write_text(body)
    (tmp_path / "earthquake-hourly-current.md").write_text(body)
    chosen = select_clean_reports(tmp_path, now=MORNING)
    names = [p.name for p in chosen]
    assert names == ["morning-boot-current.md"]


def test_afternoon_skips_stale_morning(tmp_path: Path):
    body = (
        "This is the Ava Core Root Record morning status for September 15, 2026.\n\n"
        "End of status.\n" + ("x" * 40)
    )
    mid = (
        "This is the Ava Core Root Record midday status for September 15, 2026.\n\n"
        "End of status.\n" + ("y" * 40)
    )
    (tmp_path / "morning-boot-current.md").write_text(body)
    (tmp_path / "midday-boot-current.md").write_text(mid)
    names = [p.name for p in select_clean_reports(tmp_path, now=AFTERNOON)]
    assert names == ["midday-boot-current.md"]
    assert current_slot(AFTERNOON) == "midday"
    assert desk_report_kind(13) == "midday"
    assert desk_report_kind(9) == "morning"
    assert report_matches_slot("boot", "morning-boot-current.md", AFTERNOON) is False
    assert report_matches_slot("midday", "midday-boot-current.md", AFTERNOON) is True


def test_refuses_september_4_current_pointer(tmp_path: Path):
    old = (
        "This is the Ava Core Root Record midday status for Friday, September 4, 2026.\n\n"
        "Restore stamp is 2026-09-04 07:54.\n" + ("z" * 40)
    )
    (tmp_path / "midday-boot-current.md").write_text(old)
    (tmp_path / "morning-boot-2026-09-04.md").write_text(old)
    assert report_is_for_today(tmp_path / "midday-boot-current.md", AFTERNOON) is False
    assert report_is_for_today(tmp_path / "morning-boot-2026-09-04.md", AFTERNOON) is False
    assert select_clean_reports(tmp_path, now=AFTERNOON) == []


def test_fresh_stamp_alone_does_not_make_stale_body_today():
    from apps.council.boot_brief import report_text_is_for_today

    stamped = (
        "**Ava morning report** — 2026-09-15 14:02 HST\n\n"
        "Leftover facts from September 4 with no today marker.\n"
    )
    assert report_text_is_for_today(stamped, AFTERNOON) is False
    good = (
        "**Ava morning report** — 2026-09-15 09:02 HST\n\n"
        "This is the Ava Core Root Record morning status for September 15, 2026.\n"
    )
    assert report_text_is_for_today(good, MORNING) is True


def test_boot_status_does_not_telegram_except_catchup(tmp_path: Path):
    body = (
        "This is the Ava Core Root Record morning status for September 15, 2026.\n\n"
        "End of status.\n" + ("x" * 40)
    )
    boot = tmp_path / "morning-boot-current.md"
    boot.write_text(body)
    assert notify_generated_report("boot", boot) is False
    assert notify_generated_report("morning", boot) is False
