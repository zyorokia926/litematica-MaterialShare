#!/usr/bin/env python3
"""Japanese wording of the test log, for the progress page (progress_view.py).

The test (two-clients.py) and the probe (TwoClientProbe.java.txt) write English lines: they stay as they are (the page can show
them as "English"). to_ja() turns one line into a plain Japanese sentence: who did what ("Bob が、石をチェストに入れました").
When the wording of a line changes in the probe or in two-clients.py, change the matching rule here.

    python -I ports\\test\\log_ja.py          prints every known line in Japanese and fails when a rule is missing
"""
import re
import sys

ITEMS = {
    "minecraft:stone": "石",
    "minecraft:oak_planks": "オークの板材",
    "minecraft:oak_log": "オークの原木",
    "minecraft:cobblestone": "丸石",
    "minecraft:dirt": "土",
    "minecraft:glass": "ガラス",
    "minecraft:iron_ingot": "鉄インゴット",
}


def item(i):
    return ITEMS.get(i) or i.split(":")[-1].replace("_", " ")


def n_items(i, n):
    return f"{item(i)} {n} 個"


def claims(s):
    """'Bob=64 (handed in 64) Cara=15 (handed in 0)  stock={minecraft:stone=64}' -> who has how many, how many are handed in, the stock"""
    s = s.strip()
    stock = None
    m = re.search(r"\s+stock=(.*)$", s)
    if m:
        s, stock = s[:m.start()], m[1].strip()
    people = [f"{who} は担当 {n} 個(納品済み {d} 個)" for who, n, d in re.findall(r"(\w+)=(\d+) \(handed in (\d+)\)", s)]
    out = "、".join(people) if people else "(まだ誰もいません)"
    if stock is not None:
        if stock in ("{}", "0", ""):
            out += "。保管場所の在庫: なし"
        elif stock.startswith("{"):
            parts = [n_items(i, n) for i, n in re.findall(r"([\w:]+)=(\d+)", stock)]
            out += "。保管場所の在庫: " + "、".join(parts)
        else:
            out += f"。保管場所の在庫: 石 {stock} 個"
    return out


def xyz(s):
    a = s.split()
    return f"{a[0]}, {a[1]}, {a[2]}" if len(a) >= 3 else s


# (regex of the text after "ACT ", Japanese sentence; {who} is the player; \1.. are the groups)
ACT = [
    (r"opens the material list", "{who} が、マテリアルリストを開きました"),
    (r"shares the material list as a project: (.*)", lambda who, m: f"{who} が、マテリアルリストを共有しました(" + "、".join(n_items(i, n) for i, n in re.findall(r"([\w:]+)=(\d+)", m[1])) + ")"),
    (r"takes charge of (\S+)", lambda who, m: f"{who} が、{item(m[1])}を担当にしました"),
    (r"sets own count of (\S+) to (\d+)", lambda who, m: f"{who} が、{item(m[1])}の自分の担当数を {m[2]} 個にしました"),
    (r"changes own count of (\S+) to (\d+)", lambda who, m: f"{who} が、{item(m[1])}の自分の担当数を {m[2]} 個に変えました"),
    (r"places a chest at (.*)", lambda who, m: f"{who} が、チェストを置きました(座標 {xyz(m[1])})"),
    (r"registers a storage box around (.*)", lambda who, m: f"{who} が、保管場所(範囲)を登録しました(中心 {xyz(m[1])})"),
    (r"gives (\w+) (\d+) (\S+)", lambda who, m: f"{who} が、{m[1]} に {n_items(m[3], m[2])}を渡しました"),
    (r"teleports (\w+) next to the chest (.*)", lambda who, m: f"{who} が、{m[1]} をチェストのそばへ移動させました(座標 {xyz(m[2])})"),
    (r"opens the chest again at (.*)", lambda who, m: f"{who} が、もう一度チェストを開きました(座標 {xyz(m[1])})"),
    (r"opens the chest at (.*) to take stone out", lambda who, m: f"{who} が、石を取り出すために、チェストを開きました(座標 {xyz(m[1])})"),
    (r"opens the chest at (.*)", lambda who, m: f"{who} が、チェストを開きました(座標 {xyz(m[1])})"),
    (r"moves (\S+) into the chest", lambda who, m: f"{who} が、{item(m[1])}をチェストに入れました(納品)"),
    (r"takes the stone out of the chest", "{who} が、石をチェストから取り出しました"),
    (r"closes the chest", "{who} が、チェストを閉じました"),
]

