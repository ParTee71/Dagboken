# Dagboken 4.0 – arkitektur och ombyggnadsplan

> Enda källan för arkitekturbesluten bakom ombyggnaden. Vad appen ska **göra** står i
> [KRAVLISTA.md](KRAVLISTA.md); hur arbetet bedrivs står i [CLAUDE.md](CLAUDE.md).
> Design: Design-canvas med struktur, datamodell, känsla och nyckelskärmar –
> https://claude.ai/artifact/MP2fPHimTk8tAmKkpr3K5t (vald känsla: **I · Papper och teal**).

---

## ADR-001 · Ombyggnad av Dagboken på ReseApotekets grund

**Status:** beslutad 2026-10-04 (intervju med användaren, se canvasen).
**Gäller från:** första PR på `master` efter att 3.27.0 frysts på branchen `legacy`.

### Kontext

Dagboken 3.x är en enmodulsapp (Room v11 + DataStore, Drive-backup, navigation-compose
med strängrutter, Compose BOM 2024.12) på ca 21 500 rader. Kravlistan (~235 aktiva krav)
beskriver väl *vad* appen gör och behålls som grund. Navigationen har vuxit fram
etappvis (#84) och är otydlig: Hantera-fliken blandar bibliotek (recept, vid behov,
sjukdomar), hälsodata och inställningar; mediciner ligger utspridda över tre ytor; Idag
har tre separata kort för samma dag. CI kostar 35–45 Actions-minuter per PR.

ReseApoteket har sedan dess gett en beprövad verkstad: `:core` + `:app`, generisk
`FirestoreCollection<T>`/`DocCodec<T>`, ramar för list- och redigeringsskärmar, regel 4
med hook och Konsist, Roborazzi, `tools/db` för databasåtkomst från sessionen,
paths-filtrerad CI på ~8 minuter och krypterad veckobackup.

### Beslut

| # | Beslut | Alternativ som valdes bort |
|---|---|---|
| 1 | **Firestore offline-först är primär lagring.** Telefonen har persistent cache; molnet är källan. | Room + Firestore-spegling (två sanningar); Room som idag (ingen åtkomst från sessionen). |
| 2 | **Strikt personlig data under `users/{uid}`.** Ingen hushållsmodell, inga medlemslistor. | ReseApotekets `households/{hid}` med `memberUids` (delning "kanske senare"). |
| 3 | **Google-inloggning krävs.** AUTH-5 ("fungerar utan konto") stryks. | Anonym Firebase-auth som länkas senare. |
| 4 | **Drive-backupen pensioneras.** Firestore är molnkopian; veckovis krypterad export i GitHub Actions (`backup.yml`) + manuell JSON-export i appen. Drive-import behålls som legacyimport minst en version. | Drive-backup parallellt. |
| 5 | **Samma repo, samma `applicationId`, ny kodbas på `master`.** 3.27.0 fryses som tagg och branch `legacy`. Nya appen installeras som uppdatering och kan läsa Room-filen på enheten. | Nytt repo; stegvis ombyggnad i befintlig kod. |
| 6 | **Fyra flikar: Idag · Dagbok · Trender · Mediciner.** Inställningar i ett ark bakom avataren. Hantera-fliken försvinner. | Fem flikar; Mediciner kvar under Hantera. |
| 7 | **Idag och Dagbok är två flikar** – göra respektive läsa. | En dagvy med datumremsa. |
| 8 | **Sjukdomsepisoder hör till Dagbok** (filter, detalj) med pågående episod som kort på Idag. | Egen underyta; under Mediciner. |
| 9 | **Plusknappen loggar bara** (Mående, Aktivitet, Dos, Händelse, Sjukdom) mot visad dag. Recept och vid behov-mediciner skapas under Mediciner. | Dagens FAB med "Ny favorit" bland loggvalen. |
| 10 | **Känsla I · Papper och teal** med belöningsläge när dagen är klar (konfetti en gång, solgul framstegsrad, sammanfattningskort). | A–H i canvasen; se avsnittet Designspråk. |
| 11 | **Ingen data får tappas.** Varje fält i 3.x-schemat har en plats i 4.0-modellen; konverteraren bevisas med fixtur och rundturstest; en riktig backup ska importeras med noll skillnader innan första release. | – |

### Konsekvenser

- Appen kräver nätverk första gången (inloggning + hämtning av hushållet) men fungerar
  därefter offline via cachen. Första synk av flera års data (~3 000–5 000 dokument/år)
  är en engångskostnad inom Spark-planens läsbudget.
- Hälsodata i Firestore är känslig (GDPR art. 9): EU-region (`europe-west`), rules som
  bara släpper in ägaren, inga PII i loggar. Health Connect-data persisteras fortfarande
  **inte** (HLS-5).
- Larm fortsätter via AlarmManager men läser schemat ur Firestore-cachen. "Markera tagen"
  från notisen skriver till cachen och synkar när nätet finns.
- `tools/db`, `firestore.rules`, `backup.yml` och `CollectionContract` tas från ReseApoteket
  med sökvägsbyte `households/{hid}` → `users/{uid}`.

---

## Struktur

```
Bottenrad      Idag · Dagbok · Trender · Mediciner
Avatar (alla)  Inställningsark: Konto · Profil · Påminnelser · Tema · Listor · Export/import · Om
Plus (alla)    Loggmeny: Mående · Aktivitet · Dos · Händelse · Sjukdom  → mot visad dag
```

| Flik | Innehåll | Ersätter i 3.x |
|---|---|---|
| **Idag** | Datumremsa (vecka, punkter för dagar med poster), framstegsrad, kort för Mediciner (checklista), Mående (tillfällesrad per måltid, "Logga nu" öppnar stegformuläret som ark), Vid behov (snabbval + Fler), pågående sjukdom, Hälsa idag (steg, vilopuls, sömn + poäng), 7-dagarstrend. Belöningsläge när allt är klart. | Idag + Föregående/Nästa-raden + inline-screening |
| **Dagbok** | Tidslinje över alla posttyper grupperad per dag, filterchips (Alla, Mående, Aktiviteter, Doser, Händelser, Sjukdom), kalendervy, "Visa äldre" ett år i taget, post → redigera, episod → detalj med incheckningar. | Historik + Hantera → Sjukdomar |
| **Trender** | Grupper **Mående · Klocka · Jämför** ovanför ihopfällbara diagramkort med egen period; Health Connect-status och behörigheter under Klocka; Hälsa idag (klockans alla mått). | Trender + Hantera → Hälsa |
| **Mediciner** | Periodslutsbanner, Recept och scheman (aktiva med reglage, dagens dos med höjning, period), Vid behov-mediciner med stjärna, Avslutade recept, Nytt recept, Ny vid behov-medicin, Logga dos i efterhand. | Hantera → Recept & scheman, Hantera → Vid behov-mediciner |

Formulär (aktivitet, mående, dos, recept, vid behov-medicin, händelse, episod, incheckning)
byggs på `EntityEditScreen` + `EditorState`; listor på `EntityListScreen`. Navigation 3 med
en backstack per flik (`AppBackStack`), skärmbyten i `navigation/Transitions`.

---

## Datamodell (Firestore)

Allt under `users/{uid}`; dokumentet bär `schemaVersion`, `createdAt` och, efter migreringen från 3.x,
markören `legacyMigration` {completedAt, source `room` \| `drive` \| `json`, sourceCreatedAt?, appVersion, counts}
(`LegacyMigrationCodec`; sätts en gång, OMB-2). Modeller och codecs i `:core` (`core/model`,
`core/schema/*Codecs.kt`); KDoc på varje fält.

