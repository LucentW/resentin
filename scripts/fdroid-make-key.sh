#!/usr/bin/env bash
# Makes the signing key of the F-Droid repository (NOT the app's key: that one signs the APKs, this one signs the
# repository index) and prints what to put in GitHub. Run it once, keep the files and the passwords somewhere safe
# and offline. The key is the repository's identity: every client that added the repo pins its fingerprint, so
# losing it or replacing it means everyone has to add the repository again from scratch.
set -euo pipefail
OUT="${1:-resentin-fdroid-repo.jks}"
ALIAS="${FDROID_KEY_ALIAS:-resentin-repo}"
[ -e "$OUT" ] && { echo "$OUT exists: refusing to overwrite a key" >&2; exit 1; }
# One password for the store and the key: a PKCS12 keystore encrypts the key with the store password and ignores any other.
STORE_PASS="$(openssl rand -hex 24)"
KEY_PASS="$STORE_PASS"
keytool -genkeypair -storetype PKCS12 -keystore "$OUT" -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$STORE_PASS" -keypass "$KEY_PASS" -dname "CN=Resentin F-Droid repository, O=antani.pm"
FP="$(keytool -exportcert -alias "$ALIAS" -keystore "$OUT" -storepass "$STORE_PASS" | openssl x509 -inform DER -noout -fingerprint -sha256 | cut -d= -f2 | tr -d :)"
cat <<MSG

Keystore:    $OUT
Alias:       $ALIAS
Password:    $STORE_PASS   (the store's and the key's: PKCS12 has one)
Fingerprint: $FP   (what users check when they add the repository)

Keep $OUT and the password offline. Then give them to the CI (from the repository's directory):

  base64 -w0 $OUT | gh secret set FDROID_KEYSTORE_BASE64
  gh secret set FDROID_KEYSTORE_PASSWORD --body '$STORE_PASS'
  gh secret set FDROID_KEY_ALIAS --body '$ALIAS'
  gh secret set FDROID_KEY_PASSWORD --body '$KEY_PASS'
  gh variable set FDROID_ENABLED --body true      # turns the F-Droid job on
MSG
