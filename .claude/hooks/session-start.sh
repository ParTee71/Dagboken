#!/bin/bash
# SessionStart-hook för Dagboken (Claude Code på webben / Claude Android).
#
# Syfte: provisionera Android SDK i fjärrsessioner så att enhetstester och
# kompilering kan köras (./gradlew :app:testDebugUnitTest m.fl.).
#
# Körs ENDAST i fjärrmiljö (CLAUDE_CODE_REMOTE=true). Lokalt i Android Studio är
# det en no-op — där finns SDK:n redan. Skriptet är idempotent och avbryter
# aldrig sessionsstarten (exit 0 även om nedladdning blockeras).
set -uo pipefail

# Kör bara på webben/fjärrsessioner. Lokalt (t.ex. Android Studio) görs inget.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

log() { echo "[session-start] $*"; }

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
PLATFORM="platforms;android-35"      # compileSdk/targetSdk = 35
BUILD_TOOLS="build-tools;35.0.0"
CMDLINE_ZIP_URL="https://dl.google.com/android/repository/commandline-tools-linux-11076708_latest.zip"

persist_env() {
  # Gör ANDROID_HOME tillgängligt för resten av sessionen.
  if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
    {
      echo "export ANDROID_HOME=\"$ANDROID_HOME\""
      echo "export ANDROID_SDK_ROOT=\"$ANDROID_HOME\""
      echo "export PATH=\"\$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin\""
    } >> "$CLAUDE_ENV_FILE"
  fi
  # Gradle hittar SDK:n via local.properties (git-ignorerad).
  if ! grep -qs '^sdk.dir=' "$PROJECT_DIR/local.properties" 2>/dev/null; then
    echo "sdk.dir=$ANDROID_HOME" >> "$PROJECT_DIR/local.properties"
  fi
}

# Redan provisionerad (cachelagrad container)? Säkerställ env och avsluta snabbt.
if [ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ] \
   && [ -d "$ANDROID_HOME/platforms/android-35" ]; then
  log "Android SDK finns redan i $ANDROID_HOME — hoppar över installation."
  persist_env
  exit 0
fi

log "Provisionerar Android SDK i $ANDROID_HOME ..."
mkdir -p "$ANDROID_HOME/cmdline-tools"

# 1. Ladda ner command-line tools om de saknas.
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp_zip="$(mktemp --suffix=.zip)"
  if ! curl -fsSL --max-time 180 -o "$tmp_zip" "$CMDLINE_ZIP_URL"; then
    log "‼️  Kunde inte hämta Android SDK från dl.google.com."
    log "    Värden dl.google.com är blockerad av miljöns nätverkspolicy."
    log "    Android-bygget kräver dl.google.com (SDK + Google Maven: AndroidX/Compose/Hilt)."
    log "    Åtgärd: vidga miljöns nätverkspolicy så att dl.google.com tillåts."
    log "    Se https://code.claude.com/docs/en/claude-code-on-the-web (nätverkspolicy)."
    rm -f "$tmp_zip"
    exit 0   # Blockera inte sessionsstarten.
  fi
  rm -rf "$ANDROID_HOME/cmdline-tools/latest" "$ANDROID_HOME/cmdline-tools/cmdline-tools"
  unzip -q "$tmp_zip" -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -f "$tmp_zip"
fi

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

# 2. Acceptera licenser och installera plattform + build-tools (idempotent).
yes 2>/dev/null | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true
if ! "$SDKMANAGER" --sdk_root="$ANDROID_HOME" \
      "platform-tools" "$PLATFORM" "$BUILD_TOOLS" >/dev/null 2>&1; then
  log "‼️  sdkmanager kunde inte installera paket (sannolikt blockerad dl.google.com)."
  log "    Vidga nätverkspolicyn för att tillåta dl.google.com och starta om sessionen."
  persist_env
  exit 0
fi

persist_env
log "✅ Android SDK klart i $ANDROID_HOME (platform-tools, $PLATFORM, $BUILD_TOOLS)."
exit 0
