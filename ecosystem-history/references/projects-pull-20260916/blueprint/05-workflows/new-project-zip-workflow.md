# Workflow: New / Unrelated Project Requests

Stated requirement from the original session:

> When the agent team is asked to create something new or specific for an
> unrelated purpose, they should create a new project folder, and once
> confirmed done, zip it for presentation.

## Spec

1. On detecting a request that's unrelated to the current active project (a
   distinct one-off ask rather than a continuation of ongoing work), create a
   new, separate project folder under `/mnt/Projects`.
2. Do all work for that request inside its own folder — keep it decoupled
   from the active codebase and from other one-off projects.
3. Once the human confirms the work is done, zip the folder for hand-off/
   presentation (same dated-archive convention as
   `05-workflows/proposal-execution-gate.md` is a reasonable fit, though this
   wasn't explicitly tied to that gate in the original session — worth
   confirming whether one-off projects should go through the same
   propose-then-approve flow, or whether "confirmed done" is a lighter-weight
   sign-off).