| Samling / dokument | Nyckelfält | Ersätter 3.x |
|---|---|---|
| `settings` (ett dokument, `settings/app`) | theme {mode `light` \| `dark` \| `auto`, lightStartHour, darkStartHour, isDarkTheme}, reminders {medsEnabled, medSlots[6] {slot, enabled, time}, screeningOccasions[4] {occasion, enabled, time}, periodReminderTime}, profile {birthYear?, sex `male` \| `female` \| `unspecified`}, legacy {dynamicColor?, sheetsConfig?} (bara bevarade 3.x-värden) | DataStore + `SettingsBackup` + `BackupJson.sheetsConfig` |
| `options` | kind (`activity` \| `symptom` \| `event`), name, favorite, sortOrder, archived | DataStore-listorna |
| `prescriptions` | name, dose (text), unit, slots[], schedule {repeat `daily` \| `weekdays` \| `weekends` \| `custom` \| `interval`, days[], intervalDays}, period {start?, end?}, boosts[] {id, start, end?, dose, unit}, active, createdAt, note | `recept` + `dosperioderJson` |
| `prnMedicines` | name, dose (text), unit, slot, minHoursBetween, dispensingTime, maxPerDay, favorite, note | `favoriter` |
| `doses` | date, slot, name, dose (text), unit, status (`planned` \| `taken` \| `skipped`), plannedTime, takenAt?, prescriptionId?, prnId?, createdAt, note | `mediciner` (tagen + skipped + tagenTid) |
| `screenings` | date, time, occasion? (`breakfast` \| `lunch` \| `dinner` \| `bedtime`), customText?, energy 0–10, stress, symptoms[] {optionId, score, customText?}, createdAt, note | `aktiviteter` med type=screening |
| `activities` | date, time, optionId, customText?, energy −10..10, stress, symptoms[], recovering, drain, minutes?, createdAt, note | `aktiviteter` med type=aktivitet |
| `events` | date, time, optionId, severity, durationMinutes, triggers, actions, createdAt, note | `health_events` |
| `illnessEpisodes` | type, start, end?, createdAt, note | `sjukdomsepisoder` |
| `illnessEpisodes/{id}/checkins` | date, time, severity, symptoms[], createdAt, note | `sjukdoms_incheckningar` |

**Värden:** datum som text `yyyy-MM-dd`, klockslag som text `HH:mm` (DAT-2); ögonblick
(`createdAt`, `takenAt`) som tidsstämpel. Enum lagras med engelska namn ur `WireEnum.wire` –
samma listor i `firestore.rules` (ett test jämför). Doser är text, som i 3.x (`"0,5"`, `"1 tablett"`).
Stress, svårighetsgrad, mående-energi och symptompoäng är heltal 0–10, aktivitetens energi −10..10.
Valfri fritext (`note`, `customText`, `triggers`, `actions`, `dispensingTime`) är `null` när den saknas.
Textgränser (`TextLimits`, samma i rules): 200 tecken för namn och korta texter, 5 000 för anteckningar,
`triggers`, `actions` och `settings.legacy.sheetsConfig`.

**Tolerans** (DAT-10): saknat fält → modellens default; okänt enumvärde → default, som skrivs vid
nästa sparning – utom `screenings.occasion`, som blir `null`, och påminnelserader och tidpunkter med
okänd nyckel, som hoppas över. Okända fält på toppnivå och i nästlade objekt överlever eftersom
codecen skriver med merge, men **okända fält i ett listelement** (`symptoms[]`, `boosts[]`,
`medSlots[]`, `screeningOccasions[]`) bevaras inte: listor skrivs alltid hela ur modellen. Ett nytt
fält i ett listelement kräver därför höjd `schemaVersion`. En okänd upprepning på ett recept
(`Schedule.Unknown`) skrivs tillbaka oförändrad och kan uppdateras, men rules nekar att ett dokument
skapas med den (inte heller återskapas efter radering) tills `schemaVersion` och rules höjs.

Fyra förenklingar: anteckningen är fältet `note` på varje dokument (notes-tabellen och
kaskadraderingen försvinner); symptom lagras som `[{optionId, score, customText?}]` (summan
`somatiska` räknas i `:core`, fritexten vid "Övrigt" ligger i `customText`, AKT-6); poster
refererar alternativ via `optionId` så namnbyte aldrig behöver skriva om historiken (SET-11 blir
gratis); dosen har en `status` i stället för två booleaner.

**Id:n bevaras från 3.x** (DAT-13): poster, recept, vid behov-mediciner, episoder och
incheckningar behåller sina 3.x-id:n (UUID-strängar). Receptgenererade doser behåller 3.x-schemat
`recept_{prescriptionId}_{date}_{tidpunkt}` med tidpunktens 3.x-namn (`Morgon`, `Förmiddag` …,
`DoseIds.prescribed`), så att 4.0:s idempotenta generering (MED-4) träffar redan migrerade doser
och en upprepad import aldrig dubblerar (DAT-8). Alternativen hade inga id:n i 3.x; de får
deterministiska id:n ur lista och namn med `OptionIds.of(kind, name)` i `:core`, samma regel i
konverteraren och i 4.0: `"${kind.wire}-${slug}-${hash}"`, där *slug* är namnet NFD-normaliserat utan
kombinerande tecken (å/ä/ö → a/a/o), gement, varje tecken utom `a–z` och `0–9` ersatt med `-`, bindestreck hopslagna och trimmade, högst 32
tecken (`Huvudvärk` → `huvudvark`), och *hash* är de 6 första hex-tecknen av SHA-256 över UTF-8-byten i
det **exakta** namnet – `Promenad`, `promenad` och `Promenad ` har samma slug men olika id
(`activity-promenad-c78928`, `activity-promenad-3f4216`, `activity-promenad-967bbc`). Ett nytt
alternativ i 4.0 får id enligt samma regel; ett namnbyte ändrar inte id:t (SET-11).

**Måltidstillfället** (`screenings.occasion`) fanns inte som fält i 3.x och härleds av
`Occasion.derive` (DAT-12): 3.x-screeningens namn (`aktivitet`) när det är ett av de fyra
tillfällenas namn (`SCREENING_EVENT_LABELS`, samma jämförelse som `isScreeningLoggedFor`), annars
tillfället vars påminnelsetid ligger närmast klockslaget räknat runt dygnet; namnet bevaras då i
`customText`.

Alla codecs ligger i `:core` (`DocCodec<T>` + `Doc.*`-läsare), toleranta mot okända fält
och äldre `schemaVersion`. Samlingslistan i `tools/db/lib/collections.mjs` speglar `Paths.kt`
och kontrolleras av test.

### Fältparitet 3.x → 4.0

Varje fält i 3.x `BackupJson` (v1 och v2) och dess klasser har en plats nedan (ADR-001, beslut 11;
OMB-3). Tabellen är kontrollerad: `ParityTableTest` i `:core` kräver att varje 3.x-fält finns som rad,
att varje 4.0-fält i kolumn två finns i samlingens codec, att varje codec-fält finns i tabellen eller
bland de nya fälten nedan, och att **ingen** rad är *utelämnas*. Kolumn två anger samling och fältväg
(`[]` = element i en lista); *beräknas* och *sökväg* betyder att värdet bevaras utan eget fält. 3.x-värden
utan funktion i 4.0 (`dynamicColor`, `sheetsConfig`) bevaras i `settings.legacy` och används aldrig.
Room-entiteterna (v11) bär samma fält som `BackupJson` (listorna som JSON-text i `recept`); de
enhetslokala DataStore-nycklarna `migration_done` och `backup_needs_auth` är inte användardata.

