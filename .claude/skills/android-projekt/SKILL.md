---
name: android-projekt
description: Dagbokens anpassningar av de generella android:-skillsen från pluginen android@claude-android (android-dev, kotlin-coroutines, kotlin-flows, compose-expert, navigation-3, accessibility-compose, android-gradle-logic, firebase-auth, android-intent-security, firebase-security-rules-auditor, mockup). Ladda den alltid tillsammans med en android:-skill – den säger vad som gäller just här och går före pluginens generella råd. Täcker 4.0-ombyggnaden, projektets klasser och mönster, nuvarande avvikelser från pluginen, Gradle-uppsättningen, inloggning med users/{uid}, a11y-detaljer för appens komponenter och mockup-processen med mallens länk. Trigger-ord: Android, Kotlin, ViewModel, Hilt, Compose, coroutine, dispatcher, flöde, StateFlow, händelse, effekt, navigering, flik, Idag, Dagbok, Trender, Mediciner, Health Connect, påminnelser, 3.x-migreringen, tillgänglighet, TalkBack, Gradle, beroende, inloggning, logga in, logga ut, konto, uid, UserSession, EnsureUserUseCase, google-services, SHA-1, manifest, Intent, PendingIntent, firestore.rules, mockup, skiss, ny skärm, ny komponent, Design-canvas.
---

# Android i Dagboken

De generella skillsen kommer från pluginen `android@claude-android` och heter `android:<namn>`.
Pluginen är generell; det som bara gäller Dagboken står här, en sektion per pluginskill.
**CLAUDE.md:s fem regler och repots egna skills går före båda.** Paketträd, datamodell,
byggkonfiguration och etapper står bara i [ARKITEKTUR.md](../../../ARKITEKTUR.md).

## Baslinje (android:android-dev)

- Dagboken 4.0 byggs om på ReseApotekets grund (ADR-001, ARKITEKTUR.md); 3.27.0 ligger på branchen `legacy`. Vid en port: läs förlagan i sin helhet.
- MVVM med `StateFlow<UiState>` och sealed events (TP-3) – byt inte till MVI. Varje skärm har `*ViewModel` + `*UiState`, `@HiltViewModel` på alla.
- Material 3 Expressive via `ui/theme` i designspråket Papper och teal (skill `ui-style`).
- Lagring: Firestore med offline-cache under `users/{uid}` (TP-4), all åtkomst via `FirestoreCollection<T>` + `DocCodec<T>` (skill `firestore-data-layer`); repositories är tunna fasader under `UserSession`. Room används **bara** av den skrivskyddade 3.x-läsaren i `data/legacy` (OMB-2). DataStore bara för enhetslokalt tillstånd i filen `dagboken_device`, aldrig i 3.x-filen `dagboken_prefs`.
- `:core` (ren Kotlin/JVM: modeller, codecs, motorer, diagrammatematik, 3.x-konverteraren) + `:app`. Utseende och gemensamt beteende bara i `ui/theme`, `ui/components`, `ui/diagram`, `ui/common`; Firestore bara i `data/firestore`; Health Connect bara i `data/health` (TP-10, read-only, persisteras aldrig – HLS-5).
- Fel mappas en gång till `DataError` med `suspendRunCatching` (`data/common/`): `FirestoreCollection` för data, `AuthRepository` för inloggning. Repositories skickar `Result<T>` vidare; skärmar visar felet med `DataError.toMessage()`; `DataError.Cancelled` är tyst.
- Ramarna (regel 4): listskärmar `EntityListScreen` + `asListUiState()`, redigering `EntityEditScreen` + `EditorState<T>` – aldrig egna varianter.
- Navigation 3 (TP-2), en Activity (`MainActivity`), back stacken är vanligt state i `navigation/`, övergångar en gång i `navigation/Transitions`.
- Bakgrund: WorkManager (Hilt-worker) bara där något måste köras utan öppen app (TP-7); ingen backup-worker – Firestore synkar själv, veckobackupen körs i GitHub Actions (`tools/db`, BCK-12) och JSON-export finns i appen (BCK-13). 3.x Drive-backup är pensionerad; Drive läses bara av legacy-importen (BCK-14). Påminnelser via AlarmManager (TP-8, skill `notifications-alarms`).
- Nav-argument tas redan med assisted injection (`@HiltViewModel(assistedFactory = …)`), som pluginen säger.
- **Nuvarande läge, avviker från pluginen:** targetSdk 35 (pluginen säger 36; compileSdk är 37, TP-1), `material3` pinnad på en alpha (`1.5.0-alpha29`, skälet i katalogen: Expressive är internal i stabila 1.4.0), inga `ImmutableList` i `UiState` (`ListUiState.Content(items: List<T>)`), ingen Spotless/ktlint, ingen Renovate eller Dependabot, ingen Baseline Profile. Tas i egna PR:er senare; ny kod följer pluginen där det går.
- Java 17 utan toolchain (samma som CI). Tester JVM först (JUnit, Turbine, Robolectric, Roborazzi), fakes byggda på `FakeCollection<T>` (skill `testing-strategy`).

