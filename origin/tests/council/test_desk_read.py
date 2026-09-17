from apps.council.desk_read import read_rel, read_spec, resolve
import json


def test_desk_read_allows_apps():
    text = read_rel(".cursor/skills/council-telegram/scripts/desk_read.py")
    assert "Never dumps secrets" in text


def test_desk_read_allows_skill_desk():
    text = read_rel(".cursor/skills/ecosystem-index/SKILL.md")
    assert "Ecosystem index" in text
    assert resolve(".cursor/skills/../../../.env") is None


def test_desk_read_blocks_env_and_escape():
    assert resolve(".env") is None
    assert resolve("../.env") is None
    assert resolve("/etc/passwd") is None
    assert "blocked" in read_rel("secrets.env")
    assert "blocked" in read_rel("Media/public/x.md")
    assert "blocked" in read_spec("secrets.env")


def test_desk_read_ecoflow_alias(tmp_path, monkeypatch):
    live = tmp_path / "ecoflow-live.json"
    live.write_text('{"generator": true, "delta": {"label": "DELTA 2", "soc": 16}}\n', encoding="utf-8")
    monkeypatch.setattr("apps.core.services.ecoflow_public.live_path", lambda: live)
    body = read_spec("ecoflow")
    assert "DELTA 2" in body
    assert "R331" not in body


def test_kilauea_prompt_from_desk_files(tmp_path, monkeypatch):
    (tmp_path / "kilauea-alert.json").write_text(
        '{"alert_level": "watch", "erupting": false, "headline": "WATCH — elevated unrest", "updated_at": "2026-09-16T07:00:56+00:00"}',
        encoding="utf-8",
    )
    (tmp_path / "kilauea-situation.json").write_text(
        json.dumps(
            {
                "situation": {
                    "body": "Summary:\n New vents opened last night. Eruption History:\n 54 episodes since late December 2024.\n"
                }
            }
        ),
        encoding="utf-8",
    )
    (tmp_path / "kilauea-dashboard.json").write_text(
        '{"quakes_n": 20, "kilauea": {"alert_level": "watch", "erupting": false}}',
        encoding="utf-8",
    )
    (tmp_path / "kilauea-quakes.json").write_text(
        '{"quakes": [{"mag": 1.74, "place": "13 km S of Volcano, Hawaii"}]}',
        encoding="utf-8",
    )

    def fake_state(name: str):
        p = tmp_path / name
        return p if p.is_file() else None

    monkeypatch.setattr("apps.council.desk_read._state_file", fake_state)
    from apps.council.desk_read import kilauea_prompt_block, read_spec

    block = kilauea_prompt_block()
    low = block.lower()
    assert "watch" in low
    assert "not erupting" in low
    assert "new vents" in low
    assert "2018" not in block
    assert "m1.74" in low
    alias = read_spec("kilauea")
    assert "watch" in alias.lower()


def test_desk_facts_includes_relative_sources():
    from apps.council.desk_read import desk_facts_block, read_spec

    block = desk_facts_block(cap=2000)
    low = block.lower()
    assert "desk live files" in low
    assert "nws hawaii" in low
    assert "sun " in low and "sunrise" in low
    assert "rootmc client" in low
    assert "players_online" not in low
    assert read_spec("sun").startswith("Sun ")
    assert "Hurricane" in read_spec("hurricane") or "Hurricanes" in read_spec("hurricane")


def test_brainstorm_desk_omits_unrelated_live(monkeypatch):
    from apps.council.desk_read import brainstorm_desk_block

    monkeypatch.setattr(
        "apps.council.desk_read.kilauea_prompt_block",
        lambda **_k: "Kīlauea from desk files: watch",
    )
    soak = brainstorm_desk_block("NPU soak", cap=400)
    assert "Desk live files" not in soak
    assert "Kīlauea from desk" not in soak
    assert "quote a live desk only if the topic names it" in soak
    volc = brainstorm_desk_block("Kīlauea alert", cap=400)
    assert "Kīlauea from desk" in volc
