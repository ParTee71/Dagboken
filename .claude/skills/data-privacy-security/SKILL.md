---
name: data-privacy-security
description: Dagbokens integritets- och säkerhetsregler — appen hanterar känslig hälsodata (persondata enligt GDPR). Ladda denna ALLTID när du rör loggning, backup/molnlagring, behörigheter, autentisering, hemligheter (nycklar/tokens), release/minify eller något som exponerar eller lagrar användardata. Trigger-ord: integritet, säkerhet, privacy, GDPR, känslig data, hälsodata, PII, logga, Log, secret, hemlighet, keystore, jks, google-services, token, Drive, appDataFolder, behörighet, permission, minify, R8, ProGuard.
---

# Datasäkerhet & integritet

Dagboken lagrar **känslig hälsodata** (mående, symptom, mediciner, sjukdomar). Det är
persondata av särskild kategori (GDPR art. 9). Behandla all sådan data som hemlig som
standard. Krav: **NFR-7, NFR-3, BCK-4, AUTH-5**.

## Regler

### 1. Logga aldrig hälsoinnehåll eller PII
Inga `Log.d/i/w/e` (eller print) med symptom, anteckningar, aktivitetsinnehåll, namn,
e-post eller backup-innehåll. Logga som mest ogenomträngliga ID:n/antal vid felsökning,
och ta bort felsökningsloggar innan du anser dig klar. Ingen analytics på hälsoinnehåll.

### 2. Backup endast till privat Drive appDataFolder (NFR-7, BCK-4)
All molnbackup går till användarens **privata** `appDataFolder` med scope
`DriveScopes.DRIVE_APPDATA` — aldrig till vanlig Drive, extern lagring eller tredje part.
`DriveBackupRepository` använder konsekvent `.setSpaces("appDataFolder")` och
`parents = listOf("appDataFolder")`. Bryt inte det. Begär minsta möjliga scope; vidga
aldrig till bredare Drive-scopes "för säkerhets skull".

### 3. Lokal lagring stannar lokalt
Inga `READ/WRITE_EXTERNAL_STORAGE` eller `MANAGE_EXTERNAL_STORAGE` (appen har inga idag —
håll det så). Room-databasen ligger i appens privata lagring. Exportera bara data via de
befintliga, medvetna flödena (Drive-backup, användarinitierad fil-export/import).

### 4. Hemligheter får aldrig checkas in
- Keystore (`*.jks`, `*.keystore`) och `local.properties` är git-ignorerade — håll dem så.
- Signeringslösenord läses från `local.properties` eller miljövariabler
  (`SIGNING_STORE_PASSWORD` m.fl.), aldrig hårdkodade i `build.gradle.kts`.
- **`google-services.json`:** README säger att den är git-ignorerad, men den är i dag
  **spårad** och saknas i `.gitignore`. Detta är en avvikelse: antingen lägg till den i
  `.gitignore` och dokumentera lokal/CI-setup, eller uppdatera README så att den speglar
  verkligheten. Lägg aldrig till *nya* hemligheter (service accounts, privata nycklar,
  OAuth-klienthemligheter) i repot.

### 5. Release ska minifieras (NFR-3)
`isMinifyEnabled = true` + `isShrinkResources = true` på release-bygget. Lägger du ett
bibliotek som behöver keep-regler, uppdatera `app/proguard-rules.pro` (annars kraschar
release men inte debug). Verifiera reflektionsberoende kod (serialisering, Room, Hilt).

### 6. Behörigheter & autentisering
- Begär minsta möjliga behörigheter och först när de behövs.
- Appen ska fungera **utan inloggning**; konto krävs bara för backup/migrering (AUTH-5).
  Lås inte kärnfunktioner bakom inloggning.
- Inloggning går via `FirebaseAuthRepository` (Credential Manager + Firebase) — anropa
  aldrig Firebase direkt från UI. Avbruten inloggning är inte ett fel (AUTH-4).

## Checklista

- [ ] Inga loggar med hälsoinnehåll/PII; felsökningsloggar borttagna.
- [ ] Ny lagring/exportväg går bara till privat lagring eller Drive `appDataFolder`.
- [ ] Inga nya hemligheter incheckade; inga hårdkodade lösenord/nycklar.
- [ ] Nya bibliotek har keep-regler så release-minify inte går sönder.
- [ ] Inga nya/bredare behörigheter utan tydligt behov.