## Coroutines (android:kotlin-coroutines)

- Firestore är redan main-safe: `await()` och `snapshots()` blockerar inte – ingen `withContext` runt dem. `FirestoreCollection` är enda stället som anropar Firestore och hanterar fel och `CancellationException`.
- `suspendRunCatching` finns en gång i `data/common/`; skriv ingen egen variant.
- `:core` är ren och synkron – inga coroutines eller dispatchers där.
- `di/AppModule` ger `@IoDispatcher`, `@DefaultDispatcher` och applikationsscopet; de injiceras bl.a. i `UserFile`, `ExportUseCase` och recept- och vid behov-formulären.
- ViewModel-tester använder den delade `MainDispatcherRule` (`testing/`).
- **Nuvarande läge, avviker från pluginen:** `Dispatchers.` nämns i dag även utanför `di/` – `data/medicines/MedicineRepository.kt` (`withContext(Dispatchers.IO)`, två ställen), `data/firestore/FirestoreInstance.kt` (`clear()`, `withContext(Dispatchers.IO)`) och `AboutViewModel` i `ui/settings/AboutScreen.kt` (`flowOn(Dispatchers.IO)`). Repot ska migrera enligt pluginen (dispatcher injiceras bara där kod väljer en, och bara `di/` nämner `Dispatchers`) i en egen PR senare; ny kod följer pluginen.

## Flöden (android:kotlin-flows)

- `AuthRepository.authState: Flow<AuthUser?>` – `callbackFlow` på `FirebaseAuth.AuthStateListener` i `GoogleAuthRepository`.
- `UserSession.uid: StateFlow<String?>` och `schemaVersion` – `flatMapLatest` på uid när användaren byts; utloggad → samlingarna sänder inget.
- `FirestoreCollection<T>.observe(): Flow<List<T>>` – `snapshots()` via `DocCodec<T>`, enda källan till dataflöden (ersätter 3.x Room-DAO-flödena); repositories exponerar dem oförändrade eller kombinerade. Äldre dagar läses i historikläget (`EntityCollection.observeHistory`, NFR-24).
- `SyncStatus.syncing` (`hasPendingWrites`) driver appens enda "synkas…"-indikator.
- `Flow<List<T>>.asListUiState()` – gemensam omvandling till `ListUiState<T>` för alla listskärmar; skriv aldrig en egen variant.
- Inställningar kommer från Firestore-dokumentet `settings` (tema, påminnelser, profil) via sitt repository; DataStore håller bara enhetslokalt tillstånd (TP-4).
- ViewModels: `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), …)`.
- **Nuvarande läge, avviker från pluginen:** fel är redan state med kvittens (t.ex. `AuthEvent.ErrorShown`), men redigeringsramens engångseffekter går via `Channel` – `EditorState.effects` (`Channel(BUFFERED).receiveAsFlow()`, konsumeras av `EntityEditScreen`). Repot ska migrera till händelser i `UiState` enligt pluginen (Googles råd) i en egen PR senare; tills dess följer skärmar ramens nuvarande mönster.

