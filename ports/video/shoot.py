#!/usr/bin/env python3
"""Shoots the game footage of the introduction video: one server and two players (P1 = Alice, operator; P2 = Bob) on the
hidden desktop, driven by a director (a Java file in this folder that replaces the test probe), recorded from inside the game.

    py -I ports\\video\\shoot.py SpikeDirector --lang ja          the first try of the recorder
    py -I ports\\video\\shoot.py ShootDirector --lang en --size 1920x1080 --gui 2

Borrows everything else from ports/test/two-clients.py: the work folder of build.py, the hidden desktop, the window guard
(no focus, no mouse grab) and the memory guard (everything stops when less than 3 GB of the PC's RAM is free). The progress is
on http://localhost:8765/ like a test. The clips go to D:\\work\\materialshare-video\\shots\\<lang>\\ (MATERIALSHARE_VIDEO):
<clip>.mp4 (the frames of the game) and <clip>.jsonl (where the fake mouse was, clicks, marks of the director).
"""
import argparse
import importlib.util
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
# the places on this PC (メモ/04), unless they are set already
for k, v in {"PORT_WORK": r"D:\work\materialshare-ports", "GRADLE": r"D:\tools\gradle\gradle-8.14.3\bin\gradle.bat",
             "GRADLE9": r"D:\tools\gradle\gradle-9.2.0\bin\gradle.bat", "JAVA21_HOME": r"C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot",
             "MATERIALSHARE_LOGS": r"D:\work\materialshare-test-logs"}.items():
    os.environ.setdefault(k, v)
spec =importlib.util.spec_from_file_location("two_clients", HERE.parent / "test" / "two-clients.py")
tc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tc)
hidden_desktop = tc.hidden_desktop

VIDEO = Path(os.environ.get("MATERIALSHARE_VIDEO", r"D:\work\materialshare-video"))
NAMES = {"P1": "Alice", "P2": "Bob"}
# the games look good for the video (the tests use the lightest settings)
NICE_OPTIONS = {"maxFps": "120", "enableVsync": "false", "renderDistance": "8", "simulationDistance": "5", "graphicsMode": "1",
                "particles": "0", "entityShadows": "true", "ao": "true", "biomeBlendRadius": "2", "soundCategory_master": "0.0",
                "soundCategory_music": "0.0", "pauseOnLostFocus": "false", "narrator": "0", "tutorialStep": "none",
                "skipMultiplayerWarning": "true", "onboardAccessibility": "false"}
LANGS = {"ja": "ja_jp", "en": "en_us"}
MIXINS = ("TmpRecorderMixin", "TmpKeysMixin", "TmpTextMixin")
TIME_LIMIT = 30 * 60


def ffmpeg_path():
    found = shutil.which("ffmpeg") or str(Path(os.environ.get("LOCALAPPDATA", "")) / "Microsoft" / "WinGet" / "Links" / "ffmpeg.exe")
    return str(Path(found).resolve())


def prepare(ver, director, lang, size, gui):
    w, h = size
    tc.TILE_W, tc.TILE_H = w, h
    tc.FAST_OPTIONS = dict(NICE_OPTIONS, lang=LANGS[lang], guiScale=str(gui))
    work, port = tc.prepare(ver, 0)
    client = work / "src" / "main" / "java" / "dev" / "materialshare" / "client"
    (client / "TmpProbe.java").write_text(tc.patch_probe(ver, tc.read(HERE / f"{director}.java.txt")), encoding="utf-8", newline="\n")
    for name in ("TmpRecorder", "TmpInput", "TmpLocate", "TmpShot"):
        (client / f"{name}.java").write_text(tc.read(HERE / f"{name}.java.txt"), encoding="utf-8", newline="\n")
    for name in MIXINS:
        (client / "mixin" / f"{name}.java").write_text(tc.read(HERE / f"{name}.java.txt"), encoding="utf-8", newline="\n")
    cfg = work / "src" / "main" / "resources" / "litematica_materialshare.client.mixins.json"
    text = tc.read(cfg)
    missing = [m for m in MIXINS if f'"{m}"' not in text]  # the work folder keeps the file of earlier shoots: add each one that is not there
    if missing:
        cfg.write_text(text.replace('"client": [', '"client": [' + "".join(f'"{m}", ' for m in missing), 1), encoding="utf-8", newline="\n")
    return work, port


