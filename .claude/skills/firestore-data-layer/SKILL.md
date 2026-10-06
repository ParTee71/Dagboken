---
name: firestore-data-layer
description: Dagbokens datalager (4.0) — Firestore offline-först under users/{uid} via en generisk FirestoreCollection<T> och DocCodec<T>, tunna repositories, UserSession/UserScope, security rules och FakeCollection i tester. Ersätter 3.x-skillsen android-data-layer och room-migrations. Ladda denna ALLTID när du rör data/firestore, data/repository, data/user, en codec, en ny samling, Paths, UserSession, firestore.rules, offline/synk, larmens läsning ur cachen eller repository-tester. Trigger-ord: Firestore, databas, repository, samling, collection, dokument, codec, DocCodec, FirestoreCollection, Paths, offline, synk, synkas, cache, snapshots, hasPendingWrites, security rules, firestore.rules, users, uid, ägare, UserSession, UserScope, FakeCollection, CollectionContract, DataError, Room, DAO.
---

# Datalagret: Firestore

**Kärnregel:** ingen samling har egen Firestore-kod. All Firestore-åtkomst går genom
`FirestoreCollection<T>` i `app/.../data/firestore/`. Repositories är tunna fasader.
Modeller och codecs ligger i `:core` utan Android- eller Firebase-beroenden.

Tabellen över alla delade byggstenar står i skill `shared-ui-components` (enda källan).
Datakedjan och schemaregler står i skill `data-safety-backup`.

## Dokumentstruktur

Allt ligger under `users/{uid}` – strikt personligt, ingen delning (ADR-001, beslut 2).
Användardokumentet bär `schemaVersion` och `createdAt`; under det ligger `settings` (ett
dokument), `options`, `prescriptions`, `prnMedicines`, `doses`, `screenings`, `activities`,
`events` och `illnessEpisodes` med undersamlingen `checkins`. Struktur och fält står **bara** i
`ARKITEKTUR.md` → "Datamodell" (DAT-5–DAT-10); fälten och codecs byggs i etapp 2. Sökvägarna
finns i `Paths` och speglas i `tools/db/lib/collections.mjs` – ett test håller dem lika.

> **Ingen Room i 4.0** utom den läsande legacy-modulen `data/legacy` (Room-läsaren för
> migreringen på enheten, OMB-2). Den skriver aldrig till Room-filen och raderar den aldrig.

## Lager

```
ViewModel
  → XRepository (interface, data/repository)          domänfrågor, inget Firestore
    → EntityCollection<X> (data/common)               från CollectionFactory
      → FirestoreCollection<X>                        enda Firestore-koden (data/firestore)
        → FirestoreInstance → FirebaseFirestore (PersistentCacheSettings, 100 MB)
```

### `DocCodec<T>` (`:core`)
Se skill `data-safety-backup` och `ARKITEKTUR.md` → "Datamodell". Byggs enbart av
fälthjälparna; toleranta mot saknade och okända fält (DAT-10):

- **Saknat fält** eller `null` → modellens default.
- **Okänt enumvärde** (från en nyare app) → modellens default, som skrivs vid nästa sparning –
  utom `screenings.occasion`, som blir `null`, och påminnelserader och receptets tidpunkter med
  okänd nyckel, som hoppas över.
- **Okända fält** på toppnivå och i nästlade objekt bevaras (merge-skrivning). **Okända fält i ett
  listelement** (`symptoms[]`, `boosts[]`, `medSlots[]`, `screeningOccasions[]`) bevaras **inte** –
  listor skrivs alltid hela ur modellen; ett nytt fält där kräver höjd `schemaVersion`.
- **Okänt recept-schema** (`Schedule.Unknown(raw)`) skrivs tillbaka oförändrat. Rules godtar bara
  kända scheman i fält som skrivs, så ett sådant recept kan **uppdateras** (schemat orört) men inte
  skapas eller återskapas efter radering förrän `schemaVersion` och rules höjs.

