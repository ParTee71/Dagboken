---
name: data-safety-backup
description: Dagbokens datasäkerhetsregel (regel 1) — export/import, schemaändringar och 3.x-data som redan flyttats får ALDRIG tappas. Ladda denna ALLTID när du lägger till, ändrar eller tar bort persisterad data: ett fält på en modell, en ny Firestore-samling, en DocCodec, schemaVersion, SchemaMigrator, firestore.rules, tools/db (export, import, migrate), backup.yml, appens import och export, eller 3.x-fält som bevaras (legacySomatic, settings.legacy, legacyMigration). Trigger-ord: backup, säkerhetskopia, restore, återställ, export, import, migrering, legacy, 3.x, BackupJson, BackupMapper, konverterare, Room-fil, Drive-backup, schemaversion, schemaVersion, DocCodec, codec, fält, ny samling, collection, Firestore, rundtur, round-trip, datamodell, tools/db, dataförlust, OMB.
---

# Datasäkerhet: codecs, schemaversion, export/import

**Invariant:** all data i Firestore överlever en **`tools/db export → import`-rundtur**
med identiskt innehåll, och appen läser både äldre och okända dokument utan att krascha
eller skriva sönder dem. En ändring som lägger till persisterad data utan att föra in den
i hela kedjan är en regression även om allt kompilerar – datan kan tappas vid nästa
återställning eller skrivas över av en äldre appversion.

Krav: DAT-, BCK- och OMB-serierna i KRAVLISTA.md (särskilt BCK-9, BCK-11–16, DAT-5–10,
~~OMB-3–4~~ borttagna i 4.9.0). Villkor nummer ett för ombyggnaden: **ingen data får tappas** (ADR-001, beslut 11).

## Kedjan (var datan passerar)

```
:core-modell ⇄ DocCodec<T> ⇄ Map<String, Any?> ⇄ FirestoreCollection<T> ⇄ Firestore
                                                                            │
          tools/db import  ←  backup (gpg-krypterad i Actions)  ←  tools/db export

3.x-data: redan flyttad (konverteraren och 3.x-importen borttagna i 4.9.0); fälten den skrev bevaras
          av codecarna och listas i ARKITEKTUR.md → Fältparitet

appens export:  Firestore (servern, rått längs Paths) → ExportFormat → fil (SAF)   = tools/db export
appens import:  fil (SAF) → importing/ImportFile (:core: 4.x-export kontrollerad mot DocumentRules)
                → granskning + bekräftelse → ImportBatches → Firestore → verifiering per id
```

| Led | Fil | Ansvar |
|---|---|---|
| Modell | `core/.../model/` | `data class` med **default på alla icke-obligatoriska fält**; `Identified`, `Sortable`, `Archivable` där det passar |
| Codec | `core/.../schema/*Codec.kt` | `encode`/`decode` byggda **enbart** av de delade fälthjälparna (`map.string`, `map.int`, `map.wire`, `map.localDate`, `map.nested`, `map.stringList` …); enum alltid som `WireEnum` med stabilt lagrat namn |
| Schema | `core/.../schema/Schema.kt`, `SchemaMigrator.kt` | `Schema.CURRENT_VERSION`; migreringssteg per version |
| Åtkomst | `app/.../data/firestore/FirestoreCollection.kt`, `Paths.kt` | Enda stället som talar med Firestore; sökvägar på ett ställe |
| Regler | `firestore.rules` | Bara samlingarna i `collections.mjs`, alla bara för ägaren (`request.auth.uid == uid`), med generiska gränser och (från etapp 2) typkontroll av kända fält; `schemaVersion` minst 1, kan inte sänkas och höjs högst till `maxSchemaVersion()` |
| Samlingslista | `tools/db/lib/collections.mjs` | Enda listan export/import/query går igenom; testad mot `Paths` och rules |
| Backup | `.github/workflows/backup.yml` | Veckovis export, krypterad artifact, 90 dagar (BCK-12) |
| Appens export | `app/.../data/export/ExportUseCase.kt` + `core/.../schema/ExportFormat.kt` + `RawDocuments` (`data/firestore/`) | Export i inställningsarket (BCK-13): `awaitPendingWrites` först, sedan hela `users/{uid}` rått från servern längs `Paths` i `walk.mjs`-ordning – aldrig via codecarna, så att okända fält följer med; samma format som `tools/db export` (`serialize.mjs`, `backup.mjs`). Aldrig Health Connect-data (HLS-5) |
| Appens import | `core/.../importing/ImportFile.kt` + `app/.../data/importing/ImportUseCase.kt` | En läsare för en 4.x-export (BCK-6; en 3.x-backup är "ingen backup" sedan 4.9.0): hela filen kontrolleras mot `DocumentRules` före skrivning, annars stopp med rapport; råa dokument med merge via `RawDocumentWriter` i `ImportBatches` form (≤ 500 skrivningar, ≤ 20 episoder, episoden med sina incheckningar) och verifiering per id mot servern (`WriteSupport.kt`: `landed`, `hashOf`). Samma id ersätts, inget tas bort; ingen markör |
| Rules-gränserna i `:core` | `core/.../schema/DocumentRules.kt` | Fält för fält per samling som `valid…` i rules (textgränser, intervall, listtak, enum, datum/klockslag); `DocumentRulesTest` läser rules och kräver likhet. Importen (`ImportFile`) validerar varje dokument mot den |
| Migreringsmarkören | `core/.../model/LegacyMigration.kt`, `core/.../schema/LegacyMigrationCodec.kt`, `DocumentRules.LEGACY_MIGRATION`, `firestore.rules` | `users/{uid}.legacyMigration`, skriven av migreringen på enheten i 4.0–4.7 (borttagen i 4.8.0, ~~OMB-2~~). Ingen kod skriver den längre, men den är persisterad data: modell, codec, `DocumentRules` och rules står kvar oförändrade så att en befintlig markör godtas och överlever rundturen |

