---
name: data-safety-backup
description: Dagbokens datasäkerhetsregel (regel 1) — export/import, schemaändringar och migreringen från 3.x får ALDRIG tappa användardata. Ladda denna ALLTID när du lägger till, ändrar eller tar bort persisterad data: ett fält på en modell, en ny Firestore-samling, en DocCodec, schemaVersion, SchemaMigrator, firestore.rules, tools/db (export, import, migrate), backup.yml, konverteraren från 3.x BackupJson, legacy-läsaren för Room eller legacyimporten. Trigger-ord: backup, säkerhetskopia, restore, återställ, export, import, migrering, legacy, 3.x, BackupJson, BackupMapper, konverterare, Room-fil, Drive-backup, schemaversion, schemaVersion, DocCodec, codec, fält, ny samling, collection, Firestore, rundtur, round-trip, datamodell, tools/db, dataförlust, OMB.
---

# Datasäkerhet: codecs, schemaversion, export/import

**Invariant:** all data i Firestore överlever en **`tools/db export → import`-rundtur**
med identiskt innehåll, och appen läser både äldre och okända dokument utan att krascha
eller skriva sönder dem. En ändring som lägger till persisterad data utan att föra in den
i hela kedjan är en regression även om allt kompilerar – datan kan tappas vid nästa
återställning eller skrivas över av en äldre appversion.

Krav: DAT-, BCK- och OMB-serierna i KRAVLISTA.md (särskilt BCK-9, BCK-11–16, DAT-5–10,
OMB-2–5). Villkor nummer ett för ombyggnaden: **ingen data får tappas** (ADR-001, beslut 11).

## Kedjan (var datan passerar)

