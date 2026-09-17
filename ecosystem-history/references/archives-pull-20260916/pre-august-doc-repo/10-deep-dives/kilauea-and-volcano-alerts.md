# Kīlauea, volcanic hazards, and how Weather Manager shows alerts

**Knowledge snippet:** Alerts involving **Kīlauea** and other Hawaiian volcanoes can appear in Weather Manager through the same **`alerts`** channel as severe weather—either **NOAA / NWS** “active alerts for this location” or **AccuWeather’s** aggregated alert feed (which includes standardized volcano product codes such as **VOW**, **VOWS**, and **VOA**). RootRecord **normalizes** those feeds for the dashboard bundle on **`api.rootrecord.info`**; it does **not** operate a separate USGS-only volcano pipeline.

This page is **public-facing educational** material: it explains the hazard context, how official agencies publish information, and how RootRecord’s stack fits into that picture—without replacing government guidance or emergency procedures.

---

## Why Kīlauea matters

**Kīlauea** is one of the most active volcanoes on Earth and lies on **Hawaiʻi Island** (Big Island), Hawaiʻi, United States. Activity can affect air quality (**vog**—volcanic smog), produce **ashfall** during explosive episodes, generate **lava flows** that alter landscapes and infrastructure, and drive **localized seismicity** that often appears alongside USGS reporting.

People care about timely notices for several overlapping reasons:

- **Health:** sulfur dioxide (SO₂) and fine particulates matter for respiratory-sensitive groups; ashfall can irritate eyes and lungs.
- **Aviation and transport:** ash clouds intersect flight routing; road closures follow lava or Civil Defense instructions.
- **Property and planning:** lava inundation zones and evolving hazards shape evacuation and recovery decisions.

Official science and civil-protection messaging come from **USGS / Hawaiian Volcano Observatory (HVO)** and partners (county Civil Defense, **NWS** when coordinated products exist). RootRecord surfaces **alert-style items that are already represented** in the feeds the Worker consumes—not a bespoke volcanic advisory desk.

---

## How official agencies publish volcano information

Understanding the upstream landscape keeps expectations realistic.

| Concept | Plain-language role |
|--------|----------------------|
| **USGS / HVO** | Primary scientific monitoring for Hawaiian volcanoes: deformation, gas, seismicity, eruptive status. Products include web updates, maps, and Volcano Observatory notices (including **VONA**—Volcano Observatory Notice for Aviation—when relevant). |
| **NWS** | Issues hazard products familiar from weather apps (e.g. **Ashfall Advisory** / **Warning** where coordinated). Those products can appear in **NWS API “active alerts”** for affected geographic areas. |
| **State / county Civil Defense** | Local evacuation, sheltering, and road guidance during crises—often via radio, social channels, and county alert systems; not everything is mirrored as a generic HTTP alert polygon. |

Volcano messaging is sometimes split across **scientific status** (USGS) and **impact-oriented warnings** (NWS or Civil Defense). Your Weather Manager view reflects **what the integrated alert feeds return for your saved location**, not every channel officials might use during an emergency.

---

## How RootRecord surfaces volcano-related alerts

Weather Manager clients obtain a **combined dashboard bundle** from the primary Worker (**`api.rootrecord.info`**)—see [Weather data pipeline](weather-data-pipeline.md). One field in that bundle is **`alerts`**, built by the Worker’s weather module.

There are **two mutually exclusive upstream modes** for that field (depending on deployment configuration):

### 1. NOAA / NWS (when commercial alert aggregation is not used)

The Worker requests **active alerts for the user’s latitude and longitude** from the **National Weather Service API** (`api.weather.gov`). Any **active NWS product** that applies to that point—including coordinated volcanic ash or related hazards where issued—can appear in the normalized list. Metadata includes sender, headlines, severity/urgency/certainty where provided, and links back to NWS detail pages when available.

**Teaching note:** This path is **point-based**. If an alert is geographic but your saved location falls outside the polygon, it may not appear even though the volcano is prominent regionally.

### 2. AccuWeather alert aggregation (when configured)

When **AccuWeather** keys are enabled on the Worker, the **`alerts`** field is populated from AccuWeather’s **`/alerts/v1/{locationKey}`** style feed for the resolved location. That feed can include **government and partner-issued** products bundled into AccuWeather’s schema.

The Worker maps known abbreviated types into readable labels, including:

| Code | Typical label used in normalization |
|------|-------------------------------------|
| **VOW** | Volcano Warning |
| **VOWS** | Volcano Watch |
| **VOA** | Volcano Activity |

Third-party issuers (for example USGS-style volcano products ingested through commercial pathways) sometimes supply narrative text in nested **`Area`** blocks rather than a single top-level **`Text`** field. The Worker **collects** those fragments so mobile and web clients still receive a coherent description where possible.

**Provider attribution** in the bundle reflects the active path (**NOAA / NWS** vs **AccuWeather**), consistent with transparency for end users and developers.

---

## Caching, refresh, and “staleness”

Dashboard responses participate in **TTL-based reuse** (`WEATHER_DATA_TTL_SEC` and shared-radius reuse) so the API stays fast and upstream quotas are respected. A manual “refresh” in the app may still be subject to server-side caching policies—see [Weather data pipeline](weather-data-pipeline.md).

For rapidly evolving eruptions, **always cross-check** official live sources (USGS HVO, NWS, Civil Defense) if conditions are dangerous.

---

## What users see in Weather Manager vs other hazard screens

- **Home / dashboard flow:** Volcano-related items that arrive through the **`alerts`** feed appear alongside other alert cards (floods, high surf, etc.) for the selected location.
- **Dedicated “Hazards” style views** in the app may emphasize **earthquakes, tsunamis, cyclones, wildfires**—those routes call **separate** hazard endpoints. Volcano notice **cards** are expected under **alerts**, not as a separate volcano-only tab, unless the product UI changes in a future release.

If documentation ever diverges from the shipped UI, trust the **application** behavior and file an issue against the relevant app repo.

---

## Limitations (read carefully)

- **Not a primary alerting authority.** RootRecord does not issue volcanic warnings; it **displays** normalized third-party feeds.
- **Not exhaustive.** Some official channels (radio, SMS county alerts, social posts) will not appear in **`alerts`**.
- **Geography matters.** Point-based filtering can exclude events that feel “nearby” but lie outside alert polygons.
- **Push notifications (FCM)** are a **separate** delivery mechanism from the alert list; availability depends on product configuration, account state, and OS policies—see [Push notifications (FCM)](push-notifications-fcm.md).

---

## Official and authoritative sources (external)

Use these for verification during elevated volcanic unrest or eruptions:

- **USGS Hawaiian Volcano Observatory:** [USGS Volcano Hazards Program — Hawaiian Volcano Observatory](https://www.usgs.gov/observatories/hawaiian-volcano-observatory)
- **Current volcanic activity (USGS):** [USGS Volcano Updates](https://www.usgs.gov/volcanoes) (navigate to Hawaiʻi volcanoes as presented on the site)
- **National Weather Service — Honolulu:** [NWS Honolulu](https://www.weather.gov/hfo/) (central Pacific forecasts and coordinated hazard products when issued)
- **Hawaii County Civil Defense:** refer to **official county** civil defense and emergency management channels for evacuation and road closures.

Links are provided for reader convenience; URLs may change when agencies reorganize their sites.

---

## Related reading

- [Weather Manager](../02-products/weather-manager.md)
- [Weather data pipeline](weather-data-pipeline.md)
- [Integrations & external systems](../03-platform/integrations.md)
- [API overview](../03-platform/api-overview.md)
- [FAQ](../09-reference/faq.md)
