@echo off
REM Bootstrap: free a little C: space then run full cleanup. Logs only to D:.
set LOG=D:\.1 Work Stations\RootMC\scripts\cleanup-c-drive.log
echo START %DATE% %TIME% > "%LOG%"
del /q /f /s "%LOCALAPPDATA%\Temp\*.*" >> "%LOG%" 2>&1
for /d %%d in ("%LOCALAPPDATA%\Temp\*") do rd /s /q "%%d" >> "%LOG%" 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -File "D:\.1 Work Stations\RootMC\scripts\cleanup-c-drive.ps1" >> "%LOG%" 2>&1
echo DONE %DATE% %TIME% >> "%LOG%"
