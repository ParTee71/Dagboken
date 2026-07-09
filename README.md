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
|---------|----------|
| 2.15.0 | Enhetligt designsystem: `DagbokenCard` enda kortbyggsten, delad `EmptyState`/`ConfirmDialog`, centraliserad datum/tid-formatering (`DateUtils`), gemensamt skärmramverk `DagbokenScaffold` + `SectionHeader`, konsekventa animationer (`DagbokenAnimSpec`, `animateItem()`), Hem-skärmens statuskort med accentfärg |
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
