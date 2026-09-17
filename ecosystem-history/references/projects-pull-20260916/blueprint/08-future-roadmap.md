# Future Roadmap (post-core-build)

The original session was explicit that these come **after** the core system
(everything in `01`–`07`) is working: *"Once the core functions are done, we
will plug in some other projects."* Listed here so the context isn't lost,
but none of these are specified in enough detail to build directly — each
needs a scoping pass first.

## 1. Agent "desires" / Roadmap Contemplation Mode

Stated idea: agents should eventually be able to generate their own goals.
When not mining and otherwise idle, they may enter a "Roadmap Contemplation
Mode" and produce self-initiated proposals — described as working "kinda the
same as proposals."

**Not yet specified:**
- What a desire is allowed to be *about* — any scope, or bounded to certain
  categories (e.g. only code quality/performance, never anything financial
  or infrastructure-altering)?
- Whether desires enter the same `05-workflows/proposal-execution-gate.md`
  human-approval flow as everything else, or some separate path. Recommend:
  **the same gate, no separate path** — nothing about "self-generated" should
  mean less human oversight, if anything it should mean more.
- How "idle" is detected, and what triggers exiting contemplation mode.

## 2. Shared Litecoin wallet + per-agent wallet creation

Stated idea: the three agents eventually share a central Litecoin wallet, and
get CLI access to create additional wallets for "any roadmap function
requiring funds."

**This is the highest-risk item in the entire blueprint and should not be
built until it has an explicit design**, at minimum covering:
- Who custodies the private keys / seed — never the agents themselves in a
  way that lets them move funds autonomously.
- A hard spending limit and an explicit human-approval step for every
  outbound transaction, no exceptions — this should route through something
  at least as strict as `05-workflows/proposal-execution-gate.md`, arguably
  stricter (financial actions, not just code changes).
- What "a roadmap function requiring funds" is actually allowed to mean in
  practice — this was never defined in the original session.
- Read-only wallet balance/history access vs. spend access should likely be
  separate permission tiers, similar to the Ava/Bruce/Carly database split in
  `06-permissions-and-safety/database-rbac.md`.

## 3. Additional unrelated projects

*"There's going to be a ton of other functions we will add after all of this
is done as well."* No specifics given — `05-workflows/new-project-zip-workflow.md`
already covers how a new unrelated project request should be handled
structurally (its own folder, zipped on completion) whenever these arrive.
