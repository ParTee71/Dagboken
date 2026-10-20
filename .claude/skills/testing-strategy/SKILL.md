---
name: testing-strategy
description: Dagbokens testregel (regel 2, NFR-6) — ingen beteendeändring utan tester på rätt nivå(er), och flaky tester är buggar. Ladda denna ALLTID när du lägger till, ändrar eller fixar funktionalitet, en ViewModel, ett use case, en codec, konverteraren, en motor i :core (doser, kylperiod, periodslut, diagrammatematik), ett repository, en composable, en skärmram, larm, en hook eller en bugg. Trigger-ord: test, tester, testa, JUnit, MockK, Turbine, Robolectric, Roborazzi, skärmdump, screenshot, Konsist, UiConsistencyTest, cpdCheck, kontraktstest, FakeCollection, fixtur, emulator, rules-test, rundtur, instrument, androidTest, regression, flaky, instabil, täckning, TDD.
---

# Teststrategi i Dagboken

**Det generella står i skill `android:testing-strategy`** (pluginen): tester på varje berörd
nivå, befintliga tester uppdateras och tas aldrig bort, flaky är en bugg, mönstren,
skärmdumpsreglerna, fallgroparna och anti-mönstren. Här står det som är Dagbokens: nivåerna och
filtren, hjälparna, emulatorn, skärmdumpsdetaljerna och kommandona.

Krav: NFR-6 (och NFR-serien i övrigt) i KRAVLISTA.md.

## Nivåer (enda källan för denna tabell)

| Nivå | Var | Verktyg | CI-filter |
|---|---|---|---|
| Domän, codecs, konverteraren, motorer, diagrammatematik | `core/src/test` | JUnit4 + kotlin.test | `core` |
| Arkitektur/konsistens + dokumentdrift | `app/src/test` | Konsist (`UiConsistencyTest`) | `app` |
| Dubbelkod | `core/` + `app/src` | `cpdCheck` | `core`, `app` |
| Navigation | `app/src/test` | JUnit4 (back stack som lista) | `app` |
| ViewModel/Repository | `app/src/test` | JUnit4, MockK, Turbine, `FakeCollection` | `app` |
| Compose-UI | `app/src/test` | Robolectric + `ui-test-junit4` | `app` |
| Skärmdump | `app/src/test` + `app/src/test/screenshots/` | Roborazzi | `app` |
| Security rules + rundtur | `tools/db/test` | `node --test`, `@firebase/rules-unit-testing`, Firebase-emulatorn | `tools` |
| Dokumentkontroller + hooktest | `.github/scripts/`, `.claude/hooks/test/` | `node --test` | alltid |
| Instrument (inkl. `CollectionContract` mot riktig `FirestoreCollection`) | `app/src/androidTest` | Android-emulator + Firebase-emulatorn | i PR som rör koden de testar (filter `instrumented`, skill `ci-budget`) |

Vilka sökvägar som sätter vilket filter, och vad som körs på `master`, står i skill
`ci-budget` (enda källan). **Selektiv körning ändrar inte kravet på att skriva
tester** – ett test som inte körs i en viss PR ska ändå finnas och vara grönt när dess
kod ändras.

## Vad ska testas när du ändrar X

| Ändring | Lägg till / uppdatera |
|---|---|
| Motor i `:core` (doser, kylperiod, periodslut, dagens energi, sömn, diagrammatematik), validering | `:core`-test med kantfallen från kravlistan (MED, FAV, REC, TRD) – fasta datum, även midnatt och sommartid |
| Konverteraren 3.x → 4.0 | fixturen med **varje** 3.x-fält satt, fältvis jämförelse (OMB-3, skill `data-safety-backup`) |
| Larm och notiser | schemaläggning mot `FakeCollection` och fast klocka; omschemaläggning vid synk/boot/uppdatering (skill `notifications-alarms`) |
| Modell/codec | `assertCodecRoundTrip` + tolerans-hjälparna (skill `data-safety-backup`) |
| ViewModel | Initialt state, varje event → nytt state, fel-state; Turbine; `MainDispatcherRule` |
| Repository | Mot `FakeCollection<T>`; ändras `FirestoreCollection` → utöka `CollectionContract` – instrumenttesterna körs i PR:en |
| Skärm som använder en ram | `runListScreenContract` / `runEditScreenContract` + bara det unika |
| Delad komponent/ram | Robolectric-test, skärmdump ljust + mörkt, plats i `ComponentGallery` |
| `firestore.rules` | Rules-test: ägare, någon annan, oautentiserad; schemaVersion-reglerna; okänd samling |
| `tools/db` | Rundturstest och verktygets eget test |
| Hook eller dokumentkontroll | Nodetest med ett godkänt och ett underkänt exempel |
| Bugfix | **Först** ett test som reproducerar buggen och faller, sedan fixen |

## DRY i tester (regel 4 gäller även här)

Ramar och byggstenar testas **en gång, grundligt**. Feature-tester återanvänder
hjälparna och testar bara det som är unikt för funktionen:

