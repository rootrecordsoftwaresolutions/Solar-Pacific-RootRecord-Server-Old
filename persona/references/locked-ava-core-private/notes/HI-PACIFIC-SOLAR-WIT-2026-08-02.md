# HI Pacific Solar Root Server + wit (2026-08-02)

## Public host name
**HI Pacific Solar Root Server** — that's the only public label.
- Weather still uses private lat/lon for NWS
- Never publish the city name

## Wit / random facts
- Module: `src/randomFacts.mjs`
- Dream + Root Server packs get occasional NSA/Oahu + Snowden jokes (public lore) and misc facts
- Poller posts to `#random-facts` ~every 6h
- CLI force: `node -e "import { runOccasionalRandomFact } from './src/randomFacts.mjs'; console.log(await runOccasionalRandomFact({force:true}))"`
