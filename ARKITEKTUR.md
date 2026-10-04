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

Allt under `users/{uid}`; dokumentet bär `schemaVersion` och `createdAt`. Modeller och codecs i
`:core` (`core/model`, `core/schema/*Codecs.kt`); KDoc på varje fält.

| Samling / dokument | Nyckelfält | Ersätter 3.x |
|---|---|---|
| `settings` (ett dokument, `settings/app`) | theme {mode `light` \| `dark` \| `auto`, lightStartHour, darkStartHour, isDarkTheme}, reminders {medsEnabled, medSlots[6] {slot, enabled, time}, screeningOccasions[4] {occasion, enabled, time}, periodReminderTime}, profile {birthYear?, sex `male` \| `female` \| `unspecified`} | DataStore + `SettingsBackup` |
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

Fyra förenklingar: anteckningen är fältet `note` på varje dokument (notes-tabellen och
kaskadraderingen försvinner); symptom lagras som `[{optionId, score, customText?}]` (summan
`somatiska` räknas i `:core`, fritexten vid "Övrigt" ligger i `customText`, AKT-6); poster
refererar alternativ via `optionId` så namnbyte aldrig behöver skriva om historiken (SET-11 blir
gratis); dosen har en `status` i stället för två booleaner.

**Id:n bevaras från 3.x** (DAT-13): poster, recept, vid behov-mediciner, episoder och
incheckningar behåller sina 3.x-id:n (UUID-strängar). Receptgenererade doser behåller 3.x-schemat
`recept_{prescriptionId}_{date}_{tidpunkt}` med tidpunktens 3.x-namn (`Morgon`, `Förmiddag` …,
`DoseIds.prescribed`), så att 4.0:s idempotenta generering (MED-4) träffar redan migrerade doser
och en upprepad import aldrig dubblerar (DAT-8). Alternativen hade inga id:n i 3.x; konverteraren
ger dem deterministiska id:n ur kind och namn (fastställs med konverteraren).

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
bland de nya fälten nedan, och att exakt två rader är *utelämnas*. Kolumn två anger samling och fältväg
(`[]` = element i en lista); *beräknas*, *sökväg* och *metadata* betyder att värdet bevaras utan eget
fält. Room-entiteterna (v11) bär samma fält som `BackupJson` (listorna som JSON-text i `recept`); de
enhetslokala DataStore-nycklarna `migration_done` och `backup_needs_auth` är inte användardata.

| 3.x | 4.0 | Anmärkning |
|---|---|---|
| `BackupJson.version` | *metadata* | Väljer v1- eller v2-tolkning i konverteraren; ingen användardata. |
| `BackupJson.createdAt` | *metadata* | Backupens tidpunkt; visas i importens sammanfattning, ingen användardata. |
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
| `BackupJson.sheetsConfig` | *utelämnas* | Sheets-exporten användes aldrig och kommer inte i 4.0 (FUT-2). |
| `BackupJson.periodReminderTime` | `settings.reminders.periodReminderTime` | `HH:mm`. |
| `BackupJson.settings` | `settings` | `null` i ett fält = "rör inte": konverteraren skriver då inte fältet (merge). |
| `SettingsBackup.medsNotificationsEnabled` | `settings.reminders.medsEnabled` | |
| `SettingsBackup.themeMode` | `settings.theme.mode` | Samma strängar `light`/`dark`/`auto`. |
| `SettingsBackup.themeLightStart` | `settings.theme.lightStartHour` | |
| `SettingsBackup.themeDarkStart` | `settings.theme.darkStartHour` | |
| `SettingsBackup.isDarkTheme` | `settings.theme.isDarkTheme` | Bevaras fast 4.0 bara läser `mode`. |
| `SettingsBackup.dynamicColor` | *utelämnas* | Reglaget finns inte i 4.0 (SET-3 struket); Papper och teal har fasta färger. |
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
| `AktivitetJson.type` | `activities`, `screenings` | `aktivitet`/`screening`; tomt → ur namnet som 3.x `BackupMapper.inferType`. |
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
| `MedicinJson.tagenTid` | `doses.takenAt` | `HH:mm` på dosens datum i Europe/Stockholm → tidsstämpel. |
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
| `SjukdomsIncheckningJson.episodId` | *sökväg* | `illnessEpisodes/{episodId}/checkins/{id}`. |
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
| `NoteJson.entityId` | *sökväg* | Dokumentets id. En anteckning utan sin post rapporteras av konverteraren. |
| `NoteJson.text` | `activities.note`, `screenings.note`, `doses.note`, `prescriptions.note`, `prnMedicines.note`, `events.note`, `illnessEpisodes.note`, `checkins.note` | Tom text → `null`. |
| `ScreeningEventConfigJson.enabled` | `settings.reminders.screeningOccasions[].enabled` | |
| `ScreeningEventConfigJson.time` | `settings.reminders.screeningOccasions[].time` | |
| `MedNotificationConfigJson.tidpunkt` | `settings.reminders.medSlots[].slot` | Matchas på namnet, annars på positionen, som 3.x `toMedNotificationConfigs`. |
| `MedNotificationConfigJson.enabled` | `settings.reminders.medSlots[].enabled` | |
| `MedNotificationConfigJson.time` | `settings.reminders.medSlots[].time` | |
| `SymptomOptionBackup.name` | `options.name` | |
| `SymptomOptionBackup.isFavorite` | `options.favorite` | |

