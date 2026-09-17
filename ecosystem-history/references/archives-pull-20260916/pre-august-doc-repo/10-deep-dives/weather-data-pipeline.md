# Weather data pipeline (conceptual)

## Upstream

Providers (e.g. AccuWeather) and **NOAA** feeds supply raw meteorological and alert content.

## Worker middle layer

- **Crons** fetch upstream payloads on schedules.
- **TTL caches** avoid hammering paid APIs for identical grid points (`WEATHER_DATA_TTL_SEC`).

## Downstream

Mobile apps request **bundles** that combine current conditions, forecasts, and alert collections suitable for UI cards.

Volcano-related notices for Hawaiʻi (for example **Kīlauea**) ride the same **`alerts`** collection inside that bundle—either **NWS active alerts for the point** or **AccuWeather’s** aggregated feed when configured—including standardized volcano product codes (**VOW**, **VOWS**, **VOA**). See **[Kīlauea & volcano alerts](kilauea-and-volcano-alerts.md)** for full public-facing context.

## Teaching users

“Refresh” in UI **does not** always bypass server caches—this saves money and keeps responses fast.

## Related reading

- [../02-products/weather-manager.md](../02-products/weather-manager.md)
- [Kīlauea & volcano alerts](kilauea-and-volcano-alerts.md)