# what a player sees (the checks of the probe)
CHECK = [
    (r"sees project with (\d+) items", lambda who, m: f"{who} の画面に、プロジェクトが届きました(素材 {m[1]} 種類)"),
    (r"OK sees both people: (.*)", lambda who, m: f"確認 OK: {who} の画面で、2人の担当数が見えました。{claims(m[1])}"),
    (r"OK sees (\w+)'s hand-in live: (.*)", lambda who, m: f"確認 OK: {who} の画面で、{m[1]} の納品がリアルタイムで見えました。{claims(m[2])}"),
    (r"help picture MISSING", "{who}: 使い方の画面の写真が見つかりません"),
    (r"help picture (\d+)x(\d+)", lambda who, m: f"{who}: 使い方の画面の写真を読み込めました(幅 {m[1]} × 高さ {m[2]})"),
    (r"command opened: (\w+)", lambda who, m: f"{who} が、チャットの /materialshare storage で、保管場所の画面({m[1]})を開きました"),
    (r"sees (\w+)'s count = null", lambda who, m: f"{who} の画面には、{m[1]} の担当数がまだ届いていません"),
    (r"sees (\w+)'s count = (\d+)", lambda who, m: f"{who} の画面で、{m[1]} の担当数は {m[2]} 個です"),
    (r"(\w+) took it out again\? (.*)", lambda who, m: f"{who} の画面で、{m[1]} が取り出したあとの数: {claims(m[2])}"),
    (r"block at chest: Block\{([\w:]+)\}, retrying", lambda who, m: f"{who}: チェストの位置のブロックは {m[1]} でした。まだ開けていないので、もう一度試します"),
    (r"screen=(\w+) pos=BlockPos\{x=(-?\d+), y=(-?\d+), z=(-?\d+)\} chest=BlockPos\{x=(-?\d+), y=(-?\d+), z=(-?\d+)\} stone=(\d+)",
     lambda who, m: f"{who}: " + ("チェストの画面は開いていません" if m[1] == "null" else f"開いている画面は {m[1]}") + f"。自分の位置 ({m[2]}, {m[3]}, {m[4]})、チェストの位置 ({m[5]}, {m[6]}, {m[7]})、持っている石 {m[8]} 個"),
    (r"own view after hand-in \(chest open\): (.*)", lambda who, m: f"{who} の画面(チェストを開いたまま)の、納品後の数: {claims(m[1])}"),
    (r"after taking out: (.*)", lambda who, m: f"{who} の画面で、取り出したあとの数: {claims(m[1])}"),
    (r"FAIL project never arrived", "失敗: {who} の画面に、プロジェクトが届きませんでした"),
    (r"FAIL claims not seen: (.*)", lambda who, m: f"失敗: {who} の画面で、2人の担当数が見えませんでした。{claims(m[1])}"),
    (r"FAIL hand-in not seen: (.*)", lambda who, m: f"失敗: {who} の画面で、納品が見えませんでした。{claims(m[1])}"),
    (r"FAIL no stone or no storage box yet", "失敗: {who} は、石を持っていないか、保管場所がまだありません"),
    (r"EXC (.*)", lambda who, m: f"{who}: 例外が起きました({m[1][:80]})"),
]

# lines of two-clients.py (the part after "<ver>: ")
VER = [
    (r"preparing the work folder", "作業フォルダを準備しています"),
    (r"compiling \(gradle classes\)", "コンパイルしています(gradle classes)"),
    (r"BUILD FAILED", "ビルドに失敗しました"),
    (r"server started \(process (\d+), on the hidden desktop\)", lambda m: f"サーバーを起動しました(プロセス {m[1]}、隠しデスクトップ)"),
    (r"client (\w+) started \(process (\d+), on the hidden desktop\)", lambda m: f"{m[1]} のクライアント(ゲーム)を起動しました(プロセス {m[2]}、隠しデスクトップ)"),
    (r"the server is ready, Cara is made operator", "サーバーの準備ができました。Cara に管理者権限(op)を付けました"),
    (r"still running, (\d+) s; game windows on the hidden desktop: (\d+); free memory ([\d.]+) GB", lambda m: f"実行中です({m[1]} 秒)。隠しデスクトップのゲーム窓: {m[2]} 枚、空きメモリ: {m[3]} GB"),
    (r"finished \(ok-lines (\d+), last-lines (\d+)\)(?: - (.*))?", None),  # handled below (pass or not)
]

