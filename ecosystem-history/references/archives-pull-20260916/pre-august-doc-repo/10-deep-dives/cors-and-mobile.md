# CORS vs native apps

Browser sites must respect **CORS**. Native Capacitor WebViews often load **`file://`** bundles calling **`https://api...`**—still subject to platform networking rules but different from pure browser CORS.

## Debugging cross-origin errors

1. If reproducing in **Chrome DevTools** against API: check **`cors.ts`** helpers in Worker.
2. If only failing in **WebView**: inspect SSL pinning / proxy / corporate VPN issues.

## Teaching assistants

Ask whether the report came from **browser** or **WebView**—saves hours.

## Related reading

- [request-lifecycle.md](request-lifecycle.md)
