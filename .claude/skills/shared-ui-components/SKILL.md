---
name: shared-ui-components
description: Dagbokens återbruksregel (regel 4, NFR-9) — samma typ av element ser likadant ut och beter sig likadant överallt, och varje sak finns på ett ställe. Enda källan för komponent-, ram- och byggstenstabellerna och förbudslistan. Ladda denna ALLTID när du bygger eller ändrar UI, en skärm, en komponent, ett interaktionsmönster, en ViewModel med formulär eller lista, ett repository, en beräkning eller när du ser liknande kod på två ställen. Trigger-ord: composable, komponent, UI, skärm, vy, knapp, kort, postkort, sektionskort, lista, listrad, kryss, fält, slider, reglage, diagram, graf, datum, tid, väljare, dialog, bottom sheet, ark, snackbar, tomt tillstånd, laddning, arkivera, ångra, radera, bekräfta, redigera, spara, validering, återbruk, dubbelkod, duplicering, kopiera, likadant, enhetligt, konsekvent, beteende, mönster, ram, EntityListScreen, EntityEditScreen, EditorState, FirestoreCollection, UiConsistencyTest, cpdCheck, allowlist.
---

# Enhetligt och utan dubbelkod

**Invariant:** en elementtyp = en komponent, ett interaktionsmönster = en ram, en sorts
logik = ett ställe. Två skärmar som visar eller gör samma sak ska se ut och bete sig
identiskt. Skillnader uttrycks **bara** med parametrar på den delade delen.

Denna skill är **enda källan** för tabellerna nedan. `CLAUDE.md`, andra skills och
agenter länkar hit – de kopierar dem inte. Tabellerna under **Utseende** och **Beteende** läses
av `UiConsistencyTest` och jämförs med de publika composables i `ui/components` (se
*Dokumentdrift*). Håll formatet: en rad per komponent, komponentnamnet i backticks först i
kolumn två. Komponenter som ännu inte är byggda står **bara** i *Planerade komponenter*.

> Katalogen kommer från ReseApoteket (ARKITEKTUR.md → Komponentkatalog) och syns i
> `ComponentGallery`. Utseendet följer Papper och teal (skill `ui-style`); kort- och radstandarden
> för postkort, listrader och sektionskort är **NFR-15–18** och går före allt nedan.
> De exakta API:erna bakom kolumnen *Bygger på* är verifierade i `VerifiedApisTest` (`app/src/test`) – gissa aldrig ett API-namn.

## Utseende: elementtyp → enda tillåtna komponent

