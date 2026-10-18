#!/bin/bash
# Installerar Android SDK i /opt/android-sdk för en molnsession (skill testing-strategy).
# Körs vid behov eller från molnmiljöns setup-skript – inte av session-start-hooken (≈470 MB).
# Idempotent. Skriptet installerar verktygen och accepterar licenserna; plattform och
# build-tools hämtar Gradle själv (paketnamnen följer compileSdk, t.ex. android-37.0).
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=/opt/android-sdk
if [ -x "$sdk/cmdline-tools/latest/bin/sdkmanager" ] && [ -d "$sdk/licenses" ]; then
  echo "Android SDK finns redan: $sdk"
else
  mkdir -p "$sdk/cmdline-tools"
  tmp=$(mktemp -d)
  curl -sSfo "$tmp/clt.zip" https://dl.google.com/android/repository/commandlinetools-linux-9862592_latest.zip
  unzip -q "$tmp/clt.zip" -d "$tmp"
  rm -rf "$sdk/cmdline-tools/latest" && mv "$tmp/cmdline-tools" "$sdk/cmdline-tools/latest" && rm -rf "$tmp"
  yes | "$sdk/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
  "$sdk/cmdline-tools/latest/bin/sdkmanager" "platform-tools" >/dev/null
  echo "Android SDK installerad: $sdk"
fi
# Gradle hittar SDK:n via local.properties (gitignorerad) – inget ANDROID_HOME behövs.
if ! grep -qs '^sdk.dir=' local.properties; then echo "sdk.dir=$sdk" >> local.properties; fi
