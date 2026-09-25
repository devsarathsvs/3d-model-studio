#!/usr/bin/env bash
# Creates a local release identity once. Keep .signing/ private and back it up securely.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "$0")/.." && pwd)"
mkdir -p "$project_dir/.signing"
chmod 700 "$project_dir/.signing"
if [[ -e "$project_dir/.signing/release.properties" || -e "$project_dir/.signing/model-studio.jks" ]]; then
  printf 'Signing material already exists; leaving it unchanged.\n'
  exit 0
fi
umask 077
export STUDIO_STORE_PASS="$(openssl rand -hex 24)"
keytool -genkeypair -keystore "$project_dir/.signing/model-studio.jks" \
  -storetype JKS -storepass:env STUDIO_STORE_PASS -keypass:env STUDIO_STORE_PASS \
  -alias model-studio -keyalg RSA -keysize 3072 -validity 10000 \
  -dname 'CN=Model Studio, OU=Android, O=Independent Development, C=IN' >/dev/null 2>&1
printf 'storeFile=.signing/model-studio.jks\nstorePassword=%s\nkeyAlias=model-studio\nkeyPassword=%s\n' \
  "$STUDIO_STORE_PASS" "$STUDIO_STORE_PASS" > "$project_dir/.signing/release.properties"
unset STUDIO_STORE_PASS
printf 'Created private release signing material in .signing/. Back up this directory securely.\n'
