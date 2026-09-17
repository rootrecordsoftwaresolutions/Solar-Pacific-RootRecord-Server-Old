# Capacitor & `file://` routing

Capacitor wraps a web bundle inside Android’s WebView. Many apps choose **HashRouter** because **`file://` URLs** do not behave like normal HTTPS origins for path-based routers.

## Symptom

Blank screen after navigation—often a routing mismatch.

## Fix mindset

- Prefer **hash routes** (`#/screen`) for predictable offline shells.
- Test **deep links** with custom schemes separately from SPA routing.

## Related reading

- [../06-development/build-and-release-mobile.md](../06-development/build-and-release-mobile.md)
