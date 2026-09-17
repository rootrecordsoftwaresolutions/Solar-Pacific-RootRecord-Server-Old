# QuickShop → rootmc-shops cutover

1. Build both jars: `:plugins:rootmc:build` and `:plugins:rootmc-shops:build`
2. Deploy to host + publish to `Web/main/realm/plugins/` (manifest + heartbeat URLs)
3. Run **one admin session** with QuickShop + rootmc-shops parallel (RootMC reads both; priority: rootmc-shops first)
4. Recreate spawn admin shops with `/rootshops create` (chest + sign flow)
5. Remove `QuickShop*.jar` from `plugins/`; set `shop-providers-priority` to rootmc-shops only in `rootmc.yml`
6. Reload: `/rootshops reload`, `/rootmc reload`

Rollback: restore QuickShop jar and re-add to provider priority until shops are migrated again.
