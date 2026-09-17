import json

from apps.council.approval import create_approval, latest_pending, lookup_id, save_approvals
from apps.council.commands import COMMANDS, bot_command_payload, help_text
from apps.council.prompting import build_round_follow_prompt
from apps.council.proposals import (
    append_human_note,
    backfill_rewritten,
    conclude,
    new_plan_id,
    normalize_id,
    proposal_path,
)


def test_plan_id_shape():
    pid = new_plan_id()
    assert pid.startswith("plan-")
    assert normalize_id(pid[5:]) == pid
    assert normalize_id(pid) == pid


def test_approve_hint_in_dropdown(tmp_path, monkeypatch):
    monkeypatch.setattr("apps.council.approval.RUNS_DIR", tmp_path)
    save_approvals({"pending": {}, "history": []})
    create_approval("do x", "do x", aid="plan-abc12345")
    item = latest_pending()
    assert item and item["id"] == "plan-abc12345"
    assert lookup_id("abc12345") == "plan-abc12345"
    payload = bot_command_payload("plan-abc12345")
    names = [c["command"] for c in payload]
    assert names == [n for n, _ in COMMANDS]
    assert len(names) == len(set(names))
    approve = next(c for c in payload if c["command"] == "approve")
    assert "plan-abc12345" in approve["description"]
    assert "/help" in help_text("plan-abc12345") or "help" in help_text()


def test_carly_round_prompt_is_security(monkeypatch):
    monkeypatch.setattr("apps.council.brainstorm.status", lambda: "idle")
    monkeypatch.setattr("apps.council.desk_read.snapshot_for_prompt", lambda **_k: "")
    monkeypatch.setattr("apps.council.desk_read.desk_facts_block", lambda **_k: "")
    p = build_round_follow_prompt(
        voice="carly",
        prev_voice="bruce",
        prev_text="Can we store tokens in chat?",
        origin_text="thought session",
    )
    assert "security" in p.lower()
    assert "performance" in p.lower()
    assert "No code" in p


def _wire_plans(tmp_path, monkeypatch):
    monkeypatch.setattr("apps.council.proposals.RUNS_DIR", tmp_path)
    monkeypatch.setattr("apps.council.proposals.PROPOSALS_DIR", tmp_path / "proposals")
    monkeypatch.setattr("apps.council.proposals.INDEX_PATH", tmp_path / "proposals.json")
    monkeypatch.setattr("apps.council.approval.RUNS_DIR", tmp_path)
    monkeypatch.setattr("apps.council.chatlog.LOG", tmp_path / "chat.jsonl")
    log = tmp_path / "chat.jsonl"
    rows = [
        {
            "dir": "in",
            "chat_id": "1",
            "display": "rootrecordadmin",
            "text": "Hey team, thought session. Ava, you start.",
            "ts": 100,
        },
        {
            "dir": "out",
            "voice": "ava",
            "chat_id": "1",
            "text": "Opener only.",
            "ts": 110,
        },
        {
            "dir": "in",
            "chat_id": "1",
            "display": "rootrecordadmin",
            "text": "Do not rewrite the daily proposal. File a new one when the last finishes.",
            "ts": 200,
        },
    ]
    log.write_text("".join(json.dumps(r) + "\n" for r in rows), encoding="utf-8")


def test_same_day_publish_amends_one_file(tmp_path, monkeypatch):
    _wire_plans(tmp_path, monkeypatch)
    monkeypatch.setattr("apps.council.proposals.today_iso", lambda now=None: "2026-09-15")
    from apps.council.proposals import freeze, publish

    first = publish(chat_id="1", origin="first ask")
    original = proposal_path(first["id"]).read_text(encoding="utf-8")
    second = publish(chat_id="1", origin="amend encryption and access controls")
    assert second["id"] == first["id"]
    later = proposal_path(first["id"]).read_text(encoding="utf-8")
    assert later.startswith(original.rstrip())
    assert "first ask" in later
    assert "Amendment 1" in later
    assert "amend encryption" in later

    freeze(first["id"])
    third = publish(chat_id="1", origin="new daily after finish")
    assert third["id"] != first["id"]
    still = proposal_path(first["id"]).read_text(encoding="utf-8")
    assert "first ask" in still
    assert "new daily after finish" not in still
    assert "## Closed" in still
    new_body = proposal_path(third["id"]).read_text(encoding="utf-8")
    assert "new daily after finish" in new_body


def test_human_note_appends_without_rewrite(tmp_path, monkeypatch):
    _wire_plans(tmp_path, monkeypatch)
    rec = conclude(chat_id="1", origin="keep this ask")
    pid = rec["id"]
    original = proposal_path(pid).read_text(encoding="utf-8")
    append_human_note(pid, "amend encryption notes", "1")
    append_human_note(pid, "and access controls", "1")
    later = proposal_path(pid).read_text(encoding="utf-8")
    assert later.startswith(original.rstrip())
    assert "amend encryption notes" in later
    assert "and access controls" in later
    assert later.count("keep this ask") == original.count("keep this ask")


def test_backfill_channel_appends_once(tmp_path, monkeypatch):
    _wire_plans(tmp_path, monkeypatch)
    monkeypatch.setattr("apps.council.proposals.today_iso", lambda now=None: "2026-09-15")
    from apps.council.proposals import backfill_channel, open_daily

    pid = backfill_channel("1")
    body = proposal_path(pid).read_text(encoding="utf-8")
    assert "## Channel backfill" in body
    assert "Hey team, thought session" in body
    assert backfill_channel("1") == pid
    assert body.count("## Channel backfill") == 1
    assert open_daily("1")["id"] == pid


def test_backfill_appends_recovered_once(tmp_path, monkeypatch):
    _wire_plans(tmp_path, monkeypatch)
    rec = conclude(chat_id="1", origin="window")
    pid = rec["id"]
    path = proposal_path(pid)
    path.write_text(path.read_text(encoding="utf-8").replace("Opener only.", "…"), encoding="utf-8")
    touched = backfill_rewritten("1")
    assert pid in touched
    body = path.read_text(encoding="utf-8")
    assert "## Recovered transcript" in body
    assert "Hey team, thought session" in body
    assert backfill_rewritten("1") == []


def test_looks_owner_implemented():
    from apps.council.proposals import looks_owner_implemented

    assert looks_owner_implemented("I've implemented the proposals")
    assert looks_owner_implemented("I added the proposals to the backend")
    assert looks_owner_implemented("I added them to the backend")
    assert looks_owner_implemented("the proposals are live")
    assert not looks_owner_implemented("please implement the proposals")
    assert not looks_owner_implemented("we should implement this")
    assert not looks_owner_implemented("I added milk")


def test_clear_implemented_drops_pending(tmp_path, monkeypatch):
    _wire_plans(tmp_path, monkeypatch)
    rec = conclude(chat_id="1", origin="keep this ask")
    pid = rec["id"]
    from apps.council.approval import latest_pending
    from apps.council.proposals import clear_implemented, get, list_pending_ids

    assert latest_pending()
    assert pid in list_pending_ids()
    out = clear_implemented("1")
    assert pid in out
    assert list_pending_ids() == []
    assert get(pid) and get(pid).get("frozen")
    body = proposal_path(pid).read_text(encoding="utf-8")
    assert "## Closed" in body
    assert "implemented" in body.lower()