```
:core-modell ⇄ DocCodec<T> ⇄ Map<String, Any?> ⇄ FirestoreCollection<T> ⇄ Firestore
                                                                            │
          tools/db import  ←  backup (gpg-krypterad i Actions)  ←  tools/db export

3.x (Room-fil på enheten / Drive-backup / lokal JSON = BackupJson v1, v2)
          → legacy/BackupJsonConverter (:core) → 4.0-dokument → batchar → Firestore

appens export:  Firestore (servern, rått längs Paths) → ExportFormat → fil (SAF)   = tools/db export
appens import:  fil (SAF) / Drive → legacy/ImportFile (:core: 3.x via konverteraren, 4.0-export kontrollerad)
                → granskning + bekräftelse → MigrationBatches → Firestore → verifiering per id
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
| Appens import | `core/.../legacy/ImportFile.kt` + `app/.../data/legacy/LegacyImportUseCase.kt`, `DriveBackups.kt` | En läsare för 3.x-backup (Drive eller fil, via **samma** konverterare) och 4.0-export (BCK-6, BCK-14, OMB-5): hela filen kontrolleras mot `DocumentRules` före skrivning, annars stopp med rapport; skrivning med merge i `MigrationBatches` form och verifiering per id med flyttens räkning och likhet (`WriteSupport.kt`). Samma id ersätts, inget tas bort; ingen markör |
| Konverterare 3.x → 4.0 | `core/.../legacy/BackupJsonConverter.kt` (3.x-klasserna i `legacy/BackupJson.kt`) | `BackupJson` v1 och v2 (inkl. arvsfälten `anteckning` på posterna och `tidpunkt` på receptet) → 4.0-dokument; **enda** mappningen, delad av legacy-läsaren (OMB-2), legacyimporten (BCK-14) och grinden OMB-4. Utfall `Converted` (dokument + rapport) eller `Stopped` (alla fel) |
| Rules-gränserna i `:core` | `core/.../schema/DocumentRules.kt` | Fält för fält per samling som `valid…` i rules (textgränser, intervall, listtak, enum, datum/klockslag); `DocumentRulesTest` läser rules och kräver likhet. Konverteraren validerar varje dokument mot den |
| Grinden OMB-4 | `core/.../legacy/ConvertBackupMain.kt`, task `:core:convertLegacyBackup` | 3.x-backup → fil för `tools/db import.mjs`, rapport (antal, varningar, stopp) på stdout utan innehåll; kommandot i CLAUDE.md → Bygg & test och skill `db-access` |
| Legacy-läsare | `app/.../data/legacy/` + `core/.../legacy/LegacyRoomAssembler.kt`, `MigrationBatches.kt` | Läser Room-filen (`SQLiteDatabase.OPEN_READONLY`, `user_version` 11, WAL utan checkpoint) och `dagboken_prefs` read-only vid första start (OMB-2); raderna → `LegacyRoomAssembler` (3.x `BackupAssembler` fält för fält) → **samma** konverterare; obligatorisk verifierad 3.x-kopia före flytten (OMB-8); råa dokument med merge i batchar om ≤ 500 skrivningar och ≤ 20 episoder via `RawDocumentWriter`; verifiering per id mot servern; markören `users/{uid}.legacyMigration` sist. Ändrar och raderar aldrig filerna |

## Schemaversion

- `users/{uid}.schemaVersion` anger dataformatet. 4.0 börjar på **1** (`Schema.CURRENT_VERSION`,
  `lib/schema.mjs`, `maxSchemaVersion()` i rules – ett test håller dem lika). 3.x-formatet
  (`BackupJson` v1/v2) är inte en schemaVersion: det går bara in via konverteraren.
- **Högre än `Schema.CURRENT_VERSION`** → appen visar "Uppdatera appen" och skriver
  ingenting (en gammal app får aldrig skriva över nyare data).
- **Lägre** → `SchemaMigrator` lyfter dokumenten vid läsning; skrivning sker i nytt format.
  Versionen hör till användaren, inte till varje dokument, så **varje steg är idempotent** och
  användarens `schemaVersion` höjs av en fullständig migrering av alla dess dokument
  (`tools/db/migrate.mjs`, i release-ordningen i skill `release`) – aldrig av en vanlig skrivning.
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
6. **3.x-paritet:** motsvarar fältet något i 3.x (`BackupJson`, Room-entitet, DataStore)? Då
   ska konverteraren fylla det och konverterarens fixtur asserta på det (OMB-3).
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
| Konverteraren (OMB-3) | `core/src/test/.../legacy/BackupJsonConverterTest.kt` mot `tools/db/test/fixtures/legacy/backup-v2.json` (varje 3.x-fält icke-default, `BackupJsonTest`) och `backup-v1.json`, med `*.expected.json` | konvertera → exportformat → fältvis jämförelse mot förväntat; varje 4.0-fält fyllt; deterministisk. Stoppfallen i `ConverterStopsTest`, sommartiden i `LegacyTimeTest`, kommandoraden i `ConvertBackupCliTest` |
| Valideringen = rules | `core/src/test/.../schema/DocumentRulesTest.kt` och `tools/db/test/legacy.test.mjs` (emulatorn) | `DocumentRules` är fält för fält `firestore.rules`; de förväntade exporterna godtas av `import.mjs --dry-run`, överlever rundturen och skrivs av ägaren genom rules |
| Legacy-läsaren (OMB-2, OMB-7, OMB-8) | `core/src/test/.../legacy/LegacyRoomAssemblerTest.kt` mot `tools/db/test/fixtures/legacy/room-v11.json` (varje v11-kolumn satt) → exakt `backup-v2.expected.json`; `MigrationBatchesTest`; `Backup3xCompatibilityTest` (kopian parsad med 3.27.0:s egna klasser); `app/src/test/.../data/legacy/LegacyMigrationUseCaseTest.kt` mot fejkar (startkontroll, kopia, batchar, avbrott och omkörning, avvikelse, markör); `tools/db/test/legacy.test.mjs` (batchformen, upprepad körning, markören genom rules); instrumenttesterna `LegacyRoomReaderTest` (riktig v11-fil i WAL + DataStore-fil, kontrollsumma oförändrad) och `LegacyMigrationWriterTest` (batchar, verifiering och markör mot emulatorn) | samma dokument som konverteraren ger för samma data; före = rapportens antal, efter = verifierade; inga dubbletter; filerna orörda |

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
- Testdata (`tools/db/test/fixtures/user.json` och konverterarens 3.x-fixtur) är **syntetisk**
  – aldrig utdrag ur den riktiga databasen eller en riktig backup.

## Migreringen från 3.x (OMB)

ARKITEKTUR.md → "Migrering – ingen data får tappas" är planen; det här är reglerna.

1. **En mappning.** `legacy/BackupJsonConverter` i `:core` är enda stället som översätter 3.x till
   4.0. Legacy-läsaren (Room-filen på enheten) och legacyimporten (Drive-backup/lokal JSON) läser
   sina källor till `BackupJson`-form och kör samma konverterare – aldrig en egen mappning.
2. **Varje fält har en plats.** Ett 3.x-fält utan motsvarighet i 4.0-modellen är en blockerare,
   inte en förenkling – även ett som 4.0 inte använder: det bevaras i en `legacy`-grupp
   (`settings.legacy.dynamicColor`, `settings.legacy.sheetsConfig`) och läses aldrig. Inget fält är
   *utelämnas*; bara backupfilens egna `version` och `createdAt` är *metadata*. De fyra förenklingarna i ARKITEKTUR.md (anteckningen som `note`, symptom
   som `[{optionId, score, customText}]`, `optionId` i stället för namn, dosens `status`) är de enda.
   Paritetstabellen (ARKITEKTUR.md → Datamodell → Fältparitet) är kontrollerad av `ParityTableTest`.
3. **Stabila id:n.** 3.x-id:n bevaras (DAT-13), och receptgenererade doser behåller 3.x-schemat
   `recept_{prescriptionId}_{date}_{tidpunkt}` (`DoseIds.prescribed`, MED-4, DAT-8; bara schemalagda
   tidpunkter). Alternativen får `OptionIds.of(kind, name)` (regeln i DAT-13). Samma källa ger samma
   id vid ny import, så att en upprepad import inte dubblerar.
4. **Validera, kapa aldrig.** Konverteraren validerar varje dokument mot `DocumentRules` (= rules,
   `DocumentRulesTest`) och stoppar med en rapport över alla fel utan innehåll – kapar, avrundar eller hoppar
   aldrig över ett värde. **Ett stopp ska bara träffa det som verkligen saknar plats.** Ett värde som 3.x själv
   inte kunde läsa (och därför hoppade över eller ersatte) läses ur 3.x-koden på branchen `legacy` innan det
   blir ett stopp: det som i 3.x betyder "ej satt" mappas till saknat, det som går att bevara bevaras (och
   modellen utökas vid behov, hela datakedjan), och en fallback är motiverad och ger en varning med värdets
   form, aldrig innehåll. Exempel: symptomnamn med kommatecken och delar utan poäng (`score: null`), `spentTime`
   0 på en screening, `skapad` som inte är ett datum (OMB-3). Stopprapporten visar alla typer av fynd. Valen utanför paritetstabellen (tider och sommartid, anteckningar utan post,
   alternativ, symptomsträngen, inställningar) och grinden OMB-4 står i ARKITEKTUR.md → Migrering, punkt 1
   och 3 – ändra dem där först.
5. **Bevis före användning:** fixturtestet (OMB-3), rundturen mot emulatorn (BCK-16) och grinden
   OMB-4 – en riktig 3.x-backup konverteras, importeras med `tools/db import.mjs`, exporteras och
   jämförs fältvis med noll skillnader – innan etapp 3 och innan första release.
6. **Room-filen raderas aldrig automatiskt** (OMB-2). Migreringsskärmen visar antal per entitet
   före (rapportens) och efter (verifierade på servern) och användaren bekräftar; först då skrivs markören
   `users/{uid}.legacyMigration` (en gång, rules hindrar ändring och radering) och flaggan på enheten.
   Startkontrollen frågar servern efter markören före flaggan.
7. **Kopian är obligatorisk** (OMB-8): ingen skrivning förrän 3.x-datan sparats som 3.x-backupfil och
   verifierats (parse med `BackupJson.parse`, antal per entitet mot Room och, med en plan, exakt planens dokument) för
   Room-filen med just den kontrollsumman och dagens kontrollversion. Vid ett stopp finns ingen plan: kopian kan ändå
   sparas (samma kort i Stopp-läget) och kontrolleras då som 3.x-fil, så att 3.x-datan aldrig hänger på att 4.0 kan
   läsa den; "Flytta" kräver båda. Filen ska läsas av
   3.27.0 – `Backup3xCompatibilityTest`.
8. **Omkörning utan dubbletter och utan att skriva över 4.0-data** (OMB-7): målens läge läses först på id och ställs
   mot liggaren (egen fil per konto: `P`/`W`/`V`/`M sökväg hash`; P före batchens commit och W efter kvittot, P ersätter
   aldrig V/M – servern = P-hashen betyder att skrivningen landade; sista W/V/M-raden per sökväg gäller;
   M = avvikande). Har liggaren en rad har flytten börjat: skärmen tvingas fram tills bekräftat eller avbrutet.
   Per sökväg: saknas → skrivs; lika → verifierat; antecknad men olika → skrivs om helt (ändringar under flytten,
   även från en annan enhet, skyddas inte); aldrig vårt och olika → bara saknade fält (`existing`). `abort()` raderar
   exakt liggarens sökvägar och rör aldrig det som fanns före. Likhet = hashen över den kanoniska formen
   (`MigrationLedger.hashOf`, sorterade nycklar även i listor) – aldrig en egen jämförelse. Hash och jämförelse inom
   samlingens fasta fältmängd (`DocumentRules.fieldTree`), en gång per dokument. Antecknat som 4.0 ändrat behålls
   (`kept`, även bara skrivet), verifierat som 4.0 raderat återskapas inte (`removed`), som 3.x ändrat (servern har ännu
   vårt värde) uppdateras; aldrig vårt och olika → bara saknade fält (`existing`). Liggarens IO-fel → `LEDGER_FAILED`.
   Bekräftelsen är idempotent. Batchfel stannar direkt med felkod, aldrig innehåll. Påminnelserna pausas bara medan
   `write()` körs (`LegacyMigrationPause`, try/finally) och pausen avbokar aldrig larm. 3.x:s backupjobb avbokas vid
   varje start tills det lyckats (flagga per installation).
9. **Legacyimporten** (BCK-14) behålls minst en version efter 4.0. Den och 4.0-importen (BCK-6) går genom
   `ImportFile` – formatet känns igen på filen, aldrig en andra mappning eller en egen validering. Importen skriver
   med merge och tar aldrig bort något (samma id ersätts fält för fält); `users/{uid}` och markören skrivs aldrig.
   Fallbacken utan Room-fil (OMB-5) sätter bara flaggan på enheten – en markör där skulle tysta flytten från en
   telefon som fortfarande har 3.x – och frågar aldrig ett konto som redan har data. Importen väntar in enhetens köade
   skrivningar före varje serverläsning, så att en osynkad lokal ändring aldrig skrivs över. Drive läses bara (inget Drive-bibliotek, ingen lagrad token).
10. **Appens export = `tools/db export`.** Ett nytt fält eller en ny samling följer med av sig själv (rått längs `Paths`);
   en ny undersamling kräver `Paths.SUBCOLLECTIONS` – annars saknas den i appens export. Bevis: `ExportUseCaseTest`
   (export → radera → import → identisk fil) och `ExportImportRoundTripTest` i emulatorn.

## Fallgropar

- **Default saknas** på modellfältet → gamla dokument kraschar `decode`.
- **Egen parsning i en codec** i stället för fälthjälparna → toleransen skiljer sig mellan
  modeller och `cpdCheck` slår till.
- **`Timestamp` vs `Long` vs ISO-sträng:** datum lagras som ISO-sträng (`yyyy-MM-dd`) och
  klockslag som `HH:mm` (DAT-2), även dosens `plannedTime`; `Timestamp` bara för ögonblick
  (`createdAt`, `updatedAt`, dosens `takenAt`). 3.x-tider (`timestamp` som ISO-text eller epok-ms,
  `tagenTid` som `HH:mm` på dosens dag, receptets `skapad` som datum – eller ögonblick i importerad data) konverteras medvetet – tidszonen
  är `Europe/Stockholm` om inget annat sägs, och konverterarens test täcker sommartidsbytet.
  `tools/db` serialiserar `Timestamp` som `{ "__ts": iso }`.
- **`null` vs saknat fält:** codecen skriver alla kända fält, även `null`, och `decode`
  behandlar `null` som saknat. Se *Skrivningar bevarar okända fält*.
- **Klockslag** (`time`, påminnelsernas `time`) lagras som `HH:mm`. Byts formatet: höj
  `schemaVersion` och migrera, ändra aldrig formatet tyst.
- **Listor** (`slots`, `symptoms`, `boosts`) – ordningen bevaras i rundturen.
- **Tidsstämplar och ordning från 3.x** (jfr 3.x-buggen "bevara timestamp för sjukdomsepisoder"):
  faller konverteraren tillbaka på "nu" ska det vara medvetet och testat.
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
  episoder (`existsAfter`); `MigrationBatches` i `:core` delar upp – episoden alltid med sina incheckningar.
- **Samlingen glömd i `collections.mjs`** → den backas aldrig upp. Testet mot `Paths`
  fångar det; ta aldrig bort det testet.
- **Integritet:** logga aldrig dokumentinnehåll; backup krypteras alltid (skill
  `data-privacy-security`).
