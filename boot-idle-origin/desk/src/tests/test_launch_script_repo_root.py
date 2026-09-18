from __future__ import annotations

import os
import stat
import subprocess
from pathlib import Path


REPO = Path(__file__).resolve().parents[1]


def test_launch_script_runs_with_project_root_as_cwd(tmp_path: Path):
    uvicorn_path = REPO / ".venv" / "bin" / "uvicorn"
    assert uvicorn_path.is_file(), "venv uvicorn missing"
    backup = tmp_path / "uvicorn.backup"
    backup.write_bytes(uvicorn_path.read_bytes())

    try:
        uvicorn_path.write_text(
            "#!/bin/sh\n"
            "pwd > \"$LAUNCH_CWD_LOG\"\n"
            "printf '%s\\n' \"$PWD\" > \"$LAUNCH_PWD_LOG\"\n"
            "printf '%s\\n' \"$PYTHONPATH\" > \"$LAUNCH_PYTHONPATH_LOG\"\n"
            "exit 0\n",
            encoding="utf-8",
        )
        uvicorn_path.chmod(uvicorn_path.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

        fake_bin = tmp_path / "bin"
        fake_bin.mkdir()
        (fake_bin / "pgrep").write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
        (fake_bin / "lsof").write_text("#!/bin/sh\nexit 1\n", encoding="utf-8")
        for command in (fake_bin / "pgrep", fake_bin / "lsof"):
            command.chmod(command.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

        env = {
            **os.environ,
            "PATH": f"{fake_bin}:{os.environ['PATH']}",
            "LAUNCH_CWD_LOG": str(tmp_path / "cwd.txt"),
            "LAUNCH_PWD_LOG": str(tmp_path / "pwd.txt"),
            "LAUNCH_PYTHONPATH_LOG": str(tmp_path / "pythonpath.txt"),
            "PYTHONPATH": "",
            "AVA_CONSOLE_IDLE_STOP": "0",
            "AVA_OLLAMA_WARM": "0",
            "AVA_NPU_CHAT": "0",
            "AVA_DESK_UNITS": "0",
            "AVA_STATE_DIR": str(tmp_path / "state"),
            "AVA_LOGS_DIR": str(tmp_path / "logs"),
        }

        result = subprocess.run(
            [str(Path.home() / ".ollama" / "skills" / "launch" / "scripts" / "launch.sh")],
            cwd="/tmp",
            env=env,
            capture_output=True,
            text=True,
            check=False,
        )

        assert result.returncode == 0, result.stderr + result.stdout
        assert (tmp_path / "pwd.txt").read_text(encoding="utf-8").strip() == str(REPO)
        assert (tmp_path / "cwd.txt").read_text(encoding="utf-8").strip() == str(REPO)
        assert "idle-stop" not in result.stdout
    finally:
        uvicorn_path.write_bytes(backup.read_bytes())
        uvicorn_path.chmod(backup.stat().st_mode)


def test_launch_script_has_origin_recycle_loop_with_cap():
    text = Path.home().joinpath(
        ".ollama", "skills", "launch", "scripts", "launch.sh"
    ).read_text(encoding="utf-8")
    assert "AVA_ORIGIN_RECYCLE" in text
    assert "ORIGIN_RECYCLE_MAX" in text
    assert "recycling origin" in text
    assert "recycle-origin" in Path.home().joinpath(
        ".ollama", "skills", "recycle-origin", "scripts", "recycle-origin.sh"
    ).read_text(encoding="utf-8")
    env = Path.home().joinpath(
        ".ollama", "skills", "ollama-env", "scripts", "ollama-env.sh"
    ).read_text(encoding="utf-8")
    assert "OLLAMA_MAX_LOADED_MODELS" in env
    assert "OLLAMA_KEEP_ALIVE" in env
    assert 'OLLAMA_KEEP_ALIVE="${OLLAMA_KEEP_ALIVE:--1}"' in env
    assert "ollama_warm_chat" in env
    assert "drop_caches" not in env
    assert "AVA_FLM_AT_LAUNCH" in text
    assert "Starting FastFlowLM (NPU) first..." not in text
    assert "continuing with Ollama" not in text
    assert "nohup ollama serve" not in text
    assert "ava-console.pid" in text
    assert "setsid bash" in text
    assert "ava-flm.service" in text
    assert "Waiting for FastFlowLM (NPU) before origin" in text
