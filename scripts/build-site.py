#!/usr/bin/env python3
"""Writes the landing page of the F-Droid repository: index.html (from site/index.html.in) and the QR code.

Called by scripts/build-fdroid-site.sh after `fdroid update`, with the repository already in OUT/repo. The QR encodes
the link F-Droid opens to add a repository: fdroidrepos://<host>/<path>?fingerprint=<SHA-256 of the signing cert>.
"""
import argparse
import html
import json
import shutil
from pathlib import Path
from urllib.parse import urlparse

import segno

ap = argparse.ArgumentParser()
ap.add_argument("out")
ap.add_argument("--repo-url", required=True)
ap.add_argument("--site-url", required=True)
ap.add_argument("--fingerprint", required=True)
ap.add_argument("--gh-repo", default="LucentW/resentin")
args = ap.parse_args()

here = Path(__file__).resolve().parent.parent
out = Path(args.out)

u = urlparse(args.repo_url)
scheme = "fdroidrepos" if u.scheme == "https" else "fdroidrepo"
uri = f"{scheme}://{u.netloc}{u.path}?fingerprint={args.fingerprint}"

# The newest version the index lists, for the page; the index is the truth, not the git tags.
version = "?"
index = out / "repo" / "index-v1.json"
if index.exists():
    pk = json.loads(index.read_text()).get("packages", {}).get("pm.antani.resentin", [])
    if pk:
        version = max(pk, key=lambda p: p["versionCode"])["versionName"]

segno.make(uri, error="m").save(out / "qr.svg", scale=8, border=2, dark="#000000", light="#ffffff")
shutil.copy(here / "site" / "icon.svg", out / "icon.svg")
shots = out / "img"
shots.mkdir(exist_ok=True)
for name in ("home", "chat-bubbles", "chat-irc-line", "directory", "members", "settings"):
    shutil.copy(here / "docs" / "screenshots" / f"{name}.png", shots / f"{name}.png")

fp = args.fingerprint
spaced = " ".join(fp[i:i + 8] for i in range(0, len(fp), 8))
values = {
    "@REPO_URL@": html.escape(args.repo_url),
    "@SITE_URL@": html.escape(args.site_url),
    "@FINGERPRINT@": html.escape(fp),
    "@FINGERPRINT_SPACED@": html.escape(spaced),
    "@FDROID_URI@": html.escape(uri),
    "@VERSION@": html.escape(version),
    "@GH_URL@": f"https://github.com/{args.gh_repo}",
    "@APK_URL@": f"https://github.com/{args.gh_repo}/releases/latest/download/app-release.apk",
}
page = (here / "site" / "index.html.in").read_text()
for k, v in values.items():
    page = page.replace(k, v)
assert "@" not in page.replace("@media", "").replace("@2x", ""), "an unfilled placeholder is left in the page"
(out / "index.html").write_text(page)
(out / ".nojekyll").write_text("")
print(f"landing page for version {version}; QR -> {uri}")
