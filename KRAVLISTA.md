# Kravlista – Dagboken (Android)

> Hälsodagbok för att logga aktiviteter, daglig screening (energi, stress, symptom) och
> mediciner, med diagram, påminnelser och molnbackup via Google Drive.
>
> Version: 2.15.0 · Paket: `se.partee71.dagboken` · Språk: Svenska

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
| TP-1 | Android, **minSdk 30** (Android 11), targetSdk 35, compileSdk 36 (krävs av Health Connect `connect-client` 1.1.0). |
| TP-2 | UI byggt med **Jetpack Compose** + Material 3. |
| TP-3 | Arkitektur: **MVVM** med Hilt (DI), Repository-mönster, ViewModels med `StateFlow`. |
| TP-4 | Lokal lagring i **Room**; inställningar i **DataStore (Preferences)**. |
| TP-5 | Inloggning via **Firebase Auth + Google Credential Manager**. |
| TP-6 | Molnbackup via **Google Drive (appDataFolder)**. |
| TP-7 | Bakgrundsjobb via **WorkManager** (Hilt-integrerad worker). |
| TP-8 | Påminnelser via **AlarmManager** + `BroadcastReceiver` + notifikationskanaler. |
| TP-9 | Krävda behörigheter: `INTERNET`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`. |
| TP-10 | Hälsodata läses från **Health Connect** (`androidx.health.connect:connect-client`); ingen egen lagring, ingen Samsung-partner krävs (read-only via sideload). Se §19. |

---

## 3. Navigation

| ID | Krav |
|----|------|
| NAV-1 | ~~Appen ska ha en **bottennavigering** med fem flikar: Hem, Aktivitet, Mediciner, Händelser, Sjukdomar.~~ *(ersatt av NAV-7 — se #84 etapp 4)* |
| NAV-2 | ~~Fliken **Hälsa** (Samsung Health) ska visas som inaktiverad platshållare (nedtonad, ej klickbar).~~ *(borttaget — Hälsa är ingen bottennavflik; hälsodata (Health Connect) nås via ett kort i Hantera, se §19 HLS-6)* |
| NAV-3 | Bottennavigeringen ska döljas på underliggande skärmar (lägg till/redigera, sjukdomar, recept & scheman, migrering). |
| NAV-4 | Navigering ska bevara och återställa fliktillstånd (`saveState`/`restoreState`). |
| NAV-5 | Skärmövergångar ska animeras (slide + fade). |
| NAV-6 | Vid första start utan migrering ska **migreringsskärmen** kunna visas som startdestination. |
| NAV-7 | Appen ska ha en **bottennavigering** med fyra flikar: **Idag** (dagens checklista, se §4), **Historik** (§16), **Trender** (§17) och **Hantera** (bibliotek/konfiguration, se §18). |

---

## 4. Idag-skärm

> Hette "Hem-skärm" innan navigationsbytet i #84 etapp 4 (se §3, NAV-7). Kravtexterna
> nedan behåller sina ursprungliga HEM-ID:n för spårbarhet.

| ID | Krav |
|----|------|
| HEM-1 | Visa en **hälsningsbanner** baserad på tid på dygnet (God morgon/eftermiddag/kväll/natt) samt inloggat namn. |
| HEM-2 | Visa aktuellt **datum och veckonummer** (svensk lokalisering, ISO-vecka) samt **appversionen** (liten och diskret). |
| HEM-3 | ~~Visa **stat-pills**: antal tagna/totala mediciner idag samt senaste aktivitetens energinivå.~~ *(borttaget)* |
| HEM-4 | Visa en **checklista för vald dag** (dagens datum eller en tidigare dag, se HEM-14): alla dagens mediciner (avbockningsbara direkt) och alla aktiverade screeningtillfällen (per måltidstillfälle), med status loggad/försenad/kommande. Kort med försenade poster märks med en textetikett ("Försenat") utöver accentfärg. "Försenat"-status gäller endast dagens datum — en tidigare dags ologgade post är bara ologgad, inte försenad. |
| HEM-5 | Mediciner ska kunna markeras som tagna direkt i checklistan utan navigering. Screening ska kunna loggas **inline**: expandera radens tillhörande måltidstillfälle och fyll i direkt på Idag, utan att navigera bort. Inline-formuläret presenteras som **svepbara steg-kort** (energi → stress → symptom, där symptomsteget bara visas när symptom är konfigurerade) med stegindikator och Föregående/Nästa/Spara, via delad komponent `StepwiseScreeningForm`. Loggas mot den dag som visas i checklistan (HEM-14), inte alltid dagens datum. |
| HEM-7 | Visa **sparkline-diagram** över **genomsnittlig energi per dag** senaste 7 dagarna, baserat på screenings (minst 2 datapunkter krävs, annars uppmaning att logga); länk till Trender-ytan (§17) för fördjupning. Dagsvärdet beräknas av delad `computeDailyEnergyStats` (regel 4) — samma uträkning som Trenders "Energi (dag)" (TRD-8), så de två aldrig kan visa olika värden för samma dag. Delar linjestil (mjuk kurva + gradientfyllning, TRD-6) med Trender-diagrammet för visuell konsekvens. Diagrammet visar värden på **båda axlarna**: y-axel med energiskala, x-axel med veckodagsetiketter, samt lägsta/högsta värde som text under diagrammet (TRD-9). Y-axeln skalas smart efter de faktiska värdenas min/max (TRD-7), inte alltid nollankrad. Ingår i det gemensamma trenddiagrammet på Idag tillsammans med steg- och vilopulstrend, se HEM-17. |
| HEM-8 | ~~Visa **snabbåtgärder**: "Logga aktivitet" och "Mediciner".~~ *(ersatt av global "+"-FAB med snabbval: Aktivitet/Engångsdos/Ny vid behov-favorit/Händelse)* |
| HEM-9 | Visa **kontobubbla** (avatar/foto) som öppnar konto-bottensheet (logga in/ut, Hantera). |
| HEM-10 | Säkerställa vald dags medicinposter genereras (`ensureEntriesForDate`) — vid skärmstart för dagens datum, och på nytt varje gång användaren bläddrar till ett nytt datum (HEM-14), inklusive en tidigare dag som aldrig var "idag" senast appen var öppen. |
| HEM-11 | Favoritmarkerade vid behov-mediciner ska visas som tryckbara snabbvalskort direkt i checklistan (samma beteende som tidigare MED-7); tryck loggar en dos med befintlig cooldown-/gränslogik, långtryck öppnar redigera/ta bort/favoritmarkera. Nya favoriter skapas via "+"-FAB. |
| HEM-12 | Pågående sjukdomsepisod ska visas som ett accentmärkt kort som länkar till sjukdomsdetaljer (Hantera → Sjukdomar). |
| HEM-13 | I början av veckan (söndag/måndag) ska ett **veckosammanfattningskort** visas överst på Idag, ovanför datumnavigeringsraden (HEM-14): energitrend (senaste 7 dagarnas genomsnittliga screeningenergi jämfört med föregående 7 dagar, ↑/↓/oförändrad) och andel tagna av veckans schemalagda doser (%). Beräknas live från befintliga poster via delad `DagbokenCard` — ingen ny persisterad data. Döljs om underlag saknas. |
| HEM-14 | Idag-checklistan (mediciner + screening) ska kunna bläddras till en **tidigare dag** via en datumnavigeringsrad ("< Föregående dag · [datum] · Nästa dag >"). Kan inte bläddra in i framtiden — "Nästa dag" är avstängd på dagens datum. Att öppna "Ny händelse" från en tidigare dags Idag-vy förifyller den nya händelsens datum med den visade dagen. Datumnavigeringsraden är den översta sektionen i samlingskortet, se HEM-16. |
| HEM-15 | Idag visar ett **hälsokort** (Health Connect) med **steg och vilopuls för vald dag** (HEM-14, se HLS-7 §19) — byter siffror när användaren bläddrar till en annan dag. Döljs/ersätts av en diskret koppla-rad när data/behörighet saknas. Kortet visas längst ner på Idag, direkt ovanför det gemensamma trenddiagrammet (HEM-17), eftersom båda är hälsorelaterat innehåll snarare än dagens checklistor. |
| HEM-16 | Datumnavigeringsraden (HEM-14) och checklistorna för **mediciner**, **screening** och **vid behov**-mediciner visas grupperade i **ett gemensamt kort** på Idag (sektioner separerade med avdelare), eftersom alla styrs av samma valda dag. |
| HEM-17 | Stegtrend, vilopulstrend (HLS-7) och energitrend (HEM-7) för senaste 7 dagarna visas i **ett gemensamt diagramkort** på Idag i stället för separata kort — varje trend en egen `SparklineChart`-rad (regel 4), i ordningen **steg → vilopuls → energi**, var och en med lägsta/högsta värde som text under diagrammet (TRD-9). Steg-/vilopulstrenden visas bara när Health Connect är tillgängligt och har minst 2 dagar med värde; energitrenden kräver minst 2 loggade screeningdagar, annars en uppmaning att logga. Kortet har en länk till Trender-ytan (§17). |

---

## 5. Aktiviteter & Screening

### 5.1 Logga aktivitet

| ID | Krav |
|----|------|
| AKT-1 | Användaren ska kunna välja **aktivitetstyp** från konfigurerbara alternativ (default: Promenad, Jobb, Möte, Träning, Vila, Mat, Sällskap, Läsning, Övrigt). Ändringar gjorda i Inställningar ska synas direkt i loggningsformuläret utan omstart. |
| AKT-2 | Vid valet "Övrigt" ska ett fritextfält visas för egen beskrivning. |
| AKT-3 | Aktivitet ska kunna märkas som **Återhämtande** och/eller **Energitjuv**. |
| AKT-4 | Användaren ska kunna sätta **energi** på skala **−10 till +10** (med beskrivande etikett och färg). |
| AKT-5 | Användaren ska kunna sätta **stress** på skala **0–10**. |
| AKT-6 | Användaren ska kunna gradera **symptom** (konfigurerbara, 0–10 per symptom), med fritext vid "Övrigt". |
| AKT-7 | Användaren ska kunna ange **tidsåtgång** (timmar + minuter). |
| AKT-8 | Mätvärden och symptom ska kunna fällas ihop/ut (foldout). |
| AKT-9 | Spara-knappen (`SaveButton`, se NFR-10) kräver att en aktivitetstyp valts **och** att formuläret har osparade ändringar (dirty-state) — annars inaktiverad. |
| AKT-10 | Under registreringsformuläret ska de tre senaste loggade posterna (aktivitet och screening blandat, sorterade på tid, nyast överst) visas i en lista; varje post ska kunna redigeras eller tas bort (samma flöde som Historik). |
| AKT-11 | Användaren ska kunna lägga till, redigera och ta bort en fritextanteckning på en aktivitetsregistrering, via den delade anteckningskomponenten. |
| AKT-12 | När en **ny** aktivitet loggas via globala "+"-FAB:en ska formuläret förifyllas: tid = nu samt senaste aktivitetstyp och tidsåtgång från den senast loggade aktiviteten. Förvalen beräknas live från senaste post — inget cachas eller persisteras. |

### 5.2 Screening (dagligt mående)

| ID | Krav |
|----|------|
| SCR-1 | Användaren ska kunna logga en daglig screening: **energi 0–10** och **stress 0–10**. |
| SCR-2 | Screening ska kunna inkludera samma konfigurerbara **symptom** (0–10) som aktivitet. |
| SCR-3 | Screening sparas som post av typ `screening` och bekräftas med snackbar ("Screening sparad ✓"). |
| SCR-4 | Samma lista med de tre senaste registreringarna (se AKT-10) visas även under Screening-formuläret. |
| SCR-5 | Användaren ska kunna lägga till, redigera och ta bort en fritextanteckning på en screening-registrering, via den delade anteckningskomponenten. |
| SCR-6 | En screening loggad från Idag-skärmens inline-formulär (HEM-5) sparas mot den dag som visas där, inte alltid dagens datum — se HEM-14. |

### 5.3 Historik ~~(per-flik)~~ *(ersatt av Historik-ytan, §16, sedan navigationsbytet i #84 etapp 4)*

| ID | Krav |
|----|------|
| HIS-1 | ~~Historik ska visa loggade poster, **filtrerbara** på typ (aktivitet/screening). Minst en filtertyp måste vara aktiv.~~ *(se HIST-1/HIST-2, §16)* |
| HIS-2 | ~~Poster ska kunna **redigeras** och **tas bort** (med bekräftelse via snackbar).~~ *(Historik-ytan navigerar till redigeringsskärmen vid tryck, HIST-3 §16; borttagning sker där, inte längre inline i listan)* |
| HIS-3 | Symptom lagras i wire-format `Namn:Poäng,Namn:Poäng` och summeras till `somatiska`. *(datamodellkrav, fortsatt giltigt oavsett yta)* |
| HIS-4 | ~~Datumetiketter i historiken ska visas som **"Idag"** för dagens datum, **"Igår"** för gårdagens datum, och **"Veckodag D Månad"** för äldre datum.~~ *(motsvarande gruppering per dag i Historik-ytan, HIST-1 §16)* |
| HIS-5 | ~~Ett kort för en aktivitet eller screening som har en anteckning ska visa en liten info-ikon; tryck på ikonen visar anteckningen i en läs-only dialog med en Stäng-knapp.~~ *(ej implementerat i Historik-ytan ännu — anteckningen syns efter tryck till redigeringsskärmen)* |

---

## 6. Mediciner

### 6.1 Idag-flik *(innehållet är nu del av Idag-skärmen, se §4, sedan navigationsbytet i #84 etapp 4)*

| ID | Krav |
|----|------|
| MED-1 | Visa vald dags (se HEM-14) mediciner sorterade på tidpunkt (Morgon → Natt → Vid behov). Som standard visas endast **aktuella/försenade** poster (schemalagd tid nådd, eller "Vid behov") — tagna poster göms bakom MED-5, kommande poster (schemalagd tid ej nådd) göms bakom MED-13. Gäller endast för dagens datum; en tidigare dag har inga "kommande" poster. |
| MED-2 | Varje medicin ska kunna markeras som **tagen/ej tagen**. |
| MED-3 | Receptgenererade poster ska kunna **hoppas över** (skippas) i stället för att raderas; engångsposter raderas. |
| MED-4 | Vald dags receptposter ska genereras automatiskt och **idempotent** (stabilt ID `recept_{id}_{datum}_{tidpunkt}` förhindrar dubbletter), inklusive en tidigare dag som bläddras till (HEM-14/HEM-10). |
| MED-5 | Tagna mediciner ska kunna **döljas** i checklistan; en toggle-knapp visar antalet dolda poster och låter användaren visa dem igen. |
| MED-6 | Kryssrutan för att markera tagen ersätts med en **animerad ikonknapp** (tomt cirkelkryss → fylld bockikon med färganimering). |
| MED-7 | ~~Favoriter (vid-behov-mediciner) ska visas som ett **snabbval** direkt i Idag-fliken, under progressbaren; tryck loggar en dos med befintlig cooldown-/gränslogik.~~ *(se HEM-11, §4)* |
| MED-11 | Varje medicinpost (dos) ska kunna ha en anteckning, redigerbar via den delade `NoteField`-komponenten på redigeringsskärmen. Loggas en dos från en favorit ärvs favoritens anteckning som förvalt värde på dosen. |
| MED-12 | En medicinrad (Idag-checklistan eller Historik-ytan) som har en anteckning ska visa en liten info-ikon; tryck på ikonen visar anteckningen i en läs-only dialog med en Stäng-knapp. |
| MED-13 | Kommande mediciner (schemalagd tid ännu ej nådd, dagens datum) ska kunna **döljas** i checklistan; en toggle-knapp visar antalet dolda poster och låter användaren visa dem igen (analogt med MED-5). |

### 6.2 Schema-flik (recept) *(nås nu via Hantera → Recept & scheman, se §18, sedan navigationsbytet i #84 etapp 4)*

| ID | Krav |
|----|------|
| REC-1 | Användaren ska kunna skapa/redigera **recept** med namn, dos, enhet, en eller flera tidpunkter och en anteckning (delad `NoteField`-komponent). Anteckningen ärvs som förval på varje dos receptet genererar. |
| REC-2 | Recept ska stödja upprepningsmönster: **dagligen, vardagar, helger, anpassad (specifika veckodagar), intervall (var X:e dag)**. |
| REC-3 | Vid "anpassad" ska specifika veckodagar (0=Mån … 6=Sön) kunna väljas. |
| REC-4 | Vid "intervall" ska intervall i dagar anges; beräknas relativt receptets skapandedatum. |
| REC-5 | Recept ska kunna **aktiveras/inaktiveras** utan att raderas. |
| REC-6 | Standardklockslag per tidpunkt: Morgon 07, Förmiddag 10, Lunch 12, Eftermiddag 15, Kväll 19, Natt 22, Vid behov 12. |

### 6.3 Vid behov-flik (favoriter) *(snabbvalet flyttat till Idag-skärmen, se HEM-11 §4; favoritmarkering hanteras i Hantera, se §18, sedan navigationsbytet i #84 etapp 4)*

| ID | Krav |
|----|------|
| FAV-1 | Användaren ska kunna skapa **favoriter** (vid-behov-mediciner) med namn, dos, enhet, tidpunkt och en anteckning (delad `NoteField`-komponent), via "+"-FAB på Idag-skärmen. |
| FAV-2 | Endast **favoritmarkerade** favoriter visas som tryckbara kort (chips) i Idag-skärmens vid behov-kort; **tryck loggar en dos** direkt. Icke-favoritmarkerade favoriter nås via en "Fler"-lista i samma kort. |
| FAV-3 | **Långtryck** öppnar meny för redigera/ta bort (med bekräftelsedialog). |
| FAV-4 | Favorit ska kunna ha **minsta tid mellan doser** (kylperiod i timmar); dos blockeras med kvarvarande tid om för tidigt. |
| FAV-5 | Favorit ska kunna ha **max antal doser per dag** (0 = obegränsat); dos blockeras vid uppnådd gräns. |
| FAV-6 | Blockerad dos ska ge tydligt felmeddelande via snackbar. |
| FAV-7 | Favorit ska kunna ha dispenseringstid (fält finns i modellen). |
| FAV-8 | Långtrycksmenyn ska även kunna växla favoritmarkering, utöver redigera/ta bort. |
| FAV-9 | En favorit-chip med anteckning ska visa en liten info-ikon; tryck på ikonen visar anteckningen i en läs-only dialog med en Stäng-knapp. |

### 6.4 Historik-flik ~~(per-flik)~~ *(ersatt av Historik-ytan, §16, sedan navigationsbytet i #84 etapp 4)*

| ID | Krav |
|----|------|
| MED-8 | ~~Mediciner-fliken ska ha en fjärde underflik, **Historik**, som visar tidigare loggade medicinposter grupperade per datum (senaste överst).~~ *(se HIST-1, §16)* |
| MED-9 | ~~Historik ska kunna **filtreras** på typ: **Recept** (schemalagda) och **Vid behov** (favoriter/engångsdoser); minst en filtertyp måste vara aktiv.~~ *(Historik-ytans typfilter, HIST-2 §16, filtrerar på medicin som helhet — den finmaskiga recept/vid behov-uppdelningen bevarades inte i sammanslagningen)* |
| MED-10 | ~~Poster i Historik ska kunna **redigeras** (öppnar redigera medicin) och **tas bort** (med bekräftelse via dialog).~~ *(se HIST-3, §16 — borttagning sker på redigeringsskärmen)* |

---

## 7. Diagram ~~(DiagramScreen/SymptomDiagramScreen)~~ *(borttaget — ersatt av Trender-ytan, §17, sedan navigationsbytet i #84 etapp 4)*

| ID | Krav |
|----|------|
| DIA-1 | ~~Visa trender över **genomsnittlig energi och stress per dag**.~~ *(se TRD-1, §17)* |
| DIA-2 | ~~Tidsintervall ska kunna väljas (7/14/30/90 dagar).~~ *(se TRD-3, §17)* |
| DIA-3 | ~~**Dataserier** (Energi, Stress) ska kunna visas och döljas individuellt via en **flervalsmeny**; båda kan visas simultant.~~ *(se TRD-1/TRD-2, §17 — utökat till att även omfatta symptomserier)* |
| DIA-4 | ~~Diagram ska nås från Hem, Aktiviteter och Mediciner (källparameter styr vy).~~ *(se TRD-5, §17)* |
| DIA-5 | ~~Diagramhöjd ska vara minst **280dp** för god läsbarhet.~~ *(oförändrat värde, ärvt av `LineChartCanvas` som återanvänds av Trender-ytan)* |

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
| BCK-2 | Backup ska omfatta aktiviteter, mediciner, recept, favoriter (inklusive favoritmarkering), händelser, sjukdomar, anteckningar (generisk `notes`-tabell) samt aktivitets-/symptom-/händelsetypalternativ inklusive favoritstatus (versionerat JSON). |
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
| NOT-9 | Tryck på en medicin- eller screeningpåminnelse ska öppna appen på **Idag-skärmen** (tidigare Mediciner- respektive Aktivitet-fliken, uppdaterat sedan navigationsbytet i #84 etapp 4). |
| NOT-10 | Medicinpåminnelsen ska ha en **"Markera tagen"**-åtgärd som markerar dagens schemalagda, ej tagna doser som tagna via repository-lagret och stänger notisen — utan att appen öppnas. Vid behov-doser lämnas orörda. |
| NOT-11 | Screeningpåminnelsen ska ha en **"Logga nu"**-åtgärd som öppnar Idag-skärmen med det aktuella måltidstillfällets inline-screeningformulär förexpanderat. |

---

## 11. Inställningar *(sektionerna nedan är nu del av Hantera-ytan, se §18, sedan navigationsbytet i #84 etapp 4)*

| ID | Krav |
|----|------|
| SET-1 | **Tema-läge** ska kunna väljas via segmenterad knapp: ljust, mörkt eller **auto** (växlar på klockslag). |
| SET-2 | Vid auto-tema ska **start-timme för ljust respektive mörkt** kunna ställas in (validerade så att ljus < mörk). |
| SET-3 | ~~**Dynamiska färger** (Material You) ska kunna slås på/av.~~ *(toggle borttaget; Material You alltid aktiverat på Android 12+)* |
| SET-4 | **Medicinpåminnelser** ska kunna slås på/av. **Screeningpåminnelser** ska ställas in per måltidshändelse (På/av + tid per händelse). |
| SET-5 | **Aktivitetsalternativ** ska kunna läggas till, tas bort och stjärnmärkas som favoriter (inga dubbletter). Ändringar ska synas direkt i loggningsformuläret utan omstart. |
| SET-6 | **Symptomalternativ** ska kunna läggas till och tas bort (inga dubbletter). |
| SET-7 | Konto (in-/utloggning) ska kunna hanteras från Hantera. |
| SET-8 | Import/migrering ska kunna startas från Hantera. |
| SET-9 | **Händelsetypalternativ** ska kunna läggas till, tas bort och stjärnmärkas som favoriter (inga dubbletter). Favoritmarkerade typer visas som en-tryck-chips och övriga i en "Fler typer"-lista i Lägg till/Redigera händelse. |
| SET-10 | **Vid behov-mediciner** ska kunna stjärnmärkas som favoriter i Hantera (analogt med SET-5); ändringar syns direkt i Idag-skärmens vid behov-kort (HEM-11, §4). |

---

## 12. Datamodell (krav på fält)

| Entitet | Nyckelfält |
|---------|-----------|
| **Aktivitet** | id, timestamp, datum, tid, aktivitet, energy (−10..10 / 1..10), stress (0..10), somatiska, symptom (wire), aterhamtande, energitjuv, type (`aktivitet`/`screening`), spentTime (min). |
| **Medicin** | id, timestamp, datum, tid, namn, dos, enhet, tidpunkt, tagen, receptId?, skipped. |
| **Recept** | id, namn, dos, enhet, tidpunkter[], upprepning, dagar[], intervalDagar, aktiv, skapad. |
| **Favorit** | id, namn, dos, enhet, tidpunkt, minTidMellan (h), dispenseringsTid, maxDoserPerDag, isFavorite. |
| **Händelse** | id, timestamp, datum, tid, typ, svarighetsgrad, varaktighetMinuter, triggers, atgarder. |
| **SjukdomsEpisod** | id, typ, startDatum, slutDatum, timestamp. |
| **SjukdomsIncheckning** | id, episodId, datum, tid, svarighetsgrad, symptom, somatiska, timestamp. |
| **Note** | target (`ACTIVITY`/`SCREENING`/`MEDICATION`/`RECEPT`/`FAVORIT`/`EVENT`/`SJUKDOM_EPISOD`/`SJUKDOM_INCHECKNING`), entityId, text — generisk anteckning kopplad till valfri entitet ovan (ersätter tidigare `anteckning`-kolumner på Aktivitet/Medicin/Recept/Favorit/Händelse/Sjukdom). |

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
| NFR-9 | Appen använder ett enhetligt designsystem: kort, tomlägen, bekräftelsedialoger, sektionsrubriker och datum/tid-format byggs med delade komponenter i `ui/components/`, med konsekvent form, typografi och spacing. |
| NFR-10 | Spara-knappar byggs med den delade komponenten `SaveButton` och är inaktiverade tills formuläret har osparade, giltiga ändringar (dirty-state — jämfört mot senast laddade/sparade värde, inte bara fältvalidering). Försök att navigera bort (tillbaka-knapp eller systemets back) med osparade ändringar visar en bekräftelsedialog (`UnsavedChangesBackHandler`) med möjlighet att spara, kasta ändringarna eller avbryta. `SaveButton` använder appens gröna "positiv"-signal (Emerald400/900) som container-/textfärg i aktivt läge, samma i ljust och mörkt tema. |
| NFR-11 | Skärmar med textinmatning ska hålla det fokuserade fältet synligt ovanför skärmtangentbordet — IME-inset hanteras centralt i den delade `DagbokenScaffold` (`contentWindowInsets` inkluderar `WindowInsets.ime`), så inget inmatningsfält skyms medan man skriver. |

---

## 14. Kända begränsningar / framtida arbete

| ID | Notering |
|----|----------|
| ~~FUT-1~~ | ~~**Hälsa-fliken** är endast en inaktiverad platshållare (ej implementerad).~~ *(borttaget — Hälsa implementerad via Health Connect, se §19)* |
| FUT-2 | `sheetsConfig` (Google Sheets-koppling) finns i inställningslagret men är inte exponerat i UI. |
| FUT-3 | Backup-worker kan inte hantera Drive-auktorisering som kräver UI (returnerar success utan att ladda upp). |

---

## 15. Sjukdomar (SJ) *(nås nu via Hantera → Sjukdomar, se HANT-3 §18, sedan navigationsbytet i #84 etapp 4)*

| ID | Krav |
|----|------|
| SJ-1 | Användaren kan logga en sjukdomsepisod med typ och startdatum. |
| SJ-2 | Användaren kan lägga till löpande incheckningar under episoden med svårighetsgrad (0–10) och symptom (samma lista som screening). |
| SJ-3 | Symptom i incheckning väljs och graderas 0–10 med SymptomLogCard (samma komponent som aktiviteter och screening). |
| SJ-4 | Användaren kan markera en episod som avslutad (ange slutdatum). |
| SJ-5 | Avslutade episoder visar varaktighet i dagar och senaste incheckningens svårighetsgrad. |
| SJ-6 | En pågående episod syns som statuskort på Idag-skärmen (§4, HEM-12), med accentfärg via delad kortkomponent. |
| SJ-7 | Episoder och incheckningar ingår i backup och återställs vid restore. |
| SJ-8 | Både en episod och varje incheckning kan ha en anteckning, redigerbar via den delade `NoteField`-komponenten. |
| SJ-9 | Tas en episod bort raderas även dess incheckningar (kaskad) och samtliga tillhörande anteckningar. |
| SJ-10 | Ett episodkort i listan som har en anteckning ska visa en liten info-ikon; tryck på ikonen visar anteckningen i en läs-only dialog med en Stäng-knapp. |

---

## 16. Historik-yta (enhetlig tidslinje, HIST)

> Del av UX-omtaget #84 (etapp 2, nåbar via bottennavigeringen sedan etapp 4 — se §3 NAV-7).

| ID | Krav |
|----|------|
| HIST-1 | Historik-ytan visar alla fem posttyper (aktivitet, screening, medicindos, händelse, sjukdomsincheckning) i ett enda kronologiskt flöde, grupperat per dag. |
| HIST-2 | Poster kan filtreras per typ med filterchips; minst en typ måste vara aktiv (samma regel som HIS-1). |
| HIST-3 | Tryck på en post navigerar till dess befintliga redigerings-/detaljskärm (ingen ny redigeringslogik i Historik-ytan själv). |
| HIST-4 | ~~Historik-ytan skriver inte till någon datakälla — ren läsvy över befintliga repositories.~~ *(ändrat, se HIST-5 — #105)* |
| HIST-5 | Långtryck på en post i Historik öppnar en meny med "Ta bort" (bekräftelsedialog krävs innan radering). Raderingen anropar samma repository-metod som respektive domänskärm redan använder. |
| HIST-6 | Historik kan växlas mellan listvy och kalendervy (delad komponent `DagbokenCalendar`). I kalendervyn markeras dagar med minst en post; tryck på en dag visar postens/posternas för det datumet. Långtryck-radering (HIST-5) fungerar identiskt i båda vyerna. |

---

## 17. Trender-yta (enhetliga diagram, TRD)

> Del av UX-omtaget #84 (etapp 3, nåbar via bottennavigeringen sedan etapp 4 — se §3
> NAV-7). `DiagramScreen`/`SymptomDiagramScreen` (tidigare DIA-1..5) är borttagna —
> Trender-ytan är nu den enda vägen till aktivitets- och symptomdiagram.

| ID | Krav |
|----|------|
| TRD-1 | Trender-ytan delar upp diagrammen i egna kategorier, var och en med egen serieväljare: **Energi (dag)** (TRD-8, ingen väljare — alltid synlig), **Energi per tillfälle** (Frukost/Lunch/Kvällsmat/Läggdags), **Stress & belastning** (Stress/Somatiska/Återhämtande/Energitjuv) och **Symptom** (dynamiska symptomserier). *(#141: tidigare en gemensam serieväljare för alla serier — ett diagram för alla gav en gemensam y-skala som gjorde enskilda serier oläsliga.)* |
| TRD-2 | Inom en kategori kan flera serier väljas och overlagras i samma diagram, på en skala beräknad **enbart över den kategorins** aktiva värden (se TRD-7) — inte alltid nollankrad, och inte längre delad med andra kategoriers serier (#141). |
| TRD-3 | Gemensam periodväljare — en dropdown (7 dagar / 14 dagar / Månad / 3 månader / Allt) — gäller för alla diagram/kategorier. *(#144: ersatte de fyra fasta periodknapparna 7/14/30/90 dagar; "Allt" är nytt — ingen nedre datumgräns.)* |
| TRD-4 | Trender-ytan skriver inte till någon datakälla — ren läsvy. |
| TRD-5 | Nås från bottennavigeringen samt via en genväg från Idag-skärmens energikort (§4, HEM-7). |
| TRD-6 | Linjediagrammens linjer renderas som mjuka kurvor med en gradientfyllning under linjen i seriens färg; axeletiketterna följer appens ljusa/mörka tema (tillräcklig kontrast i båda lägena). |
| TRD-7 | Varje diagrams y-axel skalas smart efter dess egna aktiva värdens min/max (med marginal, avrundat till läsbara gränser) i stället för att alltid utgå från 0, så variationer i ett smalt värdeband blir läsbara. Gäller alla diagram i appen (delad `computeSmartYRange`, regel 4). |
| TRD-8 | **Energi (dag)** visas som ett eget intervall-/spannstapeldiagram (`IntervalBarChart`, regel 4 — inte Vicos finansiella candlestick-layer): en stapel per dag från dagens lägsta till högsta loggade screeningenergi, med dagsvärdet (samma uträkning som Idag-diagrammet, HEM-7, delad `computeDailyEnergyStats`) markerat på stapeln. Dagsvärdena förbinds med en mjuk bezier-kurva (samma S-kurve-teknik som Vicos `PointConnector.cubic()`, TRD-6) som bryts vid en dag utan data. Visas alltid när minst en dag har en screening, ingen serieväljare. |
| TRD-9 | Varje diagram (Trender och Idag) visar alltid sitt faktiska datas lägsta och högsta värde som text under diagrammet (delad `MinMaxCaption`, regel 4) — oberoende av hur y-axelns rutnät råkar skala sig. `IntervalBarChart` (TRD-8) ritar dessutom horisontella värdelinjer vid varje jämnt steg mellan lägsta och högsta axelvärde (delad `computeSmartYAxis`, regel 4) så mellanliggande värden går att avläsa utan att gissa. |
| TRD-10 | Trenders diagram stöder tvåfingerzoom och panorering horisontellt, samma känsla på samtliga fyra diagram (#144) — linjediagrammen (`LineChartCanvas`) via Vicos inbyggda zoom-/scrollstöd, "Energi (dag)" (`IntervalBarChart`) via en egen handrullad gest-hantering eftersom komponenten inte bygger på Vico. Zoom/panorering nollställs när perioden (TRD-3) byts. Gäller inte Idag-diagrammen (fasta 7-dagarsvyer utan periodval). |

---

## 18. Hantera-yta (bibliotek/konfiguration, HANT)

> Del av UX-omtaget #84 (etapp 4). Fjärde bottennavflik — samlar tidigare `Inställningar`
> (sektionerna nedan återanvänds oförändrade) med två nya navigeringskort till
> sjukdomshantering och recept/scheman, som tidigare nåddes via egna bottenflikar.

| ID | Krav |
|----|------|
| HANT-1 | Hantera-ytan nås som fjärde bottennavflik (se NAV-7, §3) — visar inte tillbakapil, till skillnad från tidigare `Inställningar` som var en underliggande skärm. |
| HANT-2 | Sektionerna Konto, Import, Tema, Notiser, Aktivitetstyper, Symptom, Vid behov-mediciner, Händelsetyper och Om appen återanvänds oförändrade från tidigare `Inställningar` (samma `DagbokenCard`/`SectionHeader`-uppbyggnad). |
| HANT-3 | Ett nytt navigeringskort **Sjukdomar** öppnar sjukdomshantering (lista/avsluta episoder) som en underliggande skärm med tillbakapil. |
| HANT-4 | Ett nytt navigeringskort **Recept & scheman** öppnar receptschemat (samma innehåll som tidigare Mediciner-flikens Schema-flik, §6.2) som en underliggande skärm med tillbakapil. |
| HANT-5 | På bred skärm (≥360dp) visas sektionerna i en sidopanel; på smal skärm i en scrollbar kolumn — samma responsiva mönster som tidigare `Inställningar`. |

---

## 19. Hälsa (Health Connect, HLS)

> Hälsodata från Galaxy Watch 7 synkas via Samsung Health till **Health Connect** och läses
> read-only på telefonen. Spike #56 fastställde vägvalet (Health Connect framför Samsung Health
> Data SDK: Maven-beroende som bygger i CI, leverantörsneutralt, ingen Samsung-partner/-licens);
> #57 implementerade skärmen.

| ID | Krav |
|----|------|
| HLS-1 | Hälsodata hämtas via **Health Connect** (`androidx.health.connect:connect-client`), read-only. Ingen Samsung-partner/-licens krävs vid sideload. |
| HLS-2 | MVP-datapunkter: **steg** (`StepsRecord`, dagens summa — när flera källor skrivit steg, t.ex. telefonens pedometer och Galaxy Watch via Samsung Health, summeras stegen **per källa** och den mest kompletta (högsta) källans summa väljs; aldrig en summering över källor (dubbelräkning) eller `COUNT_TOTAL`s per-tidslucke-dedup, som tappade steg när källorna inte överlappade och gjorde att appen visade färre steg än den bärbara enheten), **puls** (`HeartRateRecord`, dagens snitt), **sömn** (`SleepSessionRecord`, senaste natten) och **vilopuls** (`RestingHeartRateRecord`, senaste värdet). Aktiva kalorier (`ActiveCaloriesBurnedRecord`) utreds senare. |
| HLS-3 | Läsbehörigheter är scopade hälsobehörigheter (`android.permission.health.READ_STEPS`, `READ_HEART_RATE`, `READ_RESTING_HEART_RATE`, `READ_SLEEP`) med runtime-samtycke via Health Connects behörighetsflöde. Ej beviljad behörighet visar en tydlig vy med "Ge åtkomst"-knapp utan krasch. Appen deklarerar en behörighets-rationale-handler i manifestet (`SHOW_PERMISSIONS_RATIONALE` för Android ≤13, `VIEW_PERMISSION_USAGE`-alias med hälso-kategorin för Android 14+) — annars visar Health Connect ingen samtyckesdialog. |
| HLS-4 | Saknas Health Connect på enheten (`HealthConnectClient.getSdkStatus()` = `SDK_UNAVAILABLE`/`SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`) visas en tydlig uppmaning (installera/uppdatera) med knapp till Health Connect i stället för hälsodata. |
| HLS-5 | Hälsodata persisteras **inte** lokalt och ingår **inte** i Drive-backup — Health Connect/Samsung Health äger och backar upp datan; Dagboken läser live. |
| HLS-6 | Hälsa-skärmen nås via ett navigeringskort i **Hantera** (underskärm med tillbakapil), samma mönster som Sjukdomar (HANT-3) och Recept & scheman (HANT-4). Datapunkter visas som `StatPill` (regel 4); saknad datapunkt visar "—". |
| HLS-7 | **Idag-skärmen** visar ett kompakt hälsokort med **steg** och **vilopuls** som `StatPill` (regel 4) för den dag som är vald i Idag-checklistans datumnavigering (HEM-14) — byter värde när användaren bläddrar till en annan dag, "—" om Health Connect saknar data för den dagen (t.ex. utanför det hämtade 7-dagarsfönstret), när Health Connect är tillgängligt och behörighet beviljad. Vilopulsen (både dagens och tidigare dagars) tas i första hand från senaste `RestingHeartRateRecord`; saknas den (t.ex. Galaxy Watch via Samsung Health som inte skriver posten) **skattas** den i stället från periodens `HeartRateRecord`-prover (medelvärdet av den lägsta 5-percentilen ≈ den lägsta ihållande pulsen; ett enda artefaktlågt prov drar inte ner värdet) så att kortet visar en vilopuls i stället för "—". Stegtrend och vilopulstrend för senaste 7 dagarna visas i stället i det gemensamma trenddiagrammet, se HEM-17. Saknas Health Connect eller behörighet visas en diskret "Koppla hälsa"-rad som djuplänkar till Hälsa-skärmen (§18) — **ingen** behörighetsbegäran sker på Idag. Kortet laddas fristående och blockerar inte Idag-renderingen. |
