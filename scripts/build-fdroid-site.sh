#!/usr/bin/env bash
# Builds the whole static site into OUTDIR: the F-Droid repository (repo/, signed index and APKs) and the landing page
# (index.html with the QR code). It is what the CI deploys to GitHub Pages, and it runs locally the same way.
#
#   scripts/build-fdroid-site.sh OUTDIR
#
# Needs: fdroidserver and segno (pip), keytool, openssl, and the Android SDK's apksigner. Environment:
#   KEYSTORE_PATH KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD   the repository's signing key (scripts/fdroid-make-key.sh)
#   REPO_URL   where the repository will be served      (default https://resentin.antani.pm/repo)
#   APK_DIR    take the APKs from this directory instead of the GitHub releases (tests); files: <name>.apk
#   GH_REPO    default LucentW/resentin;  KEEP  how many releases to keep (default 3)
set -euo pipefail
OUT="${1:?usage: build-fdroid-site.sh OUTDIR}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
REPO_URL="${REPO_URL:-https://resentin.antani.pm/repo}"
SITE_URL="${REPO_URL%/repo}"
GH_REPO="${GH_REPO:-LucentW/resentin}"
KEEP="${KEEP:-3}"
: "${KEYSTORE_PATH:?}" "${KEYSTORE_PASSWORD:?}" "${KEY_ALIAS:?}" "${KEY_PASSWORD:?}"

APKSIGNER="$(command -v apksigner || true)"
if [ -z "$APKSIGNER" ]; then
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(sed -n 's/^sdk.dir=//p' "$HERE/local.properties" 2>/dev/null || true)}}"
  APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
fi
[ -n "$APKSIGNER" ] || { echo "apksigner not found (install the Android build-tools)" >&2; exit 1; }

rm -rf "$OUT"; mkdir -p "$OUT/repo" "$OUT/metadata"
cp -r "$HERE/fdroid/metadata/." "$OUT/metadata/"
# The repository icon: fdroidserver checks config's repo_icon in the working directory, and serves it from repo/icons/.
mkdir -p "$OUT/repo/icons"; cp "$HERE/fdroid/metadata/pm.antani.resentin/en-US/images/icon.png" "$OUT/repo/icons/icon.png"
cp "$HERE/fdroid/metadata/pm.antani.resentin/en-US/images/icon.png" "$OUT/icon.png"
sed -e "s|@REPO_URL@|$REPO_URL|" -e "s|@KEY_ALIAS@|$KEY_ALIAS|" -e "s|@KEYSTORE@|$KEYSTORE_PATH|" \
    -e "s|@KEYSTORE_PASS@|$KEYSTORE_PASSWORD|" -e "s|@KEY_PASS@|$KEY_PASSWORD|" -e "s|@APKSIGNER@|$APKSIGNER|" \
    "$HERE/fdroid/config.yml.in" > "$OUT/config.yml"
chmod 600 "$OUT/config.yml"

# The APKs: the last KEEP releases (a tag is a version; its signed APK is the release asset), oldest first so the
# newest wins on a name clash. All carry the app's own signature: the repo only re-signs its index.
if [ -n "${APK_DIR:-}" ]; then
  cp "$APK_DIR"/*.apk "$OUT/repo/"
else
  for tag in $(gh release list --repo "$GH_REPO" --exclude-drafts --exclude-pre-releases --limit "$KEEP" --json tagName --jq '.[].tagName' | tac); do
    T="$(mktemp -d)"; gh release download "$tag" --repo "$GH_REPO" --pattern '*.apk' --dir "$T"
    mv "$T"/*.apk "$OUT/repo/resentin-$tag.apk"; rm -rf "$T"
  done
fi
ls "$OUT/repo"/*.apk >/dev/null

( cd "$OUT" && fdroid update --verbose 2>&1 | tail -20 )
test -f "$OUT/repo/index-v1.jar" || test -f "$OUT/repo/entry.jar" || { echo "fdroid update produced no signed index" >&2; exit 1; }

FP="$(keytool -exportcert -alias "$KEY_ALIAS" -keystore "$KEYSTORE_PATH" -storepass "$KEYSTORE_PASSWORD" | openssl x509 -inform DER -noout -fingerprint -sha256 | cut -d= -f2 | tr -d :)"
python3 "$HERE/scripts/build-site.py" "$OUT" --repo-url "$REPO_URL" --site-url "$SITE_URL" --fingerprint "$FP" --gh-repo "$GH_REPO"
# Never publish the config (it holds the passwords) nor the working metadata.
rm -rf "$OUT/config.yml" "$OUT/icon.png" "$OUT/metadata" "$OUT/tmp" "$OUT/.fdroid"* 2>/dev/null || true
echo "site built in $OUT (fingerprint $FP)"