| Elementtyp | Komponent | Bygger på |
|---|---|---|
| Knapp (primär/sekundär/text/destruktiv) | `AppButton` | M3-knappar, `variant`- och `compact`-parameter |
| Ikonknapp | `AppIconButton` | M3 ikonknapp, min 48 dp |
| Split-knapp "Lägg till ▾" | `AddSplitButton` | Expressive split-knapp |
| Sektionskort / grupperad yta | `AppCard` | `AppShapes.card` (22 dp), vit yta utan kantlinje, `Spacing`; `tone` färgar ytan (t.ex. grönt i belöningsläget) |
| Listrad | `ItemRow` | bas för alla listor; `navigates` ger pil, `done` avbockad stil, `accent` statusfärg (NFR-16), `inactive` nedtonad, `below` en rad under, `titleHighlight` färgmarkerar en del av titeln |
| Pausbar rad (recept, vid behov-medicin) | `PausableRow` | `ItemRow` + typ som `InfoPill`; pausad nedtonad med "Pausat" |
| Kryssrad | `CheckRow` | `ItemRow` + formmorfande kryss; hela raden växlar (NFR-17); med `onClick` är krysset en egen knapp och raden öppnar detaljer |
| Sektionsrubrik med räknare | `SectionHeader` | `InfoPill` för räknaren |
| Gruppetikett över kort | `GroupLabel` | liten text, läses som rubrik |
| Formulärfält med etikett (val, väljare) | `LabeledGroup` | `GroupLabel` ovanför innehållet, valfri hjälptext och fältfel under |
| Fel under ett fält eller i en panel | `FieldError` | felfärg, läses som fel; ett skrivfel i ett bottom sheet visas via `AppBottomSheet(error)` |
| Skärmrubrik | `AppTopBar` | Expressive flexibel toppbar, `size`-parameter, Fraunces-rubrik; visar synkindikatorn före `actions` när ändringar väntar |
| Toppnivånavigering | `AppFloatingToolbar` | Expressive flytande verktygsrad med flikarna Idag · Dagbok · Trender · Mediciner och plusknappen som `action` (NAV-8, NAV-10) |
| Textfält | `AppTextField` | M3 textfält med fel/hjälptext; tak `TextLimits` (samma som rules) |
| Textfält med förslag | `SuggestionField` | `AppTextField` + kort med en `ItemRow` per förslag (matchningen färgmarkerad); antalet läses upp |
| Etikett/pill | `InfoPill` | `AppShapes.pill`; `icon` före texten, `onClick` gör den till en knapp med 48 dp tryckyta |
| Valchip | `AppFilterChip` | M3 filterchip |
| Ett val bland några | `ChoiceChips` | `ChipRow` + en `AppFilterChip` per alternativ |
| Exempel att börja från (snabbval, tom lista) | `ExampleChips` | `ChipRow` + `AppFilterChip` med plus; det valda med bock |
| Emojival | `EmojiPicker` | rutnät av runda val (`ChoiceGrid`) |
| Färgval | `ColorSwatchPicker` | rutnät av runda val (`ChoiceGrid`) över `AppColors`; TalkBack läser färgens namn |
| Antal ± | `QuantityStepper` | M3 ikonknappar i en pill, `label` för TalkBack |
| Laddning | `AppLoading` | Expressive laddningsindikator |
| Tomt tillstånd / läsfel | `EmptyState` | ikon + text + `AppButton`; `isError` för läsfel (internt `LoadErrorState` med "Försök igen") |
| Bekräftelse | `ConfirmDialog` | M3 dialog, `destructive`-parameter |
| Bottom sheet | `AppBottomSheet` | M3 modal sheet, `AppShapes.sheet` (28 dp upptill) |
| Meddelande | `AppSnackbarHost` | M3 snackbar; ett pågående meddelande ersätts aldrig av ett annat; fel via internt `ErrorSnackbar` |
| Firande | `Confetti` | egen `Canvas`, inget bibliotek; faller en gång (HEM-19) |
| Avdelare | `AppDivider` | M3-avdelare med temats färg och tjocklek |
| Inställningsrad med växel | `SwitchRow` | `ItemRow` + M3-växel; hela raden växlar (NFR-17); med `onClick` är växeln en egen kontroll och raden öppnar detaljer |
| Segmentval (t.ex. tema) | `AppSegmentedChoice` | M3 segmentknappar |
| Meny (kontextmeny, `⋮`) | `AppMenu` | M3 rullgardinsmeny; ordning Redigera, kontextspecifikt, Ta bort sist i felfärg, ikon på varje post (NFR-16) |
| Datumfält | `DateField` | internt `PickerField` (samma yta som `AppTextField`) + M3 datumväljare |
| Tidsfält | `TimeField` | samma yta som `DateField` + M3 tidsväljare (24 timmar) |
| Horisontell rad av chips | `ChipRow` | horisontell lista av `AppFilterChip` |

## Beteende: interaktionsmönster → enda tillåtna ram

| Mönster | Ram | Beteende (identiskt överallt) |
|---|---|---|
| Listskärm | `EntityListScreen` | Arkivering via `archive: ListArchive` ("Visa arkiverade" i menyn, Ångra, fel som meddelande); laddning → `AppLoading`; tomt → `EmptyState` med primärknapp och valfria exempel (`ExampleChips`); fel → läsfel med "Försök igen"; rader (`ItemRow`) i kort; valfri gruppering (`SectionHeader` med antal, `collapsible` = hopfälld) och undergrupper; lägg till (`AddSplitButton`); för en undersida `onBack`, `subtitle`, `actions` och `header` överst i listan |
| Redigeraskärm | `EntityEditScreen` | Tillsammans med `EditorState<T>`: "Spara" aktiv först när giltig **och** ändrad (NFR-10); fältfel visas när fältet ändrats eller efter ett sparförsök; läsfel → "Försök igen"; bakåt med osparat → "Släng ändringar?"; sparfel → snackbar; navigerar först när sparandet är klart (NFR-12); IME-inset hanteras i ramen (NFR-11); arkivera/återställ/radera i menyn |
| Detaljskärm | `EntityDetailScreen` | Tillsammans med `DetailUiState`/`DetailLoader`: toppbar med tillbaka, "Redigera" och meny; laddning → `AppLoading`; fel → läsfel med "Försök igen"; innehåll → huvud och sektioner i `AppCard` med `SectionHeader` (t.ex. sjukdomsepisoden med incheckningar, HIST-9) |
| Arkivera/dölj listobjekt | `SwipeToHide` | Svep → dolt direkt → `UndoSnackbar` "%s arkiverad · Ångra" i 5 s; `enabled = false` för en redan arkiverad rad. Används för listobjekt som alternativen i Listor – **inte** för postkort: postkortets svep begär radering med `ConfirmDialog` (NFR-15, byggs med postkortet i etapp 4) |
| Ångra | `UndoSnackbar` | Enda ångra-mekanismen |
| Permanent radering | `ConfirmDialog` | `destructive = true`; alltid bekräftelse – även efter svep på ett postkort, som fjädrar tillbaka tills dialogen svarat (NFR-15) |
| Komponentöversikt | `ComponentGallery` | Debug-skärm med alla komponenter och ramar i alla tillstånd |

