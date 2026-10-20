#!/bin/bash
set -euo pipefail

# Körs bara i fjärrsessioner (Claude på webben/telefonen) – lokala Android Studio-miljöer
# lämnas orörda.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

echo '{"async": true, "asyncTimeout": 300000}'

# Repots rot utifrån skriptets plats – fungerar även när sessionens projektrot är en
# föräldrakatalog med flera repon.
cd "$(dirname "$0")/../.."

# tools/db: installera beroenden om verktyget finns och inte redan är installerat.
# --omit=dev: bara verktygens egna beroenden – testverktygen (firebase-tools m.fl.) behövs
# bara i CI. --ignore-scripts: inga livscykelskript från beroenden körs i sessionen.
if [ -f tools/db/package-lock.json ] && [ ! -d tools/db/node_modules ]; then
  npm ci --prefix tools/db --omit=dev --ignore-scripts --silent --no-audit --no-fund || echo "tools/db: npm ci misslyckades"
fi

# Databasåtkomst: visa bara om nyckeln finns, aldrig dess innehåll (skill db-access).
if [ -n "${FIREBASE_SERVICE_ACCOUNT:-}" ]; then
  echo "FIREBASE_SERVICE_ACCOUNT: satt"
else
  echo "FIREBASE_SERVICE_ACCOUNT: saknas (databasåtkomst från sessionen är avstängd)"
fi

# Gradle i sessionen: bara med Android SDK (skill testing-strategy). Annars får CI testa.
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
if [ -d "$sdk/licenses" ]; then
  # Gradle hittar SDK:n via local.properties (gitignorerad), även i en ny klon.
  if ! grep -qs '^sdk.dir=' local.properties; then
    # Radbrytning först om filen inte slutar med en, så att raden inte klistras ihop med den förra.
    if [ -s local.properties ] && [ -n "$(tail -c1 local.properties)" ]; then echo >> local.properties; fi
    echo "sdk.dir=$sdk" >> local.properties
  fi
  echo "Android SDK: $sdk (Gradle kan köras i sessionen)"
else
  echo "Android SDK: saknas – installera vid behov med .claude/hooks/install-android-sdk.sh"
fi

# Maven Central via Googles spegel (429 från repo.maven.apache.org i sessionen).
mkdir -p "$HOME/.gradle/init.d"
cp .claude/hooks/maven-central-mirror.init.gradle.kts "$HOME/.gradle/init.d/"
echo "Gradle: Maven Central via Googles spegel (~/.gradle/init.d/maven-central-mirror.init.gradle.kts)"
