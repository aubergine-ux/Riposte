#!/bin/bash
# rounds.sh "<bots>" kit1 kit2 ... : every bot queues ranked for the kit, brawls, and waits for all matches to end
cd "$(dirname "$0")"
bots=($1); shift
for kit in "$@"; do
  start=$(grep -c 'ELO in' bots.log)
  for b in "${bots[@]}"; do echo "$b chat /queue ranked $kit" >> ctl.txt; done
  sleep 4
  for b in "${bots[@]}"; do echo "$b brawl $(python3 -c 'import random;print(round(random.uniform(1.0,4.0),1))')" >> ctl.txt; done
  want=$((start + ${#bots[@]}))
  for i in $(seq 1 60); do [ "$(grep -c 'ELO in' bots.log)" -ge "$want" ] && break; sleep 2; done
  echo "* stop" >> ctl.txt
  sleep 7
  echo "$kit: $(( $(grep -c 'ELO in' bots.log) - start )) rating updates"
done