GLOBAL = [
    (r"the second desktop '(.*)' is ready: the games run there, not on your screen", lambda m: f"隠しデスクトップ({m[1]})の準備ができました。ゲームはそこで動くので、あなたの画面には出ません"),
    (r"progress page: (\S+)\s+\(a copy: (.*)\)", lambda m: f"進み具合のページ: {m[1]}(ページの写し: {m[2]})"),
    (r"versions: (.*?); (\d+) at a time; progress is also written to (.*?); stop everything with (.*)",
     lambda m: f"テストする ver: {m[1]}。同時に {m[2]} つずつ動かします。進み具合の記録: {m[3]}。全部止めるときは、このファイルを作ってください: {m[4]}"),
    (r"batch (\d+) of (\d+): (.*?) \(expected (\d+) s for this batch\)", lambda m: f"{m[1]} 組目(全 {m[2]} 組): {m[3]}。この組の予想: {m[4]} 秒"),
    (r"batch (\d+) of (\d+): (.*)", lambda m: f"{m[1]} 組目(全 {m[2]} 組): {m[3]}"),
    (r"everything is finished; the second desktop is closed", "すべて終わりました。隠しデスクトップを閉じました"),
]

STATUS = re.compile(r"^server (?:ready|starting) \| Bob: .* \| Cara: ")  # the cards show this; the Japanese log leaves it out


def is_status(msg):
    return bool(STATUS.match(msg))


def _apply(rules, text, who=None):
    for pat, out in rules:
        m = re.fullmatch(pat, text.strip())
        if not m:
            continue
        if out is None:
            return None
        if callable(out):
            return out(who, m) if who is not None else out(m)
        return out.format(who=who) if who is not None else out
    return None


def _who_state(s):
    s = s.strip()
    m = re.fullmatch(r"test step (\d+)(?:: .*)?", s)
    if m:
        return f"テスト {m[1]} 段階目"
    return {"starting": "起動中", "not started": "未起動", "game is up, loading": "ゲームが起動、読み込み中"}.get(s, s)


def status_ja(msg):
    """'server ready | Bob: test step 8: ... | Cara: starting' (the line the cards use)"""
    m = re.fullmatch(r"server (ready|starting) \| Bob: (.*?) \| Cara: (.*)", msg.strip())
    if not m:
        return None
    return f"サーバー: {'準備完了' if m[1] == 'ready' else '起動中'} / Bob: {_who_state(m[2])} / Cara: {_who_state(m[3])}"


def to_ja(msg):
    """the Japanese sentence of one log line (the text after the time and the ver), or None when no rule matches"""
    msg = msg.strip()
    if is_status(msg):
        return status_ja(msg)
    m = re.fullmatch(r"\[(\w+)\] (.*)", msg)
    if m:
        who, text = m[1], m[2]
        a = re.fullmatch(r"ACT (.*)", text)
        return _apply(ACT, a[1], who) if a else _apply(CHECK, text, who)
    m = re.fullmatch(r"finished \(ok-lines (\d+), last-lines (\d+)\)(?: - (.*))?", msg)
    if m:
        good = m[1] == "3" and m[2] == "2" and not m[3]
        if m[3] and m[3].startswith("ABORTED"):
            return f"中止しました({m[3]})。確認 {m[1]}/3、最後の確認 {m[2]}/2"
        return ("終了: 合格" if good else "終了: 不合格") + f"(確認 {m[1]}/3、最後の確認 {m[2]}/2" + (f"、{m[3]}" if m[3] else "") + ")"
    return _apply(VER, msg) or _apply(GLOBAL, msg)


def card_ja(text):
    """the small text of a card"""
    if text == "waiting":
        return "待機中"
    m = re.fullmatch(r"test step (\d+)", text)
    if m:
        return f"テスト {m[1]} 段階目"
    m = re.fullmatch(r"\(ok-lines (\d+), last-lines (\d+)\)(?: - (.*))?", text)
    if m:
        return f"確認 {m[1]}/3、最後の確認 {m[2]}/2" + (f"({m[3]})" if m[3] else "")
    return to_ja(text) or text


def batch_ja(text):
    return to_ja(text) or text


