#!/bin/bash
# Rebuild the plugin, install it on the 26.1 test server (api-version patched), and stop the running server.
set -e
cd "$(dirname "$0")/../.."
./gradlew build -q -x test
python3 -I tools/bots/patch-api.py build/libs/Riposte-0.1.0.jar run261/plugins/Riposte-0.1.0.jar 26.1
cp build/libs/Riposte-0.1.0.jar run/plugins/Riposte-0.1.0.jar
if pgrep -f '^java .*paper.jar' >/dev/null; then
  echo stop >> run261/cmd.txt
  timeout 90 bash -c 'while pgrep -f "^java .*paper.jar" >/dev/null; do sleep 1; done'
fi
pkill -f '^tail -n0 -F cmd.txt' || true
# configs are copied on first start only; drop them so new defaults apply in tests
rm -f run261/plugins/Riposte/{messages,menus,scoreboard,kits}.yml
mv run261/server.out run261/server.prev.out 2>/dev/null || true
echo "deployed; start the server with run261/start.sh, then tools/bots/rejoin.sh <bots>"