| 3.x | 4.0 | Anmärkning |
|---|---|---|
| `BackupJson.version` | *metadata* | Backupfilens formatversion\*; väljer v1- eller v2-tolkning i konverteraren. |
| `BackupJson.createdAt` | *metadata* | När backupfilen skrevs\*; visas i importens sammanfattning. |
| `BackupJson.aktiviteter` | `activities`, `screenings` | Delas på `AktivitetJson.type`. |
| `BackupJson.mediciner` | `doses` | |
| `BackupJson.medicinRecipes` | `prescriptions` | |
| `BackupJson.medicinFavoriter` | `prnMedicines` | |
| `BackupJson.aktiviteterOptions` | `options.kind`, `options.name`, `options.sortOrder` | v1 (lista av namn): kind `activity`, ordningen = listans. |
| `BackupJson.symptomOptions` | `options.kind`, `options.name`, `options.sortOrder` | v1: kind `symptom`. |
| `BackupJson.aktiviteterOptionsV2` | `options.kind`, `options.name`, `options.favorite`, `options.sortOrder` | v2: kind `activity`; går före v1 när båda finns. |
| `BackupJson.symptomOptionsV2` | `options.kind`, `options.name`, `options.favorite`, `options.sortOrder` | v2: kind `symptom`; går före v1. |
| `BackupJson.handelseTypOptions` | `options.kind`, `options.name`, `options.favorite`, `options.sortOrder` | kind `event`. |
| `BackupJson.sjukdomsepisoder` | `illnessEpisodes` | |
| `BackupJson.sjukdomsIncheckningar` | `checkins` | Under sin episod. |
| `BackupJson.handelser` | `events` | |
| `BackupJson.notes` | `activities.note`, `screenings.note`, `doses.note`, `prescriptions.note`, `prnMedicines.note`, `events.note`, `illnessEpisodes.note`, `checkins.note` | Se `NoteJson`. |
| `BackupJson.screeningEventConfigs` | `settings.reminders.screeningOccasions`, `settings.reminders.screeningOccasions[].occasion` | Positionen 0–3 = Efter frukost, Lunch, Kvällsmat, Läggdags (`SCREENING_EVENT_LABELS`) blir radens `occasion`. |
| `BackupJson.medNotificationConfigs` | `settings.reminders.medSlots` | |
| `BackupJson.sheetsConfig` | `settings.legacy.sheetsConfig` | Bevaras bara: Sheets-exporten finns inte i 4.0 (FUT-2) och värdet används aldrig. Text upp till `TextLimits.LONG`; `null` = fanns inte. |
| `BackupJson.periodReminderTime` | `settings.reminders.periodReminderTime` | `HH:mm`. |
| `BackupJson.settings` | `settings` | `null` i ett fält = "rör inte": konverteraren skriver då inte fältet (merge). |
| `SettingsBackup.medsNotificationsEnabled` | `settings.reminders.medsEnabled` | |
| `SettingsBackup.themeMode` | `settings.theme.mode` | Samma strängar `light`/`dark`/`auto`. |
| `SettingsBackup.themeLightStart` | `settings.theme.lightStartHour` | |
| `SettingsBackup.themeDarkStart` | `settings.theme.darkStartHour` | |
| `SettingsBackup.isDarkTheme` | `settings.theme.isDarkTheme` | Bevaras fast 4.0 bara läser `mode`. |
| `SettingsBackup.dynamicColor` | `settings.legacy.dynamicColor` | Bevaras bara: Papper och teal har fasta färger (SET-3) och värdet används aldrig. `null` = fanns inte. |
| `SettingsBackup.birthYear` | `settings.profile.birthYear` | |
| `SettingsBackup.sex` | `settings.profile.sex` | `man`/`kvinna`/`ej_angivet` → `male`/`female`/`unspecified`. |
| `AktivitetJson.id` | `activities.id`, `screenings.id` | Dokument-id, bevaras (DAT-13). |
| `AktivitetJson.timestamp` | `activities.createdAt`, `screenings.createdAt` | ISO-ögonblick → tidsstämpel; tomt eller ogiltigt → datum + tid i Europe/Stockholm. |
| `AktivitetJson.datum` | `activities.date`, `screenings.date` | |
| `AktivitetJson.tid` | `activities.time`, `screenings.time` | |
| `AktivitetJson.aktivitet` | `activities.optionId`, `activities.customText`, `screenings.occasion`, `screenings.customText` | Aktivitet: namn bland aktivitetsalternativen → `optionId`; annat namn (fritext vid "Övrigt", AKT-2) → `customText` med alternativet "Övrigt". Screening: tillfällets namn → `occasion`; annat namn → `customText` och `occasion` ur klockslaget (DAT-12). |
| `AktivitetJson.energy` | `activities.energy`, `screenings.energy` | −10..10 respektive 0–10. |
| `AktivitetJson.stress` | `activities.stress`, `screenings.stress` | |
| `AktivitetJson.somatiska` | *beräknas* | Summan av `symptoms[].score` (DAT-6). En 3.x-post där summan avviker rapporteras av konverteraren. |
| `AktivitetJson.symptom` | `activities.symptoms[].optionId`, `activities.symptoms[].score`, `activities.symptoms[].customText`, `screenings.symptoms[].optionId`, `screenings.symptoms[].score`, `screenings.symptoms[].customText` | `Namn:Poäng,…` → symptomalternativ på namnet; `Övrigt (fritext)` → "Övrigt" + `customText`; okänt namn → arkiverat alternativ. |
| `AktivitetJson.aterhamtande` | `activities.recovering` | Screening: alltid `false` i 3.x; annat värde stoppar konverteringen i stället för att tappas. |
| `AktivitetJson.energitjuv` | `activities.drain` | Som `aterhamtande`. |
| `AktivitetJson.type` | `activities`, `screenings` | `aktivitet`/`screening`; tomt → ur namnet som 3.x `BackupMapper.inferType`; saknat är `aktivitet` (klassens default, som 3.x). |
| `AktivitetJson.spentTime` | `activities.minutes` | `null` bevaras. Screening: alltid `null` i 3.x; annat värde stoppar konverteringen. |
| `MedicinJson.id` | `doses.id` | Bevaras; `recept_…`-id:n oförändrade (DAT-8). |
| `MedicinJson.timestamp` | `doses.createdAt` | Som `AktivitetJson.timestamp`. |
| `MedicinJson.datum` | `doses.date` | |
| `MedicinJson.tid` | `doses.plannedTime` | Schemalagt klockslag. |
| `MedicinJson.namn` | `doses.name` | |
| `MedicinJson.dos` | `doses.dose` | Text. |
| `MedicinJson.enhet` | `doses.unit` | |
| `MedicinJson.tidpunkt` | `doses.slot` | `Slot.fromLegacyName`; okänt namn stoppar konverteringen. |
| `MedicinJson.tagen` | `doses.status` | `true` → `taken`. |
| `MedicinJson.skipped` | `doses.status` | `true` → `skipped`; varken eller → `planned`; båda (ogiltigt i 3.x) → `taken`. |
| `MedicinJson.tagenTid` | `doses.takenAt` | `HH:mm` på dosens datum i Europe/Stockholm → tidsstämpel (sommartidsregeln i Migrering, punkt 1). |
| `MedicinJson.anteckning` | `doses.note` | Arvsfält från före notes-tabellen; en `notes`-post för samma post går före (som 3.x `BackupMapper.toNotes`). |
| `MedicinJson.receptId` | `doses.prescriptionId` | |
| `ReceptJson.id` | `prescriptions.id` | Bevaras. |
| `ReceptJson.namn` | `prescriptions.name` | |
| `ReceptJson.dos` | `prescriptions.dose` | Text. |
| `ReceptJson.enhet` | `prescriptions.unit` | |
| `ReceptJson.tidpunkter` | `prescriptions.slots` | |
| `ReceptJson.tidpunkt` | `prescriptions.slots` | v1: en tidpunkt när `tidpunkter` är tom; båda tomma → `[Morgon]` som 3.x. |
| `ReceptJson.upprepning` | `prescriptions.schedule.repeat` | `dagligen`, `vardagar`, `helger`, `anpassad` (även "specifika dagar"), `intervall` (även "var x:e dag"); okänt → `daily` som 3.x `Upprepning.fromString`. |
| `ReceptJson.dagar` | `prescriptions.schedule.days` | 0 = måndag … 6 = söndag → ISO 1…7. Bevaras oavsett upprepning. |
| `ReceptJson.intervalDagar` | `prescriptions.schedule.intervalDays` | Bevaras oavsett upprepning. |
| `ReceptJson.anteckning` | `prescriptions.note` | Arvsfält, som `MedicinJson.anteckning`. |
| `ReceptJson.aktiv` | `prescriptions.active` | |
| `ReceptJson.skapad` | `prescriptions.createdAt` | Datum → midnatt Europe/Stockholm (dagen går att läsa tillbaka exakt). |
| `ReceptJson.startDatum` | `prescriptions.period.start` | `""` → `null`: ingen bakre gräns, intervallet räknas från skapandedagen (REC-4, REC-7). |
| `ReceptJson.slutDatum` | `prescriptions.period.end` | `null`/`""` → `null` (tills vidare). |
| `ReceptJson.dosperioder` | `prescriptions.boosts` | |
| `DosperiodJson.id` | `prescriptions.boosts[].id` | |
| `DosperiodJson.startDatum` | `prescriptions.boosts[].start` | |
| `DosperiodJson.slutDatum` | `prescriptions.boosts[].end` | `null`/`""` → `null` (till periodens slut). |
| `DosperiodJson.dos` | `prescriptions.boosts[].dose` | Text. |
| `DosperiodJson.enhet` | `prescriptions.boosts[].unit` | Bevaras fast den speglar receptets enhet. |
| `FavoritJson.id` | `prnMedicines.id` | Bevaras. |
| `FavoritJson.namn` | `prnMedicines.name` | |
| `FavoritJson.dos` | `prnMedicines.dose` | Text. |
| `FavoritJson.enhet` | `prnMedicines.unit` | |
| `FavoritJson.tidpunkt` | `prnMedicines.slot` | Som `MedicinJson.tidpunkt`. |
| `FavoritJson.anteckning` | `prnMedicines.note` | Arvsfält. |
| `FavoritJson.minTidMellan` | `prnMedicines.minHoursBetween` | |
| `FavoritJson.dispenseringsTid` | `prnMedicines.dispensingTime` | Fritext; `""` → `null`. Visas inte i UI:t men bevaras. |
| `FavoritJson.maxDoserPerDag` | `prnMedicines.maxPerDay` | 0 = obegränsat. |
| `FavoritJson.isFavorite` | `prnMedicines.favorite` | |
| `SjukdomsEpisodJson.id` | `illnessEpisodes.id` | Bevaras. |
| `SjukdomsEpisodJson.typ` | `illnessEpisodes.type` | |
| `SjukdomsEpisodJson.startDatum` | `illnessEpisodes.start` | |
| `SjukdomsEpisodJson.slutDatum` | `illnessEpisodes.end` | `""` → `null` (pågående). |
| `SjukdomsEpisodJson.anteckning` | `illnessEpisodes.note` | Arvsfält. |
| `SjukdomsEpisodJson.timestamp` | `illnessEpisodes.createdAt` | Epok-ms; 0 (v1) → `null`, aldrig "nu". |
| `SjukdomsIncheckningJson.id` | `checkins.id` | Bevaras. |
| `SjukdomsIncheckningJson.episodId` | *sökväg* | `illnessEpisodes/{episodId}/checkins/{id}`; saknas episoden i backupen stoppar konverteraren. |
| `SjukdomsIncheckningJson.datum` | `checkins.date` | |
| `SjukdomsIncheckningJson.tid` | `checkins.time` | |
| `SjukdomsIncheckningJson.svarighetsgrad` | `checkins.severity` | |
| `SjukdomsIncheckningJson.symptom` | `checkins.symptoms[].optionId`, `checkins.symptoms[].score`, `checkins.symptoms[].customText` | Som `AktivitetJson.symptom`. |
| `SjukdomsIncheckningJson.somatiska` | *beräknas* | Som `AktivitetJson.somatiska`. |
| `SjukdomsIncheckningJson.anteckning` | `checkins.note` | Arvsfält. |
| `SjukdomsIncheckningJson.timestamp` | `checkins.createdAt` | Som episodens. |
| `HandelseJson.id` | `events.id` | Bevaras. |
| `HandelseJson.timestamp` | `events.createdAt` | Som `AktivitetJson.timestamp`. |
| `HandelseJson.datum` | `events.date` | |
| `HandelseJson.tid` | `events.time` | |
| `HandelseJson.typ` | `events.optionId` | Namn → händelsetypsalternativ; okänt namn → arkiverat alternativ. |
| `HandelseJson.svarighetsgrad` | `events.severity` | |
| `HandelseJson.varaktighetMinuter` | `events.durationMinutes` | |
| `HandelseJson.triggers` | `events.triggers` | `""` → `null`. |
| `HandelseJson.atgarder` | `events.actions` | `""` → `null`. |
| `HandelseJson.anteckning` | `events.note` | Arvsfält. |
| `NoteJson.target` | `activities`, `screenings`, `doses`, `prescriptions`, `prnMedicines`, `events`, `illnessEpisodes`, `checkins` | `ACTIVITY`, `SCREENING`, `MEDICATION`, `RECEPT`, `FAVORIT`, `EVENT`, `SJUKDOM_EPISOD`, `SJUKDOM_INCHECKNING` i den ordningen. |
| `NoteJson.entityId` | *sökväg* | Dokumentets id. En anteckning utan sin post rapporteras av konverteraren som varning med antal (Migrering, punkt 1). |
| `NoteJson.text` | `activities.note`, `screenings.note`, `doses.note`, `prescriptions.note`, `prnMedicines.note`, `events.note`, `illnessEpisodes.note`, `checkins.note` | Tom text → `null`. |
| `ScreeningEventConfigJson.enabled` | `settings.reminders.screeningOccasions[].enabled` | |
| `ScreeningEventConfigJson.time` | `settings.reminders.screeningOccasions[].time` | |
| `MedNotificationConfigJson.tidpunkt` | `settings.reminders.medSlots[].slot` | Matchas på namnet, annars på positionen, som 3.x `toMedNotificationConfigs`. |
| `MedNotificationConfigJson.enabled` | `settings.reminders.medSlots[].enabled` | |
| `MedNotificationConfigJson.time` | `settings.reminders.medSlots[].time` | |
| `SymptomOptionBackup.name` | `options.name` | |
| `SymptomOptionBackup.isFavorite` | `options.favorite` | |

