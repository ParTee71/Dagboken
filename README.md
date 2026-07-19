# Dagboken – Android

Hälsodagbok för att logga aktiviteter, daglig screening (energi, stress, symptom) och mediciner, med diagram, påminnelser och molnbackup via Google Drive.

**Kravspecifikation:** [KRAVLISTA.md](KRAVLISTA.md) · **Utvecklingsregler:** [CLAUDE.md](CLAUDE.md)

> **Bidrar du (eller en AI-assistent) med kod?** Läs [CLAUDE.md](CLAUDE.md) först. Den
> samlar projektets fyra icke-förhandlingsbara regler som gäller vid varje ändring:
> datasäkerhet (backup/restore), tester på alla nivåer, aktuell kravlista och återbruk
> av delade UI-komponenter. Detaljer ligger som skills i [`.claude/skills/`](.claude/skills/).

---

## Funktioner

- **Aktivitetsloggning** — välj aktivitetstyp (favoriter som chips, övriga via dropdown), tagga som återhämtande/energitjuv, logga energi, stress och duration
- **Daglig screening** — energi- och stressnivåer (0–10), symptomloggning med svårighetsgrad, koppling till måltidshändelse
- **Symptom & aktivitetstyper** — konfigurerbara listor med favoriter, döp om och ta bort; favoriter visas framträdande i loggvyn
- **Mediciner** — schemalagda doser (dagliga, veckodagar, intervall), engångsdoser, vid-behovs-favoriter med kyldownregler och maxdos per dag
- **Händelselogg** — logga hälsohändelser med typ, svårighetsgrad, varaktighet, triggers och åtgärder
- **Diagram** — energi- och stresstrender över valbar tidsperiod
- **Påminnelser** — medicinnotiser 15 min i förväg, konfigurerbara screeningpåminnelser per måltidstillfälle
- **Backup** — automatisk daglig säkerhetskopiering till Google Drive, import från backup-fil eller Drive
- **Tema** — mörkt/ljust/auto med konfigurerbar dag- och kvällstid

---

## Kom igång

### Förutsättningar

- Senaste Android Studio (stable channel)
- JDK 17
- Android SDK API 35 (compileSdk), minSdk 30

### Bygg och kör

```bash
git clone https://github.com/ParTee71/Dagboken.git
cd Dagboken
./gradlew :app:assembleDebug
```

Öppna projektet i Android Studio och kör på en enhet eller emulator (API 30+).

### Google Services

Appen kräver en `google-services.json` från Firebase Console (Firebase Auth + Google Sign-In). Placera filen i `app/`. Filen är git-ignorerad och delas inte i repot.

---

## Releasebygge och signering

Releasebygget kräver en keystore och lösenord via `local.properties` (git-ignorerad) eller miljövariabler:

**local.properties:**
```properties
signing.storePassword=<lösenord>
signing.keyAlias=dagboken
signing.keyPassword=<lösenord>
```

**Miljövariabler (CI):**
```
SIGNING_STORE_PASSWORD
SIGNING_KEY_ALIAS
SIGNING_KEY_PASSWORD
```

Keystorefilen `dagboken.jks` placeras i `app/` och är git-ignorerad.

---

## Arkitektur

```
app/
├── data/
│   ├── auth/          FirebaseAuthRepository – Google Sign-In via Credential Manager
│   ├── datastore/     PreferencesRepository – inställningar, teman, screeningtider, symptom/aktivitetstyper
│   ├── migration/     DriveBackupRepository – import/export mot Google Drive
│   ├── repository/    AktiviteterRepository, MedicinerRepository, HandelserRepository
│   └── room/          Room-databas, DAOs, entiteter
├── di/                Hilt-moduler (AppModule, DatabaseModule, …)
├── domain/
│   ├── model/         Domänmodeller (Aktivitet, Medicin, Recept, Favorit, Handelse, …)
│   └── usecase/       EnsureTodayEntriesUseCase, DosLimitUseCase, CheckCooldownUseCase, …
├── notifications/     AlarmScheduler, påminnelsemottagare (medicin + screening)
├── ui/
│   ├── aktiviteter/   LoggaTab, ScreeningTab, HistorikTab – loggning och screening
│   ├── components/    Delade komponenter: SymptomLogCard, Foldout, GradientSliderRow, DateTimeRow, …
│   ├── diagram/       Trenddiagram (LineChartCanvas)
│   ├── handelser/     Händelselogg – logga hälsohändelser med triggers och åtgärder
│   ├── home/          Hemskärm med dagens mediciner och screeningstatus
│   ├── mediciner/     Mediciner, recept, favoriter och vid-behov-doser
│   ├── migration/     Importguide från Google Drive-backup
│   ├── settings/      Inställningar, tema, notiser, symptomtyper, aktivitetstyper
│   └── theme/         Material 3-tema (färger, typografi, animationer)
└── worker/            BackupWorker – schemalagd Drive-backup
```