## Schemaversion

- `users/{uid}.schemaVersion` anger dataformatet. 4.0 börjar på **1** (`Schema.CURRENT_VERSION`,
  `lib/schema.mjs`, `maxSchemaVersion()` i rules – ett test håller dem lika). 3.x-formatet
  (`BackupJson` v1/v2) är inte en schemaVersion och importeras inte längre (4.9.0).
- **Högre än `Schema.CURRENT_VERSION`** → appen visar "Uppdatera appen" och skriver
  ingenting (en gammal app får aldrig skriva över nyare data).
- **Lägre** → `SchemaMigrator` lyfter dokumenten vid läsning; skrivning sker i nytt format.
  Versionen hör till användaren, inte till varje dokument, så **varje steg är idempotent** och
  användarens `schemaVersion` höjs av en fullständig migrering av alla dess dokument
  (`tools/db/migrate.mjs`, i release-ordningen i skill `android-projekt` → Release) – aldrig av en vanlig skrivning.
  Ändrar inget steg dit några dokument (`SchemaMigrator.canStamp`) stämplar appen själv
  användaren när den öppnas (`UserSession`), så att en äldre app genast ber om uppdatering.
  Ett steg som ändrar data kräver `migrate.mjs`.
- Saknas `schemaVersion`, eller är den under `Schema.FIRST_VERSION` (trasigt värde), gäller
  `Schema.FIRST_VERSION` (`Schema.versionOf`, `versionOf` i `lib/schema.mjs`), aldrig "senaste".
- Höjs versionen: nytt steg i `SchemaMigrator` **och** spegling i `tools/db/migrate.mjs`,
  test per steg, och `lib/schema.mjs` samt `maxSchemaVersion()` i `firestore.rules` följer med
  (ett test jämför konstanterna). De nya rules deployas **före** appen (`rules.yml`, och
  `release.yml` före publiceringen) – annars nekas migreringens stämpling.
- De flesta ändringar kräver **ingen** versionshöjning: ett nytt valfritt fält med default
  läses redan av gamla dokument. Höj bara vid betydelseändring (omdöpt fält, ändrad enhet,
  ändrad struktur) **och vid ett nytt enum-värde eller en ny variant** (dosens `status`,
  alternativets `kind`, receptets `schedule`, måendetillfällets `occasion`) – en äldre app tolkar
  okända enum-värden som default och skulle skriva tillbaka fel värde.
- Okänd `schedule`-typ blir `Unknown(raw)` och skrivs tillbaka oförändrad. Rules känner inte
  typen, så dokumentet kan **uppdateras** (schemat orört) men inte **skapas** eller återskapas med
  den förrän `schemaVersion` och rules höjs (rules-test i `rules.test.mjs`).
- **Okända fält i ett listelement bevaras inte:** listor (`symptoms`, `boosts`, påminnelseraderna)
  skrivs alltid hela ur modellen. Ett nytt fält i ett listelement är därför en strukturändring som
  kräver höjd `schemaVersion` (testat i `CodecsTest`, DAT-10).