### `EntityCollection<T>` (`data/common/`) – ett kontrakt, två implementationer
| Medlem | Beteende |
|---|---|
| `observe(): Flow<List<T>>` | från cachen, följer den inloggade användaren (utloggad → inget emitteras); sorterat på `sortOrder` för `Sortable` |
| `observe(id): Flow<T?>` | ett dokument, `null` om det saknas |
| `observeBetween(field, from, to): Flow<List<T>>` | dokumenten där toppfältet ligger i `from..to` (båda inräknade; samma typjämförelse som `confirmedFrom`), offline först som `observe` – en dag eller period av `doses`/`screenings` på `DATE` (`>=` och `<=` på samma fält täcks av det automatiska enkelfältsindexet, inget i `firestore.indexes.json`). Följer användaren som `observe`: utloggad emitteras inget, och lyssningen tas upp igen vid inloggning. Datumfält via extensionen `observeDates(field, från, till)` |
| `cachedBetween(field, from, to)` | intervallet en gång, offline först som `cached()` (`firstFromCache`: ur cachen också utan nät, annars servern); `NotSignedIn` direkt utloggad. För kontroller inför en offline-skrivning (vid behov-dosens kylperiod och dagsgräns). Datumfält via `cachedDates` |
| `get(id)`, `getAll()` | engångsläsning som `Result` |
| `upsert(item)` | `set(…, merge)` med codecens fullständiga fältlista; okända fält bevaras, `null` tömmer; `updatedAt` sätts, `createdAt` utan värde skrivs inte (`prepareForWrite`) |
| `delete(id)` | permanent radering |
| `setArchived(id, archived)` | ändrar bara `archived` via `update` – ett raderat dokument återuppstår inte |
| `update(item, fields, remove)`, `updateAll(items, fields, remove)` | Firestores `update` (i en batch): bara [fields] plus `updatedAt` (`fieldsForUpdate`); toppfälten i `remove` tas bort (`FieldValue.delete()`), varje fält ersätts helt (en map slås inte ihop), okänt fält är fel, ett dokument som inte finns skapas inte; atomärt inom bitar om 500. Fältnamnen som konstanter i codecen (t.ex. dosens `status`) |
| `merge(item, fields)` | `set(…, merge)` av bara [fields] (`FieldPath` = lista av segment, t.ex. `listOf("theme", "mode")` – en punkt i en nyckel är aldrig en väg) plus `updatedAt` (`fieldsForMerge`): djup merge, så andra fält, andra nycklar i samma map och okända fält står kvar; dokumentet skapas om det saknas; ingen läsning. Med `changedFields(före, efter)` skrivs bara det som ändrats – så sparar inställningarna (`SettingsRepository.save/update`) |
| `cached()`, `cached(id)` | läsning inför en skrivning (`firstFromCache`): första ögonblicksbilden – direkt ur cachen när den kan svara (lyssnaren delas av Firestore med skärmar som följer samma fråga), annars från servern när nätet finns. Ett dokument som saknas i cachen medan servern inte nås (`isFromCache`) ger `Offline` direkt; `null` bara när det bekräftat saknas. `NotSignedIn` direkt utloggad; 15 s skyddsnät om ingen ögonblicksbild kommer |
| `confirmedFrom(field, from)`, `confirmed()`, `confirmed(id)` | dokument där toppfältet är minst `from` (Firestores `>=`: text med text, tal med tal; saknat fält kommer inte med), **bekräftade av servern** (`get(Source.SERVER)`) – aldrig en ofullständig lista ur cachen; offline `Offline`. Avgränsad läsning inför villkorade skrivningar, t.ex. dagens och senare doser på `DoseCodec.DATE` |
| `createIfAbsent(items)`, `deleteIf(ids, condition)`, `updateIf(items, fields, condition)` | villkorade skrivningar i en **transaktion** mot servern: skapar bara det som bevisligen saknas, raderar respektive uppdaterar (bara `fields` + `updatedAt`, skapar inget) bara det vars *lagrade* version uppfyller villkoret – aldrig ett beslut på en inaktuell cachekopia (en dos tagen på en annan enhet skrivs inte över, ändras eller raderas). Ett dokument som inte går att avkoda rörs aldrig och fäller inte de övriga (`Stored.Unreadable`). Kräver nät: offline `Offline` och ingenting skrivs eller köas (`disableNetwork()` stoppar inte transaktioner – offlinetestet pekar på en död port); skyddsnätet `serverWait` (15 s) är injicerbart. En transaktion som inte hann bekräftas spåras (`PendingCommits`, `SyncStatus`) tills den är klar |
| `awaitWrites()` | väntar tills appens egna skrivningar – också spårade sena transaktioner – nått servern, före en serverbekräftad läsning som ska se dem; offline `Offline`. Dossynken går bara via `PrescriptionRepository.syncFromServer` (receptets `LatestWins`-lås → `awaitWrites` → recept och doser från servern → diff i `:core` → villkorade skrivningar) |
| `updateChanged(codec, före, efter)` | extension: `update` av bara de toppfält som skiljer (båda sidornas nycklar) – för formulär och följdändringar där ett raderat dokument inte får återuppstå; en ändrad map skrivs i sin helhet, ett toppfält som codecen inte längre skriver tas bort |
| `batch(upserts, deletes, merges)` | i bitar om 500 – atomärt inom varje bit, inte över bitar; `merges` (`FieldMerge(item, fields)`) som `merge` med egna fält per dokument – "Markera tagen" från notisen (NOT-10) |
| `newId()` | slumpat klient-ID – fungerar offline |

