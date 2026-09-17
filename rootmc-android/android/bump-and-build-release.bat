@echo off
setlocal EnableExtensions
rem Build signed Android release for RootMC (display name; package com.rootrecord.rootmc).
rem
rem Baseline in repo: versionCode 0, versionName 1.0.0
rem First run bumps to versionCode 1, versionName 1.0.1 (Play-ready).
rem
rem 0) scripts\bump-mobile-version.ps1                                (versionCode +1; PATCH = new code)
rem 1) gradlew bundleRelease assembleRelease                          (signed APK + AAB)
rem 2) copy outputs into builds\                                      (RootRecord-RootMC-<version>.apk/.aab)
rem
rem Release signing: app/build.gradle.kts + gitignored local.properties
rem   RELEASE_STORE_FILE=keystore/blocknotes-upload.jks
rem   RELEASE_KEY_ALIAS=blocknotes-upload
rem   RELEASE_STORE_PASSWORD / RELEASE_KEY_PASSWORD
rem
rem Optional API (feedback/auth): deploy rootmc-api from RootMC Workspace

set "APP=%~dp0"
set "APP_Q=%APP%"
if "%APP_Q:~-1%"=="\" set "APP_Q=%APP_Q:~0,-1%"
set "BUMP=%APP%scripts\bump-mobile-version.ps1"
set "STAGE=%APP%scripts\stage-release-artifacts.ps1"
set "GRADLEW=%APP%gradlew.bat"
set "LOCAL=%APP%local.properties"
set "KEYSTORE=%APP%keystore\blocknotes-upload.jks"

cd /d "%APP%"

if not exist "%GRADLEW%" (
  echo gradlew.bat not found at: %GRADLEW%
  goto FAIL
)
if not exist "%BUMP%" (
  echo bump-mobile-version.ps1 not found at: %BUMP%
  goto FAIL
)
if not exist "%STAGE%" (
  echo stage-release-artifacts.ps1 not found at: %STAGE%
  goto FAIL
)
if not exist "%LOCAL%" (
  echo ERROR: Missing release signing config:
  echo   %LOCAL%
  echo Copy local.properties.example and set RELEASE_STORE_* / RELEASE_KEY_*.
  goto FAIL
)
if not exist "%KEYSTORE%" (
  echo ERROR: Missing upload keystore:
  echo   %KEYSTORE%
  echo Create it once — see README "Play signing".
  goto FAIL
)

echo.
echo === RootMC release ===
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
echo [preflight] JDK 17 for Gradle (JDK 25 breaks Android Gradle Plugin)
set "JAVA_HOME="
for /f "usebackq delims=" %%J in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$env:JAVA_HOME = $null; $patterns = @('C:\Program Files\Microsoft\jdk-17*','C:\Program Files\Eclipse Adoptium\jdk-17*','C:\Program Files\Java\jdk-17*'); foreach ($pat in $patterns) { $hit = Get-ChildItem -Path $pat -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1; if ($hit -and (Test-Path (Join-Path $hit.FullName 'bin\java.exe'))) { Write-Output $hit.FullName; exit 0 } }; exit 1"`) do set "JAVA_HOME=%%J"
if not defined JAVA_HOME (
  echo ERROR: JDK 17 not found. Install Temurin/Microsoft JDK 17 or set JAVA_HOME to a JDK 17 path.
  goto FAIL
)
echo Using JAVA_HOME=%JAVA_HOME%
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [preflight] Stop Gradle daemons and clear stale lint cache
call "%GRADLEW%" --stop >nul 2>nul
powershell -NoProfile -ExecutionPolicy Bypass -Command "Remove-Item -LiteralPath '%APP%app\build\intermediates\lint-cache' -Recurse -Force -ErrorAction SilentlyContinue"

echo.
echo [1/2] gradlew bundleRelease assembleRelease
call "%GRADLEW%" bundleRelease assembleRelease
if errorlevel 1 goto FAIL

set "DEST=%APP%builds"
echo.
echo [2/2] Stage APK + AAB -^> %DEST%
powershell -NoProfile -ExecutionPolicy Bypass -File "%STAGE%" -AppDir "%APP_Q%" -DestDir "%DEST%" -BaseName "RootRecord-RootMC" -Version "%VER%" -Native
if errorlevel 1 goto FAIL

echo.
echo Done. Upload to Play Console:
echo   %DEST%\RootRecord-RootMC-%VER%.aab
echo   %DEST%\RootRecord-RootMC-%VER%.apk
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