- Modellens defaults = vad ett tomt dokument betyder; `assertToleratesMissingFields`
  kontrollerar det.

## Skrivningar bevarar okända fält

Eftersom nya fält inte höjer `schemaVersion` kan en äldre appversion läsa ett dokument med
fält den inte känner till. Den får aldrig radera dem när den sparar:

- **`FirestoreCollection.upsert` skriver med `set(…, SetOptions.merge())`**, aldrig ett helt
  dokument. Fält som inte finns i codecen lämnas orörda i Firestore.
- **Codecens `encode` skriver alla fält den känner till – även de utan värde, som `null`.**
  Annars skulle ett fält som användaren tömt (t.ex. receptets `period.end` eller `note`) ligga kvar vid merge.
  `decode` behandlar `null` och saknat fält likadant (default).
- **Nästlade objekt** (`schedule`, `period`, `boosts`, `symptoms`, inställningarnas grupper) skrivs
  alltid med alla sina fält. Receptets `schedule` är platt (`repeat`, `days`, `intervalDays`) och
  bevarar dagar och intervall oavsett upprepning, som 3.x; en nästlad codec med varianter skriver
  i stället varje variants fält med `null` för det som inte gäller.
- **Radering av ett helt dokument** görs bara via `delete(id)`, aldrig genom att skriva om det.
- Test: `CollectionContract` har fallen "okänt fält bevaras vid upsert" och "tömt valfritt
  fält försvinner vid upsert" (skill `firestore-data-layer`).

## Checklista: nytt fält på en befintlig modell

1. Fältet på `:core`-modellen **med default** (`= ""`, `= 0`, `= false`, `= null`, `= emptyList()`).
2. Fältet i codecens `encode` och `decode` via fälthjälparna – aldrig egen parsning.
   `encode` skriver fältet även när det saknar värde (`null`).
3. Tolka äldre dokument medvetet: saknas fältet → default. Behöver det härledas från
   andra fält, gör det i `decode` och testa det.
4. Fältets typ i samlingens `valid…`-funktion i `firestore.rules` (`nullOrInt`, `nullOrShort` …)
   + rules-test; påverkar det vem som får läsa/skriva, även den regeln.
5. Tester (nedan). Uppdatera rundturstestets seed så att fältet har ett icke-default-värde.
6. **3.x-paritet:** ändrar eller tar du bort ett fält som 3.x-data redan ligger i (paritetstabellen i
   ARKITEKTUR.md → Fältparitet)? Det är en regression – konverterad data finns i kontona. Ett nytt fält
   läggs till bland "Nya fält i 4.0" i tabellen (`ParityTableTest`).
7. KRAVLISTA: DAT-raden för modellen (regel 3).

## Checklista: ny samling

1. Modell + `DocCodec` i `:core`.
2. En rad i `Paths` och en `FirestoreCollection<T>` via `CollectionFactory` – ingen ny
   repository-mekanik (skill `firestore-data-layer`).
3. Egen `match` med `valid…`-funktion i `firestore.rules` + rules-test – en samling utan
   regel nekas (och skulle annars stoppa backupen, BCK-16).
4. En rad i `tools/db/lib/collections.mjs` och i `ARKITEKTUR.md` → Datamodell (testet
   `collections.test.mjs` jämför alla tre med `Paths`).
5. Fixturen (`tools/db/test/fixtures/user.json`) får minst ett dokument i samlingen.
6. Appens export och legacyimporten tar med samlingen.
7. KRAVLISTA: DAT-5 (samlingslistan) och ny DAT-rad för modellen.

## Tester som ALLTID krävs

