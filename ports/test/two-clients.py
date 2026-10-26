#!/usr/bin/env python3
"""Windows version of two-clients.sh: one server and two clients (Cara operator, Bob) on the work folder that ports/build.py made.

    py ports\\test\\two-clients.py 1.20.4                 one version
    py ports\\test\\two-clients.py 1.20.1 1.20.2 --jobs 3  several versions, JOBS at the same time (default 3 = 6 windows; own ports)

The game windows really open. They are put on the secondary monitor in a grid, behind everything else, without taking the
keyboard focus (and the focus is given back to the window that had it). Needs psutil (py -m pip install psutil).
Environment: PORT_WORK (work folders of build.py), MATERIALSHARE_LOGS (logs), GRADLE (up to 1.20.4), GRADLE9 (1.20.5 and later),
JAVA21_HOME (the JDK that runs Gradle). A pass is "ok-lines: 3 last-lines: 2" and no MIXIN FAILED.
"""
import argparse
import atexit
import ctypes
import os
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from ctypes import wintypes
from pathlib import Path

import psutil

HERE = Path(__file__).resolve().parent
DATA = HERE / "data"
PROBE = HERE / "TwoClientProbe.java.txt"
WORK = Path(os.environ.get("PORT_WORK", Path(tempfile.gettempdir()) / "materialshare-ports"))
LOGS = Path(os.environ.get("MATERIALSHARE_LOGS", Path(tempfile.gettempdir()) / "materialshare-test-logs"))
ALL_PROCS = []  # every process this script started (the last safety net kills them)
STOP_FILE = LOGS / "STOP"  # create this file and all tests stop at once
TIME_LIMIT = 20 * 60  # seconds for the whole run
TILE_W, TILE_H, TILE_ROWS = 640, 360, 2  # set by set_layout()
# the options that make a test client light: small world, few frames (the probe works on ticks)
FAST_OPTIONS = {"maxFps": "60", "enableVsync": "false", "renderDistance": "2", "simulationDistance": "5", "graphicsMode": "0",
                "particles": "2", "entityShadows": "false", "ao": "false", "biomeBlendRadius": "0", "soundCategory_music": "0.0"}

user32 = ctypes.windll.user32
user32.GetForegroundWindow.restype = wintypes.HWND
user32.SetForegroundWindow.argtypes = [wintypes.HWND]
user32.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
user32.SetWindowPos.argtypes = [wintypes.HWND, wintypes.HWND, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int, wintypes.UINT]
user32.GetClassNameW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
user32.IsWindowVisible.argtypes = [wintypes.HWND]
HWND_BOTTOM = wintypes.HWND(1)
SWP_NOACTIVATE, SWP_NOOWNERZORDER = 0x0010, 0x0200
GWL_EXSTYLE, WS_EX_NOACTIVATE = -20, 0x08000000
user32.GetWindowLongW.argtypes = [wintypes.HWND, ctypes.c_int]
user32.SetWindowLongW.argtypes = [wintypes.HWND, ctypes.c_int, ctypes.c_long]
user32.IsWindow.argtypes = [wintypes.HWND]


def secondary_monitor():
    """(x, y, width, height) of the first monitor that is not the primary one (the primary one if there is only one)"""
    class MONITORINFO(ctypes.Structure):
        _fields_ = [("cbSize", wintypes.DWORD), ("rcMonitor", wintypes.RECT), ("rcWork", wintypes.RECT), ("dwFlags", wintypes.DWORD)]
    found = []

    def cb(hmon, hdc, rect, data):
        mi = MONITORINFO()
        mi.cbSize = ctypes.sizeof(mi)
        user32.GetMonitorInfoW(hmon, ctypes.byref(mi))
        r = mi.rcWork
        found.append((bool(mi.dwFlags & 1), r.left, r.top, r.right - r.left, r.bottom - r.top))
        return True
    proc = ctypes.WINFUNCTYPE(ctypes.c_int, wintypes.HANDLE, wintypes.HDC, ctypes.POINTER(wintypes.RECT), wintypes.LPARAM)
    user32.EnumDisplayMonitors(None, None, proc(cb), 0)
    others = [m for m in found if not m[0]]
    m = (others or found)[0]
    return m[1:]


