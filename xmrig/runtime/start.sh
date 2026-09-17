#!/bin/bash
# Operator shortcut — same as xmrig_ctl start (visible terminal when DISPLAY is set).
exec python3 "$HOME/.ollama/skills/xmrig/scripts/xmrig_ctl.py" start "$@"
