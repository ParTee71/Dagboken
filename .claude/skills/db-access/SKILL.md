---
name: db-access
description: Läsa Dagbokens Firestore-databas (users/{uid}) från en Claude-session (telefon/webb) med tools/db. Ladda denna när användaren frågar om faktisk data, vill se vad som ligger i databasen, felsöka data, kontrollera en import från 3.x, köra export/import/migrering eller när FIREBASE_SERVICE_ACCOUNT nämns. Trigger-ord: databasen, vad finns i, visa data, hur många, doser i databasen, mående i databasen, mina loggar, kom importen fram, Firestore-data, query, fråga databasen, tools/db, stats, export, import, migrera, OMB-4, service account, FIREBASE_SERVICE_ACCOUNT, FIREBASE_PROJECT_ID.
---

# Databasåtkomst från sessionen

Firestore nås med Node-verktygen i `tools/db/` (firebase-admin). All data ligger under
`users/{uid}` (ARKITEKTUR.md → Datamodell) och är hälsodata. För frågor om data – använd
agenten **`db-inspektor`**, så stannar dokumentinnehållet i agenten och bara svaret kommer
till huvudsessionen. Krav: TP-11.

## Förutsättningar

- Miljövariabeln `FIREBASE_SERVICE_ACCOUNT` (service-account-JSON, rå eller base64, rollen
  **Cloud Datastore Viewer**) i Claude-miljöns inställningar. Kontrollera bara **om** den
  finns (`[ -n "$FIREBASE_SERVICE_ACCOUNT" ] && echo satt || echo saknas`) – skriv aldrig
  ut värdet. Nyckeln måste höra till projektet `dagboken-711d2` – verktyget läser projektet ur
  `app/google-services.json` och stoppar en nyckel för ett annat projekt.
- `FIREBASE_PROJECT_ID` behövs bara om ett annat projekt än det i `app/google-services.json`
  ska läsas (t.ex. ett testprojekt).
- Nätverk till `firestore.googleapis.com`, `oauth2.googleapis.com` och `registry.npmjs.org`.
- `tools/db/node_modules` – installeras av session-start-hooken (`npm ci`).

**Saknas variabeln:** förklara att användaren lägger in den under miljöns inställningar
(miljömenyn i sessionens titelrad → Edit → miljövariabler) och att en ny session krävs.
**Be aldrig användaren klistra in nyckeln i chatten.**

## Kommandon (läsning – förhandsgodkända i `settings.json`)

```bash
node tools/db/stats.mjs                                   # samlingar, antal dokument, schemaVersion
node tools/db/query.mjs users                             # användare (uid) nyckeln ser
node tools/db/query.mjs options --fields kind,name        # relativt användaren
node tools/db/query.mjs doses --where date=2026-09-21 --fields slot,status --limit 20
node tools/db/query.mjs illnessEpisodes/<eid>/checkins --json   # rå JSON
node tools/db/get.mjs users/<uid>/settings/app            # ett dokument (full sökväg)
node tools/db/get.mjs settings/app                        # samma, relativt användaren
```

`--where` kan anges flera gånger (likhet); `true`, `false`, `null` och decimaltal tolkas
som sådana, text inom citattecken (`fält="007"`) förblir text. Tidsstämplar visas som `{ "__ts": "…Z" }`. Okända samlingar och sökvägar vägras.

Samlingar: `settings`, `options`, `prescriptions`, `prnMedicines`, `doses`, `screenings`,
`activities`, `events`, `illnessEpisodes`, `illnessEpisodes/<eid>/checkins`
(`tools/db/lib/collections.mjs`, enda listan).

Alla har `--help`. Finns bara en användare väljs den automatiskt; annars `--user <uid>`.

## Skrivande kommandon (kräver uttrycklig begäran och bekräftelse)

```bash
node tools/db/import.mjs --in tools/db/backup.json --dry-run   # visa vad som skulle skrivas
node tools/db/import.mjs --in tools/db/backup.json             # skriv
node tools/db/migrate.mjs --to <N>                        # schemamigrering (BCK-15)
```

- Kör **alltid** `--dry-run` först och visa sammanfattningen.
- Skrivning kräver en nyckel med skrivroll (`FIREBASE_SERVICE_ACCOUNT_RW`) – den ska bara
  finnas i miljön när användaren uttryckligen bett om det.
- `import` kontrollerar hela filen först och vägrar användare med högre `schemaVersion` än verktyget.
- `import --replace` tar bort dokument under filens användare som inte finns i filen (exakt
  återställning); visa alltid torrkörningens "Skulle ta bort" först.
- `import` läser bara 4.0-formatet (`tools/db export`). En 3.x Drive-backup (`BackupJson`)
  konverteras först av konverteraren i `:core` (OMB-3, BCK-14):
  `./gradlew :core:convertLegacyBackup --args="--in tools/db/backup-3x.json --out tools/db/export-4.json --user <uid>"`
  (från repots rot; rapporten med antal, varningar och stopp skrivs utan innehåll, exitkod 1 = stopp).
  Grinden OMB-4 är just den vägen: konvertera → `import --dry-run` → importera → exportera → jämför
  fältvis, noll skillnader (ARKITEKTUR.md → Migrering, punkt 3). **Alltid mot ett tomt scratch-uid**:
  `import` skriver dokumenten som de är (ingen merge) och skulle mot ett befintligt konto radera
  `createdAt` och inställningar som backupen saknade. Backupen, exporten och scratch-användaren raderas
  efteråt.
- Aldrig ad hoc-skrivningar med egna skript eller `node -e`.

## Integritet (skill `data-privacy-security`)

- Visa bara de fält och dokument frågan gäller – inga fulla dumpar i chatten.
- Spara aldrig utdrag på disk utanför `tools/db/` och aldrig i repot.
- Exportfiler (`tools/db/*.json`) är git-ignorerade; radera dem när de inte behövs.

## Återställning från veckobackup

1. Ladda ner senaste artifact från körningen av `backup.yml` i GitHub Actions.
2. `gpg --decrypt --output tools/db/backup.json backup.json.gpg` – alltid **till
   `tools/db/`**, som är git-ignorerad (lösenfras från användarens lösenordshanterare – be
   inte om den i chatten; användaren kör steget). gpg kontrollerar integriteten: en
   manipulerad eller trasig fil ger fel i stället för felaktig data.
3. `node tools/db/import.mjs --in tools/db/backup.json --dry-run`, visa, och kör sedan utan
   `--dry-run` efter bekräftelse. Radera `tools/db/backup.json` efteråt.
