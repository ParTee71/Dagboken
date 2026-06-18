# Åtgärdslista – Dagboken

Sammanställning av kodgranskningens fynd och planerade tester att åtgärda.
Prioritet: 🔴 beteende/bugg · 🟡 robusthet · 🟢 städning.

Krav-ID refererar till [`KRAVLISTA.md`](./KRAVLISTA.md).

---

## A. Kodgranskning – att åtgärda

### ✅ F1 – Inkonsekvent klockslag för "Kväll"
- [x] `TIDP_DEFAULT_TIMES` sätter `Kväll → 19:00`, men `tidpunktToHour()` returnerade `20`.
- [x] Effekt: Kväll-medicin visas 19:00 men "Försenat" triggar först 20:00; larm går 19:45.
- [x] **Fix:** `tidpunktToHour` härleds nu från `TIDP_DEFAULT_TIMES` (en källa). Kväll returnerar 19.
- Filer: `domain/usecase/EnsureTodayEntriesUseCase.kt`, `ui/home/HomeViewModel.kt`, `notifications/AlarmScheduler.kt`

### ✅ F2 – Duplicerad tidpunkt-logik (DRY)
- [x] `tidpunktToHour` fanns i både `HomeViewModel` och `AlarmScheduler`.
- [x] `tidpunktSortIndex` fanns i `domain/model/Medicin.kt` men återimplementerades i `HomeViewModel`.
- [x] **Fix:** `TIDP_DEFAULT_TIMES` och `tidpunktToHour` samlade i `domain/model/Medicin.kt`; kopior borttagna.
- Filer: `domain/model/Medicin.kt`, `ui/home/HomeViewModel.kt`, `notifications/AlarmScheduler.kt`

### ✅ F3 – Notifikations-ID-krock för mediciner
- [x] `NotificationHelper.postMedReminder()` använde `manager.notify(namn.hashCode(), …)`.
- [x] Effekt: två doser av samma medicin (t.ex. Morgon + Kväll) skriver över varandras notis.
- [x] **Fix:** ID är nu `"$namn-$tidpunkt".hashCode()` — unikt per dos.
- Filer: `notifications/NotificationHelper.kt`

### ✅ F4 – Tyst backup-lucka
- [x] `BackupWorker.doWork()` returnerade `success` vid `NeedsAuthorization` → backup uteblir tyst.
- [x] Periodiskt jobb saknade nätverks-constraint.
- [x] **Fix:** `NetworkType.CONNECTED`-constraint tillagt; DataStore-flagga `backup_needs_auth` sparas vid `NeedsAuthorization` och nollställs vid `Success`.
- Filer: `worker/BackupWorker.kt`, `DagbokenApp.kt`, `data/datastore/PreferencesRepository.kt`

### ✅ F5 – `getScreeningToday()` filtrerar i minnet
- [x] Läste `getByDate` och filtrerade i Kotlin i stället för dedikerad query.
- [x] **Fix:** DAO-query `WHERE type='screening' AND datum=:datum ORDER BY tid ASC` tillagd.
- Filer: `data/repository/AktiviteterRepository.kt`, `data/room/daos/AktivitetDao.kt`

### Redan korrekt (ingen åtgärd)
- [x] POST_NOTIFICATIONS begärs i runtime – `MainActivity.kt:29`.
- [x] Daglig backup schemaläggs – `DagbokenApp.kt:44`.
- [x] Exakta larm faller tillbaka till inexakta – `AlarmScheduler.kt:121`.

---

## B. Nya tester – att lägga till

Befintlig täckning är stark på data-/domänlagret (SymptomUtils, EnsureTodayEntries, kylperiod/dosgräns,
BackupMapper, alla DAO:er, AccountViewModel). Luckorna nedan rör ViewModel-, UI-, notifikations- och tidpunkt-logik.

### Nivå 1 – Rena enhetstester (`src/test`, JVM)
- [x] `TidpunktLogicTest` – `tidpunktToHour` (Kväll=19 efter F1), sortordning, "Vid behov"→null · *DAT-1, REC-6, F1/F2*
- [x] `HomeViewModelTest` – overdueMediciner, overdueScreeningTimes, tagenCount, screeningLabels, stat-pills · *HEM-3..7, NOT-4*
- [x] `AktiviteterViewModelTest` – save ny/edit, Övrigt-encode, historyFilter (≥1 kvar), screening + snackbar · *AKT-1..9, SCR-1..3, HIS-1..3*
- [x] `MedicinerViewModelTest` – quickDos blockeras (dosgräns/kylperiod), delete vs skip via receptId · *MED-2,3, FAV-2,4,5,6*
- [x] `SettingsViewModelTest` – tema-klamp (ljus<mörk), add/remove utan dubbletter, omschemaläggning · *SET-1,2,5,6, NOT-4,7*
- [x] `MigrationViewModelTest` – tillståndsmaskin (NoAccount/NoBackup/NeedsAuth/Importing/Done) · *BCK-5,6,7,8, NAV-6*
- [x] `DiagramViewModelTest` – dygns-gruppering + medel, range-cutoff, setRange/setSeries · *DIA-1,2,3*
- [x] `DateFormatTest` – `formattedDate()` veckonummer + svensk locale, `greeting()` · *HEM-1,2*
- [x] `AlarmTimeTest` – triggertid = tidpunkt−15 min, screening → nästa dag · *NOT-2,5*
  - Extraherade `medAlarmTriggerMs` och `screeningAlarmTriggerMs` som `internal` top-level-funktioner i `AlarmScheduler.kt`

### Nivå 2 – Integration (in-memory Room, `src/androidTest`)
- [x] `MedicinerRepositoryTest` – ensureTodayEntries idempotent, countDailyDoses/getLastTaken, skip vs delete · *MED-3,4, FAV-4,5*
- [x] `AktiviteterRepositoryTest` – getScreeningToday (efter F5), getRecent, screeningFromDate · *HEM-7, SCR-3, HIS-1*
- [x] `MigrationRoundTripTest` – BackupMapper → repo-import → DAO → läs tillbaka · *BCK-2,5,9*
- Tillagde även `getScreeningToday`-tester i befintlig `AktivitetDaoTest`

### Nivå 3 – Instrumenterade Compose UI-tester (`src/androidTest`)
- [x] `HomeScreenTest` – Försenat-kort, bock markerar tagen, sparkline döljs < 2 punkter · *HEM-4,5,6,7*
- [x] `LoggaTabTest` – Spara inaktiverad utan typ, "Övrigt"-fält, chips · *AKT-1,2,3,9*
- [x] `VidBehovTabTest` – tomt tillstånd, tryck loggar dos, blockerad → snackbar, långtryck → meny · *FAV-1,2,3,6*
- [x] `HistorikTabTest` – filterchips växlar, minst en typ kvar · *HIS-1*
- [x] `SettingsScreenTest` – toggla teman/notiser, lägg till/ta bort alternativ syns · *SET-1,3,4,5,6*

---

## C. Körning

- Enhetstester (Nivå 1): `.\gradlew.bat :app:testDebugUnitTest`
- Instrumenterade (Nivå 2–3): `.\gradlew.bat :app:connectedDebugAndroidTest` (kräver emulator/enhet)
- Ordning: Nivå 1 → 2 → 3. Börja med `TidpunktLogicTest` för att fånga F1/F2-regressioner.