| Hjälpare | Var | Ersätter |
|---|---|---|
| `FakeCollection<T>`, `FakeCollectionFactory`, `FakeStore` | `app/src/test/.../data/` | egna in-memory-fakes per repository |
| `CollectionContract`, `TestUserScope`, `FixedClock` | `app/src/sharedTest/.../data/` (JVM mot fake, enhet mot Firebase-emulatorn) | separata tester per repository-implementation |
| `StuckTestTimeout.rule()` som yttersta regel (`@get:Rule(order = StuckTestTimeout.OUTERMOST)`) | `app/src/sharedTest/.../testing/` (redan i `CollectionContract`) | egna `Timeout`-regler; ett hängande test äter annars CI-jobbets gräns |
| `FirebaseEmulator` (JUnit-regel) → `newUser()`/`newSignedInUser()` ger en `EmulatorUser` med egen FirebaseApp, Auth och Firestore | `app/src/androidTest/.../data/` | egen inloggning, `signOut`/`signIn` eller delad Firestore-instans i instrumenttester |
| `assertCodecContract` (samlar `assertEveryFieldDiffersFromDefault`, `assertCodecRoundTrip`, `assertToleratesMissingFields`, `assertIgnoresUnknownFields`, `assertEncodesAllFields`), `assertVariantCodecContract` | `core/src/test/.../schema/CodecAssertions.kt` | egna codec-asserts |
| `MainDispatcherRule` | `app/src/test/.../testing/` | egen `Dispatchers.setMain` per test |
| `editor.state.test { …; awaitDone(editor) }`, `awaitFailure(editor)` (kvitterar som ramen) och `expectNoEditorEvents()` | `app/src/test/.../testing/EditorTurbine.kt` | egna väntelopar på `EditorUiState.done`/`failure` i ViewModel-tester |
| `UserFixture` (riktig `UserSession` + fejkad Firestore + fejkad `UserDirectory`, `EnsureUserUseCase`) | `app/src/test/.../testing/` | egen uppställning av användare och session per test |
| `FakeAuthRepository` | `app/src/test/.../ui/auth/` | Google-inloggning i ViewModel-tester |
| `captureLightAndDark(name) { … }`, `rule.captureLightAndDark(name, settle) { … }` och `rule.captureScreenLightAndDark(name, open) { … }` | `app/src/test/.../testing/Screenshots.kt` | egna Roborazzi-anrop; regelvarianten behåller en visad snackbar mellan bilderna, skärmvarianten fotograferar dialoger, menyer och sheets |
| `SemanticsNodeInteraction.pixels(color)` | `app/src/test/.../testing/Pixels.kt` | egna pixelräkningar för att visa att något ritas i en viss färg (framstegsraden, diagrammen) |
| `runListScreenContract(DagbokenContracts, item, itemText, emptyTitle, addLabel) { state, onAdd, onRetry -> … }` (`addLabel = null`: ingen lägg till-knapp får finnas) | android-ui `ui-testing` (`se.partee71.ui.testing`); Dagbokens tema och fel i `DagbokenContracts` (`app/src/test/.../ui/ScreenContracts.kt`) | att testa tomt/fel/laddar/lägg till per skärm |
| `runEditScreenContract(DagbokenContracts, editor, makeInvalid, makeValid, invalidMessage) { state, events, onSave, onClose -> … }`, `runDetailScreenContract(DagbokenContracts, …)` | android-ui `ui-testing`, med `DagbokenContracts` | att testa spara-aktivering/släng-dialog/sparfel per skärm |
| `test/fixtures/user.json` | `tools/db/test/` | egna seeds per Nodetest |

Behövs samma testuppställning på två ställen → gör en hjälpare innan det tredje.

Tester som delas mellan JVM och enhet ligger i `app/src/sharedTest` (t.ex. `CollectionContract`).
Där och i `app/src/androidTest` heter testmetoderna vanliga identifierare utan mellanslag
(`upsert_syns_i_observe`) – D8 kan inte dexa metodnamn med mellanslag. JVM-tester får ha
svenska meningar i backticks.
`cpdCheck` täcker även testkällor.

## Projektmönster

- Repositories byggs i test med `FakeCollection`.
- **`:core` är synkron** – testa rena funktioner direkt, inga coroutines.

## Tester mot Firebase-emulatorn

Tester i `tools/db/test/` och instrumenttester mot Firestore körs **endast mot emulatorn**,
via den gemensamma testhjälparen som avbryter om `FIRESTORE_EMULATOR_HOST` saknas och
använder projekt-ID:t `demo-dagboken`. En session med `FIREBASE_SERVICE_ACCOUNT` i
miljön får aldrig köra dem utan emulator (skill `data-safety-backup`).