## Compose (android:compose-expert)

- Ordning: skill `shared-ui-components` (regel 4) går före allt, sedan `ui-style` (Papper och teal), skill `diagram` för diagram. Skärmdumpar och kontraktstester: `testing-strategy`.
- Tema: `MaterialExpressiveTheme`, `MotionScheme.expressive()`, `AppColors`, `AppTypography`, `AppShapes`, `Spacing`. 3.x-paletten "Sunrise Garden" finns inte längre.
- Expressive- och Nav3-API:er används bara med namn och signaturer som `VerifiedApisTest` (`app/src/test`) bevisar.
- Inga bildbibliotek i UI:t utom kontots foto (Coil utan diskcache, skill `data-privacy-security`); ikoner är linjeikoner i `res/drawable`.
- Skärmstruktur: `XScreen(vm = hiltViewModel())` samlar state och lämnar till ramen, t.ex. `EntityListScreen(title, state = state.list, row = { PausableRow(…) }, add = AddAction(label, onClick), key = …)` med `vm.onEvent(PrescriptionsEvent.Add)`.
- Fel, laddning och knappar: `DataError.toMessage()` i snackbar, `AppLoading`, `AppButton(loading = …)`. Sektionskort: `AppCard` + `SectionHeader`, ihopfällbara med `Foldout` (NFR-18). Postkort med `DagbokenEntryCard` enligt NFR-15/16, listrader enligt NFR-17.

## Navigation 3 (android:navigation-3)

Googles referens är generell. Här gäller vår `AppBackStack` (`navigation/AppBackStack.kt`) med en
stack per flik (Idag · Dagbok · Trender · Mediciner, NAV-8), nycklarna i `navigation/AppKeys.kt`,
övergångarna i `navigation/Transitions` och Hilt via `hiltViewModel`. Inställningsarket öppnas
från avataren på alla flikar (NAV-9); migreringsskärmen kan vara startdestination (NAV-6).

## Tillgänglighet (android:accessibility-compose)

Krav NFR-14 (talbar sammanfattning för diagram, tryckytor ≥ 48 dp även inuti sammansatta kontroller), DSN-3.

- Små ikoner får tryckyta via `AppIconButton` (inne i `ui/components`: `Modifier.minimumInteractiveComponentSize()`, `TouchTargets`).
- Dosens kryssrad: `stateDescription` "tagen"/"ej tagen". Listrader och ihopfällbara sektionskort följer NFR-17/18 (hela raden är tryckytan, chevronen är en ren indikator).
- `QuantityStepper`: − och + är själva 48 dp. Varje `StatPill` läses som en enhet (värde och etikett); en klickbar `StatPill` (HLS-14) och statusbannerns knappar är minst 48 dp; varje trendrad på Idag har egen talbar sammanfattning.
- Sliders och graderingar (`ValueSlider`, `SymptomLogCard`) kommunicerar värde och etikett – utöka den delade komponenten om semantiken saknas, aldrig per anropare.
- Energifärg (`EnergyLabel` visar alltid etiketten bredvid), "försenat"-status och solgul "gör något" kompletteras alltid med text eller ikon.
- `ComponentGallery` har en Roborazzi-skärmdump med `fontScale = 1.3` (`ComponentGalleryTest`).

## Gradle (android:android-gradle-logic)

Versioner och SDK-nivåer: KRAVLISTA TP-1 (minSdk 30, targetSdk 35, compileSdk 37; AGP 9, Kotlin 2.4,
Gradle 9) och ARKITEKTUR.md → "Byggkonfiguration". En höjning som ändrar SDK-nivåer uppdaterar TP-1 i samma PR.

