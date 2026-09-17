# Command Testing Onboarding — Discord #updates post

**Post after:** Launch retention pack / map return grant update

---

## Copy-paste for `#updates` (1520665313631408251)

```
**New — Command testing onboarding (`/cmdtest`)**

We added a guided way to learn every command available to you and earn up to **1000 G** from the server treasury while you do it.

**How it works**
• On join (including if you already play here), you'll get a short intro and gentle reminders every ~2 minutes
• **Run each command once** — the reminder for that command stops when you use it
• **1000 G total**, split evenly across the commands you have access to (permissions vary)
• Commands with a **service fee** (`/survey`, `/warp create`, founding a town, etc.) get **one free test use** — the fee is refunded during onboarding
• Manual steps (e.g. right-click a shop chest): **`/cmdtest try shop-right-click`**

**Useful commands**
• **`/cmdtest`** — progress + next suggested command
• **`/cmdtest list`** — everything still pending
• **`/cmdtest report <key> [note]`** — tell staff if something breaks

This is a one-time onboarding pass for everyone. Notifications stop command-by-command as you go, and fully when you're done.

Questions? Reply here or use **`/feedback`** in-game.
```

---

## Deploy

| Item | Action |
|------|--------|
| **roothelp** | `1.1.0` jar + `roothelp.yml` |
| **rootmc-realm-api** | `command-test-report` endpoint |
| **rootmc** | `1.3.38` (reachout relay, optional with this pack) |
| **MySQL** | Creates `root_command_test_progress` + `root_command_test_done` on first run |

**Restart** Paper after jar + YAML upload.

---

## Smoke test

1. Join as returning player → intro message + action bar reminder
2. Run `/balance` → reminder stops for balance, gold credited
3. `/cmdtest` → shows progress
4. `/cmdtest report balance test note` → staff Discord embed
5. Complete all → no more reminders, completion message
