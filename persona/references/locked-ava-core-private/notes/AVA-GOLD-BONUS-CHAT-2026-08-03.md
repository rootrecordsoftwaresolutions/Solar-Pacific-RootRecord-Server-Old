# Ava Gold bonus chat (solar bank) — 2026-08-03

When EcoFlow bank is **online** and mine mult > 1.0×:
- Join MOTD (`Root-Times`) adds: `&dAva &8· &6Gold bonus &8· &f{mult}x &7from solar bank &f{bank}%`
- Gold ore → loan sweep and gold-ore mining (throttled 8s) same Ava line
- Shared helper: `rootrecord-common` `AvaChat.solarGoldBonusLine`

Build: `publishPlugins` then stage Root-Times + Root-Economy (+ Root-Loans if separate) into host handoffs.
