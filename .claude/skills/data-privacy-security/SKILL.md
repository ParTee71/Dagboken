---
name: data-privacy-security
description: Integritet och säkerhet i Dagboken — mående, symptom, mediciner, doser och anteckningar är hälsodata (GDPR art. 9). Ladda alltid vid loggning, firestore.rules, backup/export/import, 3.x-migreringen, tools/db, Health Connect, notiser, behörigheter, inloggning, hemligheter eller release/minify. Trigger-ord: integritet, säkerhet, GDPR, hälsodata, PII, logga, Log, hemlighet, service account, keystore, BACKUP_PASSPHRASE, EU-region, Drive, Health Connect, notis, låsskärm, R8, ProGuard.
---

# Datasäkerhet & integritet

Dagboken lagrar **känslig hälsodata**: dagligt mående (energi, stress, symptom), aktiviteter,
händelser, mediciner och doser, sjukdomsepisoder och fria anteckningar. Det är persondata av
särskild kategori (GDPR art. 9). Behandla all sådan data som hemlig som standard.
Krav: TP-11, TP-12, BCK-11–14, HLS-5, NFR-13, NFR-3, AUTH-6 (ARKITEKTUR.md → Konsekvenser).

## Regler

### 1. Logga aldrig hälsoinnehåll eller PII
Inga `Log.d/i/w/e`, `println` eller `console.log` med symptom, energi, medicinnamn, doser,
anteckningar, sjukdomar, aktivitetsinnehåll, namn, e-post, uid eller exporterat innehåll. Logga som mest undantagsklass,
ogenomträngliga ID:n eller antal. Ta bort felsökningsloggar innan du anser dig klar.
Ingen analytics eller crash-rapportering som skickar innehåll. Släppt app loggar inget alls
(loggning strippas i release, NFR-13). Samma gäller `tools/db`, importens rapport (felmeddelanden
anger sökväg eller fält, aldrig värdet) och GitHub Actions-loggar.

### 2. All data i Firestore ligger bakom security rules
- All användardata ligger under `users/{uid}/…`. `firestore.rules` släpper bara in ägaren
  (`request.auth.uid == uid`) – ingen delning, inga medlemslistor (ADR-001, beslut 2).
- Databasen ligger i **EU-region** (`europe-west`, ARKITEKTUR.md → Konsekvenser). Ett nytt
  Firebase-projekt eller en ny databas skapas aldrig utanför EU.
- Ny samling = ny rules-rad **och** rules-test i samma ändring (skill
  `firestore-data-layer`). En samling utan rules är ett säkerhetshål eller oåtkomlig.
- Rules deployas medvetet: av `release.yml` före publiceringen eller av `rules.yml` på begäran
  (dry-run som standard) – aldrig från en PR och aldrig automatiskt på `master`. Deploynyckeln
  (`FIREBASE_RULES_DEPLOYER`) får bara rollen Firebase Rules Admin (+ Service Usage Consumer och Service Usage Viewer, som firebase-tools behöver för att se att Firestore-API:t är på)
  och stoppas om den hör till ett annat projekt än `dagboken-711d2`.
- Appen **kräver inloggning** (AUTH-6; 3.x-kravet AUTH-5 "fungerar utan konto" är struket);
  ingen data läses eller skrivs utan `request.auth`.

### 3. Backup och export är krypterade och medvetna
- Veckobackupen (`tools/db export` i GitHub Actions) krypteras med gpg (AES-256, med
  integritetskontroll) och `BACKUP_PASSPHRASE`
  innan den laddas upp som artifact; klartextfilen raderas i samma jobb. Ladda aldrig
  upp okrypterad data som artifact, i en issue eller i en PR.
- Appens export (BCK-13) sker bara på uttrycklig användarhandling, till en fil som användaren
  väljer via dokumentväljaren.
- Inga andra exportvägar, ingen extern lagring, inga `READ/WRITE_EXTERNAL_STORAGE` eller
  `MANAGE_EXTERNAL_STORAGE`.
- **Drive-backupen är pensionerad** (BCK-1–5 strukna), och sedan 4.9.0 läser appen inte Drive alls
  (legacyimporten, ~~BCK-14~~, är borttagen). Inget Drive-scope begärs; ett nytt kräver beslut.
- **Room-filen från 3.x** läses inte sedan 4.8.0 (migreringen på enheten är borttagen) och raderas aldrig
  automatiskt; den ligger kvar i appens privata lagring.
- **Health Connect-data persisteras inte** – varken i Firestore eller i exporten (HLS-5). Den
  läses live och visas; aldrig i loggar.
