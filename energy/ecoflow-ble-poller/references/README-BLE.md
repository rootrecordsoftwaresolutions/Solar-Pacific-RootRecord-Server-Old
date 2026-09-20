# EcoFlow BLE primary (Delta 2 + River 2 Pro)

BLE is the live quota source. Cloud Open API is fallback when BLE is stale
(>60s) or the session is down. Do not invent SOC or watts.

- Service: `systemctl --user status ava-ecoflow-ble`
- Script: `ecoflow_ble_poller.py`
- Store: `ecoflow_ble_store.py`
- Vendor: `vendor/eflib` (rabits/ha-ef-ble, no Home Assistant)
- Packs:
  - Delta 2 `R331ZAB5SG6S2858` / MAC `24:58:7C:20:92:61` (`AVA_ECOFLOW_BLE_MAC`)
  - River 2 Pro `R621ZA16XH6K1155` / MAC `DC:06:75:56:AC:1D` (`AVA_ECOFLOW_RIVER_BLE_MAC`)
- Starlink AC is **Delta only** (`STARLINK_SN = DELTA_SN`). Never switched.
  The 400 W PV gate owns **Delta USB-C** daytime (feeds River USB-C, ~100 W DC).
  River AC stays on for the laptop.
- Writes: `quota/{SN}.json` with `source=ble` (chmod 444 while the session owns it)
- Keep the phone EcoFlow app off while this laptop owns the GATT session.
- User id is `AVA_ECOFLOW_USER_ID` in Ava-Core `.env` (not git).
