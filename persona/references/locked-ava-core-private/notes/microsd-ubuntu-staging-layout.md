# microSD layout - Ubuntu installer staging

**Date:** 2026-08-03 (operator local ~2026-08-02 evening HST)  
**Disk:** Generic STORAGE DEVICE, serial `000000000819`, UniqueId contains `000000000819`, ~119 GB USB (Disk # may drift)

## Before (this session)
- One partition: **F:** UBUNTU (FAT32 ~19 GB)
- Remainder unallocated (~100 GB)

## After (DATA partition added)
- Partition 1: **F:** **UBUNTU** — FAT32 ~19 GB (installer staging)
- Partition 2: **G:** **DATA** — NTFS ~100 GB (all remaining space, quick format)
- No unallocated space left on this card

## Earlier history (same card)
- Had been three partitions (BOOT / Clean / unlabeled), then cleaned to GPT single ~19 GiB UBUNTU + unallocated; then DATA filled the rest

## Touch rule
Only this serial. Never C/D/E, JMicron 2TB (E:), or SanDisk Cruzer Ubuntu stick.
