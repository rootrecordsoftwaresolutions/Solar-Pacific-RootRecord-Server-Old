# Repository Guidance: RootRecord Core Ops

This repository is the local RootRecord operator and transparency system. It has no license and must not be treated as a redistributable product.

## Scope

- Put Electron/operator controls, review queues, SSH/VPN settings, OBS integration, local backup and restore workflows, and operational visibility here.
- Keep the laptop as the human control plane for the Processor.
- Keep backup artifacts and secrets outside Git.

## Boundaries

- RootMC development belongs only in `RootRecord-RootMC`.
- Self-hostable public software belongs in `RootRecord-Core-Node`.
- Hosted long-running automation belongs in `RootRecord-Core-Processor`.

## Safety

Never commit credentials, tokens, private keys, live database copies, session cookies, or unreviewed personal data. Any destructive restore or production control requires explicit operator action.