SAMPLES = """\
the second desktop 'MaterialShareTest' is ready: the games run there, not on your screen
progress page: http://localhost:8765/  (a copy: D:\\work\\x\\progress.html)
versions: 1.20.2, 1.20.3; 2 at a time; progress is also written to D:\\a\\progress.txt; stop everything with D:\\a\\STOP
batch 1 of 1: 1.20.2, 1.20.3 (expected 115 s for this batch)
1.20.2: preparing the work folder
1.20.2: compiling (gradle classes)
1.20.2: server started (process 123, on the hidden desktop)
1.20.2: client Bob started (process 124, on the hidden desktop)
1.20.2: the server is ready, Cara is made operator
1.20.2: still running, 60 s; game windows on the hidden desktop: 4; free memory 36.9 GB
1.20.2: finished (ok-lines 3, last-lines 2)
1.20.2: finished (ok-lines 2, last-lines 1)
1.20.2: finished (ok-lines 0, last-lines 0) - ABORTED: stopped by the file x
1.20.2: BUILD FAILED
everything is finished; the second desktop is closed
1.20.2: [Cara] ACT opens the material list
1.20.2: [Cara] ACT shares the material list as a project: minecraft:stone=500, minecraft:oak_planks=100
1.20.2: [Bob] ACT takes charge of minecraft:stone
1.20.2: [Bob] ACT sets own count of minecraft:stone to 20
1.20.2: [Cara] ACT places a chest at 12 -60 3
1.20.2: [Cara] ACT registers a storage box around 12 -60 3
1.20.2: [Cara] ACT gives Bob 64 minecraft:stone
1.20.2: [Cara] ACT teleports Bob next to the chest 12 -60 5
1.20.2: [Cara] ACT changes own count of minecraft:stone to 15
1.20.2: [Bob] ACT opens the chest at 12 -60 3
1.20.2: [Bob] ACT opens the chest again at 12 -60 3
1.20.2: [Bob] ACT moves minecraft:stone into the chest
1.20.2: [Bob] ACT closes the chest
1.20.2: [Bob] ACT opens the chest at 12 -60 3 to take stone out
1.20.2: [Bob] ACT takes the stone out of the chest
1.20.2: [Bob] sees project with 2 items
1.20.2: [Cara] OK sees both people: Bob=20 (handed in 0) Cara=10 (handed in 0)
1.20.2: [Cara] OK sees Bob's hand-in live: Bob=64 (handed in 64) Cara=15 (handed in 0)  stock=64
1.20.2: [Bob] help picture 800x450
1.20.2: [Bob] command opened: StorageScreen
1.20.2: [Bob] sees Cara's count = 15
1.20.2: [Bob] sees Cara's count = null
1.20.2: [Cara] Bob took it out again? Bob=64 (handed in 0) Cara=15 (handed in 0)  stock={}
1.20.2: [Bob] block at chest: Block{minecraft:chest}, retrying
1.20.2: [Bob] screen=GenericContainerScreen pos=BlockPos{x=12, y=-60, z=5} chest=BlockPos{x=12, y=-60, z=3} stone=64
1.20.2: [Bob] screen=null pos=BlockPos{x=-19, y=64, z=27} chest=BlockPos{x=-19, y=76, z=25} stone=64
1.20.2: [Bob] own view after hand-in (chest open): Bob=64 (handed in 64) Cara=15 (handed in 0)  stock={minecraft:stone=64}
1.20.2: [Bob] after taking out: Bob=64 (handed in 0) Cara=15 (handed in 0)  stock={}
1.20.2: [Cara] FAIL hand-in not seen: Bob=20 (handed in 0) Cara=15 (handed in 0)  stock=0
1.20.2: [Bob] FAIL claims not seen: Bob=20 (handed in 0)
1.20.2: [Bob] FAIL project never arrived
1.20.2: [Bob] FAIL no stone or no storage box yet
1.20.2: [Bob] EXC java.lang.NullPointerException""".splitlines()


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    missing = 0
    for line in SAMPLES:
        msg = re.sub(r"^1\.20\.2: ", "", line)
        ja = to_ja(msg)
        print(("OK   " if ja else "NONE ") + (ja or msg))
        missing += ja is None
    print("status line hidden in Japanese:", is_status("server ready | Bob: test step 8: x | Cara: test step 6: y"))
    sys.exit(1 if missing else 0)
