# GitHub push — auth gate (2026-08-02)

`ava-github-push` **committed locally** on MonoRepo `main`:

- Commit: `65815d8f` — `Ava: greenlights + post-ops dig ship (autopush, silent PROP close, automate-future)`
- Branch: `main` ahead of origin (also behind 1 — needs pull --rebase when auth works)

## Blocker
Active `gh` account is **RootRecord**; **Rootmcnet** keyring token is invalid.
Both `Rootmcnet/MonoRepo` and `Rootmcnet/rootmc-emergent` return *repository not found* (no access under current creds).

## Operator fix
```bat
gh auth login -h github.com -u Rootmcnet
gh auth switch -u Rootmcnet
cd /d D:\
git pull --rebase --autostash origin main
git push -u origin main
```
Then: `cd "Web Files\rootmc-ava" && node scripts/ava-github-push.mjs` for future digs.

Never force-push main.

— Ava
