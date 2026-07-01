---
name: data-safety-backup
description: Dagbokens datasäkerhetsregel (regel 1) — backup och restore får ALDRIG tappa användardata. Ladda denna ALLTID när du lägger till, ändrar eller tar bort persisterad data: en Room-entitet eller -kolumn, ett DataStore-värde, en domänmodell som sparas, en ny lista som loggas, eller något som rör backup/restore/migrering/Drive/export/import/JSON. Trigger-ord: backup, restore, återställ, migrering, BackupJson, BackupMapper, DriveBackupRepository, BackupWorker, Room migration, entity, @Entity, DataStore, exportSchema, round-trip, rundtur.
---

# Datasäkerhet: backup & restore

**Invariant:** All användardata måste överleva en **backup → restore-rundtur** med
identiskt innehåll. En ändring som lägger till persisterad data utan att föra in den
i backup-kedjan är en regression även om appen kompilerar och alla gamla tester är
gröna — datan tappas tyst vid nästa enhetsbyte eller ominstallation.

Relaterade krav: **BCK-1…9**, **SJ-7**, **NFR-7**, **DAT-3**.

## Backup-kedjan (var datan passerar)

```
Room/DataStore  →  domänmodell  →  *Json (BackupJson.kt)  →  serialiserad JSON  →  Drive (appDataFolder)
       ▲                                                              │
       └──────────────  BackupMapper.toX(json)  ←  parsa JSON  ←──────┘
```

Filer:
- `data/migration/BackupJson.kt` — `@Serializable`-DTO:er. `BackupJson` är rotobjektet;
  varje datatyp är ett fält med en `*Json`-klass.
- `data/migration/BackupMapper.kt` — `*Json.toDomain()` + `toX(json)`-funktioner.
- `data/migration/DriveBackupRepository.kt` — bygger ihop `BackupJson` (export) och
  skriver tillbaka via repositories (import). Här samlas alla flöden in.
- `worker/BackupWorker.kt` — schemalagd daglig Drive-backup (WorkManager).
- `ui/migration/MigrationViewModel.kt` — import-/migreringsflödet i UI.

## Checklista: lägga till ett fält på en befintlig entitet

1. Lägg fältet på Room-entiteten **och** skriv en Room-migration (höj `version`,
   `exportSchema = true` — se skill `android-data-layer`). Lägg ett
   `MigrationXYTest` (instrument).
2. Lägg fältet på domänmodellen.
3. Lägg fältet i motsvarande `*Json` i `BackupJson.kt`. **Ge det ett default-värde**
   (`= ""`, `= 0`, `= false`, `= null`) — annars går gamla backuper sönder.
4. Mappa fältet i `BackupMapper.kt` (`toDomain()`) och i export-hopbyggnaden i
   `DriveBackupRepository`.
5. Bevara semantik vid äldre backuper (jfr `timestamp.takeIf { it != 0L } ?: now`,
   `tidpunkter.ifEmpty { … }` i `BackupMapper`).
6. **Tester:** utöka rundturs- och serialiseringstest så det nya fältet ingår
   (se nedan).

## Checklista: lägga till en HELT NY datatyp som ska backas upp

1. Ny `@Serializable data class XJson(...)` i `BackupJson.kt` med default-värden, och
   ett nytt fält `val xs: List<XJson> = emptyList()` på `BackupJson`.
2. `XJson.toDomain()` + `fun toX(json): List<X>` i `BackupMapper.kt`.
3. Export: fyll `xs` i `DriveBackupRepository` när `BackupJson` byggs.
4. Import: skriv tillbaka via rätt repository i `DriveBackupRepository`/
   `MigrationViewModel`.
5. Uppdatera **BCK-2** i KRAVLISTA.md (regel 3) så att datatypen listas som
   "ingår i backup", och lägg ett SJ-7-liknande krav om datatypen är en egen domän.
6. **Tester** enligt nedan.

## Tester som ALLTID krävs för backup-data

| Test | Plats | Vad det skyddar |
|---|---|---|
| Serialisering | `test/.../data/migration/BackupJsonSerializationTest.kt` | JSON ↔ DTO; okända fält ignoreras (`ignoreUnknownKeys`, BCK-9); gamla backuper utan nya fält parsas. |
| Mapper | `test/.../data/migration/BackupMapperTest.kt` | `*Json.toDomain()` mappar alla fält; fallback-logik för v1-backuper. |
| Rundtur (round-trip) | `androidTest/.../data/room/MigrationRoundTripTest.kt` | DB → backup → DB ger identisk data, inkl. nya fält. |
| Room-migration | `androidTest/.../data/room/MigrationXYTest.kt` | Schemaändring migrerar utan dataförlust. |

Regel: **varje** persisterat fält ska kunna spåras till minst ett rundturs- eller
serialiseringstest som faktiskt asserterar på fältet. Lägg inte ett fält som bara
finns i `*Json` men aldrig assertas.

## Vanliga fallgropar

- **Default-värde saknas på ett `*Json`-fält** → äldre backup utan fältet kraschar
  deserialiseringen. Alla `*Json`-fält ska ha default.
- **Fält tillagt i entitet men glömt i `BackupJson`** → datan backas aldrig upp.
  Detta är den klassiska tysta dataförlusten — fånga den med rundturstest.
- **Timestamp/ordning tappas** (jfr commit "bevara timestamp för sjukdomsepisoder").
  Bevara tidsstämplar; faller du tillbaka på `now` ska det vara medvetet.
- **Privacy (NFR-7):** backup får bara till användarens privata Drive `appDataFolder`.
  Logga aldrig backup-innehåll. Lägg inte data någon annanstans.
- **Retention (BCK-3):** endast de 5 senaste backuperna behålls — rör inte den logiken
  utan att uppdatera testet.