- **Offline först:** en skrivning läggs i cachen och `Result` blir klart direkt – den väntar
  aldrig på servern. Servern svarar senare; väntande skrivningar och sena fel syns i
  `SyncStatus` (`syncing` → "synkas…", `lastWriteError` ligger kvar tills UI:t visat det och
  anropat `clearWriteError()` – ett fel medan appen är i bakgrunden försvinner inte).
- **Skrivskydd, stängt som standard:** är användarens `schemaVersion` nyare än appen vägras
  skrivningar med `DataError.UpdateRequired`; läsning fungerar. Är versionen för den inloggade
  användaren ännu okänd väntar skrivningen upp till 5 s och vägras sedan (`Offline`) hellre än
  att en äldre app skriver över nyare data (`writeBlocker` i `data/common/UserScope.kt`). Går
  användardokumentet inte att läsa (rules nekar) blir det `PermissionDenied`. Versionen är knuten
  till uid:t, så en gammal version gäller aldrig ett annat konto (BCK-15).
- **Ordning:** `sortForList` – på dokument-ID och sedan `sortOrder`, likadant i riktig och fejkad samling.
- **Läsning:** `readDocument` kör `SchemaMigrator` före `decode`. `Timestamp` ↔ `Instant`
  översätts i `data/firestore/FirestoreValues.kt`, rekursivt; `:core` ser aldrig Firebase-typer.
- **Fel:** `firestoreError` mappar `FirebaseFirestoreException` → `DataError` en gång, via
  `suspendRunCatching`. `DataError` (`Offline`, `PermissionDenied`, `Cancelled`,
  `UpdateRequired`, `SignInRejected`, `NotSignedIn`, `NotFound`, `Unknown`) används även av `AuthRepository`. UI visar
  texten via `DataError.toMessage()`.
- **`FakeCollection`** (test) använder samma `prepareForWrite`/`readDocument` och emulerar
  Firestores merge (djup för nästlade maps, heltal som `Long`). `CollectionContract` i
  `app/src/sharedTest` körs mot båda. `FakeStore.online = false` ger Firestore utan nät: läsflöden,
  `cached…` (svaren märkta `fromCache`) och skrivningar i cachen fungerar; `confirmed…`, en villkorad
  skrivning med något att pröva och `awaitWrites` när appen skrivit något utan nät blir `Offline` (en tom
  transaktion och `awaitWrites` utan väntande skrivningar lyckas, som i Firestore) – så att ett
  repositorytest bevisar att något fungerar offline.

### Samlingar och sökvägar
`CollectionTable` (`data/firestore/`) är enda tabellen över samlingarnas namn, codecs och
sökvägar (`Paths`). `FirestoreCollectionFactory` och testernas `FakeCollectionFactory` ärver
den. `tools/db/lib/collections.mjs` speglar `Paths`; `tools/db/test/collections.test.mjs`
jämför dem.

