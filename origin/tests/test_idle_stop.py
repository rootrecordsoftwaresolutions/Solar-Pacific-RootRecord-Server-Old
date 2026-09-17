from __future__ import annotations

import os
import stat
import subprocess
from pathlib import Path


REPO = Path(__file__).resolve().parents[1]
IDLE_STOP = Path.home() / ".ollama" / "skills" / "idle-stop" / "scripts" / "idle-stop.sh"


def test_idle_stop_dry_run_discovers_services_audio_and_ports(tmp_path: Path):
    fake_bin = tmp_path / "bin"
    fake_bin.mkdir()
    (fake_bin / "ps").write_text(
        "#!/bin/sh\n"
        "printf '%s\\n' '1001 uvicorn apps.core.main:app'\n"
        "printf '%s\\n' '1002 python -m apps.voice.director'\n"
        "printf '%s\\n' '1003 ollama serve'\n"
        "printf '%s\\n' '1007 /home/rootrecord/.local/opt/fastflowlm/flm-real serve llama3.2:3b'\n"
        "printf '%s\\n' '1004 ffplay AVA_MUSIC_BED track.wav'\n"
        "printf '%s\\n' '1005 node server.mjs'\n"
        "printf '%s\\n' '1006 python3 ava_bt_bridge.py'\n"
        "printf '%s\\n' '1008 python ecoflow_ble_poller.py'\n"
        "printf '%s\\n' '1009 python hybrid_night_poller.py'\n",
        encoding="ascii",
    )
    (fake_bin / "lsof").write_text(
        "#!/bin/sh\n"
        "case \"$2\" in\n"
        "  tcp:8787) printf '%s\\n' 2001 ;;\n"
        "  tcp:8791) printf '%s\\n' 2002 ;;\n"
        "  tcp:11434) printf '%s\\n' 2003 ;;\n"
        "  tcp:52625) printf '%s\\n' 2004 ;;\n"
        "esac\n",
        encoding="ascii",
    )
    (fake_bin / "systemctl").write_text("#!/bin/sh\nexit 0\n", encoding="ascii")
    for command in (fake_bin / "ps", fake_bin / "lsof", fake_bin / "systemctl"):
        command.chmod(command.stat().st_mode | stat.S_IXUSR)

    result = subprocess.run(
        [str(IDLE_STOP)],
        env={
            **os.environ,
            "HOME": str(tmp_path),
            "PATH": f"{fake_bin}:{os.environ['PATH']}",
            "IDLE_STOP_DRY_RUN": "1",
            "AVA_DATA_DIR": str(tmp_path),
        },
        capture_output=True,
        text=True,
        check=True,
    )

    output = result.stdout
    for pid in (1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 2001, 2002, 2003, 2004):
        assert f"would stop PID {pid}" in output
    assert "desk returned to idle state" in output
    assert "ava-bt-bridge" in output
    assert "ava-flm.service" in output
    assert "ava-ecoflow-ble.service" in output
    assert "ava-hybrid-night.service" in output
    assert "ava-auto-push.timer" in output