| Test | Var | Skyddar |
|---|---|---|
| Hela codec-kontraktet | `core/src/test/.../schema/CodecsTest.kt` via `assertCodecContract(codec, sample, defaults)` – en rad per codec; nästlade via `assertVariantCodecContract` | provet har icke-default-värde i **varje** fält (kontrolleras med reflektion), rundtur, tolerans, okända fält och fullständig skrivning |
| Codec-rundtur | ingår i kontraktet: `assertCodecRoundTrip(codec, sample)` | modell → map → modell identisk, inklusive nya fält |
| Tolerans | samma test via `assertToleratesMissingFields` + `assertIgnoresUnknownFields` | äldre och nyare dokument |
| Fullständig skrivning | samma test via `assertEncodesAllFields(codec, sampleWithNulls)` | `encode` skriver varje känt fält, även `null`, så att merge tömmer och bevarar rätt |
| Migrering | `core/src/test/.../schema/SchemaMigratorTest.kt` | varje versionssteg |
| Rundtur end-to-end | `tools/db/test/roundtrip.test.mjs` **endast mot emulatorn** | seed → export → radera → import → export identisk |
| Samlingslistan | `tools/db/test/collections.test.mjs` | `collections.mjs` = `Paths` = rules |
| Appens export = tools/db-formatet | `core/.../schema/ExportFormatTest.kt` och appens exporttest mot `tools/db/test/fixtures/user.json` | varje dokument och värde i rundturens testdata kommer ut exakt som `tools/db export` skriver det, alltså läsbart för `tools/db import` |
| Paritet med 3.x-datan | `core/src/test/.../schema/ParityTableTest.kt` (3.x-fälten frysta), `engine/MigratedDosesTest.kt` (receptdoser med 3.x-id), `model/TraitsTest.kt` (`legacySomatic`) | varje 3.x-fält har en plats; data i 3.x-form läses och beter sig som förut |
| Valideringen = rules | `core/src/test/.../schema/DocumentRulesTest.kt` och `tools/db/test/rules.test.mjs` (emulatorn) | `DocumentRules` är fält för fält `firestore.rules`; fixturens dokument skrivs av ägaren genom rules |
| Importen (BCK-6) | `core/src/test/.../importing/ImportFileTest.kt` och `ImportBatchesTest`; `app/src/test/.../data/importing/ImportUseCaseTest.kt` mot fejkar (fixturen = filens dokument, batchar, omkörning, batchfel, avvikelse, stopp, 3.x-backup avvisad) och `WriteSupportTest` (likheten); `tools/db/test/import.test.mjs` (batchformen, upprepad körning, importen och markören genom rules); instrumenttestet `ExportImportRoundTripTest` (fixturen från ett annat konto, export → radera → import mot emulatorn) | exakt filens dokument; inga dubbletter; inget tas bort |

Varje persisterat fält ska kunna spåras till minst ett test som **asserterar på fältet**
med ett icke-default-värde.

### Tester rör aldrig den riktiga databasen

Rundturstestet raderar och importerar data. Därför:
- Alla tester i `tools/db/test/` går via en gemensam testhjälpare som **avbryter direkt om
  `FIRESTORE_EMULATOR_HOST` saknas**, och som använder projekt-ID:t `demo-dagboken`
  (Firebase-emulatorn behandlar `demo-*` som ett projekt utan koppling till molnet).
- `tools/db/lib/admin.mjs` i testläge godkänner **bara** `FIRESTORE_EMULATOR_HOST` satt och
  ett projekt-ID som börjar med `demo-` – oavsett hur testet startats. Testläge är när
  `NODE_TEST_CONTEXT` är satt eller när anroparen uttryckligen begär det; testhjälparen
  begär det alltid.
- Krav: BCK-16.
- Testdata (`tools/db/test/fixtures/user.json`) är **syntetisk**
  – aldrig utdrag ur den riktiga databasen eller en riktig backup.

## 3.x-datan (OMB)

ARKITEKTUR.md → "Migrering – ingen data får tappas" beskriver flytten; den är klar. Konverteraren, 3.x-importen
(~~BCK-14~~, ~~OMB-3~~, ~~OMB-4~~) och migreringen på enheten (~~OMB-2~~, ~~OMB-5~~, ~~OMB-7~~, ~~OMB-8~~,
~~NAV-6~~) är borttagna (4.8.0, 4.9.0). Det här är reglerna för datan de lämnade efter sig.

1. **Varje 3.x-fält har kvar sin plats.** Paritetstabellen (ARKITEKTUR.md → Datamodell → Fältparitet,
   `ParityTableTest`) visar var konverterad data ligger; även värden 4.0 inte använder bevaras
   (`settings.legacy.dynamicColor`, `settings.legacy.sheetsConfig`, `legacySomatic`). Ett sådant fält tas aldrig
   bort ur modell, codec, rules eller `collections.mjs`.
2. **Stabila id:n.** 3.x-id:n bevaras (DAT-13), och receptgenererade doser behåller 3.x-schemat
   `recept_{prescriptionId}_{date}_{tidpunkt}` (`DoseIds.prescribed` med `Slot.legacyName`, MED-4, DAT-8). Alternativen
   har `OptionIds.of(kind, name)` (regeln i DAT-13), "Övrigt" = `OptionIds.OTHER`. Inget av det får ändras.
