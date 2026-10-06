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
av `UiConsistencyTest` och jämförs med de publika composables i `ui/components` och `ui/diagram` (se
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
| Listrad | `ItemRow` | bas för alla listor; titel (högst två rader) och undertext (hel) med internt `RowText`, samma som postkortet; `navigates` ger pil, `done` avbockad stil, `accent` statusfärg (NFR-16), `inactive` nedtonad, `below` en rad under, `titleHighlight` färgmarkerar en del av titeln |
| Pausbar rad (recept, vid behov-medicin) | `PausableRow` | `ItemRow` + typ som `InfoPill`; pausad nedtonad med "Pausat" |
| Kryssrad | `CheckRow` | `ItemRow` + formmorfande kryss; hela raden växlar (NFR-17); med `onClick` är krysset en egen knapp och raden öppnar detaljer |
| Favoritmarkering | `FavoriteStar` | `AppIconButton` med ifylld/tom stjärna; TalkBack säger vad tryck gör ("Markera X som favorit"); radens enda inline-direktkontroll (NFR-17) – Listor och vid behov-medicinerna |
| Meddelande som leder vidare (banner) | `NoticeBanner` | `AppCard` i en `Tone` (standard varning) med ikon, text och pil; hela kortet är en knapp med `onClickLabel` ("Öppna") – periodsluten i Mediciner (MEDF-2) |
| Sektionsrubrik med räknare | `SectionHeader` | `InfoPill` för räknaren |
| Gruppetikett över kort | `GroupLabel` | liten text, läses som rubrik; samma stil är etiketten över varje formulärkontroll (`LabeledGroup`, `ValueSlider`) |
| Formulärfält med etikett (val, väljare) | `LabeledGroup` | `GroupLabel` ovanför innehållet, valfri hjälptext och fältfel under |
| Fel under ett fält eller i en panel | `FieldError` | felfärg, läses som fel; ett skrivfel i ett bottom sheet visas via `AppBottomSheet(error)` |
| Skärmrubrik | `AppTopBar` | Expressive flexibel toppbar, `size`-parameter, Fraunces-rubrik; visar synkindikatorn före `actions` när ändringar väntar |
| Toppnivånavigering | `AppFloatingToolbar` | Expressive flytande verktygsrad med flikarna Idag · Dagbok · Trender · Mediciner och plusknappen som `action` (NAV-8, NAV-10) |
| Textfält | `AppTextField` | M3 textfält med fel/hjälptext och valfritt `suffix` (t.ex. enheten på en doshöjning); tak `TextLimits` (samma som rules) |
| Textfält med förslag | `SuggestionField` | `AppTextField` + kort med en `ItemRow` per förslag (matchningen färgmarkerad); antalet läses upp |
| Etikett/pill | `InfoPill` | `AppShapes.pill`; `icon` före texten, `onClick` gör den till en knapp med 48 dp tryckyta |
| Valchip | `AppFilterChip` | M3 filterchip |
| Ett val bland några | `ChoiceChips` | `ChipRow` + en `AppFilterChip` per alternativ |
| Medicinens enhet | `UnitChoice` | `LabeledGroup` ("Enhet") + `ChoiceChips` över `MEDICINE_UNITS`; en lagrad enhet utanför listan står kvar som val (REC-1, FAV-1) |
| Exempel att börja från (snabbval, tom lista) | `ExampleChips` | `ChipRow` + `AppFilterChip` med plus; det valda med bock |
| Emojival | `EmojiPicker` | rutnät av runda val (`ChoiceGrid`, som äger valmarkeringen: ring i teal med luft runt den valda) |
| Färgval | `ColorSwatchPicker` | rutnät av runda val (`ChoiceGrid`, samma valmarkering som `EmojiPicker`) över `AppColors`; TalkBack läser färgens namn |
| Antal ± | `QuantityStepper` | M3 ikonknappar (48 dp, NFR-14) i en pill, `label` för TalkBack; `valueText` visar värdet som text ("4 h", "Ingen spärr") |
| Laddning | `AppLoading` | Expressive laddningsindikator |
| Tomt tillstånd / läsfel | `EmptyState` | ikon + text + `AppButton`; `isError` för läsfel (internt `LoadErrorState` med "Försök igen") |
| Skärm som inte byggts än ("Snart här") | `UpcomingScreen` | `AppTopBar` (stor för en flik med `actions`, liten med tillbakapil när `onBack` finns) + `EmptyState` centrerat ovanför verktygsraden; flikarna (`TabPlaceholderScreen`) och Export och import |
| Bekräftelse | `ConfirmDialog` | M3 dialog, `destructive`-parameter |
| Bottom sheet | `AppBottomSheet` | M3 modal sheet, `AppShapes.sheet` (28 dp upptill) |
| Inställningsark | `AccountSheet` | `AppBottomSheet` + en `ItemRow` per val bakom avataren (NAV-9): kontot överst (tonad `ItemRow` med `AccountAvatar`, namn, e-post, "Inloggad med Google"), en rad per `SettingsPage` med pil, "Komponentgalleri" bara i debug, "Logga ut" sist |
| Plusknappens loggmeny | `LogMenuSheet` | `AppBottomSheet` + en `ItemRow` per `LogChoice` – exakt fem val i ordning (NAV-10) |
| Meddelande | `AppSnackbarHost` | M3 snackbar; ett pågående meddelande ersätts aldrig av ett annat; fel via internt `ErrorSnackbar` |
| Firande | `Confetti` | egen `Canvas`, inget bibliotek; faller en gång (HEM-19) |
| Avdelare | `AppDivider` | M3-avdelare med temats färg och tjocklek |
| Inställningsrad med växel | `SwitchRow` | `ItemRow` + M3-växel; hela raden växlar (NFR-17); med `onClick` är växeln en egen kontroll och raden öppnar detaljer |
| Segmentval (t.ex. tema, diagramgrupperna Mående · Klocka · Jämför i Trender, TRD-19) | `AppSegmentedChoice` | M3 segmentknappar |
| Meny (kontextmeny, `⋮`) | `AppMenu` | M3 rullgardinsmeny; ordning Redigera, kontextspecifikt, Ta bort sist i felfärg, ikon på varje post (NFR-16) |
| Datumfält | `DateField` | internt `PickerField` (samma yta som `AppTextField`) + M3 datumväljare; `emptyLabel` visas nedtonat utan datum ("Periodens slut"), `onClear` ger en rensa-knapp tillbaka till den, `context` läggs till fältets och knappens namn för TalkBack när fältet upprepas ("Startdatum, doshöjning 2"); knappens plats hålls utan datum |
| Tidsfält | `TimeField` | samma yta som `DateField` + M3 tidsväljare (24 timmar) |
| Horisontell rad av chips | `ChipRow` | horisontell lista av `AppFilterChip` |
| Postkort (sparad post: dos, aktivitet, mående, händelse, incheckning, episod, recept) | `DagbokenEntryCard` | `AppCard` + menyn från `AppMenu` + `AppIconButton`; tryck öppnar, långtryck = samma meny som `⋮` (Redigera, `actions`, Radera sist), svep höger→vänster begär radering via `ConfirmDialog` och fjädrar tillbaka (NFR-15); trailing i fast ordning `toggle` (enda direktkontrollen, ett reglage – t.ex. receptets aktiv; titeln står då på hela bredden ovanför, och växeln delas med `SwitchRow`), `status`, anteckningsikon (`note`), chevron (`expandedContent`), `⋮` (NFR-16); `below` (t.ex. pills) under texten; `accent` statusfärg, `inactive` nedtonad (inte reglaget) |
| Ihopfällbart sektionskort | `Foldout` | hela titelraden växlar, minst 48 dp, `Role.Button` + `stateDescription`, fjädrande chevron (NFR-18); `trailing` före chevronen, `summary` i stängt läge; samma titelrad som `EntityListScreen`s hopfällbara grupper |
| Reglage (alla skalor: energi, stress, symptom, −10…+10) | `ValueSlider` | M3 `Slider(state)` med eget spår i energiskalan (`AppColors.extended.energy`): `higherIsBetter` rött→grönt, annars grönt→rött; nolla i mitten under noll; värdet som text och nivån (`scaleLevel`) som `InfoPill` |
| Hjulväljare | `WheelPicker` | lat kolumn som snäpper; TalkBack läser valt värde och kan öka/minska |
| Kalender | `DagbokenCalendar` | månadsrutnät (måndag först) med `AppIconButton` för månadsbyte; punkt = dag med poster, solgul punkt med ring = idag, vald dag fylld teal i `AppShapes.row` – samma markering som `DateStrip` (internt `DayMarkerDot`); TalkBack läser datum, "idag" och "har poster"; varje dag 48 dp (HIST-6) |
| Datumremsa (veckan i Idag) | `DateStrip` | sju chips i `AppShapes.row` (18 dp), vald dag fylld teal; punkt = dag med poster, solgul punkt med mörk ring = idag (internt `DayMarkerDot`, samma som kalendern), bock när dagen är klar (läses "Klar"); framtida dagar tonade och inte valbara; svep höger/vänster (och TalkBack-åtgärder) byter vecka via `onWeekChange`, anroparen äger veckan och `today` (HEM-14) |
| Tillfällesrad för mående | `OccasionRow` | `ItemRow` + `InfoPill` + `AppButton`: Efter frukost · Lunch · Kvällsmat · Läggdags (namnet från anroparen); `OccasionStatus` loggad (avbockad stil, "Loggad", värdechips i `scaleLevel`-ton), "Försenat" (varningston, bara `isToday`, annars "Ej loggad") och "Snart" (solgul) med "Logga nu" (primär `AppButton`, compact), "Kommande" dämpad (HEM-4, HEM-5) |
| Framstegsrad | `ProgressBar` | spår i pill-form (`AppColors.extended.track`, samma som stegprickarna i `StepwiseScreeningForm`) som fylls animerat i teal ("4 av 9 klara"), solgul med mörk kontur och "9 av 9 · allt klart" när allt är klart; TalkBack läser "x av y klara" (HEM-18) |
| Dagen klar | `DayDoneCard` | `AppCard(tone = Tone.Positive)` med `SectionHeader(tone = Tone.Positive)` (bock, "Allt klart för idag") och tre nyckeltal (snittenergi, mot igår, dagar i rad; `null` → "—", samma `value_missing` som `StatPill`) + `Confetti` en gång per `play` som anroparen äger (HEM-19) |
| Kontoavatar | `AccountAvatar` | foto-slot eller `photoUrl` (Coil, bara minnescache – AUTH-3) ovanpå initialerna ur namnet, utloggad person-ikon; 48 dp, läses "Konto och inställningar"; i `AppTopBar(actions)` (NAV-9); utan `onClick` en ren bild (kontokortet i arket) |
| Mående i steg | `StepwiseScreeningForm` | pager energi → stress → symptom med "Steg 1 av 3", fjädrande stegprickar och `AppButton` (HEM-5) |
| Symptom med gradering | `SymptomLogCard` | `AppCard` + `Foldout`: `AppFilterChip` per symptom, ett `ValueSlider` (högre är sämre) per valt, `AppTextField` för "Övrigt", summan under (AKT-6, SJ-3) |
| Anteckning | `NoteField` | `Foldout` med textens början i stängt läge och ett flerradigt `AppTextField` (DAT-7) |
| Mätvärde | `StatPill` | ikon, värde och etikett i en `Tone`-yta; `onClick` gör den till en knapp med 48 dp (HLS-6) |
| Datum + tid | `DateTimeRow` | `DateField` + `TimeField` på en rad |
| Tidsåtgång | `DurationRow` | `LabeledGroup` + `QuantityStepper` i minuter (`step`) + snabbval som `AppFilterChip` (AKT-7) |
| Påminnelsetid | `ReminderTimeRow` | `SwitchRow` med `onClick` (raden öppnar tidsväljaren, reglaget slår av/på) eller `ItemRow` utan reglage (NOT-18); avslagen eller `inactive` (gruppen av) = nedtonad |
| Linjediagram | `LineChart` | `ui/diagram`, Vico: teal kurva med gradientfyllning och punkter, luckor (aldrig nollor), heltalsaxel, streckad trend, föregående period nedtonad (`previous`), zoom/panorering helt utzoomat från början; `MinMaxCaption` och tomt läge ingår (skill `diagram`) |
| Intervallstapel (dagens spann) | `IntervalBarChart` | `ui/diagram`, egen `Canvas`: spann min–max (35 %) och dagsvärdets punkt i energiskalans färg (`scaleLevel`), mjuk kurva bruten vid luckor (TRD-8) |
| Staplat stapeldiagram | `StackedBarChart` | `ui/diagram`, egen `Canvas`: segment nedifrån och upp (`StackSegment`, sömnstadier ur `AppColors.extended.sleepStages`), axel från noll, teckenförklaring (TRD-16) |
| Minidiagram (7-dagarstrend) | `SparklineChart` | `ui/diagram`, Vico utan zoom: dagens punkt solgul med ring, "Lägst · Högst · Idag", länk till Trender (HEM-7) |
| Min/max-text under diagram | `MinMaxCaption` | "Lägst · Högst · Snitt" (och "Idag") + trendpill (`InfoPill`) – under varje diagram (TRD-9) |
| Kompakt rullgardin i diagram | `CompactDropdownButton` | pill med valt värde och pil; menyn är `AppMenu`s (`AppMenuItem`), tryckyta 48 dp (TRD-3, TRD-12) |

## Beteende: interaktionsmönster → enda tillåtna ram

| Mönster | Ram | Beteende (identiskt överallt) |
|---|---|---|
| Listskärm | `EntityListScreen` | Arkivering via `archive: ListArchive` ("Visa arkiverade" i menyn, Ångra, fel som meddelande); laddning → `AppLoading`; tomt → `EmptyState` med primärknapp och valfria exempel (`ExampleChips`); fel → läsfel med "Försök igen"; rader (`ItemRow`) i kort; valfri gruppering (`SectionHeader` med antal, `collapsible` = hopfälld, `cards` = postkort var för sig med rubriken på bakgrunden) och undergrupper; lägg till (`AddSplitButton`); för en undersida `onBack`, `subtitle`, `actions`, `header` överst i listan och `topBarSize = Small` i inställningsarket; `filter` fast under rubriken i alla lägen (t.ex. `AppSegmentedChoice` Aktiviteter · Symptom · Händelser i Listor) |
| Redigeraskärm | `EntityEditScreen` | Tillsammans med `EditorState<T>`: "Spara" aktiv först när giltig **och** ändrad (NFR-10); fältfel visas när fältet ändrats eller efter ett sparförsök; läsfel → "Försök igen"; bakåt med osparat → "Släng ändringar?"; sparfel → snackbar; navigerar först när sparandet är klart (NFR-12); IME-inset hanteras i ramen (NFR-11); arkivera/återställ/radera i menyn |
| Detaljskärm | `EntityDetailScreen` | Tillsammans med `DetailUiState`/`DetailLoader`: toppbar med tillbaka, "Redigera" och meny; laddning → `AppLoading`; fel → läsfel med "Försök igen"; innehåll → huvud och sektioner i `AppCard` med `SectionHeader` (t.ex. sjukdomsepisoden med incheckningar, HIST-9) |
| Arkivera/dölj listobjekt | `SwipeToHide` | Svep → dolt direkt → `UndoSnackbar` "%s arkiverad · Ångra" i 5 s; `enabled = false` för en redan arkiverad rad. Används för listobjekt som alternativen i Listor – **inte** för postkort: postkortets svep begär radering med `ConfirmDialog` (NFR-15, `DagbokenEntryCard`) |
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
| – | – | inga kvar: diagrammen byggdes i etapp 4.3 (#233) och står under *Utseende*; y-axel, trendlinje och talbar sammanfattning är byggstenar (*Kod* nedan) |

## Kod: delade byggstenar i andra lager

| Behov | Enda stället |
|---|---|
| Firestore-CRUD | `EntityCollection<T>` från `CollectionFactory` (`data/common/`); enda implementationen `FirestoreCollection<T>` i `data/firestore/`, i test `FakeCollection<T>` |
| Sökvägar, codecs per samling | `Paths` och `CollectionTable` i `data/firestore/` (speglade i `tools/db/lib/collections.mjs`) |
| Map ↔ modell | `DocCodec<T>` + fälthjälpare (`Fields`) i `:core` |
| Formulär | `EditorState<T>` + `Validator<T>` i `ui/common/` (fel per fält visas efter ändring eller sparförsök; `load(…, showInvalid = true)` visar dessutom fel i det lagrade värdet direkt, även sådana som uppstår vid `revalidate()`; `revalidate()` när det valideringen läser har kommit, utan att läsfel försvinner; `clearLoadError()` när ett läsfel åtgärdats utan ny läsning; `errorMessage` ger egen text för ett fel som inte är ett `DataError`; `EditorEffect` till ramen) |
| Läsa in ett formulär | `EditorLoader` i `ui/common/` ("Försök igen", `stored` = det lagrade); `project` gör formulärets värde av det lagrade (t.ex. en grupp ur inställningarna), `showInvalid` som i `EditorState.load` |
| Fel till det som visas | `toFailure`/`failureOrNull` → `Failure` (`DataError` + text) i `ui/common/DataErrorMessage.kt` – används av `EditorState`, `ArchiveActions` och Tema (`EntityDetailScreen(failure)`); två fel i rad är två händelser |
| Spara ett inställningsformulär | `SettingsDifference` i `ui/settings/` – skriver bara skillnaden mot det laddade eller senast sparade (`SettingsRepository.save`) |
| Listtillstånd | `Flow<List<T>>.asListUiState()` → `ListUiState<T>`, och `ListLoader` (med "Försök igen") i `ui/common/` |
| Svep-arkivera, Ångra, "Visa arkiverade" i en lista | `ArchiveActions` + `ArchiveEvent` i `ui/common/`; skärmen skickar `archive.collectAsListArchive()` till `EntityListScreen` |
| Detaljtillstånd | `DetailUiState<T>` och `DetailLoader` (med "Försök igen") i `ui/common/` |
| Datum i UI | `DateFormat` i `ui/common/` ("lör 4 okt 2026", millis för datumväljaren) |
| Vecka och dagsmarkering | `weekMonday`, `ONE_WEEK`, `DAYS_PER_WEEK` och `DayMarkerDot` i `ui/components/DayMarker.kt` – för `DateStrip` och `DagbokenCalendar` |
| Namn på modellens val (tidpunkt, måendetillfälle, kön, alternativlista) | `label()` i `ui/common/ModelLabels.kt` |
| Inställningar och alternativlistor | `SettingsRepository` (`update` läser det lagrade och skriver med merge – `legacy` och okända fält bevaras, DAT-11) och `OptionsRepository` (nytt med `OptionIds.of`, namnbyte behåller id) i `data/repository/` |
| Temats och listornas regler | `isDarkAt`/`hasValidHours` (SET-1, SET-2) och `hasActiveName` (inga dubbletter) i `:core/engine/SettingsRules.kt` |
| Skalans nivå (etikett, ton och färg) för reglage, chips och postkortets accent | `scaleLevel` → `ScaleLevel` (`label`, `tone`, `color`) och `scaleValueText` i `ui/common/EnergyLabel.kt` – båda riktningarna (`higherIsBetter`) |
| Tidsåtgång i text ("1 tim 30 min") | `durationText` i `ui/common/DateFormat.kt` |
| Sidmarginal och sektionsavstånd i ramarna | `SCREEN_MARGIN` och `SECTION_GAP` (båda `Spacing.l`) i `ui/components/ScreenMargin.kt` – `EntityListScreen`, `EntityEditScreen`, `EntityDetailScreen` |
| Ledande element i en rad (kryss, ikon) | `LeadingSlot` (48 dp ruta) i `ui/components/TouchTargets.kt` – `CheckRow` och inställningsarkets rader, så att titlarna linjerar |
| Nedtoning | `Modifier.inactive(…)` med `INACTIVE_ALPHA` i `ui/components/ItemRow.kt` – rader, postkort, datumremsa, reglage, påminnelser |
| Visa först efter en fördröjning | `Flow<Boolean>.shownAfter(delay)` i `ui/common/` |
| Navigation | `AppKey`, `AppBackStack` (en stack per flik), `AppNavHost`, `Transitions` i `navigation/` |
| Feltyp | `DataError` (`Offline`, `PermissionDenied`, `Cancelled`, `UpdateRequired`, `SignInRejected`, `NotSignedIn`, `NotFound`, `Unknown`) i `data/common/` |
| Fel till text | `DataError.toMessage()` i `ui/common/` |
| Lista + data per nyckel ur den | `combineByKey` i `data/common/` |
| Tillägg som inte får fälla skärmen | `withFallback(värde)` i `data/common/FlowRetry.kt` |
| Ordning för något nytt | `upsertPlaced`/`nextSortOrder` i `data/common/` – ur cachen, väntar inte på nätet |
| Resultat av suspend-anrop | `suspendRunCatching` i `data/common/` (används av `FirestoreCollection` och `AuthRepository`) |
| Läs- och skrivregler för dokument | `prepareForWrite`, `readDocument`, `sortForList` i `data/common/DocumentRules.kt` och `writeBlocker` i `UserScope.kt` – används av både `FirestoreCollection` och `FakeCollection` |
| Firestore-instans | `FirestoreInstance` (`.db` vid varje anrop) från `FirestoreModule` (Hilt) i `data/firestore/` |
| Töm lokala cachen | `LocalCacheCleaner` i `data/common/` (Firestore: `FirestoreInstance`), anropas bara av `SignOutUseCase` |
| Inloggad användare | `UserSession` i `data/user/` (`UserScope`), `EnsureUserUseCase` vid inloggning |
| Synkläge | `SyncStatus` i `data/common/`; för UI:t `SyncViewModel` (`ui/sync/`) |
| Export | `RawDocuments` + `ExportFormat` (`:core`) – samma format som `tools/db export` (skill `firestore-data-layer`) |
| 3.x → 4.0 | `legacy/BackupJsonConverter` i `:core` *(etapp 2)* – enda mappningen, även för legacy-läsaren (skill `data-safety-backup`) |
| Receptets kalender och dos, doser, kylperiod, periodslut, receptregler | `Period.covers`/`Schedule.appliesOn`/`Prescription.appliesOn`/`lastDoseDay`/`lastDoseDayOf`/`nextDoseDayAfter`/`hasExpiredOn`/`boostFor`/`doseFor`/`parseDose`/`formatDose` (`Dosing.kt`), `plannedDoses`/`ensureDoses`/`syncDoses` (`EnsureDoses.kt`), `checkDose`/`cooldownRemaining`/`dailyLimitReached`/`takenDose`/`extraDose` (`Cooldown.kt`), `endingOn`/`endingSoon` (`PeriodEndings.kt`), `validate`/`problem`/`nextBoostDefaults` (`PrescriptionRules.kt`), receptformulärets val `choice`/`chosenDays`/`withDays`/`withChoice`/`withLength`/`withStart`/`withFormStart`/`extendedFrom` (`PrescriptionForm.kt`) och `totalWith` (`Dosing.kt`) i `:core/engine` – ingen doslogik i `app` |
| Dagens energi, sömnkvalitet, diagrammatematik | `computeDailyEnergyStats` (`DailyEnergyStats.kt`), `scoreSleepQuality`/`scoreNightlySleep`/`ageFromBirthYear` (`SleepQuality.kt`), `computeSmartYAxis`/`chartAxisFor`/`intervalAxisFor`/`stackedAxisFor` (`SmartYAxis.kt`), `computeTrendLine`/`trendSegment` (`TrendLine.kt`), `stackTotal`/`stackBases`/`dominantSegment` (`StackedBars.kt`), `summarize`/`gapFreeRuns` (`SeriesMath.kt`), `BarViewport`/`ZoomPan` (`ChartViewport.kt`) i `:core/engine` – ingen diagrammatematik i `app` (skill `diagram`) |
| Diagrammens talbara sammanfattning, ram och stil | `ChartSemantics.kt` (NFR-14), `ChartFrame` (gemensamt tomt läge), `ChartStyle.kt` (färger ur temat, mått), `VicoLinePlot` (enda Vico-anropet) och `BarCanvas` (stapeldiagrammens rityta) – internt i `ui/diagram/` |
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

- **Feature-kod** (Kotlin i ett ui-paket utom `ui/components`, `ui/diagram`, `ui/theme`, `ui/common` –
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
- **All kod utom `ui/diagram/`:** diagrambiblioteket Vico (`[vico]`) – ett diagram byggs alltid med
  komponenterna i `ui/diagram`. `ui/diagram` räknas som delad kod (inte feature-kod) och får rita själv.
- **I `ui/components/` och `ui/diagram/`:** publik komponent utan skärmdump (ljust + mörkt) eller utan plats i
  `ComponentGallery`.
- **Undantag:** en rad i `ui-allowlist.txt` (`fil:symbol – motivering`). Undantaget gäller bara
  träffar vars matchade text överlappar symbolen – andra förbjudna mönster på samma rad stoppas
  ändå. En rad utan symbol eller motivering fäller hooken och bygget. Filen är tom som
  standard. Varje ny rad kräver användarens uttryckliga ok i PR:en.

## Dokumentdrift

`UiConsistencyTest` läser tabellerna under **Utseende** och **Beteende** i denna fil och jämför
med de publika `@Composable`-funktionerna i `ui.components` och `ui.diagram`:
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
