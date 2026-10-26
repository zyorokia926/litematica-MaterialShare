#!/usr/bin/env python3
"""Writes the feature sections of README.md from the how-to of the game, so that the README lists every feature the how-to has.

    python ports/readme_features.py            rewrites README.md between the FEATURES markers
    python ports/readme_features.py --check    only reports (exit code 1 when README.md is out of date or a picture is missing)

The source is the same as the in-game guide (HelpScreen): the page list HelpScreen.PAGES (page ids and pictures) and the texts
help.<id>.title / short / body of ja_jp.json and en_us.json, plus the settings (config.name / config.comment). When a feature is
added, add its how-to page first (see CLAUDE.md), then run this script.
Differences between Minecraft versions go into VERSION_NOTES (page id -> {"ja": ..., "en": ...}); they appear under the feature.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources/assets/litematica_materialshare"
PREFIX = "litematica_materialshare."
IMG = "src/main/resources/assets/litematica_materialshare/textures/gui/help"

# page id -> {"ja": "...", "en": "..."}: a note for a feature that works differently on some Minecraft versions
VERSION_NOTES = {}

TEXT = {
    "ja": dict(file="ja_jp.json", features="機能の一覧", details="機能の説明", press="各項目を押すと、説明と写真が開きます。",
               cols=("機能", "ひとこと", "詳しく"), more="設定・ホットキーの一覧", setcols=("名前", "内容"), note="ver ごとの注意",
               marker="JA"),
    "en": dict(file="en_us.json", features="Features", details="Feature details", press="Press an entry to open its explanation and pictures.",
               cols=("Feature", "In short", "Details"), more="All settings and hotkeys", setcols=("Name", "What it does"), note="Note for some versions",
               marker="EN"),
}


def pages():
    src = (ROOT / "src/main/java/dev/materialshare/client/HelpScreen.java").read_text(encoding="utf-8")
    block = src[src.index("PAGES = List.of("):]
    block = block[:block.index(");")]
    return [(m.group(1), re.findall(r'"(\w+)"', m.group(2))) for m in re.finditer(r'new Page\("(\w+)",\s*List\.of\(([^)]*)\)\)', block)]


def body_lines(pid, text):
    out = []
    for line in text.split("\n"):
        line = line.strip()
        if not line:
            continue
        cmd = re.match(r"^(/materialshare[^…]*?)\s*(?:…|\.\.\.)\s*(.*)$", line)
        if cmd:
            out.append(f"- `{cmd.group(1).strip()}` — {cmd.group(2)}")
        elif re.match(r"^\d+\. ", line):
            out.append(line)
        else:
            out.append("- " + line)
    return out


def section(lang, problems):
    t = TEXT[lang]
    d = json.loads((RES / "lang" / t["file"]).read_text(encoding="utf-8"))
    rows, parts = [], []
    for pid, images in pages():
        key = PREFIX + "help." + pid
        if key + ".title" not in d or key + ".body" not in d:
            problems.append(f"{lang}: page {pid} has no text")
            continue
        title, short, body = d[key + ".title"], d.get(key + ".short", ""), d[key + ".body"]
        anchor = f"f-{lang}-{pid}"
        rows.append(f"| {title} | {short} | [↓](#{anchor}) |")
        part = [f'<a id="{anchor}"></a>', "", f"### {title}", "", "<details>", f"<summary><b>{short or title}</b></summary>", ""]
        for name in images:
            if not (RES / "textures/gui/help" / lang / f"{name}.png").exists():
                problems.append(f"{lang}: picture {name}.png of page {pid} is missing")
                continue
            part.append(f'<img src="{IMG}/{lang}/{name}.png" width="640" alt="{title} ({name})">')
        part += ["", *body_lines(pid, body), ""]
        note = VERSION_NOTES.get(pid, {}).get(lang)
        if note:
            part += ["> [!NOTE]", f"> **{t['note']}**: {note}", ""]
        part += ["</details>", ""]
        parts.append("\n".join(part))
    settings = [f"| {d[k]} | {d.get(k.replace('config.name.', 'config.comment.'), '')} |" for k in d if "config.name." in k]
    out = [f"## {t['features']}", "", f"| {t['cols'][0]} | {t['cols'][1]} | {t['cols'][2]} |", "|---|---|---|", *rows, "",
           f"## {t['details']}", "", t["press"], "", *parts,
           f"### {t['more']}", "", "<details>", f"<summary><b>{t['more']}</b></summary>", "",
           f"| {t['setcols'][0]} | {t['setcols'][1]} |", "|---|---|", *settings, "", "</details>", ""]
    return "\n".join(out), len(rows)


def main():
    check = "--check" in sys.argv
    readme = ROOT / "README.md"
    text = readme.read_bytes().decode("utf-8")
    crlf = "\r\n" in text
    text = text.replace("\r\n", "\n")
    problems, new = [], text
    for lang in ("ja", "en"):
        marker = TEXT[lang]["marker"]
        start, end = f"<!-- FEATURES-{marker}:START -->", f"<!-- FEATURES-{marker}:END -->"
        if start not in new or end not in new:
            problems.append(f"README.md has no {start} / {end} markers")
            continue
        body, count = section(lang, problems)
        new = new[:new.index(start) + len(start)] + "\n" + body + new[new.index(end):]
        print(f"{lang}: {count} features")
    for p in problems:
        print("PROBLEM:", p)
    if new != text:
        if check:
            print("README.md is out of date")
        else:
            readme.write_bytes((new.replace("\n", "\r\n") if crlf else new).encode("utf-8"))
            print("README.md updated")
    sys.exit(1 if problems or (check and new != text) else 0)


if __name__ == "__main__":
    main()