3. **Markören** `users/{uid}.legacyMigration` (4.0–4.7) är persisterad data: `LegacyMigration`/`LegacySource`,
   `LegacyMigrationCodec`, `DocumentRules.LEGACY_MIGRATION` och regeln i rules står kvar oförändrade, så att en
   befintlig markör godtas och överlever rundturen. 3.x:s Room- och DataStore-filer på telefonen raderas aldrig
   automatiskt. Tas modellen, codecen eller regeln bort är det en regression.
4. **Importen** (BCK-6) läser bara en 4.x-export genom `ImportFile` – aldrig en andra läsare eller en egen
   validering. Den skriver med merge och tar aldrig bort något (samma id ersätts fält för fält); `users/{uid}` och
   markören skrivs aldrig. Den väntar in enhetens köade skrivningar före varje serverläsning, så att en osynkad lokal
   ändring aldrig skrivs över.
5. **Appens export = `tools/db export`.** Ett nytt fält eller en ny samling följer med av sig själv (rått längs `Paths`);
   en ny undersamling kräver `Paths.SUBCOLLECTIONS` – annars saknas den i appens export. Bevis: `ExportUseCaseTest`
   (export → radera → import → identisk fil) och `ExportImportRoundTripTest` i emulatorn.

## Fallgropar

- **Default saknas** på modellfältet → gamla dokument kraschar `decode`.
- **Egen parsning i en codec** i stället för fälthjälparna → toleransen skiljer sig mellan
  modeller och `cpdCheck` slår till.
- **`Timestamp` vs `Long` vs ISO-sträng:** datum lagras som ISO-sträng (`yyyy-MM-dd`) och
  klockslag som `HH:mm` (DAT-2), även dosens `plannedTime`; `Timestamp` bara för ögonblick
  (`createdAt`, `updatedAt`, dosens `takenAt`). 3.x-tider (`timestamp` som ISO-text eller epok-ms,
  `tagenTid` som `HH:mm` på dosens dag, receptets `skapad` som datum) konverterades i `Europe/Stockholm`
  (`HOME_ZONE`); kod som läser dem räknar i samma zon.
  `tools/db` serialiserar `Timestamp` som `{ "__ts": iso }`.
- **`null` vs saknat fält:** codecen skriver alla kända fält, även `null`, och `decode`
  behandlar `null` som saknat. Se *Skrivningar bevarar okända fält*.
- **Klockslag** (`time`, påminnelsernas `time`) lagras som `HH:mm`. Byts formatet: höj
  `schemaVersion` och migrera, ändra aldrig formatet tyst.
- **Listor** (`slots`, `symptoms`, `feelings`, `boosts`) – ordningen bevaras i rundturen.
- **Heltal vs decimaltal:** JSON skiljer dem inte åt – ett decimaltal med heltalsvärde (`2.0`)
  kommer tillbaka från en rundtur som heltal (`2`). Fälthjälparna i `:core` läser alla `Number`,
  så det är ofarligt; låt aldrig en codec bero på den skillnaden.
- **Skapa offline utan att veta om dokumentet finns:** Firestore kan inte villkora en skrivning utan nät, så
  "skapa om det saknas" kräver `createIfAbsent` (nät). Det enda medvetna undantaget är "Markera tagen" i
  medicinpåminnelsen (NOT-10, `DoseRepository.markTaken`), som avbockningen i appen: offline först, **en** batch
  med bara fältvisa merges (aldrig avvisad för ett saknat dokument). Alla doser (i cachen och saknade) får samma fält:
  `status`, `takenAt`, `name`, `strength`, `dose`, `unit` och id:ts fält (`date`, `slot`, `prescriptionId`) – aldrig `note`,
  `createdAt`, `plannedTime` eller okända fält. Avvägning (last-write-wins): en status som en annan enhet satt och
  som enheten inte sett skrivs över, liksom namn/dos/enhet som en annan enhet ändrat, och en dos som raderats på
  servern återuppstår hel som tagen. Anteckning, skapandetid och okända fält bevaras alltid.
- **Batch-storlek:** Firestore tillåter 500 skrivningar per batch, och rules slår upp högst 20 befintliga
  episoder (`existsAfter`); `ImportBatches` i `:core` delar upp – episoden alltid med sina incheckningar.
- **Samlingen glömd i `collections.mjs`** → den backas aldrig upp. Testet mot `Paths`
  fångar det; ta aldrig bort det testet.
- **Integritet:** logga aldrig dokumentinnehåll; backup krypteras alltid (skill
  `data-privacy-security`).
