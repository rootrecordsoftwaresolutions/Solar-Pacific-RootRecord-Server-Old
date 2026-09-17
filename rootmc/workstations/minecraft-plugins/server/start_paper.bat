@echo off
setlocal
cd /d "%~dp0"

REM Optional: set memory (uncomment to override defaults in start_paper.py)
REM set PAPER_XMS=2G
REM set PAPER_XMX=4G

REM Paper 26.x needs Java 25; PATH may still be JDK 17 — force Temurin 25 for this server
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"

where py >nul 2>&1
if %errorlevel%==0 (
  py -3 "%~dp0start_paper.py"
) else (
  python "%~dp0start_paper.py"
)

if errorlevel 1 pause
endlocal
