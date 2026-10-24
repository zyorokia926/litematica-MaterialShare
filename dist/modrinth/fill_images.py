#!/usr/bin/env python3
"""Fills the picture links of description_with_images.md.

1. Upload the pictures of gallery/ja and gallery/en to the Modrinth gallery.
2. Copy the address of each picture and write it into image_urls.txt, one per line:
       en/list https://cdn.modrinth.com/data/.../images/....png
       ja/list https://cdn.modrinth.com/data/.../images/....png
3. Run:  python3 fill_images.py   (writes description_final.md; paste that into the Description box)
"""
import re
import sys
from pathlib import Path

here = Path(__file__).parent
urls = {}
for line in (here / "image_urls.txt").read_text(encoding="utf-8").splitlines():
    parts = line.split()
    if len(parts) == 2:
        urls[parts[0]] = parts[1]
text = (here / "description_with_images.md").read_text(encoding="utf-8")
names = sorted(set(re.findall(r"IMG:([a-z]+/[a-z_]+)", text)))
missing = [n for n in names if n not in urls]
if missing:
    print("Missing addresses in image_urls.txt for:", ", ".join(missing))
    sys.exit(1)
(here / "description_final.md").write_text(re.sub(r"IMG:([a-z]+/[a-z_]+)", lambda m: urls[m.group(1)], text), encoding="utf-8")
print("Wrote description_final.md (%d pictures)" % len(names))
