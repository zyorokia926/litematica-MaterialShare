#!/usr/bin/env python3
"""Shows the progress of ports/test/two-clients.py in a web page that updates itself in place (no page reload).

    py -I ports\\test\\progress_view.py          serves http://localhost:8765/ and writes progress.html (stop with Ctrl+C)
    py -I ports\\test\\progress_view.py --once   writes progress.html only

Open http://localhost:8765/ in a browser. The page asks /data.json every second; progress.html (a copy of the same page) works too while this
script runs. It only reads <logs>/progress.txt (MATERIALSHARE_LOGS), so it never disturbs the test.
Environment: MATERIALSHARE_LOGS (logs), MATERIALSHARE_HTML (where progress.html is written), MATERIALSHARE_PORT (default 8765).
"""
import json
import os
import re
import sys
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import log_ja  # noqa: E402  (the Japanese wording of the log lines)

LOGS = Path(os.environ.get("MATERIALSHARE_LOGS", Path(tempfile.gettempdir()) / "materialshare-test-logs"))
PORT = int(os.environ.get("MATERIALSHARE_PORT", "8765"))
LINE = re.compile(r"^\[(\d+):(\d+)\] (?:(1\.\d+(?:\.\d+)?): )?(.*?)(?: \| [^|]*ends about .*)?$")
STEP = re.compile(r"test step (\d+)")
STAGES = (("the server is ready", 60), ("game is up", 55), ("client", 30), ("server started", 25), ("compiling", 10), ("preparing", 5))


def snapshot():
    path = LOGS / "progress.txt"
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines() if path.exists() else []
    vers, order, total, log = {}, [], [], []
    info = {"eta": "", "mem": "", "batch": "", "all_done": False}
    for raw in lines:
        m = re.search(r"ends about (\d+):(\d+):(\d+)", raw)
        if m:
            info["eta"] = ":".join(m.groups())
        m = re.search(r"free memory ([\d.]+) GB", raw)
        if m:
            info["mem"] = m[1]
        m = LINE.match(raw)
        if not m:
            continue
        sec, ver, msg = int(m[1]) * 60 + int(m[2]), m[3], m[4]
        if msg.startswith("versions: "):
            total = [x.strip() for x in msg[10:].split(";")[0].split(",")]
        elif msg.startswith("batch "):
            info["batch"] = msg.split(" (expected")[0]
        elif "everything is finished" in msg:
            info["all_done"] = True
        log.append({"t": f"{m[1]}:{m[2]}", "v": ver or "", "m": msg, "ja": log_ja.to_ja(msg) or "", "st": log_ja.is_status(msg)})
        if not ver:
            continue
        v = vers.setdefault(ver, {"ver": ver, "pct": 0, "text": "", "best": 0, "result": "", "step": 0, "since": sec})
        if ver not in order:
            order.append(ver)
        steps = [int(x) for x in STEP.findall(msg)]
        if steps:
            v["best"] = max([v["best"]] + steps)
            v["pct"] = max(v["pct"], min(95, 62 + 4 * v["best"]))
            v["text"] = f"test step {v['best']}"
        for key, pct in STAGES:
            if not steps and key in msg:
                v["pct"] = max(v["pct"], pct)
                v["text"] = msg[:70]
                v["raw"] = msg
                break
        if msg.startswith("finished"):
            ok = re.search(r"ok-lines (\d+), last-lines (\d+)", msg)
            good = bool(ok) and ok[1] == "3" and ok[2] == "2" and "FAIL" not in msg
            v.update(result="pass" if good else "fail", pct=100, text=msg[len("finished "):] if msg.startswith("finished ") else msg)
    rows =[vers.get(v["ver"] if isinstance(v, dict) else v) or {"ver": v if isinstance(v, str) else v["ver"], "pct": 0, "text": "waiting", "best": 0, "result": ""} for v in (total or order)]
    for r in rows:
        r["ja"] = log_ja.to_ja(r["raw"]) or r["text"] if r.get("raw") else log_ja.card_ja(r["text"])
    done = sum(1 for r in rows if r["result"])
    finished = info["all_done"] or (bool(rows) and done == len(rows))
    eta_ms = 0
    if info["eta"] and not finished:
        h, mi, s = (int(x) for x in info["eta"].split(":"))
        t = time.localtime()
        eta_ms = int(time.mktime((t.tm_year, t.tm_mon, t.tm_mday, h, mi, s, 0, 0, -1)) * 1000)
    return {"rows": rows, "done": done, "total": len(rows), "finished": finished, "eta": eta_ms,
            "start": int(path.stat().st_ctime * 1000) if path.exists() else 0,
            "mem": info["mem"], "batch": info["batch"], "batch_ja": log_ja.batch_ja(info["batch"]) if info["batch"] else "", "log": log[-400:], "now": int(time.time() * 1000),
            "stop": (LOGS / "STOP").exists()}


