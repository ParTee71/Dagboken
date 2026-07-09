# Dagboken – projektets grundregler

> Hälsodagbok (Android/Kotlin · Compose · MVVM · Hilt · Room · DataStore · Firebase · Google Drive).
> Kravspecifikation: [KRAVLISTA.md](KRAVLISTA.md) · Arkitektur: [README.md](README.md)

Den här filen laddas automatiskt vid **varje** uppgift. Den gäller alltid, för alla
ändringar, oavsett storlek. De detaljerade reglerna ligger som skills i
`.claude/skills/` — den här filen är kontraktet som binder ihop dem.

**Svarslängd:** håll chattsvar minimala. Inga sammanfattningar, ingen upprepning av vad
som gjordes, inga rubriker/listor om inte nödvändigt. Kod, commits och PR-beskrivningar
skrivs normalt.

---

## De fyra icke-förhandlingsbara reglerna

Vid **varje** kodändring ska du, innan du anser arbetet klart, gå igenom alla fyra:

### 1. Datasäkerhet — backup/restore får aldrig tappa data
All användardata måste överleva en **backup → restore-rundtur** utan förlust.
Lägger du till eller ändrar ett persisterat fält/en entitet ska det in i hela
backup-kedjan *och* täckas av rundturstest.
→ Skill: **data-safety-backup**. Krav: BCK-1…9, SJ-7, NFR-7, DAT-3.

### 2. Tester på alla nivåer
Ingen beteendeändring utan att tester läggs till eller uppdateras på rätt nivå
(enhet / instrument / migrering). Befintliga tester som påverkas ska uppdateras,
aldrig tas bort för att "bli gröna".
→ Skill: **testing-strategy**. Krav: NFR-6.

### 3. Kraven hålls aktuella
Ändrar du synligt beteende ska [KRAVLISTA.md](KRAVLISTA.md) uppdateras i samma
ändring — nytt krav läggs till, borttaget beteende stryks med
`~~…~~ *(borttaget)*`. Versionstabellen i README och `versionName` följer med.
→ Skill: **requirements-kravlista**.

### 4. Återanvänd generiska komponenter
Behöver du en sifferslider, ett diagram, ett kort, en datum/tid-rad, en
symptomgradering osv. — använd den befintliga delade komponenten i
`ui/components/` eller `ui/diagram/`. Bygg inte en ny variant av något som redan
finns; utöka den delade komponenten i stället.
→ Skill: **shared-ui-components**.

---

## Innan du skriver ny kod (snabb checklista)

1. **Sök efter befintligt mönster.** Det finns nästan alltid en sibling-ViewModel,
   en delad komponent eller en repository-metod som visar hur projektet gör.
   `Grep`/läs grannfiler först (se skill `android-dev`).
2. **Följ arkitekturen** även om en genväg vore enklare: MVVM, `StateFlow<UiState>`,
   Repository som single source of truth, Hilt-DI, fel mappas i repository-lagret.
3. **Svenska** i allt användarvänt: UI-strängar (i `strings.xml`), commit-meddelanden
   får vara svenska, kod/identifierare på engelska enligt befintlig stil.

## Innan du anser dig klar (slutkontroll)

- [ ] **Datasäkerhet:** nya/ändrade persisterade fält finns i `BackupJson` + `BackupMapper` + assembly i `DriveBackupRepository`, och har rundturstest. (regel 1)
- [ ] **Tester:** lagt till/uppdaterat på alla berörda nivåer; allt grönt lokalt. (regel 2)
- [ ] **Krav:** KRAVLISTA.md (och ev. README/version) speglar ändringen. (regel 3)
- [ ] **Återbruk:** ingen ny komponent som dubblerar en befintlig delad. (regel 4)
- [ ] **Arkitektur:** följer projektets etablerade mönster (skill `android-dev`).

---

## Bygg & test (kommandon)

```bash
./gradlew :app:compileDebugKotlin            # kompilera
./gradlew :app:testDebugUnitTest             # enhetstester (JUnit/MockK/Turbine)
./gradlew :app:compileDebugAndroidTestKotlin # kompilera instrumenttester
./gradlew :app:connectedDebugAndroidTest     # instrumenttester (kräver emulator/enhet)
./gradlew :app:assembleDebug                 # debug-APK
```

**Tester körs i GitHub Actions** — inte i sessionen. Vid PR/push mot `master` kör
`.github/workflows/android.yml` kompilering + enhetstester, och
`.github/workflows/instrumented.yml` instrumenttester på emulator. Det är så testning
sker när du arbetar från telefonen (Claude Android-appen): **pusha branchen och öppna
en PR mot `master`** så kör Actions testerna.

> I fjärr-/telefonsessioner finns ingen Android SDK och `dl.google.com` (Google Maven)
> kan vara blockerad av nätverkspolicyn — försök därför **inte** köra `./gradlew` där.
> Lita på CI. Kommandona ovan gäller lokal utveckling i Android Studio.

## Arkitektur i korthet

`Compose → ViewModel (StateFlow<UiState>) → Repository → Room/DataStore/Drive`.
Paket under `app/src/main/kotlin/se/partee71/dagboken/`:
`data/` (auth, datastore, migration=backup, repository, room) · `di/` · `domain/`
(model, usecase) · `notifications/` · `ui/<feature>/` + `ui/components/` +
`ui/diagram/` · `worker/`. Detaljer i [README.md](README.md).

---

## Skills i detta repo (`.claude/skills/`)

| Skill | När |
|---|---|
| `android-dev` | Baslinje för all Android/Kotlin-utveckling (ladda alltid). |
| `android-data-layer` | Repository/Room/DAO/offline-först. |
| `compose-expert` · `kotlin-coroutines` · `kotlin-flows` | Compose-, coroutine- och flow-detaljer. |
| `firebase-auth` | Inloggning (Credential Manager + Firebase). |
| `android-gradle-logic` | Gradle/version catalogs/build-konfiguration. |
| `notifications-alarms` | Påminnelser/larm: exakta larm, notiskanaler, boot-omschemaläggning. |
| `room-migrations` | Säkra Room-schemamigreringar utan dataförlust. |
| `data-privacy-security` | Känslig hälsodata: loggning, Drive-scope, hemligheter, minify. |
| `accessibility-compose` | Tillgänglighet: TalkBack, tryckytor, dynamisk text. |
| **`data-safety-backup`** | Regel 1 — backup/restore och dess tester. |
| **`testing-strategy`** | Regel 2 — tester på alla nivåer. |
| **`requirements-kravlista`** | Regel 3 — hålla KRAVLISTA.md aktuell. |
| **`shared-ui-components`** | Regel 4 — återbruk av delade komponenter. |
| `refine-issue` | Förfina en idé/bugg till ett planerat GitHub-issue (med DoD enligt de fyra reglerna). |
| `implement-issue` | Genomför ett issue/bugg/feature hela vägen till PR enligt de fyra reglerna. |
| `release` | Endast vid uttrycklig release. |

> Reglerna gäller både i Claude Android-appen och i Claude inuti Android Studio —
> båda läser denna fil och `.claude/`. Skill-filerna är vanlig Markdown och kan läsas
> direkt.
