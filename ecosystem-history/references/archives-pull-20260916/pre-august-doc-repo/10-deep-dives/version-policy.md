# Mobile version policy endpoint

`GET /api/mobile/version-policy` returns semver floors for each Android package (`MIN_APP_VERSION_*`) and store URLs when forced upgrade paths trigger.

## Why it exists

Weather and Business move fast; **ancient** APKs may hit incompatible D1 schemas or missing routes.

## Teaching users

If the app says **update required**, the Play Store link in policy JSON is the fastest fix.

## Teaching engineers

Bump floors **only after** stratifying crash analytics—over-eager minimum versions strand users on old phones.

## Related reading

- [../06-development/build-and-release-mobile.md](../06-development/build-and-release-mobile.md)
