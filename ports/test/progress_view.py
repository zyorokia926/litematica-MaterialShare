#!/usr/bin/env python3
"""Shows the progress of ports/test/two-clients.py as a web page that reloads itself every 3 seconds.

    py -I ports\\test\\progress_view.py          writes <logs>/progress.html every 3 s (stop with Ctrl+C), then open that file in a browser
    py -I ports\\test\\progress_view.py --once   writes it once

It only reads <logs>/progress.txt (MATERIALSHARE_LOGS), so it never disturbs the test.
"""
import html
import os
import re
import sys
import tempfile
import time
from pathlib import Path

LOGS = Path(os.environ.get("MATERIALSHARE_LOGS", Path(tempfile.gettempdir()) / "materialshare-test-logs"))
LINE = re.compile(r"^\[(\d+):(\d+)\] (1\.\d+(?:\.\d+)?): (.*?)(?: \| .*終.*)?$")
STEP = re.compile(r"test step (\d+)")


def stage(msg, best):
    """(percent, text) of a progress line; best = the highest test step seen so far"""
    if msg.startswith("finished"):
        return 100, msg
    steps = [int(x) for x in STEP.findall(msg)]
    if steps:
        return min(95, 62 + 4 * max(steps + [best])), "test step %d" % max(steps + [best])
    for key, pct in (("the server is ready", 60), ("game is up", 55), ("client", 30), ("server started", 25), ("compiling", 10), ("preparing", 5)):
        if key in msg:
            return pct, msg[:60]
    return None, msg[:60]


def build():
    versions, order, info = {}, [], {"eta": "", "left": "", "mem": "", "batch": "", "elapsed": ""}
    path = LOGS / "progress.txt"
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines() if path.exists() else []
    for raw in lines:
        m = re.search(r"ends about (\S+) \(in ([^)]*)\)", raw)
        if m:
            info["eta"], info["left"] = m[1], m[2]
        if "batch " in raw and " of " in raw and raw.split("] ", 1)[-1].startswith("batch"):
            info["batch"] = raw.split("] ", 1)[1].split(" | ")[0]
        mm = re.search(r"free memory ([\d.]+) GB", raw)
        if mm:
            info["mem"] = mm[1]
        m = LINE.match(raw)
        if not m:
            continue
        info["elapsed"] = f"{m[1]}:{m[2]}"
        ver, msg = m[3], m[4]
        v = versions.setdefault(ver, {"pct": 0, "text": "", "best": 0, "result": ""})
        if ver not in order:
            order.append(ver)
        pct, text = stage(msg, v["best"])
        steps = [int(x) for x in STEP.findall(msg)]
        if steps:
            v["best"] = max([v["best"]] + steps)
        if pct is not None:
            v["pct"] = max(v["pct"], pct) if pct < 100 else 100
            v["text"] = text
        if msg.startswith("finished"):
            ok = re.search(r"ok-lines (\d+), last-lines (\d+)", msg)
            good = ok and ok[1] == "3" and ok[2] == "2" and "FAIL" not in msg
            v["result"] = "pass" if good else "fail"
            v["text"] = msg
    return versions, order, info


def page():
    versions, order, info = build()
    done = sum(1 for v in versions.values() if v["result"])
    rows = []
    for ver in order:
        v = versions[ver]
        cls = v["result"] or "run"
        label = {"pass": "合格 / PASS", "fail": "不合格 / NOT PASSED"}.get(v["result"], f"{v['pct']} %")
        rows.append(f'<div class="row {cls}"><b>{html.escape(ver)}</b><div class="bar"><i style="width:{v["pct"]}%"></i></div>'
                    f'<span class="lab">{label}</span><small>{html.escape(v["text"])}</small></div>')
    finished = bool(order) and done >= len(order)
    eta_ms = 0
    if info["eta"] and not finished:
        h, mi, s = (int(x) for x in info["eta"].split(":"))
        t = time.localtime()
        eta_ms = int(time.mktime((t.tm_year, t.tm_mon, t.tm_mday, h, mi, s, 0, 0, -1)) * 1000)
    path = LOGS / "progress.txt"
    start_ms = int(path.stat().st_ctime * 1000) if path.exists() else 0
    head = "全部終わりました" if finished else "計算中..."
    return f"""<!doctype html><html lang="ja"><head><meta charset="utf-8"><meta http-equiv="refresh" content="3">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>テストの進み具合</title><style>
:root{{--bg:#fff;--fg:#1c1c1c;--mut:#666;--bar:#e6e6e6;--run:#2b6cb0;--ok:#2f855a;--ng:#c53030}}
@media(prefers-color-scheme:dark){{:root{{--bg:#16181c;--fg:#e8e8e8;--mut:#9aa0a6;--bar:#2c3036;--run:#63a4e8;--ok:#48bb78;--ng:#f56565}}}}
body{{margin:0;padding:16px;background:var(--bg);color:var(--fg);font:15px system-ui,sans-serif;max-width:900px}}
h1{{font-size:20px;margin:0 0 4px}}.sum{{color:var(--mut);margin:0 0 16px}}
.row{{display:grid;grid-template-columns:64px 1fr 150px;gap:4px 12px;align-items:center;padding:6px 0;border-bottom:1px solid var(--bar)}}
.row small{{grid-column:2/4;color:var(--mut);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}}
.bar{{height:12px;background:var(--bar);border-radius:6px;overflow:hidden}}.bar i{{display:block;height:100%;background:var(--run);transition:width .5s}}
.pass .bar i{{background:var(--ok)}}.fail .bar i{{background:var(--ng)}}.pass .lab{{color:var(--ok)}}.fail .lab{{color:var(--ng)}}
@media(max-width:520px){{.row{{grid-template-columns:56px 1fr}}.lab{{grid-column:1/3}}.row small{{grid-column:1/3}}}}</style></head><body>
<h1 id="head">{html.escape(head)}</h1>
<p class="sum">{done} / {len(order)} ver 完了 ・ 経過 <span id="el">{info['elapsed'] or '-'}</span> ・ 空きメモリ {info['mem'] or '-'} GB<br>{html.escape(info['batch'])}<br>
表示を作った時刻 {time.strftime('%H:%M:%S')}(3秒ごとに更新)</p>
{''.join(rows) or '<p class="sum">まだ始まっていません</p>'}
<script>
var ETA={eta_ms},START={start_ms},FIN={'true' if finished else 'false'};
function p(n){{return (n<10?'0':'')+n}}
function tick(){{var now=Date.now();
 if(START){{var e=Math.max(0,Math.floor((now-START)/1000));document.getElementById('el').textContent=p(Math.floor(e/60))+':'+p(e%60);}}
 if(ETA&&!FIN){{var d=new Date(ETA),r=Math.floor((ETA-now)/1000);
  document.getElementById('head').textContent='終了予定 '+p(d.getHours())+':'+p(d.getMinutes())+'(あと '+(r>0?Math.floor(r/60)+'分'+p(r%60)+'秒':'まもなく')+')';}}
 else if(!FIN){{document.getElementById('head').textContent='実行中(終了予定を計算中)';}}}}
tick();setInterval(tick,1000);setTimeout(function(){{location.reload()}},3000);
</script></body></html>"""


def main():
    out = Path(os.environ.get("MATERIALSHARE_HTML", LOGS / "progress.html"))  # where the page is written (default: next to the logs)
    while True:
        tmp = out.with_suffix(".tmp")
        tmp.write_text(page(), encoding="utf-8")
        tmp.replace(out)
        if "--once" in sys.argv:
            print(out)
            return
        time.sleep(3)


if __name__ == "__main__":
    main()