Nya fält i 4.0 utan 3.x-motsvarighet: `doses.prnId`, `options.archived`.

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
| **Från Dagboken 3.x (portas till katalogen)** | `DagbokenEntryCard` → postkortet (NFR-15/16), `Foldout`, `SliderRow`, `GradientSliderRow`, `WheelPicker`, `DagbokenCalendar`, `StepwiseScreeningForm`, `SymptomLogCard`, `NoteField`, `StatPill`, `DateTimeRow`, `DurationRow`, `ReminderTimeRow`, hela `ui/diagram` (`LineChartCanvas`, `IntervalBarChart`, `StackedBarChart`, `SmartYAxis`, `TrendLine`, `MinMaxCaption`, `CompactDropdownButton`, `ChartSemantics`), `SparklineChart` |
| **Nya (NY KOMPONENT i mockupen)** | `DateStrip` (datumremsa), `OccasionRow` (tillfällesrad för mående), `ProgressBar` (framstegsrad), `DayDoneCard` (dagen klar), `ChartGroupTabs` (Mående · Klocka · Jämför), `AccountSheet` (inställningsarket), `LogMenuSheet` (plusknappens ark) |

Varje komponent: en fil i `ui/components/`, Roborazzi ljust + mörkt, plats i `ComponentGallery`,
rad i skill `shared-ui-components`. Diagrammatematik (`computeSmartYAxis`, `computeTrendLine`,
`computeDailyEnergyStats`, sömnkvalitet) flyttar till `:core`.

---

## Lager och moduler

```
:core  (ren Kotlin/JVM)   model/ · schema/ (DocCodec, Fields, Schema, SchemaMigrator) · codecs ·
                          engine/ (EnsureDoses, Cooldown, PeriodEndings, DailyEnergyStats,
                          SleepQuality, chart math) · legacy/ (BackupJson → 4.0-konverterare)
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
| cpdCheck-tröskel | 80 tokens, `*Preview.kt` undantas | rotens `build.gradle.kts` |
| Version | `version.properties` (versionCode, versionName) | höjs bara vid release |
| google-services | `app/google-services.json` (incheckad); `src/authStub` när den saknas | `app/build.gradle.kts` |
| Signering | `local.properties` eller env `SIGNING_*` | `app/build.gradle.kts` |

Trösklar och versioner ändras bara här och i filen de pekar på, med motivering i PR:en.

---

## Migrering – ingen data får tappas

1. **Konverterare i `:core`** (`legacy/BackupJsonConverter`): 3.x `BackupJson` (v1 och v2, inklusive
   arvsfälten `anteckning` på posterna och `tidpunkt` på receptet) → 4.0-dokument. Fixtur med
   **varje** fält satt. Test: konvertera → exportera → fältvis jämförelse mot förväntat.
2. **Rundtur mot emulatorn** (`tools/db/test/roundtrip.test.mjs`): import → export identiskt.
3. **Grind före etapp 3:** en riktig Drive-backup importeras via `tools/db import.mjs`, exporteras
   och jämförs med originalet. Noll skillnader krävs (OMB-4).
4. **På enheten** (etapp 3): första start av 4.0 hittar Room-filen, läser den med legacy-läsaren
   (samma mappning som konverteraren), skriver till Firestore i batchar, visar antal per entitet
   före och efter och låter användaren bekräfta. Fallback: Drive-backup eller lokal JSON.
   Room-filen raderas aldrig automatiskt.
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
- **Nya skills:** `health-connect` (destillerat ur `HealthConnectRepository`), `diagram`
  (ui/diagram-reglerna, TRD-6…18).
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
| **5 · Flikarna** | Inställningsark → Mediciner → Idag (inkl. belöningsläge) → Dagbok → Trender → formulär, sjukdomsdetalj, Hälsa idag. Use cases till `:core` med sina tester. | 8–10 | Opus · high per skärm; Trender och Idag: Fable · high; enkla formulär på ramarna: Sonnet · medium |
| **6 · Larm, Health Connect, release** | Notiser mot Firestore-cachen (skill `notifications-alarms`), Health Connect-port uppdelad, `release.yml`, första release efter grön kravlista. | 3 | Opus · high (larm); Opus · medium (Health Connect); Sonnet · low (release) |

Varje PR: mockup godkänd före GUI, tester på alla berörda nivåer, kravrader avbockade i
PR-beskrivningen, `granskare` + `/code-review` före push, en PR i taget.

---

## Risker

| Risk | Hantering |
|---|---|
| Fält tappas i konverteringen | Fixtur med alla fält, rundtur, grind OMB-4 med riktig backup, Room-filen behålls. |
| Rules nekar riktig 3.x-data vid importen på enheten | Rules räknar högst 1 000 uttryck och 20 dokumentuppslag per skrivning: listor av objekt har generösa tak (50 symptom, 50 doshöjningar) och elementen kontrolleras upp till det tionde; incheckningar kräver sin episod (`existsAfter`) och en batch får slå upp högst 20 befintliga episoder, så importen skriver episoderna i samma batch som sina incheckningar eller delar upp per högst 20 episoder. Rules-testerna täcker gränserna; grinden OMB-4 går via `tools/db` (admin, förbi rules), så importen på enheten (etapp 3) prövar rules mot riktig data. |
| Spark-planens läsbudget vid första synk | Engångskostnad; cache därefter; "Allt"-perioden i Trender räknas ur cachen i `:core`. |
| Hälsodata i molnet | EU-region, rules bara för ägaren, krypterad export, ingen PII i loggar (skill `data-privacy-security`). |
| Larm tystnar när schemat ligger i cachen | Omschemaläggning vid synk, boot och appuppdatering (NOT-14); test mot `FakeCollection`. |
| Omfattning | Kravlistan är paritetschecklistan; etapperna i värdeordning; första release först när allt är grönt. |