Gemensamt för ramarna: felmeddelanden via `DataError.toMessage()`, skärmbyten via
`navigation/Transitions`, återkommande texter ("Spara", "Släng ändringar?", "Ångra",
"Försök igen") en gång i `strings.xml`.

## Planerade komponenter (etapp 4)

Läses **inte** av `UiConsistencyTest` förrän raden flyttas upp till *Utseende* i PR:en som bygger
komponenten (med mockup, test, skärmdump ljust + mörkt och plats i `ComponentGallery`).
**portas i etapp 4** = Dagbokens 3.x-komponent flyttas in i katalogen; **ny** = NY KOMPONENT i
canvasen (ARKITEKTUR.md → Komponentkatalog).

| Elementtyp | Komponent | Status och innehåll |
|---|---|---|
| Postkort (sparad post: dos, aktivitet, mående, händelse, incheckning, episod, recept) | `DagbokenEntryCard` | portas i etapp 4 – gestmönster och trailing-ordning enligt NFR-15/16 |
| Ihopfällbart sektionskort | `Foldout` | portas i etapp 4 – hela titelraden växlar, roterande chevron (NFR-18) |
| Sifferreglage | `SliderRow` | portas i etapp 4 – standard för alla reglage 0–10 |
| Reglage med färggradient (energi −10…10) | `GradientSliderRow` | portas i etapp 4 – energiskalan ur `ui-style` |
| Hjulväljare | `WheelPicker` | portas i etapp 4 |
| Kalender | `DagbokenCalendar` | portas i etapp 4 – Dagbokens kalendervy |
| Mående i steg | `StepwiseScreeningForm` | portas i etapp 4 – "Logga nu" som ark |
| Symptom med gradering | `SymptomLogCard` | portas i etapp 4 – samma i aktivitet, mående och incheckning (SJ-3) |
| Anteckning | `NoteField` | portas i etapp 4 – fältet `note` (DAT-7) |
| Mätvärde | `StatPill` | portas i etapp 4 – Hälsa idag (HLS-6) |
| Datum + tid | `DateTimeRow` | portas i etapp 4 – bygger på `DateField`/`TimeField` |
| Tidsåtgång | `DurationRow` | portas i etapp 4 |
| Påminnelsetid | `ReminderTimeRow` | portas i etapp 4 |
| Linjediagram | `LineChartCanvas` | portas i etapp 4 – hela `ui/diagram`; matematiken till `:core` |
| Intervallstapel | `IntervalBarChart` | portas i etapp 4 |
| Staplat stapeldiagram | `StackedBarChart` | portas i etapp 4 |
| Y-axel | `SmartYAxis` | portas i etapp 4 – `computeSmartYAxis` till `:core` |
| Trendlinje | `TrendLine` | portas i etapp 4 – `computeTrendLine` till `:core` |
| Min/max-text | `MinMaxCaption` | portas i etapp 4 |
| Kompakt rullgardin i diagram | `CompactDropdownButton` | portas i etapp 4 – bygger på `AppMenu` |
| Diagrammets talbara sammanfattning | `ChartSemantics` | portas i etapp 4 – NFR-14 |
| Minidiagram (7-dagarstrend) | `SparklineChart` | portas i etapp 4 |
| Datumremsa | `DateStrip` | ny – vecka med punkter för dagar med poster, idag-chip 18 dp |
| Tillfällesrad för mående | `OccasionRow` | ny – frukost · lunch · middag · kväll |
| Framstegsrad | `ProgressBar` | ny – fylls animerat, solgul när dagen är klar |
| Dagen klar | `DayDoneCard` | ny – sammanfattningskortet i belöningsläget (HEM-19) |
| Diagramgrupper | `ChartGroupTabs` | ny – Mående · Klocka · Jämför (TRD-20) |
| Inställningsark | `SettingsSheet` | ny – bakom avataren (NAV-9) |
| Plusknappens loggmeny | `LogMenu` | ny – exakt fem val (NAV-10) |

