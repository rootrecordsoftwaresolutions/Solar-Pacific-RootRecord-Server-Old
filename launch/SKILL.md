---
name: launch
description: >-
  AVA Console boot script. Use when asked how origin, Ollama, or login
  autostart come up. Idle-stop is a separate function desk.
---

# launch

This folder **is** the runtime for AVA Console.

## How it fires

- Login: `~/.config/autostart/ava-launcher.desktop` → `scripts/autostart-launch.sh` (skips while Starlink night-sleep).
- Then `scripts/launch.sh`: writes `ava-console-up` + `ava-console.pid`, starts FastFlowLM and **waits for the NPU**, then Ollama (coder/vision only), origin `:8787`, BT bridge unless the user unit is enabled, and **supervises** council as a console child (restarts on exit while this pid owns the desk) unless the unit is enabled. Starts EcoFlow BLE, hybrid night, and auto-push **with this console** (`AVA_DESK_UNITS=0` skips). Do not `nohup` those processes. Do not warm llama GGUF next to the NPU.
- Closing the console runs **idle-stop** only if this launch still owns `ava-console.pid` (a newer console’s trap/reaper will not tear the desk down). Idle-stop drops the flag first, then stops units. A reaper still runs idle-stop if the window is killed without the shell trap, with the same ownership check. User units have `ConditionPathExists` on that flag so they cannot respawn in the background. The PC stays on. Nothing in this desk stays up after the terminal is gone.
- Login autostart Execs `scripts/autostart-launch.sh` in this folder.
- `scripts/install.sh` is the one-shot venv/systemd installer. `AVA_ROOT` stays the Ava-Core tree.

Origin cwd is Ava-Core (`AVA_CORE`). Closing the console runs the **idle-stop** skill.

Do not enable `ava-bt-bridge.service` while launch also starts the bridge.

Topic: `boot-idle-origin`.
