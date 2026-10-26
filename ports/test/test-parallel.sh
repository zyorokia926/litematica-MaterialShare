#!/bin/bash
# usage: test-parallel.sh <ver> <ver> ...  - runs two-clients.sh for the versions two at a time (own ports); summary in $MATERIALSHARE_LOGS/summary.txt
HERE=$(cd "$(dirname "$0")" && pwd)
LOGS=${MATERIALSHARE_LOGS:-/tmp/materialshare-test-logs}; mkdir -p "$LOGS"
: > "$LOGS/summary.txt"
i=0
for v in "$@"; do
  "$HERE/two-clients.sh" $v $((i % 2)) > "$LOGS/run_$v.log" 2>&1 &
  i=$((i + 1))
  if [ $((i % 2)) = 0 ]; then wait; fi
done
wait
for v in "$@"; do
  { echo "== $v"; grep -a "FAIL\|MIXIN\|BUILD\|PROBE EXC" "$LOGS/run_$v.log" | cut -c1-160
    echo "ok-lines: $(grep -ac ' OK ' "$LOGS/run_$v.log") last-lines: $(grep -ac 'took it out again\|after taking out' "$LOGS/run_$v.log")  (a pass is 3 and 2)"; } >> "$LOGS/summary.txt"
done
echo finished >> "$LOGS/summary.txt"
cat "$LOGS/summary.txt"
