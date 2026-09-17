#!/bin/bash
set -euo pipefail
cd /home/rootrecord/.ollama/skills/origin/workstations/minecraft-plugins/server
exec /home/ava-core/.local/jdk-25/bin/java -Xms1G -Xmx2G -jar paper-26.2-62.jar --nogui