Instrumenttesterna får sina användare från regeln `FirebaseEmulator` (`@get:Rule val emulator =
FirebaseEmulator()`). Varje användare har en **egen FirebaseApp** – egen Auth, egen
Firestore-instans och egen cache – och är inloggad innan Firestore-instansen skapas; regeln
avslutar deras Firestore och loggar ut efter testet (apparna raderas aldrig: `FirebaseApp.delete()` bryter Firebase Auths interna register för alla senare appar i processen). Byt **aldrig** användare med `signOut`/`signInAnonymously` under en
levande Firestore-instans: Firestore får bytet asynkront, och en skrivning direkt efteråt kan
hamna i förra användarens kö och aldrig kvitteras (CI hängde så, ungefär var 19:e byte). Behövs
två användare i ett test skapas två (`FirestoreOfflineTest`). Varje steg mot emulatorn har en
tidsgräns med ett felmeddelande som säger vilket steg som inte svarade, och varje testklass har
`StuckTestTimeout` som yttersta regel. `FirebaseEmulatorTest` kör regelns livscykel 50 gånger i
rad som regressionstest.

## Dokumentdrift-kontrollen

`UiConsistencyTest` jämför komponent- och ramtabellen i skill `shared-ui-components` med
koden i båda riktningar, och att varje publik komponent har skärmdumpar och en plats i
`ComponentGallery`.

## Skärmdumpar (Roborazzi)

- Varje delad komponent och varje ramtillstånd: ljust + mörkt. `ComponentGallery` får en
  extra bild med `fontScale = 1.3`.
- Filnamn `app/src/test/screenshots/<Komponent>_<variant>_<light|dark>.png`.
- Deterministik: fasta mått (`@Config(qualifiers = "w390dp-h844dp-xxhdpi")`), animationer
  av, ingen klocka, inga slumpvärden.
- **Avsiktlig designändring** → `./gradlew :app:recordRoborazziDebug` och nya bilder i
  **samma** PR, med före/efter i PR-beskrivningen.
- **Oväntad diff** är en bugg eller regression – aldrig något som "bara spelas in på nytt".

## Innan du anser dig klar

```bash
./gradlew :core:test                                  # om core/ ändrats
./gradlew :app:testDebugUnitTest :app:verifyRoborazziDebug cpdCheck   # om app/ eller core/ ändrats
./gradlew spotlessApply                                              # kodstil före push (inte i CI)
./gradlew :app:lintDebug :app:assembleRelease         # om manifest, resurser, proguard eller byggfiler ändrats
npx --prefix tools/db firebase emulators:exec --only firestore --project demo-dagboken "npm --prefix tools/db test"  # om tools/db/ eller rules ändrats – bara mot emulatorn
node --test '.github/scripts/*.test.mjs'              # dokumentkontroller, alltid
node --test '.claude/hooks/test/*.test.mjs'           # hooktest, alltid
./gradlew :core:convertLegacyBackup --args="--in tools/db/backup-3x.json --out tools/db/export-4.json --user <uid>"   # vid behov, ingen kontroll: 3.x-backup → 4.0-export (OMB-4), rapport utan innehåll
./gradlew :core:matchMedicines --args="--in <export.json> --catalog app/src/main/assets/medicines.tsv --suggest <map.tsv>"   # vid behov, ingen kontroll: styrka och form ur läkemedelslistan (REC-14), rapport utan innehåll
```

**Kör kontrollerna lokalt innan varje push** – en röd CI-runda är det dyraste som finns (skill
`ci-budget`). Gradle körs i sessionen med Android SDK i `/opt/android-sdk`
(saknas den: kör `.claude/hooks/install-android-sdk.sh` – det är alltid ok att installera Android SDK och liknande verktyg i sessionen; bara om installationen inte går får CI bekräfta); Firebase-emulatorn kräver Java 21 och `npm ci` i
`tools/db`. `LC_ALL=C.UTF-8` (testnamn med å/ä/ö) sätts av `.claude/settings.json`. Kör en Gradle-körning i taget. Bara instrumenttesterna kräver Android-emulator
och körs i Actions (`instrumented.yml`, i PR:er som rör koden de testar). Går ett kommando inte att köra (nät, SDK saknas):
säg det i PR:en. Vid rött CI – låt agenten `android:ci-doktor` diagnostisera.

## Dagbokens tillägg till anti-mönstren

- Blockera utan tidsgräns i en uppställning (`runBlocking`, `Tasks.await`, lås) – en hängning ska
  bli ett rött test med stackar, inte ett CI-jobb som når sin gräns.

## För testskrivaren

Agenten `android:testskrivare` (pluginen) läser det här utöver tabellerna ovan:

- **Läs också** `data-safety-backup` vid data och konverteraren, `notifications-alarms` vid larm,
  `diagram` vid diagrammatematik.
- **Hjälparna:** `FakeCollection<T>`, `CollectionContract`, codec-hjälparna, `MainDispatcherRule`,
  `runListScreenContract`, `runEditScreenContract` och fixturen `tools/db/test/fixtures/user.json`.
- **Fixturer:** 3.x-backupen byggs med **varje** fält satt (OMB-3). Datum- och dosfall med fasta
  datum, även kring midnatt; klockan injiceras (`Clock`).
- **Testdata** är syntetisk – aldrig utdrag ur en riktig backup (hälsodata).
- **Firestore** bara mot emulatorn (`demo-dagboken`).