Nya fält i 4.0 utan 3.x-motsvarighet: `doses.prnId`, `options.archived`.

\* *metadata* (bara `BackupJson.version` och `BackupJson.createdAt`): **backupfilens** metadata, inte
användarens data – de beskriver filen (formatversion och när den skrevs), inte något användaren har
loggat eller ställt in. `version` väljer v1- eller v2-tolkning i konverteraren och `createdAt` visas i
importens sammanfattning; ingen av dem har eller behöver en plats i Firestore.

## Designspråk · I "Papper och teal"

Lugn dagbokskänsla med luft, en tydlig gör-något-färg och belöning när dagen är klar.
Tokens i `ui/theme` (`AppColors`, `AppTypography`, `AppShapes`, `Spacing`); feature-kod
hårdkodar aldrig. Värdena nedan är mockupens approximation – kontrast räknas vid implementation.

| Roll | Ljust | Användning |
|---|---|---|
| Yta | papper `#FBF7EE` | bakgrund |
| Kort | vit, utan kantlinje | alla sektions- och postkort |
| Primär | teal `#0B6E66` | kryss, knappar, aktiv flik, idag-chip |
| Gör-något | solgul `#F5B631` (mörk text) | plusknapp, "Snart", punkten för idag, framstegsraden när dagen är klar |
| Varning | terrakotta `#B85C38` | försenat, periodslut |
| Text / dämpad | `#1C1A14` / `#6B655A` | brödtext / undertext och avbockat |
| Energiskala | låg `#B5443A` · mitt `#C98A1B` · hög `#2F7D5B` | diagram, energichips |

- **Typografi:** Fraunces 500/600 för rubriker (serif), Figtree för brödtext. Typsnitten
  bundlas i `res/font/` (OFL) så appen ser likadan ut offline.
- **Form:** kort 22 dp, chips och knappar helt rundade, datumremsans chip 18 dp, ark 28 dp upptill.
- **Rörelse och belöning:** fjädrande kryss (Expressive spring) som tonar raden; framstegsrad
  som fylls animerat; när alla doser och måendeloggar är klara byter rubriken till "Allt klart
  för idag", framstegsraden blir solgul, konfetti faller **en gång** och ett grönt kort
  sammanfattar dagen (snittenergi, jämförelse med igår, dagar i rad). Rörelse blockerar aldrig.
- **Mörkt tema** härleds med samma roller (djup skogsgrön botten); kontrast kontrolleras i båda.
- **Ikoner:** linjeikoner 24 dp i kontroller, aldrig emoji som knappikon.
- **Appikon "Bladet" (DSN-7):** adaptiv – teal bakgrund, pappersark med tre grå textrader och en
  solgul trendkurva; enfärgat lager för temaikoner. Färgerna är resurser i `values/colors.xml` som speglar `AppColors`.
- **Skillnad mot ReseApoteket:** inget korall/aprikos, ingen Nunito, serif-rubriker, luftigare kort.

---

## Komponentkatalog

