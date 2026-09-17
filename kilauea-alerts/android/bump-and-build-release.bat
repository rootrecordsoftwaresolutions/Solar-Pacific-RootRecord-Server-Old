@echo off
setlocal EnableExtensions
rem Build signed Android release for Kilauea Alerts (native Kotlin / Gradle KTS).
rem
rem 0) Mobile\scripts\bump-mobile-version.ps1                                (versionCode +1 in app/build.gradle.kts; versionName PATCH = new versionCode)
rem 1) gradlew bundleRelease assembleRelease                                 (same order as Mobile\scripts\release-bump-and-build.ps1 -App kilauea)
rem 2) copy outputs into Mobile\builds\kilauea-alerts\                      (see Mobile\docs\RELEASE-BUILD-OUTPUTS.md)
rem
rem No cap sync, no web build — pure native Kotlin; bump helper is invoked with -BuildGradle only.
rem Release signing: app/build.gradle.kts release signingConfig + secrets in local.properties
rem   (RELEASE_STORE_*, keystore path under repo per local.properties.example).

set "APP=%~dp0"
set "APP_Q=%APP%"
if "%APP_Q:~-1%"=="\" set "APP_Q=%APP_Q:~0,-1%"
set "BUMP=%APP%..\scripts\bump-mobile-version.ps1"
set "GRADLEW=%APP%gradlew.bat"

cd /d "%APP%"

if not exist "%GRADLEW%" (
  echo gradlew.bat not found at: %GRADLEW%
  goto FAIL
)
if not exist "%BUMP%" (
  echo bump-mobile-version.ps1 not found at: %BUMP%
  goto FAIL
)
set "GJSON=%APP%app\google-services.json"
if not exist "%GJSON%" (
  echo ERROR: Missing Firebase config:
  echo   %GJSON%
  goto FAIL
)

echo.
echo === Kilauea Alerts release ===
echo.
echo [0/2] Version bump (versionCode +1)
set "VER="
for /f "usebackq delims=" %%V in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%BUMP%" -BuildGradle "%APP%app\build.gradle.kts"`) do set "VER=%%V"
if not defined VER (
  echo bump-mobile-version.ps1 produced no output - aborting.
  goto FAIL
)
echo Building v%VER%

echo.
echo [preflight] Stop Gradle daemons and clear stale lint cache
call "%GRADLEW%" --stop >nul 2>nul
powershell -NoProfile -ExecutionPolicy Bypass -Command "Remove-Item -LiteralPath '%APP%app\build\intermediates\lint-cache' -Recurse -Force -ErrorAction SilentlyContinue"

echo.
echo [1/2] gradlew bundleRelease assembleRelease
call "%GRADLEW%" bundleRelease assembleRelease
if errorlevel 1 goto FAIL

rem %VER% was set by [0/2] above (bump-mobile-version.ps1 stdout). Artifact names use that string
rem so filenames match versionName written into app/build.gradle.kts.

set "DEST=%~dp0..\builds\kilauea-alerts"
echo.
echo [4/4] Stage APK + AAB -^> %DEST%
powershell -NoProfile -ExecutionPolicy Bypass -File "%APP%..\scripts\stage-release-artifacts.ps1" -AppDir "%APP_Q%" -DestDir "%DEST%" -BaseName "RootRecord-Kilauea-Alerts" -Version "%VER%" -Native
if errorlevel 1 goto FAIL

echo.
echo Done. See %DEST% for RootRecord-Kilauea-Alerts-%VER%.apk and .aab
pause
endlocal
exit /b 0

:FAIL
set "ERR=%ERRORLEVEL%"
if "%ERR%"=="0" set "ERR=1"
echo.
echo ============================================================
echo BUILD FAILED. Exit code: %ERR%
echo ============================================================
pause
endlocal
exit /b %ERR%
