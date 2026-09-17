# Ubuntu version lock

Docs historically mixed **24.04** and **26.04**. Use one installer for the pit-stop.

## Current intent (2026-08)

| Item | Value |
|------|--------|
| Preferred Server LTS to install | **Whatever USB was actually flashed** — verify ISO label before boot |
| Documented USB flash note | `UBUNTU-26.04-USB-F-2026-08-02.md` (SanDisk) |
| Older plan text | `ROOTATMUS_PRIME-Ubuntu-Server-SSH-Plan.md` may still say 24.04 |

**Rule:** the USB’s ISO label wins. After first boot, record:

```bash
. /etc/os-release
echo "$PRETTY_NAME"
```

Paste that one line into this file under “Installed” (operator edit).

## Installed (fill after first boot)

- PRETTY_NAME:
- Kernel:
- Install target disk serial (from `DISK-IDENTITY-LOCK.md`):

## Provision script

`scripts/ubuntu-provision-ecosystem.sh` is distro-agnostic enough for modern Ubuntu Server (NodeSource 22, Temurin 17/25). If apt repo names change on a newer release, fix the script once on E and re-run — do not fork a second provision tree on SSD-only.
