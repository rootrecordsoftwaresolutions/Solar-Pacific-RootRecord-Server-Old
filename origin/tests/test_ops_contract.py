import json

from fastapi.testclient import TestClient

from apps.core.main import app
from apps.core.routes.ops import _mobile_reports, _mobile_weather

client = TestClient(app)


def test_local_ops_contract_exposes_servers_and_actions():
    response = client.get("/api/ops/servers")
    assert response.status_code == 200, response.text
    payload = response.json()
    assert payload.get("ok") is True
    servers = payload.get("servers")
    assert isinstance(servers, list) and len(servers) > 0
    assert set(servers[0].keys()) >= {"id", "name", "status"}

    server_id = servers[0]["id"]
    perform = client.post("/api/ops/perform", json={"server_id": server_id, "action": "status"})
    assert perform.status_code == 200, perform.text
    assert perform.json().get("ok") is True

    rcon = client.post("/api/ops/rcon", json={"server_id": server_id, "command": "list"})
    assert rcon.status_code == 200, rcon.text
    assert rcon.json().get("ok") is True


def test_mobile_dashboard_fits_rfcomm():
    fat = {
        "ok": True,
        "audioManual": {"paths": ["x"] * 4000},
        "generated": "y" * 8000,
        "current": {"exists": True, "name": "evening.md", "mtimeMs": 1},
        "dueToday": [{"id": "evening", "label": "Evening", "when": "19:00", "done": False}],
    }
    slim = _mobile_reports(fat)
    assert "audioManual" not in slim
    assert "generated" not in slim
    assert slim["current"]["name"] == "evening.md"
    assert len(json.dumps(slim)) < 8_000
    weather = _mobile_weather({"ok": True, "temperature_f": 71, "period": "Tonight"})
    assert weather["temperature_f"] == "71"