### Repositories (`data/repository/`)
Interface + `Default…Repository`. **Alla dosskrivningar går genom `DoseRepository`**: dagens doser
(`PrescriptionRepository.ensureDay`: urvalet på cachade recept och `DoseRepository.missingOn` mot dosernas
cache – inget saknas, inga serveranrop – och sedan **en väg** för att skapa saknade doser, delad med
`tidyUp`: per recept under receptets lås, `LatestWins.runExclusive`, efter en serverläsning av receptet,
`DoseRepository.createDay`, så att en samtidig avaktivering aldrig följs av återskapade doser; ingen dos
före receptets skapandedag/start, `firstDoseDay` i `:core`; avslutar aldrig recept, det gör bara `tidyUp`),
avbockning (`setStatus` – fältvis `update` av `status` och `takenAt`, tagen utan tid = nu, offline först),
vid behov (`logAsNeeded` – en i taget per medicin, läser max(kylperiod, 24 h) + 7 dagar bakåt med
`cachedDates`, eftersom kylperioden mäts på tagningstiden, `checkDose`, ny dos vid `Allowed`; **känd
begränsning:** offline med ofullständig cache kan dagsgränsen passeras, FAV-5) och extrados (`logExtraDose`);
en tidpunkt i framtiden är ett fel. Dag- och
periodläsning via `observeDay`/`observeDays` (`observeDates`), också i `ScreeningRepository`, där en ny
logg skapas med `new()` (id och `createdAt` en gång). Enkla samlingar är bara delegering
(`OptionRepository : EntityCollection<Option>`, `… by collections.options()`); egen kod finns
bara för domänfrågor (t.ex. dagens doser ur recepten med stabila 3.x-id:n `recept_{prescriptionId}_{date}_{tidpunkt}`,
DAT-8; incheckningar per episod; importen från 3.x via `batch`). Ingen `FirebaseFirestore`, ingen
felmappning, ingen codec-logik. Domänlogik (dosgenerering, kylperiod, periodslut, dagens
energisnitt, diagrammatematik) ligger i `:core` (ARKITEKTUR.md → Lager och moduler).

### Inloggad användare
`UserSession` (`data/user/`, implementerar `UserScope` i `data/common/`): uid kommer från Firebase
Auth (`AuthRepository`) – inloggning krävs (AUTH-6, TP-5), så det finns inget val att spara.
`schemaVersion` läses direkt ur användardokumentet (`UserVersionSource`; går det inte att läsa
sätts `unreadable` och skrivspärren förblir stängd). En äldre användare vars väg till appens
version inte ändrar några dokument (`SchemaMigrator.canStamp`) stämplas av sessionen med
`UserVersionSource.stamp` (bara fältet `schemaVersion`, en gång per körning).
Vid inloggning skapar `EnsureUserUseCase` användardokumentet på **servern** via `UserDirectory.createIfMissing`
(atomärt – ett dokument från en annan enhet skrivs aldrig över) med `schemaVersion` och `createdAt`.

### DI
`FirestoreModule` ligger i **`data/firestore/`** (inte i `di/`), eftersom `FirebaseFirestore`
bara får förekomma där. Den ger `FirestoreInstance` – som skapar `FirebaseFirestore` med
`PersistentCacheSettings` – och binder `CollectionFactory`, `SyncStatus`, `UserVersionSource`,
`UserDirectory`, `RawDocuments` och `LocalCacheCleaner`. Klasserna i `data/firestore/` hämtar
`firestore.db` vid varje anrop och håller aldrig en egen `FirebaseFirestore`: utloggningen
(`SignOutUseCase`, AUTH-6) avslutar instansen och tömmer cachen (`terminate` + `clearPersistence`),
och nästa anrop får en ny, tom instans med samma inställningar. Låset i `FirestoreInstance` hålls
under tömningen (ingen ny instans får öppna filerna medan de raderas), men varje steg har en
tidsgräns på 10 s – hänger Firestore blir det ett fel i stället för en `db` som låser för alltid. Osynkade skrivningar töms aldrig –
då behålls cachen (skill `firebase-auth`).
Övriga moduler i `di/` (`AppModule`: app-scope, klocka, DataStore för enhetslokalt tillstånd;
`AuthModule`).