| Källa | Komponenter |
|---|---|
| **Från ReseApoteket (G, nästan rakt av)** | `EntityListScreen`, `EntityEditScreen`, `EntityDetailScreen`, `EditorState`, `ListUiState`, `AppButton`, `AppIconButton`, `AppTextField`, `AppTopBar`, `AppBottomSheet`, `AppMenu`, `AppSegmentedChoice`, `AppFilterChip`, `ChipRow`, `ChoiceChips`, `CheckRow`, `SwitchRow`, `ItemRow`, `SectionHeader`, `GroupLabel`, `InfoPill`, `EmptyState`, `LoadErrorState`, `AppLoading`, `ConfirmDialog`, `UndoSnackbar`, `ErrorSnackbar`, `SwipeToHide`, `DateField`, `TimeField`, `PickerField`, `QuantityStepper`, `Confetti`, `ComponentGallery` |
| **Från Dagboken 3.x (portas till katalogen)** | `DagbokenEntryCard` → postkortet (NFR-15/16), `Foldout`, `SliderRow` + `GradientSliderRow` → `ValueSlider` (ett reglage, färgat åt båda hållen), `WheelPicker`, `DagbokenCalendar`, `StepwiseScreeningForm`, `SymptomLogCard`, `NoteField`, `StatPill`, `DateTimeRow`, `DurationRow`, `ReminderTimeRow`, hela `ui/diagram` (`LineChartCanvas` → `LineChart` på Vico 3, `IntervalBarChart`, `StackedBarChart`, `MinMaxCaption`, `CompactDropdownButton`, `ChartSemantics`, `SparklineChart`; `SmartYAxis` och `TrendLine` är matematik och ligger i `:core/engine`) |
| **Nya (NY KOMPONENT i mockupen)** | `DateStrip` (datumremsa), `OccasionRow` (tillfällesrad för mående), `ProgressBar` (framstegsrad), `DayDoneCard` (dagen klar), `AccountAvatar` (avataren som öppnar inställningsarket). `AccountSheet` och `LogMenuSheet` byggdes i etapp 1; diagramgrupperna i Trender (Mående · Klocka · Jämför, TRD-19) är `AppSegmentedChoice` |

Varje komponent: en fil i `ui/components/`, Roborazzi ljust + mörkt, plats i `ComponentGallery`,
rad i skill `shared-ui-components`. Diagrammatematik (`computeSmartYAxis`, `computeTrendLine`,
`computeDailyEnergyStats`, sömnkvalitet) flyttar till `:core`.

---

## Lager och moduler

```
:core  (ren Kotlin/JVM)   model/ · time/ (HOME_ZONE) · schema/ (DocCodec, Fields, Schema, SchemaMigrator) · codecs ·
                          engine/ (Dosing, EnsureDoses, Cooldown, PeriodEndings,
                          PrescriptionRules, DailyEnergyStats, SleepQuality, chart math) · legacy/ (BackupJson → 4.0-konverterare, validering
                          mot DocumentRules, ConvertBackupMain för grinden OMB-4)
:app   (Android)          data/common · data/firestore · data/auth · data/health (Health Connect,
                          read-only) · data/legacy (Room-läsare för migrering) · reminders/ ·
                          ui/theme · ui/components · ui/common · ui/<flik> · navigation/
tools/db                  query · get · stats · export · import · migrate · test/ (rules, roundtrip)
```

Compose → ViewModel (`StateFlow<UiState>`) → Repository → `FirestoreCollection<T>` → Firestore.
Fel mappas en gång i `data/common/DataError`. Ingen Room i 4.0 utom den läsande legacy-modulen.

---

## Byggkonfiguration

| Inställning | Värde | Var |
|---|---|---|
| compileSdk / targetSdk / minSdk | 37 / 35 / 30 | `app/build.gradle.kts` |
| AGP · Kotlin · Gradle | 9.x · 2.4 · 9.8 | `gradle/libs.versions.toml`, `gradle/wrapper` |
| Compose BOM · Material 3 | 2026.09 · 1.5 (Expressive, pinnad) | version catalog |
| Java/JVM | 17 | rotens `build.gradle.kts` (`subprojects`) |
| Testtimeout per task | 4 min | rotens `build.gradle.kts` |
| cpdCheck-tröskel | 80 tokens, `*Preview.kt` och den ordagranna 3.x-kopian `core/src/test/…/legacy/threex/` (kompatibilitetstestet, OMB-8) undantas | rotens `build.gradle.kts` |
| Version | `version.properties` (versionCode, versionName) | höjs bara vid release |
| google-services | `app/google-services.json` (incheckad); `src/authStub` när den saknas | `app/build.gradle.kts` |
| Signering | `local.properties` eller env `SIGNING_*` | `app/build.gradle.kts` |

Trösklar och versioner ändras bara här och i filen de pekar på, med motivering i PR:en.

---

## Migrering – ingen data får tappas

