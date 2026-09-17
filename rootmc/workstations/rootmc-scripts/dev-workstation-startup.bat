@echo off
set "LOCAL=%LOCALAPPDATA%\RootMC\dev-workstation\dev-workstation-startup.ps1"
if exist "%LOCAL%" (
  powershell -NoProfile -WindowStyle Minimized -ExecutionPolicy Bypass -File "%LOCAL%" %*
) else (
  echo Not installed. Run scripts\install-dev-workstation.bat first.
  exit /b 1
)
