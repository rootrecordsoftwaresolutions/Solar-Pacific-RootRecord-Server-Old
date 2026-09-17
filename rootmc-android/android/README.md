# RootMC Android (Block Notes)

Native Kotlin / Compose companion for RootMC.

## Build

```bat
cd "D:\RootMC Workspace\Mobile App Files\rootmc-android"
.\gradlew.bat bundleRelease assembleRelease
```

## FCM / push

1. Download **`google-services.json`** → `app/google-services.json`
2. Deploy Worker with FCM secret: set `FCM_SERVICE_ACCOUNT_JSON_PATH` in `RootMC Workspace\.env`, then:

```powershell
powershell -File "D:\RootMC Workspace\Web Files\rootmc-api\deploy.ps1"
```
