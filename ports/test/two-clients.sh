#!/bin/bash
# usage: two-clients.sh <mc version> [port slot]
# One server and two clients (Cara operator, Bob) on the work folder that ports/build.py made (/tmp/materialshare-ports/<version>).
# The port slot (0, 1, ...) lets several versions run at the same time. Logs go to $MATERIALSHARE_LOGS (default /tmp/materialshare-test-logs).
# Linux only (xvfb-run, /proc); needs Gradle 9.2 for 1.20.5 and later (GRADLE9, default /opt/gradle9/gradle-9.2.0/bin/gradle).
V=$1; G=gradle; case "$V" in 1.20.5|1.20.6) G=${GRADLE9:-/opt/gradle9/gradle-9.2.0/bin/gradle};; esac; W=${PORT_WORK:-/tmp/materialshare-ports}/$V
HERE=$(cd "$(dirname "$0")" && pwd); DATA=$HERE/data; PROBE=$HERE/TwoClientProbe.java.txt
P=${MATERIALSHARE_LOGS:-/tmp/materialshare-test-logs}; mkdir -p "$P"
PORT=$((25565 + ${2:-0}))
# stops only the processes that run in this version's work folder (so several versions can be tested at the same time)
killver() { for p in $(pgrep -f "java|Xvfb|xvfb-run|tail -f|timeout"); do
  c=$(readlink /proc/$p/cwd 2>/dev/null); case "$c" in $W|$W/*) tr '\0' ' ' </proc/$p/cmdline | grep -q GradleDaemon || kill -9 $p 2>/dev/null;; esac; done; sleep 2; }
killver
cd $W || exit 1
rm -rf run run-c run-b; mkdir -p run
cp $DATA/server/eula.txt $DATA/server/server.properties $DATA/server/ops.json run/
sed -i "s/level-name=.*/level-name=m$V$RANDOM/; s/spawn-protection=.*/spawn-protection=0/; s/^server-port=.*/server-port=$PORT/; s/^query.port=.*/query.port=$PORT/" run/server.properties
for d in run-c run-b; do mkdir -p $d; cp -r $DATA/client/config $DATA/client/schematics $DATA/client/options.txt $d/; done
for d in run-c run-b; do
  # the schematic placement of the test data points to the schematics folder of this run
  sed -i "s|/home/user/matlisttracker/run-sodium/schematics|$W/$d/schematics|g" $d/config/litematica/*.json
  for f in $d/config/litematica/litematica_127.0.0.1_25565*.json; do cp $f ${f/25565/$PORT}; done
done
cp $PROBE src/main/java/dev/materialshare/client/TmpProbe.java
grep -q "TmpProbe.init" src/main/java/dev/materialshare/client/ClientNet.java || sed -i 's|^        register();|        register();\n        if (System.getProperty("matlist.check") != null) {\n            TmpProbe.init();\n        }|' src/main/java/dev/materialshare/client/ClientNet.java
case "$V" in
  1.19.3|1.19.4) ;;
  1.20.1) sed -i 's|new ServerInfo("t", "127.0.0.1:" + PORT, false));|new ServerInfo("t", "127.0.0.1:" + PORT, false), false);|' src/main/java/dev/materialshare/client/TmpProbe.java ;;
  1.20.5|1.20.6) sed -i 's|screen.ConnectScreen;|screen.multiplayer.ConnectScreen;|' src/main/java/dev/materialshare/client/TmpProbe.java; sed -i 's|new ServerInfo("t", "127.0.0.1:" + PORT, false));|new ServerInfo("t", "127.0.0.1:" + PORT, ServerInfo.ServerType.OTHER), false, null);|' src/main/java/dev/materialshare/client/TmpProbe.java ;;
  1.20.3|1.20.4) sed -i 's|screen.ConnectScreen;|screen.multiplayer.ConnectScreen;|' src/main/java/dev/materialshare/client/TmpProbe.java; sed -i 's|new ServerInfo("t", "127.0.0.1:" + PORT, false));|new ServerInfo("t", "127.0.0.1:" + PORT, ServerInfo.ServerType.OTHER), false);|' src/main/java/dev/materialshare/client/TmpProbe.java ;;
  1.20.2) sed -i 's|new ServerInfo("t", "127.0.0.1:" + PORT, false));|new ServerInfo("t", "127.0.0.1:" + PORT, ServerInfo.ServerType.OTHER), false);|' src/main/java/dev/materialshare/client/TmpProbe.java ;;
  1.18*) sed -i 's|c.player.networkHandler.sendChatCommand(s);|c.player.sendChatMessage("/" + s);|; s|interactBlock(c.player, Hand.MAIN_HAND,|interactBlock(c.player, c.world, Hand.MAIN_HAND,|g' src/main/java/dev/materialshare/client/TmpProbe.java ;;
  *) sed -i 's|c.player.networkHandler.sendChatCommand(s);|c.player.sendCommand(s, null);|' src/main/java/dev/materialshare/client/TmpProbe.java ;;
esac
$G classes processResources -q 2>&1 | grep "error" | head -5
rm -f $P/in2_$V.fifo; mkfifo $P/in2_$V.fifo
rm -f $P/srv2_$V.log $P/cb2_$V.log $P/cc2_$V.log
export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dmatlist.check=1"
# server and both clients start at once (the client only connects after its title screen, which takes longer than the server start)
(tail -f $P/in2_$V.fifo | timeout 900 $G runServer > $P/srv2_$V.log 2>&1 &)
(nohup bash -c "sleep 2000 > $P/in2_$V.fifo" >/dev/null 2>&1 &)
(JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dmatlist.role=Bob -Dmatlist.port=$PORT" timeout 700 xvfb-run -a -s "-screen 0 1280x720x24" $G runClient -PrunDir=run-b -Puser=Bob > $P/cb2_$V.log 2>&1 &)
sleep ${CARA_DELAY:-4}
(JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dmatlist.role=Cara -Dmatlist.port=$PORT" timeout 700 xvfb-run -a -s "-screen 0 1280x720x24" $G runClient -PrunDir=run-c -Puser=Cara > $P/cc2_$V.log 2>&1 &)
opped=0
for i in $(seq 1 700); do
  if [ $opped = 0 ] && grep -aq "Done (" $P/srv2_$V.log 2>/dev/null; then echo "op Cara" > $P/in2_$V.fifo; opped=1; fi
  # finished: both probes printed their last line
  if grep -aq "took it out again" $P/cc2_$V.log 2>/dev/null && grep -aq "after taking out" $P/cb2_$V.log 2>/dev/null; then sleep 2; break; fi
  if grep -aq "TMPPROBE.*FAIL" $P/cc2_$V.log $P/cb2_$V.log 2>/dev/null; then sleep 3; break; fi
  if [ $(cat $P/cc2_$V.log $P/cb2_$V.log 2>/dev/null | grep -ac "TMPPROBE.*EXC") -gt 30 ]; then echo "PROBE EXCEPTIONS"; break; fi
  if grep -aq "FAILURE: Build failed" $P/cc2_$V.log $P/cb2_$V.log $P/srv2_$V.log 2>/dev/null; then echo "BUILD FAILED"; grep -ah "error:" $P/cc2_$V.log | head -5; break; fi
  sleep 1
done
echo "== $V"; grep -ah "Mixin apply.*failed" $P/cb2_$V.log $P/cc2_$V.log $P/srv2_$V.log | cut -c1-200 | sed "s/^/MIXIN FAILED: /" | sort -u; grep -ah "TMPPROBE" $P/cb2_$V.log $P/cc2_$V.log | sed 's/.*TMPPROBE//' | sort -u | head -40
grep -ah "Exception\|ERROR" $P/srv2_$V.log | grep -v "key layers\|authlib\|OpenAL\|session" | head -3
killver
