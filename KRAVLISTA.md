# Kravlista – Dagboken (Android)

> Hälsodagbok för att logga aktiviteter, daglig screening (energi, stress, symptom) och
> mediciner, med diagram, påminnelser och molnbackup via Google Drive.
>
> Version: 2.0.0 · Paket: `se.partee71.dagboken` · Språk: Svenska

---

## 1. Översikt och syfte

| ID | Krav |
|----|------|
| ÖV-1 | Appen ska låta en användare dagligen logga sitt mående genom **screening** (energi, stress, symptom). |
| ÖV-2 | Appen ska låta användaren logga **aktiviteter** med energipåverkan, stress, symptom och tidsåtgång. |
| ÖV-3 | Appen ska hantera **mediciner**: schemalagda (recept), engångsdoser och vid-behov (favoriter). |
| ÖV-4 | Appen ska visualisera trender över tid i **diagram**. |
| ÖV-5 | Appen ska fungera **offline-först**; all data lagras lokalt och synkas/backas upp till molnet. |
| ÖV-6 | Hela gränssnittet ska vara på **svenska**. |

---

## 2. Teknisk plattform (förutsättningar)

| ID | Krav |
|----|------|
| TP-1 | Android, **minSdk 26** (Android 8.0), targetSdk/compileSdk 35. |
| TP-2 | UI byggt med **Jetpack Compose** + Material 3. |
| TP-3 | Arkitektur: **MVVM** med Hilt (DI), Repository-mönster, ViewModels med `StateFlow`. |
| TP-4 | Lokal lagring i **Room**; inställningar i **DataStore (Preferences)**. |
| TP-5 | Inloggning via **Firebase Auth + Google Credential Manager**. |
| TP-6 | Molnbackup via **Google Drive (appDataFolder)**. |
| TP-7 | Bakgrundsjobb via **WorkManager** (Hilt-integrerad worker). |
| TP-8 | Påminnelser via **AlarmManager** + `BroadcastReceiver` + notifikationskanaler. |
| TP-9 | Krävda behörigheter: `INTERNET`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`. |

---

## 3. Navigation

| ID | Krav |
|----|------|
| NAV-1 | Appen ska ha en **bottennavigering** med fyra flikar: Hem, Aktivitet, Mediciner, Hälsa. |
| NAV-2 | Fliken **Hälsa** (Samsung Health) ska visas som inaktiverad platshållare (nedtonad, ej klickbar). |
| NAV-3 | Bottennavigeringen ska döljas på underliggande skärmar (lägg till/redigera, inställningar, diagram, migrering). |
| NAV-4 | Navigering ska bevara och återställa fliktillstånd (`saveState`/`restoreState`). |
| NAV-5 | Skärmövergångar ska animeras (slide + fade). |
| NAV-6 | Vid första start utan migrering ska **migreringsskärmen** kunna visas som startdestination. |

---

## 4. Hem-skärm

| ID | Krav |
|----|------|
| HEM-1 | Visa en **hälsningsbanner** baserad på tid på dygnet (God morgon/eftermiddag/kväll/natt) samt inloggat namn. |
| HEM-2 | Visa aktuellt **datum och veckonummer** (svensk lokalisering, ISO-vecka) samt **appversionen** (liten och diskret). |
| HEM-3 | ~~Visa **stat-pills**: antal tagna/totala mediciner idag samt senaste aktivitetens energinivå.~~ *(borttaget)* |
| HEM-4 | Visa ett **"Försenat"-kort** när medicintidpunkt passerats utan att vara tagen, eller screeningpåminnelse passerats utan loggning. |
| HEM-5 | Från försenat-kortet ska medicin kunna markeras som tagen direkt, och screening kunna nås via "Logga". |
| HEM-7 | Visa **sparkline-diagram** över **genomsnittlig energi per dag** senaste 7 dagarna, baserat på screenings (minst 2 datapunkter krävs, annars uppmaning att logga). |
| HEM-8 | Visa **snabbåtgärder**: "Logga aktivitet" och "Mediciner". |
| HEM-9 | Visa **kontobubbla** (avatar/foto) som öppnar konto-bottensheet (logga in/ut, inställningar). |
| HEM-10 | Säkerställa dagens medicinposter genereras vid skärmstart (`ensureTodayEntries`). |

---

## 5. Aktiviteter & Screening

### 5.1 Logga aktivitet

| ID | Krav |
|----|------|
| AKT-1 | Användaren ska kunna välja **aktivitetstyp** från konfigurerbara alternativ (default: Promenad, Jobb, Möte, Träning, Vila, Mat, Sällskap, Läsning, Övrigt). |
| AKT-2 | Vid valet "Övrigt" ska ett fritextfält visas för egen beskrivning. |
| AKT-3 | Aktivitet ska kunna märkas som **Återhämtande** och/eller **Energitjuv**. |
| AKT-4 | Användaren ska kunna sätta **energi** på skala **−10 till +10** (med beskrivande etikett och färg). |
| AKT-5 | Användaren ska kunna sätta **stress** på skala **0–10**. |
| AKT-6 | Användaren ska kunna gradera **symptom** (konfigurerbara, 0–10 per symptom), med fritext vid "Övrigt". |
| AKT-7 | Användaren ska kunna ange **tidsåtgång** (timmar + minuter). |
| AKT-8 | Mätvärden och symptom ska kunna fällas ihop/ut (foldout). |
| AKT-9 | Spara kräver att en aktivitetstyp valts (annars inaktiverad spara-knapp). |

### 5.2 Screening (dagligt mående)

| ID | Krav |
|----|------|
| SCR-1 | Användaren ska kunna logga en daglig screening: **energi 0–10** och **stress 0–10**. |
| SCR-2 | Screening ska kunna inkludera samma konfigurerbara **symptom** (0–10) som aktivitet. |
| SCR-3 | Screening sparas som post av typ `screening` och bekräftas med snackbar ("Screening sparad ✓"). |

### 5.3 Historik

| ID | Krav |
|----|------|
| HIS-1 | Historik ska visa loggade poster, **filtrerbara** på typ (aktivitet/screening). Minst en filtertyp måste vara aktiv. |
| HIS-2 | Poster ska kunna **redigeras** och **tas bort** (med bekräftelse via snackbar). |
| HIS-3 | Symptom lagras i wire-format `Namn:Poäng,Namn:Poäng` och summeras till `somatiska`. |
| HIS-4 | Datumetiketter i historiken ska visas som **"Idag"** för dagens datum, **"Igår"** för gårdagens datum, och **"Veckodag D Månad"** för äldre datum. |

---

## 6. Mediciner

### 6.1 Idag-flik

| ID | Krav |
|----|------|
| MED-1 | Visa dagens mediciner sorterade på tidpunkt (Morgon → Natt → Vid behov). |
| MED-2 | Varje medicin ska kunna markeras som **tagen/ej tagen**. |
| MED-3 | Receptgenererade poster ska kunna **hoppas över** (skippas) i stället för att raderas; engångsposter raderas. |
| MED-4 | Dagens receptposter ska genereras automatiskt och **idempotent** (stabilt ID `recept_{id}_{datum}_{tidpunkt}` förhindrar dubbletter). |
| MED-5 | Tagna mediciner ska kunna **döljas** i Idag-fliken; en toggle-knapp visar antalet dolda poster och låter användaren visa dem igen. |
| MED-6 | Kryssrutan för att markera tagen ersätts med en **animerad ikonknapp** (tomt cirkelkryss → fylld bockikon med färganimering). |
| MED-7 | Favoriter (vid-behov-mediciner) ska visas som ett **snabbval** direkt i Idag-fliken, under progressbaren; tryck loggar en dos med befintlig cooldown-/gränslogik. |

### 6.2 Schema-flik (recept)

| ID | Krav |
|----|------|
| REC-1 | Användaren ska kunna skapa/redigera **recept** med namn, dos, enhet, en eller flera tidpunkter och anteckning. |
| REC-2 | Recept ska stödja upprepningsmönster: **dagligen, vardagar, helger, anpassad (specifika veckodagar), intervall (var X:e dag)**. |
| REC-3 | Vid "anpassad" ska specifika veckodagar (0=Mån … 6=Sön) kunna väljas. |
| REC-4 | Vid "intervall" ska intervall i dagar anges; beräknas relativt receptets skapandedatum. |
| REC-5 | Recept ska kunna **aktiveras/inaktiveras** utan att raderas. |
| REC-6 | Standardklockslag per tidpunkt: Morgon 07, Förmiddag 10, Lunch 12, Eftermiddag 15, Kväll 19, Natt 22, Vid behov 12. |

### 6.3 Vid behov-flik (favoriter)

| ID | Krav |
|----|------|
| FAV-1 | Användaren ska kunna skapa **favoriter** (vid-behov-mediciner) med namn, dos, enhet, tidpunkt och anteckning. |
| FAV-2 | Favoriter ska visas som tryckbara kort; **tryck loggar en dos** direkt. |
| FAV-3 | **Långtryck** öppnar meny för redigera/ta bort (med bekräftelsedialog). |
| FAV-4 | Favorit ska kunna ha **minsta tid mellan doser** (kylperiod i timmar); dos blockeras med kvarvarande tid om för tidigt. |
| FAV-5 | Favorit ska kunna ha **max antal doser per dag** (0 = obegränsat); dos blockeras vid uppnådd gräns. |
| FAV-6 | Blockerad dos ska ge tydligt felmeddelande via snackbar. |
| FAV-7 | Favorit ska kunna ha dispenseringstid (fält finns i modellen). |

---

## 7. Diagram

| ID | Krav |
|----|------|
| DIA-1 | Visa trender över **genomsnittlig energi och stress per dag**. |
| DIA-2 | Tidsintervall ska kunna väljas (7/14/30/90 dagar). |
| DIA-3 | **Dataserier** (Energi, Stress) ska kunna visas och döljas individuellt via en **flervalsmeny**; båda kan visas simultant. |
| DIA-4 | Diagram ska nås från Hem, Aktiviteter och Mediciner (källparameter styr vy). |
| DIA-5 | Diagramhöjd ska vara minst **280dp** för god läsbarhet. |

---

## 8. Konto & autentisering

| ID | Krav |
|----|------|
| AUTH-1 | Användaren ska kunna **logga in med Google** (Credential Manager + Firebase Auth). |
| AUTH-2 | Användaren ska kunna **logga ut** och rensa credential-state. |
| AUTH-3 | Inloggad användares **namn, e-post och profilfoto** ska visas (kontobubbla/sheet/inställningar). |
| AUTH-4 | Inloggningsfel ska visas, men **avbruten inloggning** ska inte behandlas som fel. |
| AUTH-5 | Appen ska fungera utan inloggning; konto krävs endast för molnbackup/migrering. |

---

## 9. Backup & migrering

| ID | Krav |
|----|------|
| BCK-1 | Appen ska **automatiskt säkerhetskopiera** all data till Google Drive (appDataFolder) via WorkManager. |
| BCK-2 | Backup ska omfatta aktiviteter, mediciner, recept, favoriter samt aktivitets-/symptomalternativ (versionerat JSON). |
| BCK-3 | Endast de **5 senaste** backuperna ska behållas (äldre rensas). |
| BCK-4 | Backup ska kräva inloggat konto och Drive-auktorisering (`DRIVE_APPDATA`-scope); auktorisering kan kräva användarsamtycke. |
| BCK-5 | Användaren ska kunna **importera/migrera** data från senaste Drive-backup. |
| BCK-6 | Användaren ska kunna **importera från lokal fil** (JSON via dokumentväljare). |
| BCK-7 | Migrering ska visa tydliga tillstånd (kontrollerar, laddar ner, importerar med progress, klar/fel). |
| BCK-8 | Användaren ska kunna **hoppa över** migrering; status ska sparas så att den inte upprepas. |
| BCK-9 | Import ska vara robust mot okända JSON-fält (`ignoreUnknownKeys`). |

---

## 10. Notifikationer & påminnelser

| ID | Krav |
|----|------|
| NOT-1 | Två notifikationskanaler ska finnas: **Medicinpåminnelser** (default) och **Screeningpåminnelser** (low). |
| NOT-2 | **Medicinpåminnelser** ska kunna aktiveras/avaktiveras; larm sätts **15 minuter före** medicinens tidpunkt. |
| NOT-3 | Endast ej tagna/ej skippade mediciner ska generera larm. |
| NOT-4 | **Screeningpåminnelser** ska vara kopplade till fyra namngivna måltidshändelser: **Efter frukost, Lunch, Kvällsmat, Läggdags**. Varje händelse har ett eget på/av-reglage och en konfigurerbar tidpunkt. |
| NOT-5 | Screeninglarm som passerat dagens tid ska schemaläggas till nästa dag. |
| NOT-6 | Larm ska **återskapas efter omstart** av enheten (BOOT_COMPLETED). |
| NOT-7 | Vid ändrade inställningar ska samtliga larm schemaläggas om. |
| NOT-8 | Exakta larm ska användas när tillåtet; annars falla tillbaka på inexakt schemaläggning (`canScheduleExactAlarms`). |

---

## 11. Inställningar

| ID | Krav |
|----|------|
| SET-1 | **Tema-läge** ska kunna väljas via segmenterad knapp: ljust, mörkt eller **auto** (växlar på klockslag). |
| SET-2 | Vid auto-tema ska **start-timme för ljust respektive mörkt** kunna ställas in (validerade så att ljus < mörk). |
| SET-3 | ~~**Dynamiska färger** (Material You) ska kunna slås på/av.~~ *(toggle borttaget; Material You alltid aktiverat på Android 12+)* |
| SET-4 | **Medicinpåminnelser** ska kunna slås på/av. **Screeningpåminnelser** ska ställas in per måltidshändelse (På/av + tid per händelse). |
| SET-5 | **Aktivitetsalternativ** ska kunna läggas till och tas bort (inga dubbletter). |
| SET-6 | **Symptomalternativ** ska kunna läggas till och tas bort (inga dubbletter). |
| SET-7 | Konto (in-/utloggning) ska kunna hanteras från inställningar. |
| SET-8 | Import/migrering ska kunna startas från inställningar. |

---

## 12. Datamodell (krav på fält)

| Entitet | Nyckelfält |
|---------|-----------|
| **Aktivitet** | id, timestamp, datum, tid, aktivitet, energy (−10..10 / 1..10), stress (0..10), somatiska, symptom (wire), aterhamtande, energitjuv, type (`aktivitet`/`screening`), spentTime (min). |
| **Medicin** | id, timestamp, datum, tid, namn, dos, enhet, tidpunkt, tagen, anteckning, receptId?, skipped. |
| **Recept** | id, namn, dos, enhet, tidpunkter[], upprepning, dagar[], intervalDagar, anteckning, aktiv, skapad. |
| **Favorit** | id, namn, dos, enhet, tidpunkt, anteckning, minTidMellan (h), dispenseringsTid, maxDoserPerDag. |

| ID | Krav |
|----|------|
| DAT-1 | Tidpunkter ska sorteras enligt fast ordning: Morgon, Förmiddag, Lunch, Eftermiddag, Kväll, Natt, Vid behov. |
| DAT-2 | Datum ska lagras som `YYYY-MM-DD`, tid som `HH:MM`. |
| DAT-3 | Room-schema ska exporteras för migreringsspårning. |

---

## 13. Icke-funktionella krav

| ID | Krav |
|----|------|
| NFR-1 | **Offline-först**: all kärnfunktionalitet ska fungera utan nätverk. |
| NFR-2 | UI-tillstånd ska vara reaktivt (Flow/StateFlow) och överleva konfigurationsändringar. |
| NFR-3 | Release-bygge ska använda **R8/ProGuard** (minify + resource shrinking). |
| NFR-4 | Appen ska stödja **RTL** och systemets **predictive back**. |
| NFR-5 | Splash screen ska visas vid uppstart. |
| NFR-6 | Koden ska ha **enhetstester** (JUnit, MockK, Turbine) och **instrumenttester** (Compose UI, Room). |
| NFR-7 | Känslig data (backup) ska endast lagras i användarens privata Drive-appmapp. |
| NFR-8 | Appstorlek/prestanda: listor ska använda lazy-rendering; tunga operationer på IO-dispatcher. |

---

## 14. Kända begränsningar / framtida arbete

| ID | Notering |
|----|----------|
| FUT-1 | **Samsung Health / Hälsa-fliken** är endast en inaktiverad platshållare (ej implementerad). |
| FUT-2 | `sheetsConfig` (Google Sheets-koppling) finns i inställningslagret men är inte exponerat i UI. |
| FUT-3 | Backup-worker kan inte hantera Drive-auktorisering som kräver UI (returnerar success utan att ladda upp). |
