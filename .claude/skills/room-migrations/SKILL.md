---
name: room-migrations
description: Dagbokens Room-schemamigreringar — varje databasändring måste migreras utan dataförlust (kärnan i datasäkerhetsregeln). Ladda denna ALLTID när du ändrar en @Entity, lägger till/ändrar en kolumn eller tabell, höjer databasversionen eller rör Room-schemat. Trigger-ord: Room, migration, migrering, @Entity, schema, AppDatabase, databasversion, SupportSQLiteDatabase, execSQL, exportSchema, fallbackToDestructiveMigration, MigrationXYTest, kolumn, tabell.
---

# Room-migreringar

En schemaändring utan migration raderar **all** användardata vid uppdatering. Detta är
den allvarligaste formen av regel 1-brott. Krav: **DAT-3, BCK-***. Se även skill
`data-safety-backup` (backup-rundturen) och `android-data-layer` (DAO/repository).

## Nuläge i projektet

- `data/room/AppDatabase.kt` — `version = 5`, `exportSchema = true`. Migreringar
  `MIGRATION_1_2 … MIGRATION_4_5` ligger i `companion object`, samlade i `MIGRATIONS`.
- Schemafiler är incheckade: `app/schemas/se.partee71.dagboken.data.room.AppDatabase/1..5.json`
  (`room.schemaLocation = $projectDir/schemas`). Dessa **ska committas** — de är facit
  för migreringstesterna.
- `androidTest` mountar `schemas` som assets (`sourceSets["androidTest"].assets.srcDir`).
- Migreringstester finns redan: `Migration23Test`, `Migration34Test`, `MigrationRoundTripTest`.

## Procedur: ändra schemat

1. **Ändra entiteten** (`@Entity`) — ny kolumn, tabell, index osv.
2. **Höj `version`** i `@Database` (5 → 6).
3. **Skriv ett `Migration(5, 6)`-objekt** som gör exakt motsvarande `execSQL`. Nya
   `NOT NULL`-kolumner måste ha `DEFAULT` (jfr `slut_datum TEXT NOT NULL DEFAULT ''`).
   Återskapa index som entiteten deklarerar.
4. **Lägg objektet i `MIGRATIONS`-arrayen** (annars används det aldrig).
5. **Bygg** så att Room genererar `schemas/<...>/6.json` — **committa** den filen.
6. **Skriv `Migration56Test`** (instrument): migrera en v5-databas med data och verifiera
   att data finns kvar och nya kolumner har rätt värden. Använd `MigrationTestHelper` +
   `androidx.room.testing`.
7. **Uppdatera backup-kedjan** för det nya fältet (skill `data-safety-backup`) och
   **rundturstestet**.

## Förbjudet

- **`fallbackToDestructiveMigration()`** (eller `...OnDowngrade`) i produktion — det
  raderar all data. Förekommer inte i projektet; lägg inte till det. Behövs en destruktiv
  reparation (som `MIGRATION_3_4`) ska den vara villkorad och bevisat säker, med kommentar
  som förklarar varför.
- **Att höja versionen utan ett migrationsobjekt** → krasch/datatapp vid uppdatering.
- **Att ändra en gammal, redan släppt migration** → enheter som kört den får inkonsekvent
  schema. Lägg en ny migration i stället.
- **Att inte committa den nya `schemas/*.json`** → `MigrationRoundTripTest`/`validateMigration`
  saknar facit och CI blir opålitlig.

## Reparationsmigreringar (mönster)

`MIGRATION_3_4` visar mönstret för att laga databaser som hamnat i ett trasigt
mellanläge: inspektera faktiskt schema med `PRAGMA table_info(...)` och agera bara om det
gamla schemat upptäcks (no-op annars). Använd samma defensiva stil om en tidigare
dev-build läckt ut ett felaktigt schema.

## Checklista

- [ ] Entitet ändrad + `version` höjd + `Migration(N, N+1)` skriven och lagd i `MIGRATIONS`.
- [ ] Nya `NOT NULL`-kolumner har `DEFAULT`; index återskapade.
- [ ] Ny `schemas/<...>/<version>.json` genererad och **committad**.
- [ ] `MigrationXYTest` bevisar att data överlever.
- [ ] Backup-kedja + rundturstest uppdaterade (regel 1).
- [ ] Ingen `fallbackToDestructiveMigration` i produktionsvägen.
