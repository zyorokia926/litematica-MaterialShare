#!/bin/bash
# usage: test2.sh <mc version>  - one server, two clients (Cara operator, Bob), after ports/build.py made /tmp/materialshare-ports/<version>
V=$1; G=gradle; case "$V" in 1.20.5|1.20.6) G=/opt/gradle9/gradle-9.2.0/bin/gradle;; esac; W=/tmp/materialshare-ports/$V; P=/home/user/port
K=/tmp/claude-0/-home-user/21d00089-7f2e-54eb-8542-967e38025553/scratchpad/killall.sh
bash $K
cd $W || exit 1
rm -rf run run-c run-b; mkdir -p run
cp $P/p192/run/eula.txt $P/p192/run/server.properties $P/p192/run/ops.json run/
sed -i "s/level-name=.*/level-name=m$V$RANDOM/; s/spawn-protection=.*/spawn-protection=0/" run/server.properties
for d in run-c run-b; do mkdir -p $d; cp -r $P/p192/run-c/config $P/p192/run-c/schematics $P/p192/run-c/options.txt $d/; done
[ -n "$NOSYNC" ] || true
cp $P/probe2/TmpProbe.java src/main/java/dev/materialshare/client/TmpProbe.java
grep -q "TmpProbe.init" src/main/java/dev/materialshare/client/ClientNet.java || sed -i 's|^        register();|        register();\n        if (System.getProperty("matlist.check") != null) {\n            TmpProbe.init();\n        }|' src/main/java/dev/materialshare/client/ClientNet.java
case "$V" in
  1.19.3|1.19.4) ;;
  1.20.1) sed -i 's|new ServerInfo("t", "127.0.0.1:25565", false));|new ServerInfo("t", "127.0.0.1:25565", false), false);|' src/main/java/dev/materialshare/client/TmpProbe.java ;;
  1.20.5|1.20.6) sed -i 's|screen.ConnectScreen;|screen.multiplayer.ConnectScreen;|' src/main/java/dev/materialshare/client/TmpProbe.java; sed -i 's|new ServerInfo("t", "127.0.0.1:25565", false));|new ServerInfo("t", "127.0.0.1:25565", ServerInfo.ServerType.OTHER), false, null);|' src/main/java/dev/materialshare/client/TmpProbe.java ;;
  1.20.3|1.20.4) sed -i 's|screen.ConnectScreen;|screen.multiplayer.ConnectScreen;|' src/main/java/dev/materialshare/client/TmpProbe.java; sed -i 's|new ServerInfo("t", "127.0.0.1:25565", false));|new ServerInfo("t", "127.0.0.1:25565", ServerInfo.ServerType.OTHER), false);|' src/main/java/dev/materialshare/client/TmpProbe.java ;;
  1.20.2) sed -i 's|new ServerInfo("t", "127.0.0.1:25565", false));|new ServerInfo("t", "127.0.0.1:25565", ServerInfo.ServerType.OTHER), false);|' src/main/java/dev/materialshare/client/TmpProbe.java ;;
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
(JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dmatlist.role=Bob" timeout 700 xvfb-run -a -s "-screen 0 1280x720x24" $G runClient -PrunDir=run-b -Puser=Bob > $P/cb2_$V.log 2>&1 &)
sleep ${CARA_DELAY:-4}
(JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dmatlist.role=Cara" timeout 700 xvfb-run -a -s "-screen 0 1280x720x24" $G runClient -PrunDir=run-c -Puser=Cara > $P/cc2_$V.log 2>&1 &)
opped=0
for i in $(seq 1 700); do
  if [ $opped = 0 ] && grep -aq "Done (" $P/srv2_$V.log 2>/dev/null; then echo "op Cara" > $P/in2_$V.fifo; opped=1; fi
  # finished: both probes printed their last line
  if grep -aq "took it out again" $P/cc2_$V.log 2>/dev/null && grep -aq "after taking out" $P/cb2_$V.log 2>/dev/null; then sleep 2; break; fi
  if grep -aq "TMPPROBE.*FAIL" $P/cc2_$V.log $P/cb2_$V.log 2>/dev/null; then sleep 3; break; fi
  if grep -aq "FAILURE: Build failed" $P/cc2_$V.log $P/cb2_$V.log $P/srv2_$V.log 2>/dev/null; then echo "BUILD FAILED"; grep -ah "error:" $P/cc2_$V.log | head -5; break; fi
  sleep 1
done
echo "== $V"; grep -ah "Mixin apply.*failed" $P/cb2_$V.log $P/cc2_$V.log $P/srv2_$V.log | cut -c1-200 | sed "s/^/MIXIN FAILED: /" | sort -u; grep -ah "TMPPROBE" $P/cb2_$V.log $P/cc2_$V.log | sed 's/.*TMPPROBE//' | sort -u | head -40
grep -ah "Exception\|ERROR" $P/srv2_$V.log | grep -v "key layers\|authlib\|OpenAL\|session" | head -3
bash $K
