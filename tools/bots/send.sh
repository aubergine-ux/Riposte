#!/bin/bash
# send.sh <wait-seconds> "<line>" ["<line>"...]  -> appends control lines, waits, prints new log lines
cd "$(dirname "$0")"
wait=$1; shift
before=$(wc -l < bots.log)
srv=../../${SERVER_DIR:-run261}/server.out
sbefore=$(wc -l < "$srv")
for line in "$@"; do
  if [[ $line == console\ * ]]; then echo "${line#console }" >> ../../${SERVER_DIR:-run261}/cmd.txt; else echo "$line" >> ctl.txt; fi
  sleep 0.4
done
sleep "$wait"
tail -n +$((before + 1)) bots.log
tail -n +$((sbefore + 1)) "$srv" | grep -E "WARN|ERROR|Exception|at io.github" | grep -v "moved too quickly\|moved wrongly" | head -20