| Modul | Plugins | Regel |
|---|---|---|
| `:core` | `kotlin("jvm")`, `kotlin("plugin.serialization")` | Modeller, codecs, motorer, diagrammatematik och 3.x-konverteraren (`BackupJson`). Test `junit`, `kotlin-test`. |
| `:app` | `android.application` (AGP 9, inbyggd Kotlin), `kotlin.compose`, `ksp`, `hilt`, `kotlin.serialization`, `google-services` (bara när `app/google-services.json` finns), `roborazzi` | `implementation(project(":core"))`. Material 3 Expressive, Navigation 3, Firestore, Hilt, WorkManager, Health Connect (read-only). Room bara i `data/legacy`. |

- Gemensam konfiguration (Java 17 utan toolchain, kompilatorflaggor, testtimeout, testloggning) en gång i rotens `build.gradle.kts` (`subprojects {}` + `plugins.withId {}`).
- `material3` är pinnad utanför Compose BOM för Expressive, bevisat av `VerifiedApisTest`; skälet står i katalogen.
- `androidTest`-versioner som krockar höjs i `constraints {}` i `app/build.gradle.kts` med kommentar.
- `cpdCheck` (PMD CPD via CLI som `JavaExec`) över `core/src`, `app/src/main`, `app/src/test`, `app/src/sharedTest`, `app/src/androidTest`, kopplad till `check`. `gradle.properties`: caching, configuration cache, parallel (skill `ci-budget`).
- `app/google-services.json` är **incheckad** (klientkonfiguration för `dagboken-711d2`, ingen hemlighet); CI behöver ingen hemlighet för den. Utan filen ger `app/src/authStub/res` `default_web_client_id` och en Firebase-konfiguration för `demo-dagboken`, så att appen startar.
- Nytt beroende: byggtidseffekten anges i PR:en (skill `ci-budget`).

## Inloggning (android:firebase-auth)

Inloggning är **obligatorisk** (AUTH-6, TP-5; AUTH-5 är struket): inget data läses utloggat, rules
bygger på `request.auth.uid == uid`. Inget hushåll och ingen delning – ett konto, en dagbok.

- `AuthViewModel.state.gate`: `Loading | SignedOut | NeedsUser | UpdateRequired | Ready`; `AppRoot` visar `SignInScreen` tills det finns en användare.
- `AuthRepository` (`data/auth/`), enda implementationen `GoogleAuthRepository` (bunden i `di/AuthModule`), fel mappas i `data/auth/AuthErrors.kt`; tester använder en fake. `AuthUser(uid, name?, email?, photoUrl?)` – namn, e-post och foto visas i avataren och inställningsarket (AUTH-3, SET-7) och hålls bara i minnet.
- Uppstarten: när `authState` har en användare och `users/{uid}` saknas på servern skapar `EnsureUserUseCase` dokumentet via `UserDirectory.createIfMissing` (bara servern, atomärt, med `schemaVersion` + `createdAt`; Firestore-implementation `FirestoreUserDirectory`). `UserSession` (`data/user/`, implementerar `UserScope`) följer uid.
- En knapp, ett event `AuthEvent.SignIn(context)`: utloggad → logga in; inloggad utan `users/{uid}` → `gate = NeedsUser`, samma knapp försöker igen. `gate = Loading` tills `schemaVersion` är känd; nyare än appen → `UpdateRequiredScreen` (BCK-15). Fel visas en gång via `AppSnackbarHost`, kvitteras med `AuthEvent.ErrorShown`; `Cancelled` är tyst (AUTH-4).
- Första start av 4.0 med 3.x-data: inloggning först, sedan migreringsskärmen (OMB-2, OMB-5) som skriver under `users/{uid}`.
- Utloggning bara via `SignOutUseCase` (inställningsarket → Konto, bakom `ConfirmDialog`, anropas av `AuthViewModel`): `LocalCacheCleaner.awaitPendingWrites(3 s)`, `AuthRepository.signOut()`, och bara om allt synkat `LocalCacheCleaner.clear()` (`FirestoreInstance`: `terminate()` + `clearPersistence()`); `HistoryCoverage` glöms före och efter. Larmen avbokas och notiserna stängs (AUTH-6, skill `notifications-alarms`). Tester: `SignOutUseCaseTest`, `AuthViewModelTest` med `FakeLocalCacheCleaner` (`testing/UserFixture.kt`), `FirestoreLocalCacheTest` mot emulatorn.
- Workers och larmmottagare kontrollerar inloggad användare innan de rör data; utloggade gör de inget.
- Debug-SHA-1 för `dagboken-711d2`: `50:5B:DC:3B:C9:49:F5:96:91:84:F2:23:0F:D1:BE:25:1B:10:7E:59`; release-nyckelns SHA-1 ska också vara registrerad (README → Firebase-setup).