## Kod: delade byggstenar i andra lager

| Behov | Enda stället |
|---|---|
| Firestore-CRUD | `EntityCollection<T>` från `CollectionFactory` (`data/common/`); enda implementationen `FirestoreCollection<T>` i `data/firestore/`, i test `FakeCollection<T>` |
| Sökvägar, codecs per samling | `Paths` och `CollectionTable` i `data/firestore/` (speglade i `tools/db/lib/collections.mjs`) |
| Map ↔ modell | `DocCodec<T>` + fälthjälpare (`Fields`) i `:core` |
| Formulär | `EditorState<T>` + `Validator<T>` i `ui/common/` (fel per fält visas efter ändring eller sparförsök; `EditorEffect` till ramen) |
| Listtillstånd | `Flow<List<T>>.asListUiState()` → `ListUiState<T>`, och `ListLoader` (med "Försök igen") i `ui/common/` |
| Svep-arkivera, Ångra, "Visa arkiverade" i en lista | `ArchiveActions` + `ArchiveEvent` i `ui/common/`; skärmen skickar `archive.collectAsListArchive()` till `EntityListScreen` |
| Detaljtillstånd | `DetailUiState<T>` och `DetailLoader` (med "Försök igen") i `ui/common/` |
| Datum i UI | `DateFormat` i `ui/common/` ("lör 4 okt 2026", millis för datumväljaren) |
| Visa först efter en fördröjning | `Flow<Boolean>.shownAfter(delay)` i `ui/common/` |
| Navigation | `AppKey`, `AppBackStack` (en stack per flik), `AppNavHost`, `Transitions` i `navigation/` |
| Feltyp | `DataError` (`Offline`, `PermissionDenied`, `Cancelled`, `UpdateRequired`, `SignInRejected`, `NotSignedIn`, `NotFound`, `Unknown`) i `data/common/` |
| Fel till text | `DataError.toMessage()` i `ui/common/` |
| Lista + data per nyckel ur den | `combineByKey` i `data/common/` |
| Tillägg som inte får fälla skärmen | `withFallback(värde)` i `data/common/FlowRetry.kt` |
| Ordning för något nytt | `upsertPlaced`/`nextSortOrder` i `data/common/` – ur cachen, väntar inte på nätet |
| Resultat av suspend-anrop | `suspendRunCatching` i `data/common/` (används av `FirestoreCollection` och `AuthRepository`) |
| Läs- och skrivregler för dokument | `prepareForWrite`, `readDocument`, `sortForList` i `data/common/DocumentRules.kt` och `writeBlocker` i `UserScope.kt` – används av både `FirestoreCollection` och `FakeCollection` |
| Firestore-instans | `FirestoreModule` (Hilt) i `data/firestore/` |
| Inloggad användare | `UserSession` i `data/user/` (`UserScope`), `EnsureUserUseCase` vid inloggning |
| Synkläge | `SyncStatus` i `data/common/`; för UI:t `SyncViewModel` (`ui/sync/`) |
| Export | `RawDocuments` + `ExportFormat` (`:core`) – samma format som `tools/db export` (skill `firestore-data-layer`) |
| 3.x → 4.0 | `legacy/BackupJsonConverter` i `:core` *(etapp 2)* – enda mappningen, även för legacy-läsaren (skill `data-safety-backup`) |
| Doser, kylperiod, periodslut | `EnsureDoses`, `Cooldown`, `PeriodEndings` i `:core/engine` *(etapp 5)* |
| Dagens energi, sömnkvalitet, diagrammatematik | `DailyEnergyStats`, `SleepQuality`, `computeSmartYAxis`, `computeTrendLine` i `:core/engine` *(etapp 4)* |
| Påminnelser | `reminders/` i appen (skill `notifications-alarms`) – läser ur Firestore-cachen via repositories |
| Test-fakes och hjälpare | se skill `testing-strategy` |
| `tools/db`-logik | `tools/db/lib/` (admin, credentials, collections, walk, serialize, backup, query, migrate, cli) |
| CI-steg | composite actions i `.github/actions/` |
| Byggkonfiguration | ett ställe (skill `android-gradle-logic`) |

## Förbjudet

Kontrolleras av `UiConsistencyTest` (Konsist) i bygget och av hooken `regel4-check`
direkt efter varje filändring. De exakta mönstren står **bara** i
`app/src/test/resources/ui-forbidden.txt` (enda källan, `mönster<TAB>ersättning`), och
undantag bara i `ui-allowlist.txt`. Att hooken och testet tolkar filen likadant bevisas av
`ui-forbidden-examples.txt`, som båda kör – ändras ett mönster, lägg till ett exempel där. I korthet:

