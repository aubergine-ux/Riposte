#!/bin/bash
# Rebuild the plugin, install it on the 26.1 test server (api-version patched) and restart it.
set -e
cd "$(dirname "$0")/../.."
./gradlew build -q -x test
python3 -I tools/bots/patch-api.py build/libs/Riposte-0.1.0.jar run261/plugins/Riposte-0.1.0.jar 26.1
cp build/libs/Riposte-0.1.0.jar run/plugins/Riposte-0.1.0.jar
# messages.yml is copied on first start only; drop it so new defaults apply in tests
rm -f run261/plugins/Riposte/messages.yml run261/plugins/Riposte/menus.yml run261/plugins/Riposte/scoreboard.yml
echo stop >> run261/cmd.txt