### Export från appen
"Exportera" i inställningsarket (BCK-13, SET-8) läser användarens dokument **rått** via
`RawDocuments` (aldrig via codecarna – okända fält ska med) längs `Paths` och skriver dem med
`ExportFormat` i `:core`, samma format som `tools/db export`. Ny samling → med i exporten också;
exporttestet jämför med `tools/db`:s testdata (`tools/db/test/fixtures/user.json`), som täcker
alla samlingar. Legacyimporten (BCK-14) går via konverteraren i `:core` (skill `data-safety-backup`).

### Larm läser cachen
Påminnelserna (skill `notifications-alarms`) läser schemat ur Firestore-cachen via samma
repositories – aldrig egen Firestore-kod i en receiver. "Markera tagen" från notisen skriver till
cachen och synkas när nätet finns; larmen schemaläggs om vid synk, omstart och appuppdatering (NOT-14).

## Offline

- Firestore-cachen är källan för UI; väntar aldrig på nätverket.
- Skrivningar lyckas lokalt direkt och synkas senare; `syncing` visar läget.
- ID:n skapas på klienten med `newId()`.
- **Inställningar utan underlag:** `SettingsRepository.get()` ger standardvärdena bara när
  dokumentet bekräftat saknas (ny användare). Finns det inte i cachen och servern inte nås blir det
  `Offline` – formuläret visar laddfel med Försök igen, och `update` skriver ingenting; standardvärden
  visas aldrig som om de vore lagrade. Formulär sparar med `save(laddat, redigerat)`, som bara
  skriver ändrade fält (`merge`), så ett fält som en annan enhet ändrat efter att formuläret laddades
  inte skrivs tillbaka. Nycklade listor (`KeyedList`, t.ex. påminnelseraderna på `slot`/`occasion`,
  fasta uppsättningar) byggs rad för rad på det lagrade värdet (`withChangedRows`); finns inget
  lagrat att bygga på skrivs listorna inte – vid `Offline` skrivs övriga ändringar, vid annat fel
  ingenting.
- **Accepterad risk:** att lägga till ett alternativ kontrollerar id-kollision mot cachen; är den
  ofullständig (första synken inte klar) kan ett omdöpt alternativ på samma id få tillbaka namnet
  och en ny plats och blir aktivt; stjärna och okända fält står kvar (`add` skriver bara `name`,
  `kind`, `sortOrder` och `archived = false` med `merge`).

## Security rules

- Allt under `users/{uid}` kräver `signedIn() && request.auth.uid == uid` (TP-12). Ingen kan
  lista `users` eller fråga över allas samlingar (`collectionGroup` nekas).
- Användardokumentet skapas bara av ägaren och då med `schemaVersion == 1` (4.0:s första
  format); det raderas aldrig från appen. `schemaVersion` skrivs som heltal, kan inte sänkas och
  höjs högst till `maxSchemaVersion()` (saknad lagrad version räknas som 1, BCK-15).
- Bara samlingarna i `collections.mjs` har regler; allt annat under användaren nekas (en okänd
  samling skulle stoppa backupen, BCK-16). Varje samling har en `valid…`-funktion med codecens
  fält, typer, intervall och enum-listor, i mönstret `(!('fält' in w) || nullOr…(d.get('fält', null)))`
  med `w = written()` (`let`, en gång per skrivning) – bara fält skrivningen ändrar kontrolleras, så
  att ett redan lagrat felaktigt värde inte låser dokumentet. Saknat eller `null` är tillåtet, okända
  fält får finnas (BCK-9, DAT-10). Enum-listorna (`slots()`, `doseStatuses()` …) jämförs med
  `WireEnum` av `RulesEnumsTest` i `:core`. Rules-testet "fixturens dokument godtas" skriver
  `tools/db`:s fixtur genom klientens SDK och fångar drift mellan codecs och rules; varje fält har
  minst ett ogiltigt fall.
- **Uttrycksbudget:** rules räknar högst 1 000 uttryck per skrivning. Listor av objekt (`symptoms`,
  `boosts`) kontrolleras därför som lista med tak (50), men elementen bara upp till det tionde;
  håll kontrollerna platta och mät med emulatorns `ruleCoverage` vid ändring.