1. **Konverterare i `:core`** (`legacy/BackupJsonConverter`): 3.x `BackupJson` (v1 och v2, inklusive
   arvsfälten `anteckning` på posterna och `tidpunkt` på receptet; klasserna i `legacy/BackupJson.kt` speglar
   3.x fält för fält) → 4.0-dokument enligt paritetstabellen. Fixtur med **varje** fält satt
   (`tools/db/test/fixtures/legacy/backup-v2.json`, plus `backup-v1.json`) och förväntad export
   (`*.expected.json`). Test: konvertera → exportera → fältvis jämförelse mot förväntat
   (`BackupJsonConverterTest`); varje 3.x-fält har ett icke-default-värde i fixturen (`BackupJsonTest`) och varje
   4.0-fält får ett värde ur den. Utfallet är antingen alla dokument (`ConversionResult.Converted`, sorterade på
   sökväg, med en rapport: antal per samling och varningar) eller ett **stopp** (`Stopped`) med **alla** fel –
   aldrig en del. Rapporten pekar på 3.x-id:n (UUID, `recept_…`) och fält; alternativ anges som lista och
   plats (`options/symptom#3`, id:t bär namnet), okända värden och ogiltiga id:n med sin längd. Deterministisk: ingen klocka, samma backup ger samma dokument överallt; alternativ-id ur
   `OptionIds.of`.
   **Validering mot rules-gränserna:** 3.x hade inga längdgränser eller intervall i lagringen, men
   4.0:s rules har det (`TextLimits.SHORT` 200 tecken för namn och korta texter, `TextLimits.LONG`
   5 000 för anteckningar och fritext; heltalsintervallen som 0–10, −10..10, 0–23 och ≥ 0; listtaken 50
   symptom och 50 doshöjningar; giltiga datum och klockslag; enumvärdena). Gränserna står på **ett** ställe i
   `:core`, `schema/DocumentRules` (fält för fält per samling som `valid…`-funktionerna i rules;
   `DocumentRulesTest` läser rules och kräver likhet). Konverteraren validerar **varje genererat
   dokument** mot den och **stoppar med en rapport** (dokument, fält, värdets längd, tal eller enum-namn – aldrig
   innehållet) om något inte ryms. Den kapar, avrundar eller hoppar aldrig över ett värde; beslutet om en gräns
   ska höjas tas här och i rules, inte i datan. Valideringen behövs även där rules inte gäller: `tools/db
   import.mjs` skriver med admin-SDK förbi rules, så utan den kunde grinden OMB-4 släppa igenom data som appen
   sedan inte kan spara om. `tools/db/test/legacy.test.mjs` skriver dessutom varje dokument i de förväntade
   exporterna som ägaren genom rules, så att valideringen bevisligen motsvarar dem.
   **Val som inte står i paritetstabellen** (alla deterministiska, inga "nu"):
   - *Tider.* Tidszonen är alltid Europe/Stockholm (`legacy/LegacyTime`), aldrig enhetens. `tagenTid` och
     reservvärdet för ett tomt eller ogiltigt `timestamp` (dag + klockslag; utan klockslag midnatt) följer
     sommartidsregeln: i luckan när klockan ställs fram (sista söndagen i mars, 02:00–03:00 finns inte) flyttas
     klockslaget fram med luckans längd (`02:30` → `03:30` sommartid); vid överlappningen när klockan ställs
     tillbaka (sista söndagen i oktober) gäller den **första** förekomsten (sommartid, UTC+2). Båda fallen ger
     en varning i rapporten. Ögonblicket läses tillbaka till samma dag; ett ogiltigt `timestamp` ersätts med varning. Exportfilens `exportedAt` är
     backupens `createdAt` (3.x lokal tid utan zon, tolkad i Stockholm), saknas den epoken.
   - *Anteckning utan sin post* (`notes` vars `entityId` inte finns, eller okänt `target`) och anteckning med
     text men utan `target` eller `entityId`: **varning** med antal, inte stopp. Posten var redan borta i 3.x (anteckningar visades bara genom sin post, och
     före DAT-4 kunde raderingar lämna dem kvar), så inget användaren kunde se tappas, och ett stopp hade
     ingen åtgärd – backupen går inte att laga. Räknas i rapporten så att grinden OMB-4 ser antalet.
     Finns både en `notes`-post och arvsfältet med olika text går `notes` före (som 3.x) med varning.
   - *Incheckning utan sin episod:* **stopp** – det finns ingen sökväg, och 3.x hade en främmande nyckel som
     gjorde en sådan backup omöjlig att återställa. En episod skapas aldrig på gissning.
   - *Alternativ.* V2-listorna går före v1; saknas listan (`null`) gäller 3.x standardlista
     (`DEFAULT_AKTIVITET_OPTIONS`, `DEFAULT_SYMPTOM_OPTIONS`, `DEFAULT_HANDELSE_TYP_OPTIONS`), en tom lista
     förblir tom. Aktivitetsnamn utanför listan är fritexten vid "Övrigt" → `customText` + alternativet "Övrigt"
     (skapas synligt, sist, om listan saknar det – 3.x hade det alltid som fast sentinel). Symptomnamn och
     händelsetyper utanför listan blir nya **arkiverade** alternativ; `Övrigt (fritext)` (även utan parenteser:
     allt efter "Övrigt") → "Övrigt" + `customText`. Dubbletter i en lista är samma alternativ (det första
     gäller, varning). `sortOrder` är listans ordning, skapade alternativ fortsätter numreringen.
   - *Symptomsträngen* tolkas som 3.x `SymptomUtils.decode` (sista kolonet skiljer namn och poäng, dubbla namn
     samlas och den sista poängen gäller – med varning); en del utan kolon, utan namn eller med en poäng som
     inte är ett heltal kunde 3.x inte läsa och **stoppar**. Avviker `somatiska` från summan: varning.
   - *Poster.* Saknat `type` är "aktivitet" (klassens default, som 3.x); bara tomt `type` härleds ur namnet
     (`inferType`). En aktivitet vars namn är ett måendetillfälles får en varning. En screening med
     `aterhamtande`/`energitjuv` sant eller `spentTime` satt **stoppar** (ingen plats). Blank `datum`/`tid` →
     `null`; ogiltigt **stoppar** (även ett datum som inte finns, `2026-02-30`, fast rules mönster godtar det).
     Dubbla id:n i en samling och id:n Firestore inte godtar stoppar. Tagen och överhoppad samtidigt → `taken`.
   - *Recept.* Okänd `upprepning` → `daily` (som 3.x `Upprepning.fromString`) med varning; `dagar` utanför 0–6
     stoppar; blank `tidpunkt` räknas som saknad (båda tomma → Morgon). `skapad` → midnatt Stockholm.
   - *Inställningar.* Dokumentet innehåller **bara** de fält backupen hade (`null` = "rör inte"; v1 ger inget
     dokument alls); de typade modellerna får 3.x-defaults i övrigt. `medNotificationConfigs` matchas på
     namn, annars på position för rader utan namn (3.x `toMedNotificationConfigs`); en rad utan tidpunkt att
     höra till stoppar, liksom ett femte `screeningEventConfigs`-element, okänt `themeMode`/`sex` och
     ogiltiga klockslag. Tomt klockslag → standardtiden.
   - *Användardokumentet* `users/{uid}` skrivs med `schemaVersion = Schema.CURRENT_VERSION` och inget mer
     (`createdAt` sätts aldrig på gissning; `EnsureUserUseCase` skriver inte över ett befintligt dokument).
2. **Rundtur mot emulatorn** (`tools/db/test/roundtrip.test.mjs`): import → export identiskt.
3. **Grind före etapp 3:** en riktig Drive-backup konverteras (med valideringen i punkt 1 – noll
   stopp), importeras via `tools/db import.mjs`, exporteras och jämförs med originalet. Noll
   skillnader krävs (OMB-4). **Mot ett tomt scratch-uid**, aldrig användarens riktiga uid: `import.mjs`
   skriver dokumenten som de är (`set` utan merge), så mot ett befintligt konto skulle `users/{uid}.createdAt`
   och inställningsfält som backupen saknade försvinna – "`null` = rör inte" gäller bara legacy-läsaren på
   enheten, som skriver med merge (etapp 3). Körningen, från repots rot och med backupen i den
   git-ignorerade `tools/db/`:
   ```bash
   ./gradlew :core:convertLegacyBackup --args="--in tools/db/backup-3x.json --out tools/db/export-4.json --user <scratch-uid>"
   node tools/db/import.mjs --in tools/db/export-4.json --dry-run      # kontroll, skriver inget
   node tools/db/import.mjs --in tools/db/export-4.json                # skrivande nyckel, uttrycklig begäran
   node tools/db/export.mjs --user <scratch-uid> --out tools/db/export-igen.json && cmp tools/db/export-4.json tools/db/export-igen.json
   ```
   Konverteraren skriver rapporten (antal per samling, varningar – även klockslag som flyttats av
   sommartidsbytet – och stopp) till stdout utan något innehåll; exitkod 0 = filen skrevs, 1 = stopp (ingen
   fil), 2 = fel argument, ogiltigt uid, saknad infil eller befintlig utfil (skrivs över bara med `--force`).
   Rapportens varningar granskas av användaren innan importen; scratch-användaren raderas efter grinden och
   filerna raderas (skill `data-privacy-security`).
