---
name: hybrid-night-poller
description: >-
  Hybrid daily inserts every 30 minutes without Ava origin. Use when asked
  how hybrid stamps keep writing while the desk is idle.
---

# hybrid-night-poller

This folder **is** the runtime. Writes Core Ops Reports hybrid markdown. Does not invent watts.

## How it fires

User unit `ava-hybrid-night.service`. Launch starts it with AVA Console. Idle-stop stops it when the console closes.

Topic: `media-hybrid`.
