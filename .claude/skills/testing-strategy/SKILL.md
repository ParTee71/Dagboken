---
name: testing-strategy
description: Dagbokens testregel (regel 2, NFR-6) — ingen beteendeändring utan tester på rätt nivå(er), och flaky tester är buggar. Ladda denna ALLTID när du lägger till, ändrar eller fixar funktionalitet, en ViewModel, ett use case, en codec, konverteraren, en motor i :core (doser, kylperiod, periodslut, diagrammatematik), ett repository, en composable, en skärmram, larm, en hook eller en bugg. Trigger-ord: test, tester, testa, JUnit, MockK, Turbine, Robolectric, Roborazzi, skärmdump, screenshot, Konsist, UiConsistencyTest, cpdCheck, kontraktstest, FakeCollection, fixtur, emulator, rules-test, rundtur, instrument, androidTest, regression, flaky, instabil, täckning, TDD.
---

# Teststrategi: tester på alla nivåer

**Regel:** en ändring av beteende är inte klar förrän tester lagts till eller uppdaterats
på varje berörd nivå. Befintliga tester **uppdateras** så att de speglar det nya
beteendet – de tas aldrig bort, `@Ignore`:as eller försvagas för att bli gröna.
**Ett test som ibland faller är en bugg** – hitta grundorsaken (klocka, ordning, delat
tillstånd, otestad asynkronitet) och gör det deterministiskt. Kör aldrig bara om.

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
| `assertCodecContract` (samlar `assertEveryFieldDiffersFromDefault`, `assertCodecRoundTrip`, `assertToleratesMissingFields`, `assertIgnoresUnknownFields`, `assertEncodesAllFields`), `assertVariantCodecContract` | `core/src/test/.../schema/CodecAssertions.kt` | egna codec-asserts |
| `MainDispatcherRule` | `app/src/test/.../testing/` | egen `Dispatchers.setMain` per test |
| `UserFixture` (riktig `UserSession` + fejkad Firestore + fejkad `UserDirectory`, `EnsureUserUseCase`) | `app/src/test/.../testing/` | egen uppställning av användare och session per test |
| `FakeAuthRepository` | `app/src/test/.../ui/auth/` | Google-inloggning i ViewModel-tester |
| `captureLightAndDark(name) { … }`, `rule.captureLightAndDark(name, settle) { … }` och `rule.captureScreenLightAndDark(name, open) { … }` | `app/src/test/.../testing/Screenshots.kt` | egna Roborazzi-anrop; regelvarianten behåller en visad snackbar mellan bilderna, skärmvarianten fotograferar dialoger, menyer och sheets |
| `runListScreenContract(item, itemText, emptyTitle, addLabel) { state, onAdd, onRetry -> … }` | `app/src/test/.../ui/ScreenContracts.kt` | att testa tomt/fel/laddar/lägg till per skärm |
| `runEditScreenContract(editor, makeInvalid, makeValid, invalidMessage) { state, effects, onSave, onClose -> … }` | `app/src/test/.../ui/ScreenContracts.kt` | att testa spara-aktivering/släng-dialog/sparfel per skärm |
| `test/fixtures/user.json` | `tools/db/test/` | egna seeds per Nodetest |

Behövs samma testuppställning på två ställen → gör en hjälpare innan det tredje.

Tester som delas mellan JVM och enhet ligger i `app/src/sharedTest` (t.ex. `CollectionContract`).
Där och i `app/src/androidTest` heter testmetoderna vanliga identifierare utan mellanslag
(`upsert_syns_i_observe`) – D8 kan inte dexa metodnamn med mellanslag. JVM-tester får ha
svenska meningar i backticks.
`cpdCheck` täcker även testkällor.

## Projektmönster

- **Fake framför mock för datalagret.** Repositories byggs i test med `FakeCollection`.
  MockK för samverkande beroenden som inte är datalager.
- **Injicera dispatcher**; kalla aldrig `Dispatchers.IO` direkt i testbar kod.
- **Turbine** för `StateFlow`/`Flow`, inte manuell `collect`.
- **`:core` är synkron** – testa rena funktioner direkt, inga coroutines.
- **Nytt konstruktorberoende på en ViewModel** → alla dess tester får beroendet.

## Tester mot Firebase-emulatorn

Tester i `tools/db/test/` och instrumenttester mot Firestore körs **endast mot emulatorn**,
via den gemensamma testhjälparen som avbryter om `FIRESTORE_EMULATOR_HOST` saknas och
använder projekt-ID:t `demo-dagboken`. En session med `FIREBASE_SERVICE_ACCOUNT` i
miljön får aldrig köra dem utan emulator (skill `data-safety-backup`).

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
./gradlew :app:lintDebug :app:assembleRelease         # om manifest, resurser, proguard eller byggfiler ändrats
npx --prefix tools/db firebase emulators:exec --only firestore --project demo-dagboken "npm --prefix tools/db test"  # om tools/db/ eller rules ändrats – bara mot emulatorn
node --test '.github/scripts/*.test.mjs'              # dokumentkontroller, alltid
node --test '.claude/hooks/test/*.test.mjs'           # hooktest, alltid
```

**Kör kontrollerna lokalt innan varje push** – en röd CI-runda är det dyraste som finns (skill
`ci-budget`). Gradle kan köras i sessionen när Android SDK finns i `/opt/android-sdk`
(session-start-hooken säger om den finns); Firebase-emulatorn kräver Java 21 och `npm ci` i
`tools/db`. Sätt `LC_ALL=C.UTF-8` (testnamn med å/ä/ö) och kör en Gradle-körning i taget. Bara instrumenttesterna kräver Android-emulator
och körs i Actions (`instrumented.yml`, i PR:er som rör koden de testar). Går ett kommando inte att köra (nät, SDK saknas):
säg det i PR:en. Vid rött CI – låt agenten `ci-doktor` diagnostisera.

## Anti-mönster

- Ta bort, `@Ignore`:a eller försvaga ett rött test för att bli klar.
- Köra om ett test tills det blir grönt.
- Testa implementation (interna anrop) i stället för beteende.
- Skriva om ramens beteende-tester i varje feature i stället för att köra kontraktet.
- Spela in nya referensbilder för att tysta en diff du inte förstår.
- Ny funktion utan test "för att den är liten".
