@echo off
cd /d "D:\Gen2 Current Handoff"
"C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\java.exe" -Xms1G -Xmx2G -jar paper-26.2-62.jar --nogui <nul >logs\agent-run.out.log 2>logs\agent-run.err.log
