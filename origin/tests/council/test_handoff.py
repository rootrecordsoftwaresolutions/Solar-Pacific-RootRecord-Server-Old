from pathlib import Path

from apps.council.handoff import bundle, parse_zip_line, zip_allowed
from apps.council.skills import match_exec_skill


def test_handoff_zip_is_under_handoff_dir(tmp_path, monkeypatch):
    from apps.council import handoff as h

    monkeypatch.setattr(h, "HANDOFF_DIR", tmp_path / "handoff")
    monkeypatch.setattr(h, "default_sources", lambda: [])
    live = tmp_path / "ecoflow-live.json"
    live.write_text('{"generator": false}\n', encoding="utf-8")
    monkeypatch.setattr(h, "default_sources", lambda: [(live, "ecoflow-live.json")])
    out = h.bundle()
    z = Path(out["zip"])
    assert out["ok"] and zip_allowed(z)
    assert parse_zip_line(f"HANDOFF_ZIP={z}") == z


def test_handoff_rejects_env(tmp_path, monkeypatch):
    from apps.council import handoff as h

    monkeypatch.setattr(h, "HANDOFF_DIR", tmp_path / "handoff")
    env = tmp_path / ".env"
    env.write_text("TOKEN=secret\n", encoding="utf-8")
    monkeypatch.setattr(h, "default_sources", lambda: [(env, ".env")])
    out = h.bundle()
    assert ".env" not in (out.get("files") or [])


def test_exec_keywords_handoff_and_reports():
    assert match_exec_skill("please zip handoff for the other api")["id"] == "handoff-zip"
    assert match_exec_skill("publish reports now")["id"] == "report-publish"


def test_bundle_includes_all_plan_names(tmp_path, monkeypatch):
    from apps.council import handoff as h

    monkeypatch.setattr(h, "HANDOFF_DIR", tmp_path / "handoff")
    monkeypatch.setattr(h, "RUNS_DIR", tmp_path)
    plans = tmp_path / "proposals"
    plans.mkdir()
    (plans / "plan-aaa.md").write_text("# a\n", encoding="utf-8")
    (plans / "plan-bbb.md").write_text("# b\n", encoding="utf-8")
    monkeypatch.setattr(
        "apps.core.services.ecoflow_public.live_path",
        lambda: tmp_path / "missing.json",
    )
    names = [arc for _, arc in h.default_sources()]
    assert "proposals/plan-aaa.md" in names
    assert "proposals/plan-bbb.md" in names


def test_bundle_includes_ops_guide(tmp_path, monkeypatch):
    from apps.council import handoff as h
    from apps.core.services import ecoflow_public as pub

    monkeypatch.setattr(h, "HANDOFF_DIR", tmp_path / "handoff")
    live = tmp_path / "ecoflow-live.json"
    live.write_text('{"generator": false, "detail": "idle", "card": "Generator off."}\n', encoding="utf-8")
    guide = tmp_path / "ops-guide-20260915.md"
    guide.write_text("# Ops guide\n", encoding="utf-8")
    monkeypatch.setattr(pub, "live_path", lambda: live)
    monkeypatch.setattr(pub, "write_ops_guide", lambda: guide)
    monkeypatch.setattr(h, "RUNS_DIR", tmp_path)
    names = [arc for _, arc in h.default_sources()]
    assert "ecoflow-live.json" in names
    assert "ops-guide-20260915.md" in names
