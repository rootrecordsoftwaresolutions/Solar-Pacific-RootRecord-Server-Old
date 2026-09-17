# Disk identity lock — wipe by serial, not letter

Drive **letters move**. Before any flash / repartition / installer target, match **serial + size**.

Snapshot taken on Windows before Ubuntu pit-stop (2026-08-03). Re-run `Get-Disk` after any USB shuffle and update this table.

## Known roles (this workstation)

| Disk # | FriendlyName | Serial | ~Size | Typical letter / role |
|--------|--------------|--------|-------|------------------------|
| 0 | HGST HTS721010A9E630 | `JR100X4M1R3TUE` | 931 GB | **D:** Work Station twin (flash / wipe target when operator says) |
| 1 | LITEON CV8-8E128-11 SATA 128GB | `TW059X3VLOH008AF04M1` | 119 GB | **C:** OS SSD (tight free space) |
| 2 | JMicron Tech | `ZDZSCFL4` | 1863 GB | **E:** Portable Archive — **RootMC source / Ubuntu mount `/mnt/e`** |
| 3 | Generic STORAGE DEVICE | `000000000819` | 119 GB | Removable / installer staging (letter may be F/G) |

Volume labels observed: D = `Work Station`, E = `Portable Archive`.

## Rules

1. **Never** “wipe D:” or “wipe E:” by letter alone in a checklist step — write the **serial**.
2. Ubuntu installer target = choose the disk by serial in the installer UI / `lsblk -o NAME,SIZE,SERIAL,MODEL`.
3. E (`ZDZSCFL4`) is the pit-stop mass-storage. Do not format it as the Ubuntu system disk by accident.
4. Installer media (`000000000819` or SanDisk USB) is **never** the RootMC tree.
5. After Ubuntu installs, put E’s UUID in `/etc/fstab` for `/mnt/e` so Remote-SSH paths stay stable.

## Linux discovery

```bash
lsblk -o NAME,SIZE,SERIAL,MODEL,MOUNTPOINT
realpath /dev/disk/by-id/* | sort
```

## Operator sign-off (fill when flashing)

- Date:
- Flash target serial:
- Flash target new role (Ubuntu SSD / Windows twin wipe / other):
- E serial confirmed mounted at `/mnt/e`:
- Installer media serial left in the machine? (y/n):