**Stack:** Kotlin · Jetpack Compose + Material 3 · MVVM · Hilt · Room · DataStore · Firebase Auth · Google Drive API · WorkManager · kotlinx.serialization

### Datamodell: SymptomOption

Symptom och aktivitetstyper lagras som `List<SymptomOption>` i DataStore med stöd för favoriter:

```kotlin
@Serializable
data class SymptomOption(val name: String, val isFavorite: Boolean = false)
```

DataStore-migrering hanteras transparent: befintliga `List<String>`-värden migreras automatiskt till det nya formatet vid läsning.

---

## CI

GitHub Actions kör vid push/PR mot `master`:
- `:app:compileDebugKotlin`
- `:app:compileDebugUnitTestKotlin`
- `:app:compileDebugAndroidTestKotlin`
- `:app:testDebugUnitTest`

Se [.github/workflows/android.yml](.github/workflows/android.yml).

---

## Versionshistorik

| Version | Innehåll |
|---|---|
| 3.8.1 | Fix: Hantera-sidopanelens sektionsikoner var inte scrollbara på de flesta telefoner — de nedersta ikonerna gick inte att nå (HANT-5). Fix: Trender-ytan saknade helt diagram för Steg och Vilopuls (Health Connect) — visas nu som egna periodväljbara diagram (TRD-11). Fix: "+"-FAB på Idag saknade ett sätt att logga en fristående screening — nytt snabbval "Logga screening" (HEM-8b) (#146) |
| 3.8.0 | Trender: alla fyra diagram får tvåfingerzoom + panorering, samma känsla överallt — linjediagrammen via Vicos inbyggda zoom, "Energi (dag)" via egen handrullad gest-hantering. De fyra periodknapparna (7/14/30/90 dagar) ersätts av en gemensam dropdown: 7 dagar / 14 dagar / Månad / 3 månader / **Allt** (nytt — ingen nedre datumgräns) (#144) |
| 3.7.1 | Trender: "Energi (dag)"-diagrammets dagsvärden förbinds nu med en mjuk bezier-kurva (samma stil som övriga diagram, TRD-6), och diagrammet ritar horisontella värdelinjer vid jämna steg mellan lägsta och högsta axelvärde (`computeSmartYAxis`, delat steg) så mellanliggande energivärden går att avläsa direkt (#141) |
| — | Fix: fokuserat textfält (t.ex. anteckning) skymdes av tangentbordet på skrollbara redigeringsskärmar — delad `DagbokenScaffold` inkluderar nu `WindowInsets.ime` i `contentWindowInsets` (NFR-11, #145) |
| 3.7.0 | **Trender:** diagrammen delas upp per kategori (Energi (dag) / Energi per tillfälle / Stress & belastning / Symptom) i stället för ett gemensamt diagram med gemensam y-skala — varje kategori skalas nu efter sina egna aktiva serier (#141). Ny "Energi (dag)": ett intervall-/spannstapeldiagram (`IntervalBarChart`) som per dag visar lägsta–högsta loggade screeningenergi med dagsvärdet markerat, delad uträkning (`computeDailyEnergyStats`) med Idag-energidiagrammet. Alla diagram i appen (Trender + Idag) visar nu alltid sitt lägsta och högsta värde som text under diagrammet (`MinMaxCaption`) |
| 3.6.0 | **Idag:** hälsokortet delas upp — steg och vilopuls följer nu checklistans valda dag i stället för att alltid visa dagens datum; stegtrend, vilopulstrend och energitrend slås ihop i ett gemensamt diagramkort (#138/#140). Kommande mediciner (schemalagd tid ej nådd) döljs nu bakom en "Visa kommande"-toggle, analogt med tagna doser (#139). Diagram: y-axeln skalas nu smart efter de aktiva värdenas min/max i stället för att alltid utgå från 0 — gäller Trender (`LineChartCanvas`) och Idag-diagrammen (delad `SparklineChart`). Ett smalt värdeband fyller nu diagramhöjden i stället för att klämmas ihop i toppen (#136/#137) |
| 3.5.1 | Fix: stegantalet på Idag/Hälsa visade färre steg än enheten — `COUNT_TOTAL` de-duplicerade per tidslucka och tappade steg när flera källor (telefon + klocka via Samsung Health) inte överlappade; summeras nu per källa och den mest kompletta källan väljs. Fix: vilopulsskattningen (när `RestingHeartRateRecord` saknas) låg för högt — skattas nu som medelvärdet av lägsta 5-percentilen ≈ lägsta ihållande pulsen (#135) |
| 3.5.0 | Fix: stegantalet på Idag/Hälsa dubbelräknades när flera källor (t.ex. telefon och klocka) skrev överlappande stegposter — läses nu via Health Connects aggregeringsmotor i stället för en rå summering. Nytt: vilopulstrend för senaste 7 dagarna på Idag-hälsokortet, delad `SparklineChart` (#134) |
| 3.4.1 | **Idag:** checklistan för mediciner/screening/vid behov-mediciner grupperas nu visuellt i ett gemensamt kort, hälsokortet flyttat till energisektionen (#130). Fix: vilopuls på Idag skattas från veckans pulsdata (5:e percentilen) när `RestingHeartRateRecord` saknas (t.ex. Galaxy Watch via Samsung Health) i stället för att visa "—" (#131). Fix: energidiagrammet (HEM-7) och stegtrenden (HLS-7) på Idag visar nu värden på både x- och y-axeln — delad `SparklineChart` fick riktiga axlar (#133) |
| ~~3.4.0~~ | *(taggad felaktigt mot en gammal commit av misstag — samma innehåll som 3.4.1 ovan, inget APK publicerat)* |
| 3.3.1 | Fix: "Ge åtkomst" på Hälsa-skärmen öppnar nu Health Connects samtyckesdialog — appen deklarerar den behörighets-rationale-handler som Health Connect kräver (`SHOW_PERMISSIONS_RATIONALE` / `VIEW_PERMISSION_USAGE`), annars hände inget vid tryck (#128) |
| 3.3.0 | **Hälsa (Health Connect):** ny Hälsa-skärm som läser steg, puls och sömn read-only via Health Connect, nås via kort i Hantera (#54/#56/#57); hälsokort på Idag med stegtrend (7 dagar, delad `SparklineChart`) och vilopuls (#124). Stabil `connect-client` 1.1.0, compileSdk 36 (#127). **Idag:** datumnavigering till tidigare dagar för mediciner/screening/händelser (#115). **Diagram:** bättre mörkt tema-kontrast samt mjuka kurvor med gradientfyllning (#125) |
| 3.2.0 | Historik: kalendervy som växlingsbart komplement till listvyn (HIST-6, delad `DagbokenCalendar`-komponent). Trender- och Hem-diagrammen (`LineChartCanvas`, `SparklineChart`) byggs nu på Vico i stället för handrullad Canvas-kod. Spara-knappen (`SaveButton`) är nu grön (`Emerald400`) i stället för tonal (#107) |
| 3.1.0 | Historik: radera poster (aktivitet/screening/medicindos/händelse/sjukdomsincheckning) via långtryck (#105). Generaliserad spara-knapp (`SaveButton`) på alla formulär, inaktiverad tills osparade giltiga ändringar finns (dirty-state); bekräftelsedialog vid navigering bort med osparat (#104) |
| 3.0.0 | UX-omarbetning (#84): uppgiftsorienterad bottennavigering med fyra flikar **Idag / Historik / Trender / Hantera** (ersätter de fem entitetsflikarna). Idag-ytan som handlingsyta med checklistor, enhetlig Historik-yta för alla posttyper, Trender-ytan som slår ihop diagrammen, Hantera-ytan. Polish: notisåtgärder ("Markera tagen"/"Logga nu"), smarta FAB-förval, stegvis svepbar screening, veckosammanfattning på Idag |
| 2.14.1 | Tillgänglighetsfixar (48dp tryckytor, TalkBack-semantik på energireglaget), korrekt tidszonskonvertering för loggade tidsstämplar (medicinnedkylning kunde bli ~2h för lång), avkodad Settings-layout och medicindosloggning, borttagen död kod, städad KRAVLISTA/README |
| 2.14.0 | Info-ikon med läs-only anteckningsdialog på kort som saknade en synlig anteckningsindikator (Aktivitet/Screening, medicindos, favorit, sjukdomsepisod) |
| 2.13.0 | Sjukdomsepisod- och incheckningsanteckning migrerad till det generiska anteckningssystemet (delad `NoteField`) |
| 2.12.0 | Händelse-anteckning migrerad till det generiska anteckningssystemet (delad `NoteField`) |
| 2.11.0 | Medicin/Recept/Favorit-anteckning migrerad till det generiska anteckningssystemet (delad `NoteField`); Favorit-anteckning har nu ett UI-fält |
| 2.10.0 | Anteckning (delad `NoteField`) på Screening- och Aktivitet-formulären |
| 2.9.0 | Vid behov-mediciner kan favoritmarkeras (som aktivitetstyper); endast favoriter visas som chips, resten nås via "Fler"-lista; ny inställningssektion för favoritmarkering |
| 2.2.1 | Symptomloggning med svårighetsgrad och favoriter; aktivitetstyper med favorit-chips och dropdown; OptionSettingsCard för full CRUD av båda listor |
| 2.2.0 | Händelselogg (Handelse) med triggers och åtgärder |
| 2.1.0 | Måltidshändelseväljare i ScreeningTab |
| 2.0.x | Strängextraktion till strings.xml, utökad testtäckning, CI-fixes, Upprepning-enum, IoDispatcher-injektion |