4. **På enheten** (etapp 3, `data/legacy` + `core/legacy/LegacyRoomAssembler`, OMB-2, OMB-7, OMB-8):
   - *Startkontroll (NAV-6):* Room-filen `dagboken.db` finns, flaggan för kontot saknas i 4.0:s egen
     DataStore-fil `dagboken_device` (aldrig 3.x-filen) **och** servern har ingen markör
     `users/{uid}.legacyMigration`. Finns markören sätts flaggan och ingen migrering erbjuds, oavsett enhetens
     läge – en omkörning får aldrig skriva över data som 4.0 hunnit ändra. Finns filen avbokas 3.x:s backupjobb
     (`dagboken_daily_backup`) vid varje start tills det lyckats (flagga per installation i `dagboken_device`, satt
     först efter lyckad avbokning, oberoende av kontots flagga; fel fångas), och 3.x-sessionens konto sparas vid första
     starten, så att bekräftelsen kan varna för ett annat konto. Utan nät kan kontrollen inte avgöras (`Offline`).
   - *Läsning:* `SQLiteDatabase.OPEN_READONLY` (inget Room-beroende), `PRAGMA user_version` måste vara 11 (annars
     tillståndet "äldre version"), WAL-filen läses utan checkpoint; `dagboken_prefs` via `PreferenceDataStoreFactory`
     i ett eget scope, en läsning, utan `ReplaceFileCorruptionHandler`. Raderna och nycklarna lämnas råa till
     `LegacyRoomAssembler` i `:core`, som speglar 3.x `BackupAssembler` fält för fält (JSON-kolumnerna och
     alternativlistorna i båda 3.x-formerna; ett värde 3.x inte kunde läsa ger 3.x:s standardvärde med varning; ett
     blankt temaläge räknas som saknat; en talkolumn med fel typ läses som 0 med varning; arvsfälten `anteckning`
     finns inte i v11 och är tomma). Kontrollsumman över `.db` och `-wal` (inte `-shm`) följer med som filens
     fingeravtryck. Sedan **samma** konverterare som grinden; "före" = rapportens antal per entitet.
   - *Obligatorisk kopia (OMB-8):* innan något skrivs sparas 3.x-datan som en 3.x-backupfil (`BackupJson.encode`,
     format v2, UTF-8 utan BOM) till en fil användaren väljer (SAF) och verifieras: läses tillbaka och parsas med
     `BackupJson.parse`, antalet per entitet jämförs med Room-raderna, konverteraren ska gå igenom utan stopp och
     ge exakt samma dokument. Kopian sparas på enheten med fingeravtryck, tid, filnamn och antal och gäller bara
     Room-filen som den såg ut då; ändras filen krävs en ny kopia. `Backup3xCompatibilityTest` parsar filen med
     3.27.0:s egna klasser (kopia i testkällan).
   - *Läget på servern först (återupptagning, OMB-7):* målens id läses direkt från servern (`RawDocuments.documents`:
     `documentId() in …` i grupper om 30, åtta parallellt – aldrig hela samlingar) och ställs mot **liggaren**
     (`MigrationLedger`: egen appprivat fil `files/legacy-migration/<uid>.ledger`, bara tillägg per batch –
     `W sökväg hash` för skrivet med kvitto, `V sökväg hash` för verifierat (hashen över det som skrevs), `M sökväg hash`
     för avvikande i verifieringen (hashen över serverns felaktiga läge då, eller `MISSING`), `P sökväg hash` för
     intentionen **före** batchens commit (W först efter kvittot; P ersätter aldrig en tidigare V-/M-rad och tas bort av
     nästa W/V/M), så att ett skrivet dokument aldrig faller ur liggaren och en skrivning som inte landade aldrig tränger
     undan det tidigare tillståndet; bara rader med giltigt format (64 hex eller `MISSING`) räknas och ett tillägg börjar
     på ny rad – läses en gång
     per körning i ordning, sista raden per sökväg gäller, raderas vid bekräftelsen; inte DataStore, som skriver om hela
     mängden varje gång; IO-fel → `Failed` med `LEDGER_FAILED`). Likhet avgörs på **ett** sätt: hashen
     (`MigrationLedger.hashOf`) över den kanoniska formen (`canonical`: nycklar sorterade rekursivt, också i mappar
     inne i listor som symptom och doshöjningar – Firestore ger dem i godtycklig ordning – begränsade till samlingens
     **fasta fältmängd** `DocumentRules.fieldTree`), beräknad en gång per dokument: framtida 4.0-fält rör inte utfallet,
     ett fält som tömts i 3.x är ett nytt 3.x-värde. **Förenklad policy:** så fort liggaren har en rad har flytten börjat
     – startkontrollen (`isPending`: Room-filen först, sedan liggaren utan serverfråga) och `MigrationGate` visar skärmen
     tills `confirm()` eller `abort()` är klar, "Inte nu" finns bara före första skrivningen. Bara flyttens egna dokument
     antecknas: ett som var lika redan före första skrivningen (t.ex. efter en OMB-4-import) får ingen V-rad; en batch
     som avvisas definitivt stryker sina P-rader (`X`), en timeout lämnar dem. Serverläsningar väntar inte in
     `waitForPendingWrites` (kan ta en minut); dokument med `metadata.hasPendingWrites` utelämnas och räknas som
     overifierade. Per sökväg: saknas → skrivs; serverns hash = vår → verifierad
     (V-rad); antecknad (P/W/V/M) men olika → skrivs om helt inom fältmängden (`withDeletions`) – ändringar under flytten,
     även från en annan enhet, skyddas inte; aldrig vårt och olika → bara saknade fält (`existing`). Inga `kept`/`removed`.
     *Avbryt flytten* (`abort()`): raderar exakt liggarens sökvägar (`RawDocumentWriter.deleteBatch`, incheckningar före
     episoder, ≤ 500 per batch, serverns kvitto), läser tillbaka på id, kräver tomt, rensar sedan liggaren; dokument som
     fanns före flytten (bara ifyllda, ingen rad) rörs aldrig. Valt framför per-dokument-domar (kept/removed): en regel
     som går att förklara på skärmen, och ingen risk att felaktig data verifieras som rätt. Jämförelser, hashar och
     batchplanen körs på `Dispatchers.Default` (injicerad). Valt framför en `writtenIds`-markör på servern: tusentals id:n ryms inte i ett
     dokument (1 MiB) och skulle kräva egna rules, medan enhetens fil delar öde med Room-filen. **Godtaget kantfall:**
     en batch som fick timeout kan landa senare; har användaren hunnit radera ett sådant dokument i 4.0 före nästa körning
     räknas det som "saknas" och skrivs igen. Vid timeout stannar körningen direkt, så inga fler batchar köas.
   - *Skrivning:* bara det som saknas, råa dokument (`RawDocumentWriter`) med merge i `MigrationBatches` form – högst
     500 skrivningar och högst 20 episoder per batch, episoden i samma batch som sina incheckningar (`existsAfter`), en
     episod med fler incheckningar än som ryms delas med episoden först; `users/{uid}` skrivs inte. Varje batch väntar
     in serverns kvitto (inte offline först, NFR-1); ett fel stannar med Firestores felkod i rapporten
     (`RESOURCE_EXHAUSTED` → `DataError.QuotaExceeded`, eget meddelande) utan markör och kan göras om.
   - *Verifiering per id:* de skrivna dokumenten läses tillbaka på id (`RawDocuments` väntar först in enhetens köade
     skrivningar – `Source.SERVER` säger inget om dem) och hashas inom samlingens fasta fältmängd (fält utanför den, t.ex.
     framtida 4.0-fält, rör inte utfallet; delvis fyllda dokument jämförs bara på det som fylldes); "efter" = verifierat
     lika per entitet, före = efter + existing. En avvikelse är ett eget tillstånd med "Försök igen" (skriver om det som
     skiljer sig)/"Avbryt flytten"; avvikande sökvägar antecknas som `M` med serverns hash.
   - *Bekräftelse:* markören `legacyMigration {completedAt == request.time, source, sourceCreatedAt, appVersion,
     counts}` – exakt de fälten (`keys().hasOnly`), `counts` = rapportens "före" med bara kända samlingar som heltal –
     skrivs **sist** (rules: en gång, aldrig ändrad eller borttagen; `LegacyMigrationCodec`,
     `DocumentRules.LEGACY_MIGRATION`), flaggan sätts per konto och liggaren raderas. **Idempotent:** nekas eller timar
     markören ut men finns på servern räknas bekräftelsen som lyckad. Påminnelserna pausas **bara medan `write()` körs**
     (`LegacyMigrationPause`, i minnet med startvärde av, satt i try/finally) – `ReminderSync` hoppar över ny
     schemaläggning under pausen och **avbokar aldrig** något; när pausen släpps läggs larmen som vanligt. Inget utfall
     (fel, avvikelse, skrivet utan bekräftelse), ingen utgång från skärmen och ingen processdöd kan lämna larmen av.
   - *Kontot:* 3.x-sessionen fångas i `Application.onCreate`, före inloggningsgrinden (`LegacySessionCapture`:
     `FirebaseAuth.currentUser`, som 3.x:s inloggning bevarats i) och sparas en gång; bekräftelsen visar samma/annat/
     okänt konto – okänt kräver att e-posten bekräftas uttryckligen. 3.x:s WorkManager-jobb avbokas vid varje start tills det lyckats;
     WorkManager initieras på begäran (`Configuration.Provider`, startup-initieraren borttagen) eftersom 4.0 inte har
     några workers och det kvarliggande jobbet inte ska kunna köras före avbokningen.
     Fallback utan Room-fil: Drive-backup eller lokal JSON (#230). Room-filen och DataStore-filen ändras aldrig och
     raderas aldrig automatiskt.
5. **Legacyimport** (Inställningar → Export och import) behålls minst en version efter 4.0.
6. Firestore-sidan: `schemaVersion` på `users/{uid}`, `SchemaMigrator` i `:core`, `migrate.mjs`
   i `tools/db`; appen vägrar skriva mot en okänd högre version.

---

## CI, verktyg, skills och agenter

- **CI** från ReseApoteket: `android.yml` med `dorny/paths-filter` (core, app, tools, build,
  instrumented), ett Gradle-jobb (`cpdCheck`, `:core:test`, `:app:testDebugUnitTest`,
  `verifyRoborazziDebug`), `rules`-jobb mot emulatorn, `ci-ok` som enda obligatoriska kontroll;
  `quality.yml` måndagar (lint, release-bygge); `backup.yml` måndagar (krypterad export);
  `release.yml` med grindar. Budget ~8 Actions-minuter per PR; instrumenttester bara när
  berörda sökvägar ändrats. Dagbokens `instrumented.yml` (25–30 min varje PR) pensioneras.
- **Hooks:** `regel4-check.mjs` + `ui-forbidden.txt`/`ui-allowlist.txt`; `session-start.sh`
  (npm ci för `tools/db`, rapporterar om `FIREBASE_SERVICE_ACCOUNT` finns).
- **Skills som tas över (G/G\*):** `ci-budget`, `shared-ui-components`, `firestore-data-layer`,
  `data-safety-backup` (Firestore-versionen), `db-access`, `mockup`, `ui-style` (skrivs om för
  Papper och teal), `testing-strategy`, `refine-issue`, `implement-issue`, `release`,
  `android-gradle-logic`, Googles `navigation-3`, `android-intent-security`,
  `firebase-security-rules-auditor`.
- **Skills som behålls från Dagboken:** `notifications-alarms`, `data-privacy-security`
  (hälsodataversionen), `accessibility-compose`, `android-dev`, `compose-expert`,
  `kotlin-coroutines`, `kotlin-flows`, `firebase-auth`.
- **Nya skills:** `health-connect` (destillerat ur `HealthConnectRepository`, etapp 6), `diagram`
  (ui/diagram-reglerna, TRD-6…18 – byggd i etapp 4.3).
- **Pensioneras:** `room-migrations`, `android-data-layer`.
- **Agenter** (`.claude/agents/`, läggs i etapp 1-PR:en) med modell och effort i frontmatter, så
  att huvudsessionen kan delegera varje steg till rätt nivå utan att själv byta modell:

  | Agent | Modell · effort | Används för |
  |---|---|---|
  | `byggare` (ny) | Opus 5.5 · medium | portar, komponenter, skärmar på ramarna, CI-filer |
  | `arkitekt` (ny) | Fable 5.1 · high | datamodell, codecs, konverterare, rules, migrering |
  | `testskrivare` | Sonnet 5.5 · medium | tester på tre eller fler nivåer, fixturer |
  | `granskare` | Sonnet 5.5 · medium | diffen mot reglerna före varje PR |
  | `ci-doktor` | Sonnet 5.5 · medium | röd CI, loggar, skärmdumpsdiffar |
  | `db-inspektor` | Haiku 4.5 · low | läsfrågor mot Firestore via `tools/db` |

  Regel: går ett steg fel två gånger i rad flyttas det upp en nivå (`byggare` → `arkitekt`,
  Sonnet → Opus). Huvudsessionen orkestrerar och granskar; den byter aldrig modell själv.
- **Kravlistan** behåller sina ID-serier; 3.x-lydelser som ändras står kvar strukna med
  hänvisning. NFR-15–18 (kort- och radstandarden) är mer genomarbetade än ReseApotekets och
  behålls som de är.

---

## Etapper, modell och effort

Grundregel: **Fable high** för allt som rör datamodell, migrering, rules och arkitekturval;
**Opus 5.5** för skärmar, komponenter och CI-arbete; **Sonnet 5.5** för mekaniska portar med
färdiga tester och för agenterna; **Haiku 4.5** bara för rena läsfrågor (`db-inspektor`).
Byt upp en nivå så fort något går fel två gånger i rad.

| Etapp | Innehåll | PR | Modell · effort |
|---|---|---|---|
| **0 · ADR och krav** | Denna fil, KRAVLISTA-justeringar, README-not. | 1 | Fable · high *(klar)* |
| **1 · Skelett och verkstad** | `:core` + `:app`, versionskatalog, tema-tokens för Papper och teal, `data/common`, `data/firestore`, `ui/common`, ramarna, `navigation/`, `tools/db` (users/{uid}), hooks, `android.yml`, `quality.yml`, `backup.yml`, skills, agenter, CLAUDE.md. Resultat: app som loggar in och visar `ComponentGallery`, CI på budget. | 3 | Opus · medium; CI-filer och rules: Fable · high |
| **2 · Datamodell och konverterare** | Modeller + codecs för alla samlingar, `settings`, `schemaVersion 1`, `firestore.rules`, `collections.mjs`, rundturstest, `BackupJsonConverter` med full fixtur. **Grind OMB-4.** | 4 | Fable · xhigh (konverterare, rules); Opus · high (codecs) |
| **3 · Migrering på enheten** | Legacy-Room-läsare, migreringsskärm med räkning och bekräftelse, fallback Drive/JSON, legacyimport i Inställningar. | 2 | Fable · high |
| **4 · Komponenter** | Port av Dagbokens komponenter + de sju nya, mockup per komponent, Roborazzi, galleri, skill-tabell. Diagrammatematik till `:core`. | 3 | Opus · medium; `ui/diagram`: Opus · high |
| **5 · Flikarna** | Inställningsark → Mediciner → Idag (inkl. belöningsläge) → Dagbok → Trender → formulär, sjukdomsdetalj, Hälsa idag. Use cases till `:core` med sina tester. Hälsa idag (#241) byggs här mot gränssnitten `HealthRepository` och `HealthPermissions` med `UnavailableHealthRepository`/`UnavailableHealthPermissions` bundna (skärmarna visar "Health Connect saknas") och testas mot fejkerna; den riktiga Health Connect-porten kommer i etapp 6. | 8–10 | Opus · high per skärm; Trender och Idag: Fable · high; enkla formulär på ramarna: Sonnet · medium |
| **6 · Larm, Health Connect, release** | Notiser mot Firestore-cachen (skill `notifications-alarms`), Health Connect-port uppdelad (hälsomotorn i `:core` #276, sedan porten #243 som byter bindningarna för `HealthRepository` och `HealthPermissions` – manifest, behörighetsflöde och läsning; skärmarna från etapp 5 ändras inte), `release.yml`, första release efter grön kravlista. | 3 | Opus · high (larm); Opus · medium (Health Connect); Sonnet · low (release) |

Varje PR: mockup godkänd före GUI, tester på alla berörda nivåer, kravrader avbockade i
PR-beskrivningen, `granskare` + `/code-review` före push, en PR i taget.

---

## Risker

| Risk | Hantering |
|---|---|
| Fält tappas i konverteringen | Fixtur med alla fält (varje 3.x-fält icke-default, varje 4.0-fält fyllt), fältvis jämförelse mot förväntad export, rundtur och rules-skrivning av den i emulatorn, grind OMB-4 med riktig backup, Room-filen behålls. Anteckningar utan sin post är det enda som inte får plats – de räknas i rapporten (Migrering, punkt 1). |
| 3.x-text längre än rules-gränserna (3.x hade inga) | `TextLimits.LONG` är 5 000 tecken för anteckningar, det enda 3.x-fältet som rimligen kan vara långt. Konverteraren validerar varje dokument mot `TextLimits` och rules-intervallen och stoppar med rapport – kapar aldrig (Migrering, punkt 1). Grinden OMB-4 omfattar valideringen, eftersom admin-importen går förbi rules. Visar en riktig backup längre text höjs gränsen i `TextLimits` och rules i samma PR. |
| Rules nekar riktig 3.x-data vid importen på enheten | Rules räknar högst 1 000 uttryck och 20 dokumentuppslag per skrivning: listor av objekt har generösa tak (50 symptom, 50 doshöjningar) och elementen kontrolleras upp till det tionde; incheckningar kräver sin episod (`existsAfter`) och en batch får slå upp högst 20 befintliga episoder, så importen skriver episoderna i samma batch som sina incheckningar eller delar upp per högst 20 episoder. Rules-testerna täcker gränserna; grinden OMB-4 går via `tools/db` (admin, förbi rules), så importen på enheten (etapp 3) prövar rules mot riktig data. |
| Spark-planens läsbudget vid första synk | Engångskostnad; cache därefter; "Allt"-perioden i Trender räknas ur cachen i `:core`. |
| Hälsodata i molnet | EU-region, rules bara för ägaren, krypterad export, ingen PII i loggar (skill `data-privacy-security`). |
| Larm tystnar när schemat ligger i cachen | Omschemaläggning vid synk, boot och appuppdatering (NOT-14); test mot `FakeCollection`. |
| Omfattning | Kravlistan är paritetschecklistan; etapperna i värdeordning; första release först när allt är grönt. |