PAGE = r"""<!doctype html><html lang="ja"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>テストの進み具合</title><style>
:root{--bg:#f6f7f9;--card:#fff;--fg:#1d2330;--mut:#6b7385;--line:#e4e7ee;--run:#3b82f6;--ok:#16a34a;--ng:#dc2626;--wait:#c3c9d6;--logbg:#0f141c;--logfg:#cfd6e4}
@media(prefers-color-scheme:dark){:root{--bg:#0e1116;--card:#171b23;--fg:#e8ebf2;--mut:#8b93a7;--line:#262c38;--run:#60a5fa;--ok:#4ade80;--ng:#f87171;--wait:#3a4153;--logbg:#0a0d12;--logfg:#cfd6e4}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:14px/1.5 system-ui,"Yu Gothic UI",sans-serif}
main{max-width:1100px;margin:0 auto;padding:20px 16px 40px}
.hero{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:18px 20px}
.top{display:flex;flex-wrap:wrap;gap:8px 24px;align-items:baseline;justify-content:space-between}
h1{margin:0;font-size:26px;font-weight:650;letter-spacing:-.01em;font-variant-numeric:tabular-nums}
.chips{display:flex;flex-wrap:wrap;gap:8px;margin-top:10px}.chip{padding:3px 10px;border-radius:99px;background:var(--bg);border:1px solid var(--line);color:var(--mut);font-size:12.5px;font-variant-numeric:tabular-nums}
.chip b{color:var(--fg);font-weight:600}
.total{height:10px;background:var(--line);border-radius:99px;overflow:hidden;margin-top:14px}.total i{display:block;height:100%;width:0;background:var(--run);border-radius:99px;transition:width .6s ease}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:12px;margin-top:16px}
.card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:12px 14px;cursor:pointer;transition:border-color .2s,box-shadow .2s}
.card:hover{border-color:var(--mut)}.card.sel{border-color:var(--run);box-shadow:0 0 0 2px color-mix(in srgb,var(--run) 25%,transparent)}
.card h3{margin:0;font-size:16px;display:flex;justify-content:space-between;align-items:center}
.badge{font-size:11.5px;font-weight:600;padding:1px 8px;border-radius:99px;color:#fff;background:var(--wait)}
.run .badge{background:var(--run)}.pass .badge{background:var(--ok)}.fail .badge{background:var(--ng)}
.bar{height:6px;background:var(--line);border-radius:99px;margin:10px 0 8px;overflow:hidden}.bar i{display:block;height:100%;width:0;background:var(--run);transition:width .6s ease}
.pass .bar i{background:var(--ok)}.fail .bar i{background:var(--ng)}
.dots{display:flex;gap:4px}.dots s{width:100%;height:4px;border-radius:2px;background:var(--line);text-decoration:none}.dots s.on{background:var(--run)}.pass .dots s.on{background:var(--ok)}.fail .dots s.on{background:var(--ng)}
.card small{display:block;margin-top:8px;color:var(--mut);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:12px}
.logh{display:flex;flex-wrap:wrap;gap:8px 16px;align-items:center;justify-content:space-between;margin:22px 0 8px}.logh h2{margin:0;font-size:15px}
.logh label{color:var(--mut);font-size:12.5px;display:flex;align-items:center;gap:6px}button{font:inherit;font-size:12.5px;padding:3px 10px;border-radius:8px;border:1px solid var(--line);background:var(--card);color:var(--fg);cursor:pointer}
#log{background:var(--logbg);color:var(--logfg);border-radius:12px;padding:10px 12px;height:340px;overflow:auto;font:12.5px/1.55 ui-monospace,Consolas,monospace}
#log div{white-space:pre-wrap;word-break:break-word}#log .t{color:#6b7385;margin-right:8px}#log .v{display:inline-block;min-width:54px;margin-right:8px;font-weight:600}#log .quiet{opacity:.55}#log .bad{color:#ff8b8b}#log .good{color:#7ee2a0}
button.stop{border-color:var(--ng);color:var(--ng);font-weight:600;padding:5px 14px}button.stop:hover:not(:disabled){background:var(--ng);color:#fff}button.stop:disabled{opacity:.55;cursor:default}button.stop.hide{display:none}
#warn{display:none;margin-top:12px;padding:8px 12px;border-radius:10px;background:color-mix(in srgb,var(--ng) 14%,var(--card));color:var(--ng);font-size:13px}
@media(max-width:520px){h1{font-size:21px}.grid{grid-template-columns:repeat(2,1fr)}}
</style></head><body><main>
<section class="hero"><div class="top"><h1 id="head">読み込み中...</h1><span style="display:flex;gap:10px;align-items:center"><span class="chip" id="cnt"></span><button id="lang" title="ログとカードの表示を、日本語(わかりやすい説明)と英語(細かい元のログ)で切り替えます">English</button><button id="stop" class="stop" title="全部のテストを止めます(ゲームの窓も閉じます)">■ テストを停止</button></span></div>
<div class="total"><i id="tot"></i></div>
<div class="chips"><span class="chip">経過 <b id="el">-</b></span><span class="chip">空きメモリ <b id="mem">-</b></span><span class="chip" id="batch">-</span><span class="chip">最終更新 <b id="upd">-</b></span></div></section>
<div id="warn">更新用のプログラム(progress_view.py)が止まっています。表示は最後の状態です。</div>
<div class="grid" id="grid"></div>
<div class="logh"><h2>作業中のログ <span id="logsel" style="color:var(--mut);font-weight:400"></span></h2><span style="display:flex;gap:12px;align-items:center">
<label><input type="checkbox" id="follow" checked> 最新を追う</label><label><input type="checkbox" id="quiet"> 定期メッセージを隠す</label><button id="all">全 ver を表示</button></span></div>
<div id="log"></div></main>
<script>
var BASE=location.protocol==='http:'?'':'http://localhost:__PORT__',D=null,sel='',COL={},LANG='ja',PAL=['#60a5fa','#f59e0b','#34d399','#f472b6','#a78bfa','#fb7185','#22d3ee','#fbbf24','#4ade80','#c084fc','#f97316','#38bdf8','#e879f9','#a3e635'];
function p(n){return (n<10?'0':'')+n}function mmss(s){s=Math.max(0,Math.floor(s));var h=Math.floor(s/3600);return (h?h+':'+p(Math.floor(s%3600/60)):Math.floor(s/60))+':'+p(s%60)}
function color(v){if(!COL[v]){COL[v]=PAL[Object.keys(COL).length%PAL.length]}return COL[v]}
var skew=0;
function clock(){if(!D)return;var now=Date.now()+skew;
 if(D.start)document.getElementById('el').textContent=mmss((D.finished?D.now:now)/1000-D.start/1000);
 var h=document.getElementById('head');
 if(D.finished){h.textContent='全部終わりました'}
 else if(D.eta){var r=Math.floor((D.eta-now)/1000),d=new Date(D.eta);h.textContent='終了予定 '+p(d.getHours())+':'+p(d.getMinutes())+'  あと '+(r>0?Math.floor(r/60)+'分'+p(r%60)+'秒':'まもなく')}
 else h.textContent='実行中(終了予定を計算中)'}
function draw(){var g=document.getElementById('grid'),html='';
 D.rows.forEach(function(r){var cls=r.result||(r.pct?'run':''),lab=r.result==='pass'?'合格':r.result==='fail'?'不合格':r.pct?r.pct+'%':'待機中',dots='';
  for(var i=1;i<=8;i++)dots+='<s class="'+(r.pct>=100||r.best>=i?'on':'')+'"></s>';
  html+='<div class="card '+cls+(sel===r.ver?' sel':'')+'" data-v="'+r.ver+'"><h3>'+r.ver+'<span class="badge">'+lab+'</span></h3><div class="bar"><i style="width:'+r.pct+'%"></i></div><div class="dots">'+dots+'</div><small>'+esc((LANG==='ja'?(r.ja||r.text):r.text)||'')+'</small></div>'});
 g.innerHTML=html;
 var sum=0;D.rows.forEach(function(r){sum+=r.pct});document.getElementById('tot').style.width=(D.total?sum/D.total:0)+'%';
 var sb=document.getElementById('stop');sb.classList.toggle('hide',D.finished);sb.disabled=D.stop;sb.textContent=D.stop?'停止中...':'■ テストを停止';
 document.getElementById('cnt').innerHTML='<b>'+D.done+'</b> / '+D.total+' ver 完了';
 document.getElementById('mem').textContent=D.mem?D.mem+' GB':'-';document.getElementById('batch').textContent=(LANG==='ja'?(D.batch_ja||D.batch):D.batch)||'-'}
function esc(s){return s.replace(/[&<>]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;'}[c]})}
function drawLog(){var L=document.getElementById('log'),q=document.getElementById('quiet').checked,html='';
 D.log.forEach(function(e){if(sel&&e.v!==sel&&e.v!=='')return;var quiet=/^still running/.test(e.m);if(q&&quiet)return;
  if(LANG==='ja'&&e.st)return;
  var c=/FAIL|NOT PASSED|BUILD FAILED|ABORTED/.test(e.m)?' bad':/ok-lines 3, last-lines 2|OK /.test(e.m)?' good':'';
  html+='<div class="'+(quiet?'quiet':'')+c+'"><span class="t">'+e.t+'</span><span class="v" style="color:'+(e.v?color(e.v):'inherit')+'">'+(e.v||'')+'</span>'+esc(LANG==='ja'?(e.ja||e.m):e.m)+'</div>'});
 var follow=document.getElementById('follow').checked;L.innerHTML=html;if(follow)L.scrollTop=L.scrollHeight;
 document.getElementById('logsel').textContent=sel?'('+sel+' だけ)':'(全 ver)'}
async function poll(){try{var r=await fetch(BASE+'/data.json',{cache:'no-store'});D=await r.json();skew=D.now-Date.now();skew=Math.abs(skew)<2000?0:skew;
  document.getElementById('warn').style.display=(Date.now()-D.now>8000&&!D.finished)?'block':'none';
  document.getElementById('upd').textContent=new Date().toLocaleTimeString('ja-JP');draw();drawLog();clock()}
 catch(e){document.getElementById('warn').style.display='block'}}
document.getElementById('grid').addEventListener('click',function(e){var c=e.target.closest('.card');if(!c)return;sel=sel===c.dataset.v?'':c.dataset.v;if(D){draw();drawLog()}});
try{if(localStorage.getItem('lang')==='en')LANG='en'}catch(e){}
function langBtn(){var b=document.getElementById('lang');b.textContent=LANG==='ja'?'English':'日本語';b.title=LANG==='ja'?'ログとカードを、英語(細かい元のログ)で見ます':'ログとカードを、日本語(わかりやすい説明)で見ます'}
langBtn();document.getElementById('lang').onclick=function(){LANG=LANG==='ja'?'en':'ja';try{localStorage.setItem('lang',LANG)}catch(e){}langBtn();if(D){draw();drawLog()}};
document.getElementById('all').onclick=function(){sel='';if(D){draw();drawLog()}};document.getElementById('quiet').onchange=function(){if(D)drawLog()};
document.getElementById('stop').onclick=async function(){
 if(!confirm('全部のテストを止めますか？\n動いているゲームの窓も閉じます(結果は「不合格」ではなく「中止」になります)。'))return;
 this.disabled=true;this.textContent='停止を指示しました...';
 try{var r=await fetch(BASE+'/stop',{method:'POST',headers:{'X-Stop':'1'}});if(!r.ok)throw 0}catch(e){alert('停止の指示が送れませんでした。\n手動で止めるときは、次のファイルを作ってください:\nSTOP(テストのログのフォルダの中)');this.disabled=false;this.textContent='■ テストを停止'}};
poll();setInterval(poll,1000);setInterval(clock,250);
</script></body></html>""".replace("__PORT__", str(PORT))


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/data.json"):
            body, kind = json.dumps(snapshot(), ensure_ascii=False).encode("utf-8"), "application/json; charset=utf-8"
        elif self.path in ("/", "/index.html", "/progress.html"):
            body, kind = PAGE.encode("utf-8"), "text/html; charset=utf-8"
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def _origin_ok(self):
        # only this page may press the button (a web page of another site must not stop the test)
        origin = self.headers.get("Origin")
        return origin in (None, "null", f"http://localhost:{PORT}", f"http://127.0.0.1:{PORT}")

    def do_OPTIONS(self):
        self.send_response(204 if self._origin_ok() else 403)
        self.send_header("Access-Control-Allow-Origin", self.headers.get("Origin") or "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST")
        self.send_header("Access-Control-Allow-Headers", "X-Stop")
        self.end_headers()

    def do_POST(self):
        if self.path != "/stop" or self.headers.get("X-Stop") != "1" or not self._origin_ok():
            self.send_error(403)
            return
        (LOGS / "STOP").write_text("stopped from the progress page\n", encoding="utf-8")  # two-clients.py watches this file
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", self.headers.get("Origin") or "*")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def log_message(self, *args):
        pass


def main():
    out = Path(os.environ.get("MATERIALSHARE_HTML", LOGS / "progress.html"))  # a copy of the page (works while this script runs)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(PAGE, encoding="utf-8")
    print(f"page: {out}")
    if "--once" in sys.argv:
        return
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"open http://localhost:{PORT}/  (Ctrl+C stops)")
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