- Androids egen backup och enhetsöverföring är avstängda: `android:allowBackup="false"` och
  `res/xml/data_extraction_rules.xml` som undantar alla domäner (testas av `PrivacyManifestTest`).
  En ny telefon hämtar datan från Firestore efter inloggning.

- Dekrypterade backuper och exporter läggs bara i `tools/db/` (git-ignorerad) och raderas
  efter användning. `.gitignore` fångar dessutom `backup*.json` och `export*.json` överallt, utan undantag.
- **Tester rör aldrig den riktiga databasen** – bara Firebase-emulatorn med projekt-ID
  `demo-dagboken` (skill `data-safety-backup`). Fixturer är syntetiska, aldrig utdrag ur en
  riktig backup eller export.

### 4. Hemligheter får aldrig checkas in eller klistras in
- Git-ignorerade och aldrig i repot: `*.jks`/`*.keystore` (`dagboken.jks`), `local.properties`,
  service-account-JSON, `tools/db/*.json`-exporter, `backup*.json`, `export*.json`, `*.gpg`.
- **`app/google-services.json` är incheckad** sedan 3.x. Den är Firebase-klientkonfiguration
  (projekt-ID, API-nyckel för Android, OAuth-klient-ID) och ingen hemlighet – skyddet är rules,
  inloggning och nyckelbegränsningen till appens paket och signatur i Google Cloud. Lägg aldrig
  till *nya* hemligheter bredvid den.
- I CI kommer hemligheterna från GitHub-secrets (`FIREBASE_SERVICE_ACCOUNT`,
  `FIREBASE_RULES_DEPLOYER`, `BACKUP_PASSPHRASE`, `SIGNING_*`).
- I Claude-sessionen kommer `FIREBASE_SERVICE_ACCOUNT` från miljöns inställningar.
  **Be aldrig användaren klistra in en nyckel, token eller lösenfras i chatten**, och
  skriv aldrig ut värdet – kontrollera bara om variabeln är satt.
- Service account för Claude-miljön har **läsroll** (Cloud Datastore Viewer). En
  skrivande nyckel används bara i backup/import och bara när användaren uttryckligen bett
  om det (skill `db-access`).
- Signeringslösenord läses från `local.properties` eller miljövariabler, aldrig hårdkodat.

### 5. Visa bara det som frågats
När en Claude-session läser databasen (`tools/db`, agenten `db-inspektor`) visas bara de
fält och dokument frågan gäller – inga fulla dumpar i chatten.

### 6. Release ska minifieras
`isMinifyEnabled = true` + `isShrinkResources = true` på release. Bibliotek som kräver
keep-regler (kotlinx.serialization, Firestore-modeller, Hilt) får regler i
`app/proguard-rules.pro`. Minifierat bygge verifieras i veckoflödet `quality.yml` (när master ändrats) och av
release-bygget – inte per PR (skill `ci-budget`).

### 7. Behörigheter och autentisering
- Behörigheterna är de i TP-9 (`INTERNET`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
  `RECEIVE_BOOT_COMPLETED`) plus Health Connects läsbehörigheter (HLS). Nya behörigheter bara vid
  tydligt behov, och de begärs först när de behövs (skill `notifications-alarms`).
- **Notiser** visar aldrig hälsoinnehåll på låsskärmen: privat synlighet, och medicinnamn eller
  symptom bara i den utfällda notisen när telefonen är upplåst.
- Inloggning går via `AuthRepository` (Credential Manager + Firebase) – anropa aldrig
  Firebase direkt från UI. Avbruten inloggning är inte ett fel.

## Checklista

- [ ] Inga loggar med hälsoinnehåll/PII – i appen, i `tools/db` och i CI.
- [ ] Ny samling har rules och rules-test; inget utanför `users/{uid}`, inget utanför EU.
- [ ] Health Connect-data persisteras inte (HLS-5); notiser är privata på låsskärmen.
- [ ] Backup/export är krypterad eller användarinitierad; inga nya exportvägar.
- [ ] Inga hemligheter incheckade, utskrivna eller efterfrågade i chatten.
- [ ] Nya bibliotek har keep-regler så release-minify inte går sönder.
- [ ] Inga nya/bredare behörigheter utan tydligt behov.
- [ ] Ny eller ändrad exporterad komponent, notis med `PendingIntent`, extras eller delning:
  granskad med Googles skill `android:android-intent-security`.
- [ ] Ändrade `firestore.rules`: granskade med Googles skill `android:firebase-security-rules-auditor`.
