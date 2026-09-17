from pathlib import Path
import json
import sys

from apps.council.skills import disk_session_argv, get_skill, match_exec_skill
from apps.council import commands
from apps.council.people import apply_tags, strip_tags
from apps.council.sanitize import sanitize_outbound


def test_catalog_has_income_desks():
    for sid in (
        "goals-desk",
        "ltc-status",
        "disk-session",
        "web-facts",
        "storm-plot",
        "cooking-desk",
        "pantry-desk",
        "nutrition-desk",
    ):
        meta = get_skill(sid)
        assert meta and meta["id"] == sid
        assert meta["risk"] == "exec"
        assert meta["entrypoint"] == "run.py"


def test_exec_keywords_income_desks():
    assert match_exec_skill("list our goals please")["id"] == "goals-desk"
    assert match_exec_skill("is the chain synced")["id"] == "ltc-status"
    assert match_exec_skill("turn on the drives")["id"] == "disk-session"
    assert match_exec_skill("turn off the drives")["id"] == "disk-session"
    assert match_exec_skill("check drive power")["id"] == "disk-session"
    assert match_exec_skill("look up nws hawaii")["id"] == "web-facts"
    assert match_exec_skill("how far is the storm")["id"] == "storm-plot"
    assert match_exec_skill("publish reports now")["id"] == "report-publish"
    assert match_exec_skill("what can we cook")["id"] == "cooking-desk"
    assert match_exec_skill("what's in the pantry")["id"] == "pantry-desk"
    assert match_exec_skill("nutrient dense")["id"] == "nutrition-desk"


def test_help_includes_goals():
    assert "/goals" in commands.help_text()
    assert "/goals" in commands.help_text(voice="bruce")
    assert "/recipes" in commands.help_text()
    assert "/pantry" in commands.help_text()
    assert "/nutrition" in commands.help_text()


def test_goal_tags_edit_store(tmp_path, monkeypatch):
    scripts = Path.home() / ".ollama" / "skills" / "goals" / "scripts"
    sys.path.insert(0, str(scripts))
    import council_goals as g

    monkeypatch.setattr(g, "STORE", tmp_path / "council-goals.json")
    monkeypatch.setattr(g, "IDEAS", tmp_path / "ideas")
    g._save({"updated": "", "rules": [], "goals": []})
    apply_tags("ok <<<GOAL add matching ddr5 stick>>>", 1, voice="bruce")
    data = json.loads((tmp_path / "council-goals.json").read_text(encoding="utf-8"))
    ids = [x.get("id") for x in data.get("goals") or []]
    assert "matching-ddr5-stick" in ids
    apply_tags("<<<SKILLIDEA ads landing|bill existing pages>>>", 1, voice="ava")
    ideas = list((tmp_path / "ideas").glob("*.md"))
    assert ideas
    raw = "visible <<<GOAL note matching-ddr5-stick wait for purchase>>>"
    assert "GOAL" not in strip_tags(raw)
    assert "GOAL" not in sanitize_outbound(raw, voice="bruce")


def test_recipe_tags_save_user_source(tmp_path, monkeypatch):
    cook = Path.home() / ".ollama" / "skills" / "cooking" / "scripts"
    pan = Path.home() / ".ollama" / "skills" / "pantry" / "scripts"
    sys.path.insert(0, str(cook))
    sys.path.insert(0, str(pan))
    import recipes as r
    import pantry as p

    monkeypatch.setattr(r, "STORE", tmp_path / "recipes.json")
    monkeypatch.setattr(p, "STORE", tmp_path / "stock.json")
    r._save({"updated": "", "recipes": []})
    p._save({"updated": "", "items": [], "aliases": {}})
    apply_tags(
        "yum <<<RECIPE leftover chili | beans, onion | cooked tonight>>>",
        1,
        voice="ava",
    )
    data = json.loads((tmp_path / "recipes.json").read_text(encoding="utf-8"))
    rec = (data.get("recipes") or [])[0]
    assert rec.get("source") == "user"
    assert rec.get("id") == "leftover-chili"
    apply_tags("<<<PANTRY add onion | 2 | ea>>>", 1, voice="ava")
    stock = json.loads((tmp_path / "stock.json").read_text(encoding="utf-8"))
    assert any(i.get("id") == "onion" and float(i.get("qty") or 0) == 2 for i in stock.get("items") or [])
    hidden = "x <<<RECIPE a | b>>> <<<PANTRY use onion | 1>>>"
    assert "RECIPE" not in strip_tags(hidden)
    assert "PANTRY" not in sanitize_outbound(hidden, voice="ava")


def test_web_facts_allowlist():
    scripts = Path.home() / ".ollama" / "skills" / "web-facts" / "scripts"
    sys.path.insert(0, str(scripts))
    import web_facts

    assert web_facts.allowed("https://api.weather.gov/")
    assert not web_facts.allowed("http://api.weather.gov/")
    assert not web_facts.allowed("https://evil.example/")
    assert web_facts.fetch("https://evil.example/")["error"] == "host_not_allowlisted"


def test_ltc_forbids_send_and_waits_if_no_disk(monkeypatch, tmp_path):
    scripts = Path.home() / ".ollama" / "skills" / "ltc-node" / "scripts"
    sys.path.insert(0, str(scripts))
    import ltc_status as ltc

    assert ltc._rpc("sendtoaddress")["error"] == "forbidden_rpc"
    assert ltc._rpc("dumpprivkey")["error"] == "forbidden_rpc"
    monkeypatch.setattr(ltc, "cli_path", lambda: None)
    monkeypatch.setattr(ltc, "datadir", lambda: tmp_path / "missing")
    car = Path.home() / ".ollama" / "skills" / "ecoflow-river-car" / "scripts"
    sys.path.insert(0, str(car))
    import disk_session as ds

    monkeypatch.setattr(
        ds,
        "snapshot",
        lambda: {"usb_or_sata_present": False, "chain_mounts": [], "nvme_only": True},
    )
    st = ltc.status()
    assert st.get("blocked") == "external_disk_off"
    assert st.get("balance_allowed") is False
    bal = ltc.balance()
    assert bal.get("error") == "wait_for_sync"


def test_disk_session_dry_run(monkeypatch):
    car = Path.home() / ".ollama" / "skills" / "ecoflow-river-car" / "scripts"
    sys.path.insert(0, str(car))
    import disk_session as ds

    monkeypatch.setattr(ds, "_lsblk", lambda: [])
    monkeypatch.setattr(ds, "set_car", lambda want_on, execute: {"ok": True, "execute": execute, "want_on": want_on})
    monkeypatch.setattr(ds, "car_status", lambda live=False: {"car": "cached"})
    out = ds.prepare(execute=False)
    assert out.get("blocked") == "dry_run"
    assert out.get("car_action", {}).get("execute") is False
    snap = ds.snapshot()
    assert snap.get("nvme_only") is True


def test_disk_session_argv_owner_execute():
    assert disk_session_argv("turn on the drives", execute=True) == ["--prepare", "--execute"]
    assert disk_session_argv("run a drive session", execute=True) == ["--session", "--execute"]
    assert disk_session_argv("turn off the drives", execute=True) == ["--off", "--execute"]
    assert disk_session_argv("turn on the drives", execute=False) == ["--prepare"]
    assert disk_session_argv("is the external drive present", execute=True) == []