def set_layout(windows_count):
    """a grid of tiles that fills the secondary monitor: every version is one column (Cara above Bob, further rows if they fit)"""
    global TILE_W, TILE_H, TILE_ROWS
    _, _, mw, mh = secondary_monitor()
    TILE_ROWS = 2 if windows_count <= 8 else 3 if windows_count <= 15 else 4
    cols = -(-windows_count // TILE_ROWS)
    TILE_W, TILE_H = mw // cols, mh // TILE_ROWS


class Windows:
    """Puts the game windows of our processes on the secondary monitor, behind all others, and never keeps the focus."""

    def __init__(self):
        self.mon = secondary_monitor()
        self.roots = {}  # root pid -> tile index
        self.placed = {}  # hwnd -> index
        self.last_other = None  # the last foreground window that is not ours
        self.stop = False
        self.errors = []
        self.mem_peak = 0  # highest memory use of the whole PC (percent) and the lowest free memory (GB)
        self.free_min = 1e9
        self.abort = ""  # the reason why everything must stop (checked by the test loops)
        self.hold = 0  # how many checks in a row a game window had the focus
        self.t0 = time.time()
        self.stolen = 0  # how many checks (every 0.03 s) a game window had the focus
        threading.Thread(target=self.loop, daemon=True).start()

    def add(self, proc, index):
        self.roots[proc.pid] = index

    def pids(self):
        out = {}
        for root, index in list(self.roots.items()):
            try:
                for p in [psutil.Process(root)] + psutil.Process(root).children(recursive=True):
                    out[p.pid] = index
            except psutil.Error:
                pass
        return out

    def loop(self):
        while not self.stop:
            try:
                self.step()
            except Exception as e:
                self.errors.append(repr(e))
            time.sleep(0.03)

    def release_cursor(self):
        # safety net: if a game ever clips the mouse to its window, let it go at once
        r = wintypes.RECT()
        user32.GetClipCursor(ctypes.byref(r))
        if (r.right - r.left) < user32.GetSystemMetrics(78) or (r.bottom - r.top) < user32.GetSystemMetrics(79):
            user32.ClipCursor(None)
            self.errors.append("mouse was clipped, released")

    def step(self):
        m = psutil.virtual_memory()
        self.mem_peak = max(self.mem_peak, m.percent)
        self.free_min = min(self.free_min, m.available / 2**30)
        if m.available < 3 * 2**30:
            self.abort = "less than 3 GB of free memory"
        elif psutil.cpu_percent(None) > 99.5 and m.percent > 90:
            self.abort = "the PC is overloaded"
        elif shutil.disk_usage(WORK).free < 5 * 2**30:
            self.abort = "less than 5 GB of free disk space"
        elif STOP_FILE.exists():
            self.abort = "stopped by the file " + str(STOP_FILE)
        elif time.time() - self.t0 > TIME_LIMIT:
            self.abort = "time limit"
        pids = self.pids()
        self.release_cursor()
        fg = user32.GetForegroundWindow()
        fpid = wintypes.DWORD()
        user32.GetWindowThreadProcessId(fg, ctypes.byref(fpid))
        ours = fpid.value in pids
        if fg and not ours:
            self.last_other = fg
        mx, my, mw, mh = self.mon

        def cb(hwnd, _):
            pid = wintypes.DWORD()
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
            if pid.value in pids and user32.IsWindowVisible(hwnd):
                cls = ctypes.create_unicode_buffer(64)
                user32.GetClassNameW(hwnd, cls, 64)
                if cls.value.startswith("GLFW") and hwnd not in self.placed:
                    i = pids[pid.value]
                    # a window that never takes the focus: Minecraft only grabs the mouse while its window has the focus
                    user32.SetWindowLongW(hwnd, GWL_EXSTYLE, user32.GetWindowLongW(hwnd, GWL_EXSTYLE) | WS_EX_NOACTIVATE)
                    x = mx + (i // TILE_ROWS) * TILE_W
                    y = my + (i % TILE_ROWS) * TILE_H
                    user32.SetWindowPos(hwnd, HWND_BOTTOM, x, y, TILE_W, TILE_H, SWP_NOACTIVATE | SWP_NOOWNERZORDER)
                    self.placed[hwnd] = i
            return True
        user32.EnumWindows(ctypes.WINFUNCTYPE(ctypes.c_int, wintypes.HWND, wintypes.LPARAM)(cb), 0)
        fg = user32.GetForegroundWindow()
        user32.GetWindowThreadProcessId(fg, ctypes.byref(fpid))
        self.hold = self.hold + 1 if fpid.value in pids else 0
        if self.hold > 66 and not self.abort:  # 2 seconds in a row
            self.abort = "a game window kept the focus for 2 seconds"
        if fpid.value in pids and self.last_other and user32.IsWindow(self.last_other):
            self.stolen += 1
            self.give_back(self.last_other)

    def give_back(self, target):
        # no fake key presses: attach to the input queue of the window that has the focus, then set the focus back
        fg = user32.GetForegroundWindow()
        t_fg = user32.GetWindowThreadProcessId(fg, None)
        t_me = ctypes.windll.kernel32.GetCurrentThreadId()
        user32.AttachThreadInput(t_me, t_fg, True)
        ok = user32.SetForegroundWindow(target)
        user32.AttachThreadInput(t_me, t_fg, False)
        if not ok:
            user32.SwitchToThisWindow(target, True)


def gradle_cmd(ver):
    if ver in ("1.20.5", "1.20.6"):
        g = os.environ.get("GRADLE9", r"D:\tools\gradle\gradle-9.2.0\bin\gradle.bat")
    else:
        g = os.environ.get("GRADLE", r"D:\tools\gradle\gradle-8.14.3\bin\gradle.bat")
    return g


def base_env():
    env = dict(os.environ)
    if os.environ.get("JAVA21_HOME"):
        env["JAVA_HOME"] = os.environ["JAVA21_HOME"]
    return env


def kill_tree(proc):
    if proc and proc.poll() is None:
        subprocess.run(["taskkill", "/PID", str(proc.pid), "/T", "/F"], capture_output=True)


def kill_all():
    """last safety net: stop every process we started, let the mouse go, nothing stays running"""
    for p in ALL_PROCS:
        kill_tree(p)
    user32.ClipCursor(None)


def kill_leftovers():
    """games of an earlier run that are still alive (their command line points into the work folder)"""
    for p in psutil.process_iter(["pid", "name", "cmdline"]):
        try:
            cmd = " ".join(p.info["cmdline"] or [])
            if p.info["name"] and p.info["name"].lower().startswith("java") and str(WORK).lower() in cmd.lower() and ("runClient" in cmd or "runServer" in cmd or "GradleWrapperMain" in cmd or "net.fabricmc" in cmd):
                subprocess.run(["taskkill", "/PID", str(p.pid), "/T", "/F"], capture_output=True)
        except psutil.Error:
            pass


def read(path):
    try:
        return Path(path).read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def patch_probe(ver, text):
    """the per-version changes of the probe (the same as the case in two-clients.sh)"""
    sinfo = 'new ServerInfo("t", "127.0.0.1:" + PORT, false));'
    if ver in ("1.19.3", "1.19.4"):
        pass
    elif ver == "1.20.1":
        text = text.replace(sinfo, 'new ServerInfo("t", "127.0.0.1:" + PORT, false), false);')
    elif ver in ("1.20.5", "1.20.6"):
        text = text.replace("screen.ConnectScreen;", "screen.multiplayer.ConnectScreen;")
        text = text.replace(sinfo, 'new ServerInfo("t", "127.0.0.1:" + PORT, ServerInfo.ServerType.OTHER), false, null);')
    elif ver in ("1.20.3", "1.20.4"):
        text = text.replace("screen.ConnectScreen;", "screen.multiplayer.ConnectScreen;")
        text = text.replace(sinfo, 'new ServerInfo("t", "127.0.0.1:" + PORT, ServerInfo.ServerType.OTHER), false);')
    elif ver == "1.20.2":
        text = text.replace(sinfo, 'new ServerInfo("t", "127.0.0.1:" + PORT, ServerInfo.ServerType.OTHER), false);')
    elif ver.startswith("1.18"):
        text = text.replace("c.player.networkHandler.sendChatCommand(s);", 'c.player.sendChatMessage("/" + s);')
        text = text.replace("interactBlock(c.player, Hand.MAIN_HAND,", "interactBlock(c.player, c.world, Hand.MAIN_HAND,")
    else:
        text = text.replace("c.player.networkHandler.sendChatCommand(s);", "c.player.sendCommand(s, null);")
    return text


def prepare(ver, slot):
    w = WORK / ver
    if not w.is_dir():
        sys.exit(f"{w} does not exist: run ports\\build.py {ver} first")
    port = 25565 + slot
    for d in ("run", "run-c", "run-b"):
        shutil.rmtree(w / d, ignore_errors=True)
    (w / "run").mkdir()
    for f in ("eula.txt", "server.properties", "ops.json"):
        shutil.copy(DATA / "server" / f, w / "run" / f)
    sp = w / "run" / "server.properties"
    props = read(sp)
    props = re.sub(r"level-name=.*", f"level-name=m{ver}{int(time.time()) % 100000}", props)
    props = re.sub(r"spawn-protection=.*", "spawn-protection=0", props)
    props = re.sub(r"(?m)^server-port=.*", f"server-port={port}", props)
    props = re.sub(r"(?m)^query.port=.*", f"query.port={port}", props)
    sp.write_text(props, encoding="utf-8", newline="\n")
    for d in ("run-c", "run-b"):
        c = w / d
        c.mkdir()
        shutil.copytree(DATA / "client" / "config", c / "config")
        shutil.copytree(DATA / "client" / "schematics", c / "schematics")
        shutil.copy(DATA / "client" / "options.txt", c / "options.txt")
        opts = read(c / "options.txt")
        for k, v in FAST_OPTIONS.items():
            opts = re.sub(r"(?m)^" + k + ":.*$", "", opts)
        opts = opts.rstrip() + "\n" + "\n".join(f"{k}:{v}" for k, v in FAST_OPTIONS.items()) + "\n"
        (c / "options.txt").write_text(opts, encoding="utf-8", newline="\n")
        # the schematic placement of the test data points to the schematics folder of this run
        for f in (c / "config" / "litematica").glob("*.json"):
            f.write_text(read(f).replace("/home/user/matlisttracker/run-sodium/schematics", (c / "schematics").as_posix()), encoding="utf-8", newline="\n")
        for f in list((c / "config" / "litematica").glob("litematica_127.0.0.1_25565*.json")):
            if port != 25565:
                shutil.copy(f, f.with_name(f.name.replace("25565", str(port))))
    tmp = w / "src" / "main" / "java" / "dev" / "materialshare" / "client" / "TmpProbe.java"
    tmp.write_text(patch_probe(ver, read(PROBE)), encoding="utf-8", newline="\n")
    net = tmp.with_name("ClientNet.java")
    text = net.read_bytes().decode("utf-8")
    if "TmpProbe.init" not in text:
        text = re.sub(r"(?m)^        register\(\);(\r?\n)",
                      lambda m: '        register();' + m[1] + '        if (System.getProperty("matlist.check") != null) {' + m[1]
                      + '            TmpProbe.init();' + m[1] + '        }' + m[1], text, count=1)
        net.write_bytes(text.encode("utf-8"))
    # the size of the game windows (the grid of the secondary monitor)
    bg = w / "build.gradle"
    bg.write_text(re.sub(r"'--width', '\d+', '--height', '\d+'", f"'--width', '{TILE_W}', '--height', '{TILE_H}'", read(bg)), encoding="utf-8", newline="\n")
    return w, port


def run_version(ver, slot, windows):
    P = LOGS
    P.mkdir(parents=True, exist_ok=True)
    w, port = prepare(ver, slot)
    g, env = gradle_cmd(ver), base_env()
    r = subprocess.run([g, "classes", "processResources", "-q", "--no-daemon"], cwd=w, env=env, capture_output=True, text=True, errors="replace")
    errors = [l for l in (r.stdout + r.stderr).splitlines() if l.strip()][:12]
    if r.returncode != 0:
        return f"== {ver}\nBUILD FAILED\n" + "\n".join(errors), 0, 0
    logs = {k: P / f"{k}2_{ver}.log" for k in ("srv", "cb", "cc")}
    for f in logs.values():
        f.unlink(missing_ok=True)
    procs = []

    def start(cmd, log, extra_opts="", stdin=None):
        e = dict(env)
        e["JAVA_TOOL_OPTIONS"] = (e.get("JAVA_TOOL_OPTIONS", "") + " -Dmatlist.check=1 " + extra_opts).strip()
        p = subprocess.Popen(cmd, cwd=w, env=e, stdin=stdin, stdout=open(log, "wb"), stderr=subprocess.STDOUT,
                             creationflags=subprocess.CREATE_NO_WINDOW)
        procs.append(p)
        ALL_PROCS.append(p)
        return p
    try:
        server = start([g, "runServer", "--no-daemon"], logs["srv"], stdin=subprocess.PIPE)
        bob = start([g, "runClient", "--no-daemon", "-PrunDir=run-b", "-Puser=Bob"], logs["cb"], f"-Dmatlist.role=Bob -Dmatlist.port={port}")
        windows.add(bob, slot * 2 + 1)
        time.sleep(4)
        cara = start([g, "runClient", "--no-daemon", "-PrunDir=run-c", "-Puser=Cara"], logs["cc"], f"-Dmatlist.role=Cara -Dmatlist.port={port}")
        windows.add(cara, slot * 2)
        opped, notes = False, []
        for _ in range(700):
            srv, cb, cc = read(logs["srv"]), read(logs["cb"]), read(logs["cc"])
            if not opped and "Done (" in srv:
                server.stdin.write(b"op Cara\n")
                server.stdin.flush()
                opped = True
            if windows.abort:
                notes.append("ABORTED: " + windows.abort)
                break
            if "took it out again" in cc and "after taking out" in cb:
                time.sleep(2)
                break
            if re.search(r"TMPPROBE.*FAIL", cc + cb):
                time.sleep(3)
                break
            if len(re.findall(r"TMPPROBE.*EXC", cc + cb)) > 30:
                notes.append("PROBE EXCEPTIONS")
                break
            if "FAILURE: Build failed" in cc + cb + srv:
                notes.append("BUILD FAILED")
                break
            time.sleep(1)
        cb, cc, srv = read(logs["cb"]), read(logs["cc"]), read(logs["srv"])
        both = cb + "\n" + cc
        lines = [f"== {ver}"]
        lines += sorted({"MIXIN FAILED: " + l[:200] for l in (both + srv).splitlines() if re.search(r"Mixin apply.*failed", l)})
        lines += notes
        lines += sorted({l.split("TMPPROBE", 1)[1] for l in both.splitlines() if "TMPPROBE" in l})[:40]
        ok, last = both.count(" OK "), both.count("took it out again") + both.count("after taking out")
        return "\n".join(lines), ok, last
    finally:
        for p in procs:
            kill_tree(p)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("versions", nargs="+")
    ap.add_argument("--jobs", type=int, default=3, help="how many versions at the same time (3 = 6 windows; measured: 4 was slower and failed twice)")
    a = ap.parse_args()
    LOGS.mkdir(parents=True, exist_ok=True)
    STOP_FILE.unlink(missing_ok=True)
    atexit.register(kill_all)
    kill_leftovers()
    set_layout(2 * min(a.jobs, len(a.versions)))
    windows = Windows()
    summary = []
    start = time.time()

    def one(args):
        i, v = args
        text, ok, last = run_version(v, i % a.jobs, windows)
        (LOGS / f"run_{v}.log").write_text(text, encoding="utf-8")
        verdict = "PASS" if ok == 3 and last == 2 and "MIXIN FAILED" not in text and "FAIL" not in text else "NOT PASSED"
        return f"{text}\nok-lines: {ok} last-lines: {last}  (a pass is 3 and 2)  -> {verdict}"

    # a slot (port, grid place) is only used by one version at a time: the versions run in groups of --jobs
    for g in range(0, len(a.versions), a.jobs):
        group = list(enumerate(a.versions[g:g + a.jobs]))
        with ThreadPoolExecutor(max_workers=a.jobs) as ex:
            summary += list(ex.map(one, group))
    windows.stop = True
    summary.append(f"memory of the PC: highest use {windows.mem_peak:.0f} %, least free {windows.free_min:.1f} GB")
    summary.append(f"time a game window had the focus: {windows.stolen * 0.03:.1f} s")
    if windows.errors:
        summary.append("window notes: " + "; ".join(sorted(set(windows.errors))[:5]))
    out = "\n".join(summary) + f"\nfinished in {int(time.time() - start)} s\n"
    (LOGS / "summary.txt").write_text(out, encoding="utf-8")
    print(out)


if __name__ == "__main__":
    main()
