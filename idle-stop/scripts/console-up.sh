#!/usr/bin/env bash
# Exit 0 only while AVA Console has written the live flag.
FLAG="${AVA_STATE_DIR:-$HOME/.ollama/skills/state/store}/ava-console-up"
[ -f "$FLAG" ]