## Intent-säkerhet (android:android-intent-security)

Används vid manifest, exporterade komponenter, extras, `PendingIntent`, notiser och delning.
Här gäller skill `data-privacy-security` (hälsodata, notiser privata på låsskärmen) och skill `notifications-alarms`: påminnelsernas mottagare är inte exporterade
och nås bara via appens egna explicita `FLAG_IMMUTABLE`-PendingIntents (TP-9), actions och extras
en gång i `reminders/ReminderIntents.kt`. Bara startskärmen exporteras (`ComponentExportTest`). Appen har i dag ingen `FileProvider`;
delas filer någon gång ska det ske via en.

## Security rules (android:firebase-security-rules-auditor)

Varje ändring i `firestore.rules` granskas med den. Här gäller skill `firestore-data-layer`
(regler under `users/{uid}`, bara ägaren `request.auth.uid == uid`, nekade okända samlingar,
`schemaVersion` sänks aldrig – TP-12, tolerans för okända fält enligt BCK-9) och skill
`data-safety-backup`; rules-tester mot emulatorn i `tools/db/test/rules.test.mjs` (skill `testing-strategy`).

## Mockup (android:mockup)

Processen (när, hur, visa och vänta) står i pluginen; det här är Dagbokens delar. Krav: NFR-20.

- **Mallen:** https://claude.ai/artifact/MP2fPHimTk8tAmKkpr3K5t – Design-canvasen från
  ombyggnadsintervjun med struktur (fyra flikar, avatarark, plusmeny), datamodell, känslorna A–I
  och nyckelskärmarna i den valda känslan **I · Papper och teal** (ARKITEKTUR.md → Designspråk).
  Varje mockup startar från den; komponent- och beteendearket läggs till där när katalogen portas.
- **Facit är det byggda:** `ComponentGallery` och skärmdumparna i `app/src/test/screenshots/`.
  Ändras katalogen uppdateras mallen i samma PR som komponenten.
- **Byggs av** katalogen i skill `shared-ui-components`, i designspråket i skill `ui-style`;
  listor och formulär med ramarnas struktur (`EntityListScreen`, `EntityEditScreen`).
- **Data:** riktiga svenska texter och realistisk, påhittad hälsodata – en dag med doser i fyra
  tillfällen, måendeloggar med energi och symptom, en promenad, en pågående förkylning, verkliga
  medicinnamn och doser, en vecka i datumremsan. Aldrig användarens riktiga hälsodata.
- **Finns elementet redan** (katalogen, en byggd skärm eller 3.x-förlagan) → samma komponent och
  beteende; det är ingen designfråga och ställs inte som en.
- **Tillstånd:** för Idag även belöningsläget när dagen är klar (HEM-19, DSN-4).
- **Länken** läggs i issuet under rubriken **Design** (skill `refine-issue`); koden skrivs först
  efter användarens ok (skill `implement-issue`).
- **I PR:en:** Roborazzi-skärmdumpar (ljust + mörkt) av det byggda bredvid mockup-länken
  (`./gradlew :app:recordRoborazziDebug` vid avsiktlig designändring); avvikelser förklaras.
