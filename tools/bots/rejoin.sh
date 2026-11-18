#!/bin/bash
# waits for the 26.1 server to finish starting, then reconnects the given bots
cd "$(dirname "$0")"
timeout 120 bash -c 'until grep -q "Done (" ../../run261/server.out 2>/dev/null; do sleep 1; done'
for b in "$@"; do echo "$b join" >> ctl.txt; sleep 0.7; done
sleep 4