def main():
    sys.stdout.reconfigure(errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("director", help="a Java file of this folder without .java.txt, for example SpikeDirector")
    ap.add_argument("--ver", default="1.19.4", help="the main build (1.20.5 and 1.20.6 cannot start a project: SyncmaticaCheck, see HANDOFF)")
    ap.add_argument("--lang", choices=sorted(LANGS), default="ja")
    ap.add_argument("--size", default="1920x1080", help="the size of the game window = of the video (it cannot be larger than a monitor)")
    ap.add_argument("--gui", type=int, default=2, help="the GUI scale of the game")
    ap.add_argument("--fps", type=int, default=60)
    ap.add_argument("--explore", action="store_true", help="the director only writes the texts of some screens into the logs")
    ap.add_argument("--sharetest", action="store_true", help="only up to the first cut (Syncmatica share, start sharing): checks that a project can be started")
    ap.add_argument("--late", action="store_true", help="only the last parts (5 and 6) of the plan: house, schematic and sharing are done, the other cuts are skipped")
    a = ap.parse_args()
    size = tuple(int(x) for x in a.size.lower().split("x"))
    out = VIDEO / "shots" / a.lang
    out.mkdir(parents=True, exist_ok=True)
    for cue in list(out.glob("cue_*")) + list(out.glob("server_cmds*.txt")):  # the cues of the two players are files; old ones would let a player go on too early
        cue.unlink()
    cmds = out / "server_cmds.txt"
    tc.LOGS.mkdir(parents=True, exist_ok=True)
    tc.STOP_FILE.unlink(missing_ok=True)
    tc.PROGRESS_FILE.unlink(missing_ok=True)
    tc.atexit.register(tc.kill_all)
    tc.kill_leftovers()
    desktop = hidden_desktop.create_desktop(tc.DESKTOP_NAME)
    tc.say(f"shooting {a.director} ({a.ver}, {a.lang}, {a.size}, GUI scale {a.gui}) on the hidden desktop")
    tc.say(f"progress page: {tc.start_progress_page()}")
    windows = tc.Windows()
    t0 = time.time()
    procs = []
    try:
        tc.say("preparing the work folder")
        work, port = prepare(a.ver, a.director, a.lang, size, a.gui)
        g, env = tc.gradle_cmd(a.ver), tc.base_env()
        tc.say("compiling (gradle classes)")
        r = subprocess.run([g, "classes", "processResources", "-q", "--no-daemon"], cwd=work, env=env, capture_output=True, text=True, errors="replace")
        if r.returncode != 0:
            tc.say("BUILD FAILED")
            print("\n".join([l for l in (r.stdout + r.stderr).splitlines() if l.strip()][:30]))
            return 1
        logs = {k: tc.LOGS / f"shoot_{k}.log" for k in ("srv", "p1", "p2")}
        opts = (f"-Dmatlist.check=1 -Dmatlist.port={port} -Dshoot.ffmpeg={ffmpeg_path()} -Dshoot.out={out.as_posix()} -Dshoot.fps={a.fps}" + (" -Dshoot.explore=true" if a.explore else "") + (" -Dshoot.late=true" if a.late else "") + (" -Dshoot.sharetest=true" if a.sharetest else ""))

        def start(name, cmd, log, extra="", stdin=False):
            e = dict(env)
            e["JAVA_TOOL_OPTIONS"] = (e.get("JAVA_TOOL_OPTIONS", "") + " " + opts + " " + extra).strip()
            p = hidden_desktop.DesktopProc(cmd, work, e, tc.DESKTOP_NAME, log, stdin=stdin)
            procs.append(p)
            tc.ALL_PROCS.append(p)
            tc.say(f"{name} started (process {p.pid}, on the hidden desktop)")
            return p
        server = start("server", [g, "runServer", "--no-daemon"], logs["srv"], stdin=True)
        p2 = start(f"P2 {NAMES['P2']}", [g, "runClient", "--no-daemon", "-PrunDir=run-b", f"-Puser={NAMES['P2']}"], logs["p2"], "-Dshoot.role=P2")
        windows.add(p2, 1)
        time.sleep(4)
        p1 = start(f"P1 {NAMES['P1']}", [g, "runClient", "--no-daemon", "-PrunDir=run-c", f"-Puser={NAMES['P1']}"], logs["p1"], "-Dshoot.role=P1")
        windows.add(p1, 0)
        opped, seen, ran, beat = False, {"p1": 0, "p2": 0}, 0, 0.0
        while time.time() - t0 < TIME_LIMIT:
            if opped:  # the commands of the director, by the server console (no spam limit)
                lines = tc.read(cmds).splitlines()
                for line in lines[ran:]:
                    server.stdin.write((line + "\n").encode("utf-8"))
                server.stdin.flush()
                if len(lines) > ran:
                    ran = len(lines)
                    (out / "server_cmds_done.txt").write_text(str(ran), encoding="utf-8")
            time.sleep(0.2)
            if time.time() - beat < 1:
                continue
            beat = time.time()
            srv = tc.read(logs["srv"])
            if not opped and "Done (" in srv:
                server.stdin.write(f"op {NAMES['P1']}\n".encode())
                server.stdin.flush()
                opped = True
                tc.say(f"the server is ready, {NAMES['P1']} is made operator")
            texts = {k: tc.read(logs[k]) for k in ("p1", "p2")}
            for k, txt in texts.items():
                mine = [l.split("TMPPROBE", 1)[1].strip() for l in txt.splitlines() if "TMPPROBE" in l]
                for l in mine[seen[k]:]:
                    tc.say(l)
                seen[k] = len(mine)
            if windows.abort:
                tc.say("ABORTED: " + windows.abort)
                break
            if tc.STOP_FILE.exists():
                tc.say("stopped (the STOP file)")
                break
            if re.search(r"Mixin apply.*failed", texts["p1"] + texts["p2"]):
                tc.say("MIXIN FAILED")
            if "FAILURE: Build failed" in texts["p1"] + texts["p2"] + srv:
                tc.say("BUILD FAILED")
                break
            if p1.poll() is not None and p2.poll() is not None:
                break
    finally:
        for p in procs:
            tc.kill_tree(p)
        windows.stop = True
        tc.kill_all()
        hidden_desktop.close_desktop(desktop)
    tc.say(f"finished in {int(time.time() - t0)} s; the clips: {out}")
    for f in sorted(out.glob("*.mp4")):
        tc.say(f"  {f.name}: {f.stat().st_size / 2**20:.1f} MB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