- **Incheckningar** skrivs bara under en episod som finns efter skrivningen (`existsAfter`); en
  batch får slå upp högst 20 befintliga episoder (episoder som skapas i samma batch räknas inte), så
  importen skriver episoderna med sina incheckningar eller delar upp per högst 20 episoder.
- Ny samling = ny `match` med `valid…`-funktion + rules-test + rad i `collections.mjs` i samma PR.
- **Storlek (TP-12):** text via `nullOrShort`/`nullOrLong` (tak `maxShort()`/`maxLong()` = `TextLimits`
  i `:core`, 200/5 000, hålls lika av `schema.test.mjs`), listor med tak, antal fält via `fieldCount` –
  som typerna bara på fält som skrivs. `AppTextField` har samma tak.
- Varje rules-ändring granskas med Googles skill `firebase-security-rules-auditor` (rättigheter,
  create mot update, typer, storlek, `hasOnly`); dess fynd blir rules-tester. Våra regler ovan går före.
- Deploy sker från GitHub Actions: `rules.yml` (manuellt, dry-run som standard) och `release.yml`
  (före publiceringen, när de ändrats), båda efter rules-testerna mot emulatorn – aldrig från en PR. Reservväg:
  `npx --prefix tools/db firebase deploy --only firestore:rules` (README → Firebase-setup).
- **Fallgrop:** i `rules_version = '2'` matchar `{document=**}` även noll segment – det skulle
  ge underreglerna makt över användardokumentet. Använd uttryckliga `match /<samling>/{id}`,
  aldrig jokertecken. Rules-testet "användardokumentet kan inte raderas" och
  `collections.test.mjs` fångar det.

## Tester

| Test | Var |
|---|---|
| Codec-rundtur och tolerans | `core/src/test/.../schema/` (hjälparna i skill `testing-strategy`) |
| `CollectionContract` (i `app/src/sharedTest`) mot `FakeCollection` | `FakeCollectionContractTest`, JVM, varje PR när `app/` ändras |
| `CollectionContract` mot riktig `FirestoreCollection` + Firebase-emulatorn (firestore + auth, rules aktiva) | `FirestoreCollectionContractTest` i `app/src/androidTest` (instrumenttest: i PR:er som rör koden, skill `ci-budget`); användare och Firestore-instans per test från regeln `FirebaseEmulator` (skill `testing-strategy`) |
| Offline först och användarens version mot riktig Firestore | `FirestoreOfflineTest` i `app/src/androidTest` (`disableNetwork` → skrivning lyckas, syns lokalt, synkas efter `enableNetwork`) |
| Rules-test | `tools/db/test/rules.test.mjs` |
| `Paths` = `collections.mjs` = rules | `tools/db/test/collections.test.mjs` |

Repository-tester: bygg repositoryt med `FakeCollection` och testa bara domänfrågorna.
`FakeCollection` emulerar `upsert` med merge (okända fält bevaras, `null` tömmer), så att
JVM-testerna i varje PR fångar samma sak som instrumenttestet mot riktig Firestore.

## Kostnad (Spark-planen)

- Lyssna inte på stora samlingar utan `where`/`limit`. `doses`, `screenings`, `activities` och
  `events` växer med ~3 000–5 000 dokument per år: lyssna per dag eller period med `observeBetween` (Idag, Dagbok
  ett år i taget, Trender per vald period), aldrig på hela samlingen. "Allt"-perioden i Trender
  räknas ur cachen i `:core` (ARKITEKTUR.md → Risker).
- Första synken av flera års data efter migreringen är en engångskostnad inom Spark-planen.
- Stäng lyssnare när skärmen lämnas (`WhileSubscribed(5_000)` i ViewModel).

## Anti-mönster

- `FirebaseFirestore`, `CollectionReference` eller `DocumentReference` utanför `data/firestore/`,
  även i `di/` (fälls av `UiConsistencyTest` och hooken `regel4-check`).
- Egen felmappning eller egen `try/catch` runt Firestore i ett repository.
- Egen parsning av en `Map` i stället för en codec.
- En `Fake*Repository` med egen in-memory-logik i stället för `FakeCollection`.
- Serverdatum i `:core`, eller `Timestamp` i en modell.
