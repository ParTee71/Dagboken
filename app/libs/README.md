# Samsung Health Data SDK

Samsung Health Data SDK distribueras **inte** via Maven Central eller Googles
repo — det laddas ner manuellt från Samsung Developer efter att ha godkänt
deras SDK-licens, och kan därför inte checkas in i det här repot.

## Så här installeras SDK:t lokalt

1. Ladda ner AAR:en från https://developer.samsung.com/health/data (kräver
   inloggning och godkännande av licensvillkoren).
2. Lägg AAR-filen i den här mappen (`app/libs/`). Filnamnet spelar ingen roll
   — `app/build.gradle.kts` plockar upp alla `*.aar`-filer här automatiskt:

   ```kotlin
   implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
   ```

3. `*.aar` i den här mappen är git-ignorerad (se `.gitignore`) — varje
   utvecklare hämtar sin egen kopia.

## Krav

- Samsung Health-appen v6.30.2 eller senare på testenheten.
- Developer mode aktiverat i Samsung Health-appen (tryck 10 gånger på
  versionsnumret under Inställningar → Om Samsung Health).
- Android 10 (API 29)+ på enheten. Dagboken har `minSdk 30`, vilket redan
  uppfyller kravet.

Se issue #56 (spike) för kartläggning av datapunkter, behörigheter och
API-karaktär.
