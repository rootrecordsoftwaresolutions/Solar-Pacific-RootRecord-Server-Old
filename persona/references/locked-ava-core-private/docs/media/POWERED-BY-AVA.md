# Powered by Ava (sitewide footer)

## Live

- API: `GET /api/powered-by` → `{ ok, cpu, ram, soc, window:"1h", href }`
- Public: `https://ava.rootmc.net/api/powered-by`
- Widget: `https://rootrecord.info/ava/assets/powered-by-ava.js`

## Wired

- Ava wiki (`rootrecord-ava`) — all HTML pages
- Merged homepage (`merged.rootrecord.info`)
- Ava solar/status HTML (`solarPage.mjs` / `statusPage.mjs`)
- RootMC web static build (`Web Files/rootmc-web/build/**`) + app/public shells
- Root Record Solana site — `PoweredByAva` in Footer (`D:\Pre August\solana-rootrecord-site`) — needs site redeploy

## Metrics

Last-hour averages: CPU% + RAM% from host metrics; SOC% from EcoFlow bank rolling avg (1h window).
