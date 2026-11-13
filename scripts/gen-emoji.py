#!/usr/bin/env python3
"""Regenerates app/src/main/assets/emoji/cldr.tsv, the emoji the composer completes after a colon.

Source: the `unicode-emoji-json` npm package (MIT, (c) Mu-An Chiou), a JSON form of the Unicode emoji
data. Its `slug` is the CLDR short name in snake_case, which is what cicchetto completes on (grappa-irc
issue 2663: CLDR names, not the Slack/GitHub set). Skin-tone variants are left out: the package lists
one entry per base emoji.

Output, one emoji per line, in the package's (CLDR) order: <emoji> TAB <slug> TAB <name>.

    python3 scripts/gen-emoji.py            # pinned version below
    python3 scripts/gen-emoji.py 0.9.0
"""
import json
import sys
import tarfile
import tempfile
import urllib.request
from pathlib import Path

VERSION = sys.argv[1] if len(sys.argv) > 1 else "0.9.0"
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/emoji/cldr.tsv"
URL = f"https://registry.npmjs.org/unicode-emoji-json/-/unicode-emoji-json-{VERSION}.tgz"

with tempfile.TemporaryDirectory() as tmp:
    pkg = Path(tmp) / "pkg.tgz"
    pkg.write_bytes(urllib.request.urlopen(URL, timeout=60).read())
    with tarfile.open(pkg) as tar:
        member = tar.getmember("package/data-by-emoji.json")  # exactly one file, never a path from the archive
        data = json.load(tar.extractfile(member))

lines = []
for emoji, info in data.items():
    slug, name = info["slug"], info["name"]
    assert "\t" not in emoji + slug + name and "\n" not in name
    lines.append(f"{emoji}\t{slug}\t{name}")
OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"{len(lines)} emoji from unicode-emoji-json {VERSION} -> {OUT}")