- **Feature-kod** (Kotlin i ett ui-paket utom `ui/components`, `ui/theme`, `ui/common` –
  även filer direkt under `ui/`):
  - Material 3-komponenter och layoutelement som har en motsvarighet i tabellerna ovan –
    t.ex. kort, knappar, kryss, växlar, reglage, fält, menyer, dialoger, sheets, avdelare, chips,
    progress, snackbar, `LazyColumn`/`LazyRow`, `Scaffold`, `SwipeToDismissBox`.
  - Hårdkodat utseende: färger, former, textstilar (även `MaterialTheme.typography` – använd
    `AppTypography`) och literala dp för storlek och padding.
  - **Tillåtet:** `Text` med en `AppTypography`-stil, `Icon` (appens ikoner i `res/drawable`),
    `Column`/`Row`/`Box` och `Spacer` med `Spacing`.
- **All kod utom `ui/theme/` och `ui/components/`:** `ExperimentalMaterial3ExpressiveApi` (opt-in
  för Expressive). Behövs ett Expressive-API i en skärm → bygg eller utöka en delad komponent.
- **All kod utom `data/firestore/`** – även `di/` och `ui/`: `FirebaseFirestore`,
  `CollectionReference`, `DocumentReference`. Därför ligger `FirestoreModule` i `data/firestore/`.
- **ViewModels** som heter `*EditViewModel` och inte exponerar en `EditorState`.
- **I `ui/components/`:** publik komponent utan skärmdump (ljust + mörkt) eller utan plats i
  `ComponentGallery`.
- **Undantag:** en rad i `ui-allowlist.txt` (`fil:symbol – motivering`). Undantaget gäller bara
  träffar vars matchade text överlappar symbolen – andra förbjudna mönster på samma rad stoppas
  ändå. En rad utan symbol eller motivering fäller hooken och bygget. Filen är tom som
  standard. Varje ny rad kräver användarens uttryckliga ok i PR:en.

## Dokumentdrift

`UiConsistencyTest` läser tabellerna under **Utseende** och **Beteende** i denna fil och jämför
med de publika `@Composable`-funktionerna i `ui.components`:
- komponent i koden men inte i tabellen → bygget faller ("lägg till raden i shared-ui-components");
- komponent i tabellen men inte i koden → bygget faller ("ta bort raden eller bygg komponenten").

*Planerade komponenter* jämförs inte. En ändring av denna fil kör därför app-testerna i CI
(skill `ci-budget`).

## `cpdCheck` (copy-paste-detektor)

PMD CPD för Kotlin över `core/src`, `app/src/main`, `app/src/test`, `app/src/sharedTest` och
`app/src/androidTest`, tröskel ca 80 tokens. Fäller bygget. En träff betyder: bryt ut till en av
byggstenarna ovan eller utöka en ram. Tröskeln höjs inte för att "det bara är lite likt".

## Arbetsflöde

1. Slå upp elementtypen, mönstret eller behovet i tabellerna. Finns det → använd det.
2. Räcker det inte → **utöka** med en parameter vars default bevarar nuvarande utseende
   och beteende. Uppdatera bara de skärmdumpar som avsiktligt ändras.
3. Något nytt behövs → ny delad del i rätt paket, rad i tabellen, test, skärmdump, plats i
   `ComponentGallery` – och visad i mockup först (skill `mockup`). En port från 3.x flyttar
   raden från *Planerade komponenter* till *Utseende*.
4. Delade delar utökas i en egen commit **före** featuren som behöver dem.
5. Två ställen gör nästan samma sak → bryt ut innan det tredje kommer.
6. En ändring av en elementtyp eller ett mönster görs på ett ställe och slår igenom överallt.

## Anti-mönster

- Lokal `Card { }` eller `Button { }` "bara här".
- Egen `Slider {}` eller egen `Canvas`-graf i en skärm i stället för den delade komponenten.
- Kopierad `CheckRow` med annan färg i en feature-mapp.
- Radering som bekräftas på ett ställe men sker direkt på ett annat.
- Postkort som expanderar vid tryck eller har en åtgärd som bara nås via svep (NFR-15/16).
- Egen `LazyColumn` med eget tomt tillstånd.
- Repository som anropar Firestore direkt.
- Två ViewModels med samma dirty/validera/spara-logik.
- Samma felmeddelande eller text formulerad på två sätt.
- En regeltabell kopierad till en annan fil i stället för en länk hit.
