# Safety: Mining / Local-AI Mutual Exclusion

**Hard rule from the original session:** there must never be a window where
local AI inference (Ollama) is running at the same time as the XMRig mining
process — running both together freezes the machine and logs out the active
session.

## What's specified

- The rule itself: mining and local inference are mutually exclusive.
- The reason: RandomX (Monero's mining algorithm) and the LLM stack are both
  tuned to the same 4-thread / 8MB-L3-cache ceiling (see
  `01-hardware-and-environment.md`) — running both saturates the same
  resource and takes the system down.

## What's not yet specified — needs a decision before building

The original session states the rule but never defines how it's enforced.
Recommend picking one of:

1. **Lock file approach:** both the mining launch script and the Ollama
   launch script (`OMP_NUM_THREADS=4 ollama serve`) check for and write a
   shared lock file (e.g. `/mnt/Projects/.mining_active.lock` or
   `/mnt/Projects/.ollama_active.lock`) before starting, and refuse to start
   if the other process's lock is present.
2. **Process-check approach:** the launcher for each checks whether the
   other's process is running (`pgrep xmrig` / `pgrep ollama`) before
   starting, and refuses/warns if so.
3. **Systemd unit conflict:** if both run as systemd services, define them as
   `Conflicts=` each other so systemd itself enforces exclusivity.

Whichever is chosen, it should be a mechanism the launch scripts check
automatically — not a rule a human has to remember to follow manually before
starting either process.
