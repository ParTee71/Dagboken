# Kravlista – Dagboken (Android)

> Hälsodagbok för att logga mående (energi, stress, symptom), aktiviteter, mediciner,
> händelser och sjukdomar, med diagram, klockdata, påminnelser och synk via Firestore.
>
> Version: 3.27.0 (`legacy`) → **4.0 under ombyggnad** · Paket: `se.partee71.dagboken` · Språk: Svenska

---

> **Ombyggnad 4.0.** Sedan [ARKITEKTUR.md](ARKITEKTUR.md) (ADR-001) beskriver kravlistan på
> `master` appen 4.0 som byggs där. Krav som ändras i ombyggnaden märks *(4.0)*; 3.x-lydelsen
> står kvar struken med hänvisning, så ID:n förblir spårbara. Appen 3.27.0 ligger på branchen
> `legacy` med sin egen kravlista. Nya områden: §21 Mediciner-flik (MEDF), §22 Ombyggnad och
> migrering (OMB), §23 Designspråk (DSN).

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
| TP-1 | Android, **minSdk 30** (Android 11), targetSdk 35, compileSdk 37; AGP 9, Kotlin 2.4, Gradle 9 *(4.0 – 3.x: compileSdk 36)*. |
| TP-2 | UI byggt med **Jetpack Compose** + **Material 3 Expressive** (`MaterialExpressiveTheme`, `MotionScheme.expressive()`) och **Navigation 3** med en backstack per flik *(4.0 – 3.x: Material 3 + navigation-compose med strängrutter)*. |
| TP-3 | Arkitektur: **MVVM** med Hilt (DI), repository som single source of truth ovanpå generisk `FirestoreCollection<T>` + `DocCodec<T>`, ViewModels med `StateFlow<UiState>`. Två moduler: `:core` (ren Kotlin/JVM: modeller, codecs, motorer) och `:app` *(4.0)*. |
| TP-4 | Lagring i **Firestore offline-först** (persistent cache, molnet är källan) under `users/{uid}`; inställningar i dokumentet `settings`. DataStore används bara för enhetslokalt tillstånd (migreringsflagga, senast valda flik) *(4.0 – 3.x: Room + DataStore)*. |
| TP-5 | Inloggning via **Firebase Auth + Google Credential Manager**; inloggning krävs (AUTH-6). |
| TP-6 | ~~Molnbackup via **Google Drive (appDataFolder)**.~~ *(borttaget 4.0 – Drive-backupen ersätts av BCK-11…BCK-16)* |
| TP-7 | Bakgrundsjobb via **WorkManager** (Hilt-integrerad worker) där något måste köras utan att appen är öppen *(4.0: ingen backup-worker längre)*. |
| TP-8 | Påminnelser via **AlarmManager** + `BroadcastReceiver` + notifikationskanaler. |
| TP-9 | Krävda behörigheter: `INTERNET`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`. |
| TP-10 | Hälsodata läses från **Health Connect** (`androidx.health.connect:connect-client`); ingen egen lagring, ingen Samsung-partner krävs (read-only via sideload). Se §19. |
| TP-11 | Databasen nås från Claude-sessioner (telefon/webb) med `tools/db` (Node ≥ 22, firebase-admin): `query`, `get`, `stats`, `export` läser; `import` och `migrate` skriver bara på uttrycklig begäran. Service account läses ur miljövariabeln `FIREBASE_SERVICE_ACCOUNT`, aldrig ur chatten *(4.0)*. |
| TP-12 | `firestore.rules` släpper bara in dokumentets ägare (`request.auth.uid == uid`), validerar typer och textlängder, nekar okända samlingar och tillåter aldrig att `schemaVersion` sänks. Reglerna testas mot Firebase-emulatorn i CI *(4.0)*. |

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
| NAV-7 | ~~Appen ska ha en **bottennavigering** med fyra flikar: **Idag** (dagens checklista, se §4), **Historik** (§16), **Trender** (§17) och **Hantera** (bibliotek/konfiguration, se §18).~~ *(ersatt av NAV-8, 4.0)* |
| NAV-8 | Appen har en **bottennavigering** med fyra flikar: **Idag** (dagens checklista, §4), **Dagbok** (tidslinje och kalender över allt loggat, §16), **Trender** (§17) och **Mediciner** (recept, vid behov, perioder, §21). Hantera-fliken finns inte; dess innehåll ligger i inställningsarket (NAV-9), Mediciner (§21), Dagbok (HIST-9) och Trender (TRD-20) *(4.0)*. |
| NAV-9 | Avataren uppe till höger på alla flikar öppnar **inställningsarket** (bottom sheet) med Konto, Profil, Påminnelser, Tema, Listor (aktivitetstyper, symptom, händelsetyper), Export och import samt Om Dagboken. Varje rad öppnar en underskärm med tillbakapil och samma lilla topprad – även Listor; arkets titlar linjerar under kontots avatar *(4.0)*. |
| NAV-10 | **Plusknappen** finns på alla flikar och öppnar en loggmeny med exakt fem val: **Mående, Aktivitet, Dos, Händelse, Sjukdom** (ny episod eller incheckning på pågående). Allt loggas mot den dag som visas i Idag (HEM-14). Recept och vid behov-mediciner skapas aldrig härifrån utan under Mediciner (MEDF-4) *(4.0)*. |
| NAV-11 | Varje flik har sin egen backstack som bevaras vid flikbyte (Navigation 3, `AppBackStack`); skärmbyten delar en rörelse definierad i `navigation/Transitions` *(4.0, ersätter mekaniken bakom NAV-4/NAV-5 utan att ändra beteendet)*. |

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
| HEM-5 | Mediciner markeras som tagna direkt i checklistan utan navigering. Mående visas som en **tillfällesrad** per måltidstillfälle (Efter frukost, Lunch, Kvällsmat, Läggdags) med status och loggade värden som chips; **Logga nu** på raden öppnar det stegvisa måendeformuläret (energi → stress → symptom, `StepwiseScreeningForm`) som ett ark över skärmen och sparar mot den dag som visas (HEM-14) *(4.0 – 3.x: formuläret expanderades inline i listan)*. Ett loggat tillfälle visas avbockat som en tagen dos, med loggens tid och värdena som chips *(4.0)*. |
| HEM-7 | Visa **sparkline-diagram** över **genomsnittlig energi per dag** senaste 7 dagarna, baserat på screenings (minst 2 datapunkter krävs, annars uppmaning att logga); länk till Trender-ytan (§17) för fördjupning. Dagsvärdet beräknas av delad `computeDailyEnergyStats` (regel 4) — samma uträkning som Trenders "Energi (dag)" (TRD-8), så de två aldrig kan visa olika värden för samma dag. Delar linjestil (mjuk kurva + gradientfyllning, TRD-6) med Trender-diagrammet för visuell konsekvens. Diagrammet visar värden på **båda axlarna**: y-axel med energiskala, x-axel med veckodagsetiketter, samt lägsta/högsta värde som text under diagrammet (TRD-9). Y-axeln skalas smart efter de faktiska värdenas min/max (TRD-7), inte alltid nollankrad. Ingår i det gemensamma trenddiagrammet på Idag tillsammans med steg- och vilopulstrend, se HEM-17. |
| HEM-8 | ~~Visa **snabbåtgärder**: "Logga aktivitet" och "Mediciner".~~ *(ersatt av global "+"-FAB med snabbval: Aktivitet/Screening/Engångsdos/Ny vid behov-favorit/Händelse)* |
| HEM-8b | "+"-FAB-snabbvalet **"Logga screening"** öppnar en tillfällesväljare (Efter frukost/Lunch/Kvällsmat/Läggdags) och sedan samma stegvisa screeningformulär som checklistan (`StepwiseScreeningForm`, regel 4), sparat mot vald dag (HEM-14) — oberoende av om tillfället är schemalagt/redan loggat i checklistan (SCR-6, HEM-4), så en extra eller ett icke-påmint tillfälle går att logga (#146). |
| HEM-9 | Visa **kontobubbla** (avatar/foto) som öppnar konto-bottensheet (logga in/ut, Hantera). |
| HEM-10 | Säkerställa vald dags medicinposter genereras (`ensureEntriesForDate`) — vid skärmstart för dagens datum, och på nytt varje gång användaren bläddrar till ett nytt datum (HEM-14), inklusive en tidigare dag som aldrig var "idag" senast appen var öppen. |
| HEM-11 | Favoritmarkerade vid behov-mediciner ska visas som tryckbara snabbvalskort direkt i checklistan (samma beteende som tidigare MED-7); tryck loggar en dos med befintlig cooldown-/gränslogik. **Långtryck öppnar alltid** kontextmenyn — Redigera, favoritmarkering, Logga i efterhand (FAV-10, MED-16) och Ta bort — byggd med samma menykomponent och ordning som postkortens (NFR-16). Nya favoriter skapas via "+"-FAB. |
| HEM-12 | Pågående sjukdomsepisod ska visas som ett accentmärkt kort som länkar till sjukdomsdetaljer (Hantera → Sjukdomar). |
| HEM-13 | I början av veckan (söndag/måndag) ska ett **veckosammanfattningskort** visas överst på Idag, ovanför datumnavigeringsraden (HEM-14): energitrend (senaste 7 dagarnas genomsnittliga screeningenergi jämfört med föregående 7 dagar, ↑/↓/oförändrad) och andel tagna av veckans schemalagda doser (%). Beräknas live från befintliga poster via delad `DagbokenCard` — ingen ny persisterad data. Döljs om underlag saknas. |
| HEM-14 | Idag kan bläddras till en **tidigare dag** via en **datumremsa** med veckans sju dagar: dagens datum markerat, en punkt under dagar som har poster, svep åt höger för äldre veckor. Framtida dagar kan inte väljas. Idag markeras med en solgul punkt med ring – samma markering som i kalendern (HIST-6) och som dagens punkt i diagrammen (HEM-7) – och dagens punkt blir en bock när dagen är klar (HEM-19) *(4.0)*. Att öppna "Ny händelse" från en tidigare dag förifyller datumet med den visade dagen. Datumremsan ligger direkt under rubriken, ovanför framstegsraden (HEM-18) *(4.0 – 3.x: "< Föregående dag · [datum] · Nästa dag >")*. |
| HEM-15 | Idag visar ett **hälsokort** (Health Connect) med **steg och vilopuls för vald dag** (HEM-14, se HLS-7 §19) — byter siffror när användaren bläddrar till en annan dag. Döljs/ersätts av en diskret koppla-rad när data/behörighet saknas. Kortet visas längst ner på Idag, direkt ovanför det gemensamma trenddiagrammet (HEM-17), eftersom båda är hälsorelaterat innehåll snarare än dagens checklistor. |
| HEM-16 | Under datumremsan visas **tre separata kort** för vald dag – **Mediciner**, **Mående** och **Vid behov** – följda av pågående sjukdom (HEM-12), Hälsa idag (HEM-15) och trenddiagrammet (HEM-17) *(4.0 – 3.x: ett gemensamt kort med avdelare)*. |
| HEM-17 | Stegtrend, vilopulstrend (HLS-7) och energitrend (HEM-7) för senaste 7 dagarna visas i **ett gemensamt diagramkort** på Idag i stället för separata kort — varje trend en egen `SparklineChart`-rad (regel 4), i ordningen **steg → vilopuls → energi**, var och en med lägsta/högsta värde som text under diagrammet (TRD-9). Steg-/vilopulstrenden visas bara när Health Connect är tillgängligt och har minst 2 dagar med värde; energitrenden kräver minst 2 loggade screeningdagar, annars en uppmaning att logga. Kortet har en länk till Trender-ytan (§17). |
| HEM-18 | En **framstegsrad** under datumremsan visar hur många av dagens poster (doser + måendetillfällen) som är klara, t.ex. "4 av 9", och fylls animerat när något bockas av *(4.0)*. |
| HEM-19 | **Belöningsläge:** när alla dagens doser är tagna eller överhoppade och alla aktiverade måendetillfällen loggade byter rubriken till "Allt klart för idag", framstegsraden blir solgul, konfetti (delad `Confetti`) faller **en gång** och ett grönt sammanfattningskort visar snittenergi för dagen, jämförelse med igår och antal dagar i rad. Dagens punkt i datumremsan blir en bock. Gäller bara dagens datum *(4.0)*. |

---

## 5. Aktiviteter & Screening

### 5.1 Logga aktivitet

| ID | Krav |
|----|------|
| AKT-1 | Användaren ska kunna välja **aktivitetstyp** från konfigurerbara alternativ (default: Promenad, Jobb, Möte, Träning, Vila, Mat, Sällskap, Läsning, Övrigt). Ändringar gjorda i Inställningar ska synas direkt i loggningsformuläret utan omstart. |
| AKT-2 | Vid valet "Övrigt" ska ett fritextfält visas för egen beskrivning. |
| AKT-3 | Aktivitet ska kunna märkas som **Återhämtande** och/eller **Energitjuv**. |
| AKT-4 | Användaren ska kunna sätta **energi** på skala **−10 till +10** (med beskrivande etikett och färg). Reglaget är det delade `ValueSlider` (DSN-1): färgat rött → grönt åt höger med nollan markerad mitt på spåret, värdet med tecken ("+3") och nivån som pill (Låg −10…−3, Medel −2…+3, Hög +4…+10) *(4.0)*. |
| AKT-5 | Användaren ska kunna sätta **stress** på skala **0–10**. |
| AKT-6 | Användaren ska kunna gradera **symptom** (konfigurerbara, 0–10 per symptom), med fritext vid "Övrigt". Med det delade `SymptomLogCard`: ett reglage per valt symptom (grönt → rött, DSN-1) och summan under *(4.0)*. |
| AKT-7 | Användaren ska kunna ange **tidsåtgång** (timmar + minuter). |
| AKT-8 | Mätvärden och symptom ska kunna fällas ihop/ut (foldout). |
| AKT-9 | Spara-knappen (`EntityEditScreen`, se NFR-10) *(4.0)* kräver att en aktivitetstyp valts **och** att formuläret har osparade ändringar (dirty-state) — annars inaktiverad. |
| AKT-10 | Under registreringsformuläret ska de tre senaste loggade posterna (aktivitet och screening blandat, sorterade på tid, nyast överst) visas i en lista. Korten följer kortstandarden (NFR-15): tryck öppnar posten för redigering, chevron-knappen fäller ut symptomen, långtryck och `⋮` ger samma meny (Redigera, Ta bort) och svep från höger till vänster raderar efter bekräftelse. Energifärgen visas som accent på kortets vänsterkant. |
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
| MED-1 | Visa vald dags (se HEM-14) mediciner sorterade på tidpunkt (Morgon → Natt → Vid behov). Som standard visas **aktuella/försenade** poster (schemalagd tid nådd, eller "Vid behov") **och poster vars tid närmar sig** (inom 3 timmar, se MED-13) — tagna poster göms bakom MED-5, poster längre fram göms bakom MED-13. Gäller endast för dagens datum; en tidigare dag har inga "kommande" poster. |
| MED-2 | Varje medicin ska kunna markeras som **tagen/ej tagen** genom tryck var som helst på raden (NFR-17); ikonen till höger visar tillståndet. Tagningstidpunkten sparas separat från den schemalagda tiden (se MED-14). |
| MED-3 | Receptgenererade poster ska kunna **hoppas över** (skippas) i stället för att raderas; engångsposter raderas. |
| MED-4 | Vald dags receptposter ska genereras automatiskt och **idempotent** (stabilt ID `recept_{id}_{datum}_{tidpunkt}`, DAT-8), inklusive en tidigare dag som bläddras till (HEM-14/HEM-10). En dos vars id redan finns skapas aldrig igen, vilken status den än har – en tagen eller överhoppad dos återskapas inte, och två körningar ger samma resultat. Bara aktiva recept vars dag ligger inom perioden och upprepningen (REC-2–REC-4, REC-7, REC-8) genererar: en dos per schemalagd tidpunkt (inga tidpunkter = Morgon; "Vid behov" ger ingen receptdos, och ett recept med bara "Vid behov" ger inga doser – 3.x genererade `recept_{id}_{datum}_Vid behov` om äldre data hade tidpunkten; sådana migrerade doser behålls orörda), med dagens totala dos (REC-12), tidpunktens standardklockslag (REC-6) och receptets anteckning som förval (REC-1). *(4.0)* |
| MED-5 | Tagna mediciner ska kunna **döljas** i checklistan; en toggle-knapp visar antalet dolda poster och låter användaren visa dem igen. |
| MED-6 | Kryssrutan för att markera tagen ersätts med en **animerad ikonknapp** (tomt cirkelkryss → fylld bockikon med färganimering). |
| MED-7 | ~~Favoriter (vid-behov-mediciner) ska visas som ett **snabbval** direkt i Idag-fliken, under progressbaren; tryck loggar en dos med befintlig cooldown-/gränslogik.~~ *(se HEM-11, §4)* |
| MED-11 | Varje medicinpost (dos) ska kunna ha en anteckning, redigerbar via den delade `NoteField`-komponenten på redigeringsskärmen. Loggas en dos från en favorit ärvs favoritens anteckning som förvalt värde på dosen. |
| MED-12 | En medicinrad (Idag-checklistan eller Historik-ytan) som har en anteckning ska visa en liten info-ikon; tryck på ikonen visar anteckningen i en läs-only dialog med en Stäng-knapp. |
| MED-13 | Kommande mediciner ska kunna **döljas** i checklistan; en toggle-knapp visar antalet dolda poster och låter användaren visa dem igen (analogt med MED-5). "Kommande" betyder att den schemalagda tiden ligger **mer än 3 timmar fram** (dagens datum). En dos vars tid ligger **inom** 3 timmar döljs inte utan listas tillsammans med de aktuella/försenade, märkt **"Snart"** så att den inte förväxlas med en dos som redan är att ta — annars var kvälls- och nattmedicinen (19:00/22:00) osynlig hela dagen och gick inte att bocka av i förväg. |
| MED-14 | När en dos markeras som tagen sparas **tagningstidpunkten** separat från den schemalagda tiden (`tagenTid`). Historik (§16) visar tagningstidpunkten; doser loggade före denna funktion faller tillbaka på den schemalagda tiden. Ångrad avbockning nollställer tagningstidpunkten. |
| MED-15 | Redigering av en medicinpost från Historik (HIST-3, §16) redigerar **endast den enskilda dosen**: datum, tagningstid, dos, enhet, tagen-status och anteckning. Namn och tidpunktsslot visas som read-only kontext för receptgenererade doser (hänvisning till Hantera → Recept & scheman) — receptet eller favoriten den härstammar från ändras aldrig. Flyttas en receptgenererad dos till ett annat datum får den ett nytt id (receptkopplingen behålls); ursprungsdagens schemalagda dos genereras på nytt som otagen vid nästa dosgenerering (MED-4). |
| MED-16 | En vid behov-dos ska kunna loggas **i efterhand** med valfritt datum och klockslag (ej i framtiden) — från "Ny medicin" och från en favorits långtrycksmeny på Idag (se FAV-10). Cooldown (FAV-4) och dagsgräns (FAV-5) utvärderas mot den valda tidpunkten, inte mot aktuell tid. |

### 6.2 Recept och scheman *(4.0: fliken Mediciner, §21 – 3.x: Hantera → Recept & scheman, HANT-4)*

| ID | Krav |
|----|------|
| REC-1 | Användaren ska kunna skapa/redigera **recept** med namn, dos, enhet, en eller flera tidpunkter och en anteckning (delad `NoteField`-komponent). Anteckningen ärvs som förval på varje dos receptet genererar. |
| REC-2 | Recept ska stödja upprepningsmönster: **dagligen, vardagar (mån–fre), helger (lör–sön), anpassad (specifika veckodagar), intervall (var X:e dag)**. En upprepning appen inte känner igen (t.ex. från en nyare version) ger inga doser, rör inga befintliga doser (REC-10) och skrivs tillbaka oförändrad. *(4.0 – 3.x: "Recept ska stödja upprepningsmönster: dagligen, vardagar, helger, anpassad (specifika veckodagar), intervall (var X:e dag)." Okänd upprepning lästes som dagligen.)* |
| REC-3 | Vid "anpassad" ska specifika veckodagar (måndag–söndag) kunna väljas; utan valda dagar ger receptet inga doser. *(4.0 – 3.x lagrade 0=Mån … 6=Sön; konverteras till veckodagar)* |
| REC-4 | Vid "intervall" ska intervall i dagar anges; dosdagarna är var X:e dag räknat från receptets **startdatum** (REC-7), startdagen inräknad. Nya recept har alltid ett startdatum – formuläret sätter det med idag som förval *(etapp 5.2c)*. För recept utan startdatum (migrerade, skapade före periodstödet) räknas intervallet från skapandedagen i svensk tid (Europe/Stockholm, samma som migreringen) – så att dosdagarna blir desamma på alla enheter – i samma rytm även bakåt före den; saknas båda räknas den visade dagen som dag 0. Ett intervall på 1 dag eller mindre är dagligen. *(4.0 – 3.x: "Vid intervall ska intervall i dagar anges; beräknas relativt receptets startdatum (REC-7), vilket för recept skapade före periodstödet är skapandedatumet.")* |
| REC-5 | Recept ska kunna **aktiveras/inaktiveras** utan att raderas — via reglaget på receptkortet eller motsvarande val i kortets kontextmeny. Ett inaktivt recept visas nedtonat med grå accent och genererar inga doser; när det sparas som inaktivt tas dess planerade (otagna, ej överhoppade) doser från och med idag bort, medan tagna och överhoppade doser och tidigare dagars doser lämnas orörda (REC-10). *(4.0 – 3.x: "Recept ska kunna aktiveras/inaktiveras utan att raderas — via reglaget på receptkortet eller motsvarande val i kortets kontextmeny. Ett inaktivt recept visas nedtonat med grå accent." Redan genererade doser låg kvar.)* |
| REC-6 | Standardklockslag per tidpunkt: Morgon 07, Förmiddag 10, Lunch 12, Eftermiddag 15, Kväll 19, Natt 22, Vid behov 12. |
| REC-7 | Ett recept ska kunna ges en **period** — startdatum plus antingen längd i dagar eller ett t.o.m.-datum, eller *tills vidare* (inget slutdatum). Doser genereras endast inom perioden, start- och slutdagen inräknade. Recept utan uttalat startdatum (skapade före periodstödet) har ingen bakre gräns, så bakåtbläddring i Idag (HEM-14) fortsätter seeda deras doser som förut. Periodens slut får inte ligga före dess start (REC-9 har valideringsordningen). *(4.0)* |
| REC-8 | När perioden passerats (dagen efter sista dagen, mätt mot dagens datum – aldrig mot en dag som bläddrats till) slutar receptet generera doser och markeras automatiskt som **avslutat** (`active = false`, bara det fältet skrivs) när doserna genereras (MED-4); ett avslutat recept ger inga doser, inte heller för tidigare dagar inom perioden. Receptet raderas aldrig och kan tas i bruk igen genom att perioden förlängs och receptet aktiveras. Recept & scheman visar "Avslutat" med slutdatum. *(4.0 – 3.x: "När perioden passerats slutar receptet generera doser och markeras automatiskt som avslutat (aktiv = false). Receptet raderas aldrig och kan tas i bruk igen genom att perioden förlängs och receptet aktiveras. Recept & scheman visar Avslutat med slutdatum.")* |
| REC-9 | Ett recept ska kunna ha en eller flera **doshöjningar** (startdatum, slutdatum eller periodens slut, höjning) som gäller under en del av perioden — t.ex. dubbel dos i fem dagar. Höjningen **läggs till grunddosen** och anges alltid i receptets egen enhet; enheten kan inte väljas separat. Dagarna är inklusive; en höjning utan startdatum (bara i gammal data) räknas inte, och när en höjning löper ut återgår receptet till grunddosen. Receptet går inte att spara vid, i denna ordning och med det första felet visat: periodens slut före start, höjning utan dos, grunddos som inte är ett tal när det finns höjningar, höjning som inte är ett tal större än 0, höjning utanför perioden (start före periodens start eller efter dess slut, eller eget slut efter periodens slut), höjningens slut (eget eller periodens) före dess start, överlappande höjningar (även en gemensam dag). En höjning eller grunddos räknas som tal bara när den är siffror med valfri decimal (komma eller punkt) – inte "1e3", "5d" eller tecken. Höjningar utan startdatum (bara i äldre data) räknas inte i någon av kontrollerna, så ett migrerat recept med en sådan går att spara. En ny höjning förväljs från dagen efter den senast slutande höjningen (en utan eget slut slutar med perioden), annars periodens start, annars idag, och varar en vecka (slut sex dagar senare), klippt till perioden; dosen lämnas tom. Finns ingen plats – perioden är full, eller en höjning gäller tills vidare – ges inget förval. *(4.0)* |
| REC-10 | När ett recept sparas uppdateras receptets **planerade** (otagna, ej överhoppade) doser från och med idag: namn och dos/enhet följer det sparade receptet och dagens höjning (REC-12), och doser vars dag hamnat utanför perioden eller upprepningsmönstret **eller vars tidpunkt tagits bort ur receptet** raderas (anteckningen följer med dosen). Saknas dagens dos för en tidpunkt (t.ex. en nyss tillagd) skapas den direkt om receptet är aktivt – aldrig över en befintlig dos med samma id; ett inaktivt recept tar i stället bort sina planerade doser (REC-5); senare dagar fylls på när de visas (MED-4). Tagna och överhoppade doser, och tidigare dagars doser, ändras aldrig. *(4.0 – 3.x skapade nya doser först vid nästa dosgenerering)* |
| REC-12 | Där en dos visas ska den **totala dosen för dagen** visas, alltså grunddos plus eventuell höjning (REC-9): Idag-checklistan, medicinpåminnelsen (NOT-17) och Recept & scheman, som utöver grunddosen visar dagens gällande dos med höjningen inom parentes när en höjning pågår. Doser skrivs som siffror med punkt eller komma som decimaltecken (REC-9); summan visas som heltal utan decimaler, annars med decimalkomma avrundat till högst sex decimaler (så att t.ex. 0,1 + 0,2 visas som 0,3). Går grunddosen eller höjningen inte att räkna som tal ("1 tablett") visas grunddosen oförändrad. *(4.0)* |
| REC-13 | Receptkorten i Recept och scheman (MEDF-1) följer kortstandarden (NFR-15/NFR-16): tryck öppnar receptet för redigering, tidpunkterna står i undertexten och chevron-knappen (bara när receptet har doshöjningar) fäller ut doshöjningarna med total dos ("29 sep – 12 okt: +25 mg (totalt 75 mg)"), långtryck och `⋮` ger samma meny (Redigera, Aktivera/Avaktivera, Radera) och svep från höger till vänster raderar efter bekräftelse. Aktiv-reglaget ligger kvar som direktkontroll på kortet, och en anteckning visas med anteckningsikonen (samma mönster som MED-12/SJ-10). Ett avslutat recept (MEDF-5) har inget reglage och menyn Redigera, Radera. Att radera ett recept lämnar dess doser orörda. *(4.0 – 3.x: "chevron-knappen fäller ut tidpunkter och dosperioder"; menyn "Redigera, Aktivera/Avaktivera, Ta bort")* |
| REC-11 | Vid överlappande dosperioder (t.ex. i importerad eller äldre data, som formuläret inte längre tillåter) gäller den **senast påbörjade** — den mer specifika dosändringen vinner över en längre period den ligger inuti. Det gäller både dagens dos (REC-12) och periodsluten (NOT-12): när den inre slutar tar den yttre över. *(4.0 – 3.x: "Vid överlappande dosperioder (t.ex. i importerad eller äldre data, som formuläret inte längre tillåter) gäller den senast påbörjade — den mer specifika dosändringen vinner över en längre period den ligger inuti.")* |

### 6.3 Vid behov-mediciner (favoriter) *(4.0: snabbvalet på Idag HEM-11, hanteringen i fliken Mediciner MEDF-3 – 3.x: Hantera)*

| ID | Krav |
|----|------|
| FAV-1 | Användaren ska kunna skapa **vid behov-mediciner** med namn och dos (båda krävs), enhet (val bland mg, ml, st, g, mcg, IE, dropp, sprut – en lagrad enhet utanför listan står kvar som val), minsta tid mellan doser och högsta antal per dag (stegare, FAV-4/FAV-5) och en anteckning (`NoteField`), via **Ny vid behov-medicin** i fliken Mediciner (MEDF-4) på `EntityEditScreen`. Tidpunkten är alltid "Vid behov" och visas inte; en lagrad tidpunkt bevaras. Sparning av en befintlig skriver bara de fält som ändrats. *(4.0 – 3.x: "Användaren ska kunna skapa favoriter (vid-behov-mediciner) med namn, dos, enhet, tidpunkt och en anteckning (delad `NoteField`-komponent), via "+"-FAB på Idag-skärmen.")* |
| FAV-2 | Endast **favoritmarkerade** favoriter visas som tryckbara kort (chips) i Idag-skärmens vid behov-kort; **tryck loggar en dos** direkt. Icke-favoritmarkerade favoriter nås via en "Fler"-lista i samma kort, tillsammans med receptens mediciner (FAV-11). |
| FAV-3 | Vid behov-medicinen **redigeras** genom tryck på raden i Mediciner (MEDF-3) och **tas bort** från formulärets meny, alltid efter bekräftelse (`ConfirmDialog`); loggade doser står kvar. Långtrycksmenyn på Idag följer HEM-11. *(4.0 – 3.x: "Långtryck öppnar meny för redigera/ta bort (med bekräftelsedialog).")* |
| FAV-4 | Favorit ska kunna ha **minsta tid mellan doser** (kylperiod i timmar, 0 = ingen; i formuläret en stegare 0–24 h där 0 visas som "Ingen spärr", och i Mediciner som undertext "Minst 4 h mellan"). Loggas en dos inom kylperioden visas kvarvarande tid, och användaren kan bekräfta och ta dosen ändå. Kylperioden räknas från den senaste tagna dosen av samma medicin före den nya dosens tidpunkt. Samma medicin betyder loggad från favoriten eller med samma namn (oavsett versaler). Tiden räknas i verkliga timmar från tagningstidpunkten (MED-14), även över midnatt och sommartidsbyte. *(4.0 – 3.x: "dos blockeras med kvarvarande tid om för tidigt"; bara namnet jämfördes)* |
| FAV-5 | Favorit ska kunna ha **max antal doser per dag** (0 = obegränsat; i formuläret en stegare 0–10 där 0 visas som "Obegränsat", och i Mediciner som undertext "Högst 8 per dag" – utan kylperiod och gräns "Ingen gräns"); tagna doser av samma medicin (som FAV-4) räknas för dagen dosen gäller, i enhetens tidszon. Dos blockeras vid uppnådd gräns – alltid, även när kylperioden bekräftats bort (FAV-4). *(4.0)* |
| FAV-6 | Blockerad dos ska ge tydligt felmeddelande via snackbar. |
| FAV-7 | Favorit ska kunna ha dispenseringstid (fält finns i modellen). Den visas inte i formuläret men bevaras när medicinen sparas eller stjärnmärks *(4.0)*. |
| FAV-8 | Favoritmarkeringen växlas med **stjärnan** på raden i Mediciner (MEDF-3, radens enda direktkontroll, NFR-17) och i långtrycksmenyn på Idag (HEM-11); stjärnan skriver bara fältet `favorite`. *(4.0 – 3.x: "Långtrycksmenyn ska även kunna växla favoritmarkering, utöver redigera/ta bort.")* |
| FAV-9 | En favorit-chip med anteckning ska visa en liten info-ikon; tryck på ikonen visar anteckningen i en läs-only dialog med en Stäng-knapp. |
| FAV-10 | Långtrycksmenyn ska även innehålla **"Logga i efterhand"**, som öppnar dosformuläret förifyllt från favoriten med redigerbart datum och klockslag (MED-16). |
| FAV-11 | "Fler"-listan (FAV-2) ska lista **alla** mediciner — utöver de icke favoritmarkerade favoriterna även **de aktiva receptens** mediciner, i en egen avdelning märkt "Recept" — så att en extrados av en receptmedicin går att logga direkt från Idag. Receptets dos visas som den gällande dosen för dagen (REC-12). Ett recept vars namn redan finns som favorit utelämnas, så samma medicin aldrig står två gånger. Dosen loggas som en **vid behov-dos** (tidpunkt "Vid behov", utan receptkoppling) — den är en extrados vid sidan av schemat och ska kunna raderas, inte hoppas över (MED-3). Receptets anteckning ärvs som förval på dosen, på samma sätt som favoritens gör (MED-11/REC-1). Kylperiod (FAV-4) och dagsgräns (FAV-5) hör till favoriten och gäller inte för receptsnabbvalen. |

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
| AUTH-2 | Användaren ska kunna **logga ut** och rensa credential-state – sist i inställningsarket, efter en bekräftelse ("Logga ut?", `ConfirmDialog`) *(4.0)*. |
| AUTH-3 | Inloggad användares **namn, e-post och profilfoto** visas i inställningsarkets kontokort och i avataren på flikarna (initialer när foto saknas eller inte går att hämta). Uppgifterna hålls bara i minnet: de loggas, sparas och cachas aldrig på disk (fotot laddas med Coil utan diskcache) *(4.0)*. |
| AUTH-4 | Inloggningsfel ska visas, men **avbruten inloggning** ska inte behandlas som fel. |
| AUTH-5 | ~~Appen ska fungera utan inloggning; konto krävs endast för molnbackup/migrering.~~ *(borttaget 4.0 – inloggning krävs, se AUTH-6)* |
| AUTH-6 | Appen kräver inloggning: utan inloggad användare visas en inloggningsskärm och ingen dagbok. All data ligger under `users/{uid}`; utloggning stänger synken, rensar den lokala Firestore-cachen och återgår till inloggningsskärmen *(4.0)*. Skrivningar som inte nått servern töms aldrig: utan synk inom några sekunder loggas användaren ut men cachen behålls tills samma konto loggar in igen och synkar – ingen data tappas. |

---

## 9. Backup & migrering

| ID | Krav |
|----|------|
| BCK-1 | ~~Appen ska **automatiskt säkerhetskopiera** all data till Google Drive (appDataFolder) via WorkManager.~~ *(borttaget 4.0 – Drive-backupen ersätts av BCK-11…BCK-16)* |
| BCK-2 | ~~Backup ska omfatta aktiviteter, mediciner, recept, favoriter (inklusive favoritmarkering), händelser, sjukdomar, anteckningar (generisk `notes`-tabell) samt aktivitets-/symptom-/händelsetypalternativ inklusive favoritstatus (versionerat JSON).~~ *(borttaget 4.0 – Drive-backupen ersätts av BCK-11…BCK-16)* |
| BCK-3 | ~~Endast de **5 senaste** backuperna ska behållas (äldre rensas).~~ *(borttaget 4.0 – Drive-backupen ersätts av BCK-11…BCK-16)* |
| BCK-4 | ~~Backup ska kräva inloggat konto och Drive-auktorisering (`DRIVE_APPDATA`-scope); auktorisering kan kräva användarsamtycke.~~ *(borttaget 4.0 – Drive-backupen ersätts av BCK-11…BCK-16)* |
| BCK-5 | ~~Användaren ska kunna **importera/migrera** data från senaste Drive-backup.~~ *(borttaget 4.0 – Drive-backupen ersätts av BCK-11…BCK-16)* |
| BCK-6 | Användaren ska kunna **importera från lokal fil** (JSON via dokumentväljare) – i 4.0 en 3.x-backupfil eller en 4.0-export, via Inställningar → Export och import (BCK-13, BCK-14). |
| BCK-7 | ~~Migrering ska visa tydliga tillstånd (kontrollerar, laddar ner, importerar med progress, klar/fel).~~ *(borttaget 4.0 – Drive-backupen ersätts av BCK-11…BCK-16)* |
| BCK-8 | ~~Användaren ska kunna **hoppa över** migrering; status ska sparas så att den inte upprepas.~~ *(borttaget 4.0 – Drive-backupen ersätts av BCK-11…BCK-16)* |
| BCK-9 | Import ska vara robust mot okända JSON-fält (`ignoreUnknownKeys`) och äldre `schemaVersion`; okända fält kastas aldrig vid export → import-rundtur (BCK-16). |
| BCK-10 | ~~Backupen ska även innehålla appinställningar: huvudreglaget för medicinpåminnelser samt temainställningar (läge, ljus-/mörkerstart, mörkt tema, dynamisk färg). Inställningar som saknas i en äldre backup lämnas orörda vid återställning.~~ *(borttaget 4.0 – Drive-backupen ersätts av BCK-11…BCK-16)* |
| BCK-11 | **Firestore är molnkopian.** All data skrivs offline-först till den lokala cachen och synkas till `users/{uid}` när nätet finns; ingen separat Drive-backup *(4.0)*. |
| BCK-12 | **Veckovis krypterad export** i GitHub Actions (`backup.yml`): `tools/db export` → `import --dry-run` som validering → `gpg --symmetric` AES-256 med lösenfras ur en secret → dekryptering och `cmp` som bevis → artefakt med 90 dagars retention. Klartexten raderas alltid; fel i schemalagd körning öppnar ett issue. Återställning med `tools/db import.mjs` *(4.0)*. |
| BCK-13 | **Manuell export** från Inställningar → Export och import: hela dagboken som JSON (4.0-format med `schemaVersion`) till en fil via dokumentväljare *(4.0)*. |
| BCK-14 | **Legacyimport:** en 3.x Drive-backup (`BackupJson` v1 och v2) eller lokal 3.x-JSON kan importeras i 4.0 via konverteraren i `:core` (OMB-3). Behålls minst en version efter 4.0 *(4.0)*. |
| BCK-15 | `users/{uid}` bär `schemaVersion`. Appen tolererar okända fält och äldre version men vägrar skriva mot en okänd **högre** version; migreringar görs av `SchemaMigrator` i `:core` och `tools/db migrate.mjs` *(4.0)*. |
| BCK-16 | **Rundturstest** mot Firebase-emulatorn: export → radera → import → export ger identiskt innehåll för alla samlingar i `tools/db/lib/collections.mjs`; listan speglar `Paths.kt` och kontrolleras av test *(4.0)*. |

---

## 10. Notifikationer & påminnelser

| ID | Krav |
|----|------|
| NOT-1 | Två notifikationskanaler ska finnas: **Medicinpåminnelser** (default) och **Screeningpåminnelser** (low). |
| NOT-2 | **Medicinpåminnelser** ska kunna aktiveras/avaktiveras; larm sätts **15 minuter före medicinens egen tidpunkt** (Morgon, Förmiddag, Lunch, Eftermiddag, Kväll, Natt) — aldrig utifrån screeningtiderna (NOT-4). |
| NOT-3 | Endast ej tagna/ej skippade mediciner ska generera larm. Finns ingen otagen schemalagd dos vid tidpunkten postas **ingen notis alls**. |
| NOT-4 | **Screeningpåminnelser** ska vara kopplade till fyra namngivna måltidshändelser: **Efter frukost, Lunch, Kvällsmat, Läggdags**. Varje händelse har ett eget på/av-reglage och en konfigurerbar tidpunkt. Inställningen finns under inställningsarket → Påminnelser *(4.0)*. |
| NOT-5 | Screeninglarm som passerat dagens tid ska schemaläggas till nästa dag. |
| NOT-6 | Larm ska **återskapas efter omstart** av enheten (BOOT_COMPLETED). |
| NOT-7 | Vid ändrade inställningar ska samtliga larm schemaläggas om. |
| NOT-8 | Exakta larm ska användas när tillåtet; annars falla tillbaka på inexakt schemaläggning (`canScheduleExactAlarms`). |
| NOT-9 | Tryck på en medicin- eller screeningpåminnelse ska öppna appen på **Idag-skärmen** (tidigare Mediciner- respektive Aktivitet-fliken, uppdaterat sedan navigationsbytet i #84 etapp 4). |
| NOT-10 | Medicinpåminnelsen ska ha en **"Markera tagen"**-åtgärd som markerar dagens schemalagda, ej tagna doser som tagna via repository-lagret och stänger notisen — utan att appen öppnas. Vid behov-doser lämnas orörda. |
| NOT-11 | Screeningpåminnelsen ska ha en **"Logga nu"**-åtgärd som öppnar Idag-skärmen med det aktuella måltidstillfällets inline-screeningformulär förexpanderat. |
| NOT-12 | Dagen innan ett recepts period (REC-7) eller en dosperiod (REC-9) tar slut ska en påminnelse visas i kanalen **Medicinpåminnelser**. Bara aktiva recept räknas. Receptets slut är den sista dagen schemat faktiskt ger en dos – slutdagen eller närmaste dosdag före den (ett helgrecept som slutar en onsdag slutar söndagen före). En dosperiod räknas när den som gäller dagen (REC-11) har sin sista dosdag – eget slut, annars periodens, eller närmaste dosdag före det – och receptet ger en dos någon dag efter den inom perioden; påminnelsen anger då dosen som gäller den nästa dosdagen (grunddosen eller nästa höjning, REC-12). Okänd upprepning (REC-2) ger inga dosperiodslut. Slutar dosperioden samma dag som perioden nämns bara receptet. Flera samtidiga periodslut slås ihop till en notis; tar inget slut visas ingen notis. Tryck öppnar Hantera → Recept & scheman. *(4.0 – 3.x: "Dagen innan ett recepts period (REC-7) eller en dosperiod (REC-9) tar slut ska en påminnelse visas … Flera samtidiga periodslut slås ihop till en notis; tar inget slut visas ingen notis." Receptets slut var alltid slutdatumet.)* |
| NOT-13 | Tidpunkten för periodpåminnelsen (NOT-12) ska vara konfigurerbar under inställningsarket → Påminnelser (standard 09:00), ingå i backup och schemaläggas om vid ändring och efter omstart. *(4.0)* |
| NOT-14 | Varje påminnelse ska schemalägga om sig själv till nästa dag när den utlösts, och alla larm ska sättas om både efter omstart (`BOOT_COMPLETED`) och efter appuppdatering (`MY_PACKAGE_REPLACED`) — påminnelserna får aldrig tystna för att appen inte startats om. |
| NOT-15 | Efter en återställning från backup ska larmen schemaläggas om direkt, så återställda påminnelsetider gäller utan att appen behöver startas om. |
| NOT-17 | Medicinpåminnelsen ska lista dagens ej tagna schemalagda doser med namn och dos (den totala dosen enligt REC-12) **för den tidpunkt larmet gäller**, och ha tidpunktens namn i rubriken. Vid behov-doser listas inte. |
| NOT-16 | Notisbehörigheten ska begäras när användaren aktiverar en påminnelse, inte vid appens första start. Saknas behörighet att visa notiser eller att ställa exakta larm ska Hantera → Notifikationer visa det, med genväg till systeminställningarna. |
| NOT-18 | Varje medicintidpunkt ska ha ett eget på/av-reglage och en konfigurerbar tidpunkt under inställningsarket → Påminnelser (standard enligt tidpunkternas klockslag: 07:00, 10:00, 12:00, 15:00, 19:00, 22:00). Inställningen ingår i backup och schemaläggs om vid ändring, efter omstart och efter återställning. "Vid behov" saknar klockslag och påminner inte. *(4.0)* |
| NOT-19 | Screeningpåminnelsen ska undertryckas **endast för den måltidshändelse som redan loggats** samma dag; övriga händelser påminner som vanligt. |

---

## 11. Inställningar *(sektionerna nedan är nu del av Hantera-ytan, se §18, sedan navigationsbytet i #84 etapp 4)*

| ID | Krav |
|----|------|
| SET-1 | **Tema-läge** väljs under inställningsarket → Tema med segmenterad knapp: ljust, mörkt eller **auto** (växlar på klockslag). Valet sparas direkt och slår igenom i hela appen utan omstart (det står som hjälptext under valet, som har etiketten "Läge"); utloggad följer appen systemet *(4.0)*. |
| SET-2 | Vid auto-tema ska **start-timme för ljust respektive mörkt** kunna ställas in (valideras så att ljus < mörk). En ogiltig ordning visas som fältfel och sparas inte; ogiltiga lagrade timmar ger standardtimmarna 07/21 *(4.0)*. |
| SET-3 | Appen har **fasta färger** i designspråket Papper och teal (DSN-1, DSN-5) i ljust och mörkt tema; dynamiska färger (Material You) används inte och inget reglage finns. 3.x-värdet `dynamicColor` bevaras i `settings.legacy` men används aldrig (DAT-11) *(4.0 – 3.x: reglaget borttaget, Material You alltid på Android 12+)*. |
| SET-4 | Under inställningsarket → Påminnelser: **medicinpåminnelser** slås på/av med ett huvudreglage, varje medicintidpunkt har reglage och klockslag (NOT-18), varje måltidshändelse för mående har reglage och klockslag (NOT-4) och periodpåminnelsen sitt klockslag (NOT-13), varje grupp med sin etikett. En avslagen påminnelse tonas ned, och medicintiderna tonas ned när huvudreglaget är av – de går ändå att ställa. Ändringarna sparas med "Spara" (NFR-10) *(4.0)*. |
| SET-5 | **Aktivitetsalternativ** läggs till, byter namn, stjärnmärks som favoriter och arkiveras (svep, med Ångra – DAT-9) under inställningsarket → Listor; inga dubbletter (samma namn oavsett skiftläge). Ett nytt alternativ får id `OptionIds.of(kind, name)` (DAT-13); ett arkiverat med exakt samma namn återställs i stället. Ändringar syns direkt i loggningsformuläret utan omstart. Raden har pil till formuläret; ett arkiverat alternativ är nedtonat med "Arkiverad" som pill, som en pausad rad *(4.0 – 3.x: ta bort i stället för arkivera)*. |
| SET-6 | **Symptomalternativ** hanteras likadant som aktivitetsalternativen (SET-5) under Listor → Symptom *(4.0)*. |
| SET-7 | Konto (in-/utloggning) hanteras från inställningsarket (NAV-9) *(4.0 – 3.x: Hantera)*. |
| SET-8 | Export och import (BCK-13, BCK-14) startas från inställningsarket (NAV-9) *(4.0 – 3.x: Hantera)*. |
| SET-9 | **Händelsetypalternativ** hanteras likadant som aktivitetsalternativen (SET-5) under Listor → Händelser. Favoritmarkerade typer visas som en-tryck-chips och övriga i en "Fler typer"-lista i Lägg till/Redigera händelse *(4.0)*. |
| SET-10 | **Vid behov-mediciner** läggs till, ändras, tas bort och stjärnmärks i fliken **Mediciner** (MEDF-3); ändringar syns direkt i Idag-skärmens vid behov-kort (HEM-11). Raden följer radstandarden (NFR-17): tryck öppnar medicinen för redigering (där den också tas bort, FAV-3), stjärnan (`FavoriteStar`, samma som i Listor) är radens inline-direktkontroll och pilen visar att raden öppnar formuläret; raden har ingen egen meny *(4.0 – 3.x: Hantera)*. |
| SET-11 | Byte av namn på ett aktivitets-, symptom- eller händelsetypalternativ syns i **redan loggade poster**, eftersom posterna refererar alternativet via `optionId` (DAT-9) – ingen uppdatering av historiken behövs *(4.0 – 3.x: UPDATE av namnet i alla tabeller)*. |

---

## 12. Datamodell (krav på fält)

| Entitet | Nyckelfält |
|---------|-----------|
| **Aktivitet** | id, timestamp, datum, tid, aktivitet, energy (−10..10 / 1..10), stress (0..10), somatiska, symptom (wire), aterhamtande, energitjuv, type (`aktivitet`/`screening`), spentTime (min). |
| **Medicin** | id, timestamp, datum, tid, namn, dos, enhet, tidpunkt, tagen, receptId?, skipped, tagenTid? (faktisk tagningstidpunkt, MED-14). |
| **Recept** | id, namn, dos, enhet, tidpunkter[], upprepning, dagar[], intervalDagar, aktiv, skapad, startDatum, slutDatum? (null = tills vidare, REC-7), dosperioder[] (REC-9). |
| **Dosperiod** | id, startDatum, slutDatum? (null = receptets slut), dos, enhet — persisteras som JSON på receptet. |
| **Favorit** | id, namn, dos, enhet, tidpunkt, minTidMellan (h), dispenseringsTid, maxDoserPerDag, isFavorite. |
| **Händelse** | id, timestamp, datum, tid, typ, svarighetsgrad, varaktighetMinuter, triggers, atgarder. |
| **SjukdomsEpisod** | id, typ, startDatum, slutDatum, timestamp. |
| **SjukdomsIncheckning** | id, episodId, datum, tid, svarighetsgrad, symptom, somatiska, timestamp. |
| **Note** | target (`ACTIVITY`/`SCREENING`/`MEDICATION`/`RECEPT`/`FAVORIT`/`EVENT`/`SJUKDOM_EPISOD`/`SJUKDOM_INCHECKNING`), entityId, text — generisk anteckning kopplad till valfri entitet ovan (ersätter tidigare `anteckning`-kolumner på Aktivitet/Medicin/Recept/Favorit/Händelse/Sjukdom). |

| ID | Krav |
|----|------|
| DAT-1 | Tidpunkter ska sorteras enligt fast ordning: Morgon, Förmiddag, Lunch, Eftermiddag, Kväll, Natt, Vid behov. |
| DAT-2 | Datum ska lagras som `YYYY-MM-DD`, tid som `HH:MM`. |
| DAT-3 | ~~Room-schema ska exporteras för migreringsspårning.~~ *(borttaget 4.0 – ingen Room; dokumentmodellen i DAT-5)* |
| DAT-4 | ~~När en post raderas ska dess anteckning i den generiska `notes`-tabellen raderas i samma repository-anrop, oavsett vilken skärm raderingen görs från. Raderas en sjukdomsepisod försvinner även anteckningarna för dess incheckningar.~~ *(borttaget 4.0 – anteckningen är fältet `note` på dokumentet, DAT-7)* |
| DAT-5 | **Dokumentmodell 4.0** under `users/{uid}`: `settings` (ett dokument, DAT-11), `options`, `prescriptions`, `prnMedicines`, `doses`, `screenings`, `activities`, `events`, `illnessEpisodes` med undersamlingen `checkins`. Fältlistan står i ARKITEKTUR.md → Datamodell; varje fält i 3.x-tabellen ovan och i 3.x `BackupJson` har en plats i paritetstabellen där (Fältparitet 3.x → 4.0) – inget fält utelämnas; bara backupfilens egna `version` och `createdAt` är metadata. Tabellen kontrolleras mot codecarna av test *(4.0)*. |
| DAT-6 | Symptom lagras strukturerat som `symptoms: [{optionId, score, customText}]` i vald ordning; `customText` är fritexten vid "Övrigt" (AKT-6, i 3.x `Övrigt (fritext)` i namnet). Summan (`somatiska`) beräknas i `:core` och persisteras inte *(4.0 – ersätter wire-formatet i HIS-3)*. |
| DAT-7 | Anteckningen är fältet `note` på varje dokument (post, recept, vid behov-medicin, episod, incheckning). Ingen separat notes-samling; raderas dokumentet följer anteckningen med *(4.0)*. |
| DAT-8 | En dos har `status` ∈ {`planned`, `taken`, `skipped`}, schemalagt klockslag `plannedTime` (`HH:mm`) och `takenAt?` (tidsstämpel). Receptgenererade doser behåller 3.x-id:t `recept_{prescriptionId}_{date}_{tidpunkt}` med tidpunktens 3.x-namn (t.ex. `recept_…_2026-10-04_Förmiddag`), så att genereringen i 4.0 (MED-4) träffar redan migrerade doser och en upprepad import inte dubblerar; de får `prescriptionId`, tidpunktens standardklockslag (REC-6) som `plannedTime` och dag plus klockslag i enhetens tidszon som `createdAt`, så att två genereringar ger identiska dokument. En vid behov-dos som loggas får ett nytt id, `status` = `taken`, `takenAt` = `createdAt` = dosens tidpunkt, `plannedTime` = samma klockslag i hela minuter och `prnId` = vid behov-medicinen (en extrados från ett recept, FAV-11, får varken `prnId` eller `prescriptionId`) *(4.0 – ersätter `tagen`/`skipped`/`tagenTid`)*. |
| DAT-9 | Alternativlistorna är dokument i `options` med `kind` (`activity` | `symptom` | `event`), `name`, `favorite`, `sortOrder`, `archived`; poster refererar dem via `optionId`. Ett alternativ som används arkiveras i stället för att raderas *(4.0)*. |
| DAT-10 | Alla codecs ligger i `:core` som `DocCodec<T>` med defaultvärden för saknade fält. Ett okänt enumvärde (från en nyare app) läses som modellens default och skrivs som default vid nästa sparning – utom måltidstillfället `occasion`, som blir `null`, och påminnelserader och tidpunkter med okänd nyckel, som hoppas över. Okända fält på toppnivå och i nästlade objekt bevaras vid läs–skriv (merge); okända fält i ett **listelement** (symptom, doshöjningar, påminnelserader) bevaras inte, eftersom listor skrivs hela. En okänd upprepning på ett recept skrivs tillbaka oförändrad; dokumentet kan uppdateras men inte skapas på nytt med den förrän `schemaVersion` höjs. Datum lagras som `yyyy-MM-dd`, klockslag som `HH:mm` och ögonblick (`createdAt`, `takenAt`) som tidsstämpel; enum med engelska namn som också står i `firestore.rules` *(4.0)*. |
| DAT-11 | Inställningarna är **ett dokument**, `settings/app`, med grupperna `theme` (läge, ljus-/mörkerstart, mörkt tema), `reminders` (huvudreglaget för medicinpåminnelser, en rad per medicintidpunkt och per måendetillfälle med på/av och klockslag, periodpåminnelsens klockslag), `profile` (födelseår, kön) och `legacy` (3.x-värdena `dynamicColor` och `sheetsConfig`, som bara bevaras och aldrig används). Påminnelseraderna lagras och läses på sin nyckel (`slot`, `occasion`), inte på position. Andra dokument-id i `settings` nekas av rules *(4.0 – ersätter DataStore och `SettingsBackup`)*. |
| DAT-12 | En måendelogg har måltidstillfället `occasion` (`breakfast`, `lunch`, `dinner`, `bedtime`). 3.x hade inget sådant fält; för en 3.x-screening härleds det ur namnet (Efter frukost, Lunch, Kvällsmat, Läggdags) och annars ur klockslaget – tillfället vars påminnelsetid ligger närmast räknat runt dygnet – och ett annat namn bevaras i `customText` *(4.0)*. |
| DAT-13 | **Id:n bevaras från 3.x:** poster, doser, recept, vid behov-mediciner, episoder och incheckningar får sitt 3.x-id (UUID-sträng) som dokument-id, så att en upprepad import skriver över i stället för att dubblera. Alternativen (som saknade id i 3.x) får `OptionIds.of(kind, name)` = `{kind}-{slug}-{hash}`: slug är namnet NFD-normaliserat utan kombinerande tecken (å/ä/ö → a/a/o) och gement med allt utom `a–z`/`0–9` ersatt med `-`, bindestreck hopslagna och trimmade, högst 32 tecken; hash är de 6 första hex-tecknen av SHA-256 över det exakta namnet, så att `Promenad`, `promenad` och `Promenad ` får olika id. Samma regel i konverteraren och för nya alternativ i 4.0 *(4.0)*. |

---

## 13. Icke-funktionella krav

| ID | Krav |
|----|------|
| NFR-1 | **Offline-först**: all kärnfunktionalitet ska fungera utan nätverk. |
| NFR-2 | UI-tillstånd ska vara reaktivt (Flow/StateFlow) och överleva konfigurationsändringar. |
| NFR-3 | Release-bygge ska använda **R8/ProGuard** (minify + resource shrinking). |
| NFR-4 | Appen ska stödja **RTL** och systemets **predictive back**. |
| NFR-5 | Splash screen ska visas vid uppstart. |
| NFR-6 | Tester på alla nivåer: `:core` (ren JUnit – codecs, konverterare, motorer, diagrammatematik), ViewModel (Fake-repositories + Turbine), Compose via Robolectric i JVM, Roborazzi-skärmdumpar ljust + mörkt, Konsist (`UiConsistencyTest`), `cpdCheck`, rules- och rundturstester mot Firebase-emulatorn, samt instrumenttester bara när berörd kod ändrats *(4.0 – 3.x: JUnit/MockK/Turbine + instrumenttester vid varje PR)*. |
| NFR-7 | Känslig data (backup) ska endast lagras i användarens privata Drive-appmapp. |
| NFR-8 | Appstorlek/prestanda: listor ska använda lazy-rendering; tunga operationer på IO-dispatcher. |
| NFR-9 | Appen använder ett enhetligt designsystem: varje elementtyp har **exakt en** komponent i `ui/components/`, feature-kod anropar aldrig Material 3 direkt och hårdkodar aldrig färg, form eller typografi; allt kommer från `ui/theme`. Kontrolleras av hooken `regel4-check`, `UiConsistencyTest` och `cpdCheck`, undantag bara via `ui-allowlist.txt` *(4.0 – 3.x: delade komponenter för kort, tomlägen, dialoger, rubriker)*. Samma sak har samma form även inuti komponenterna: etiketten över en formulärkontroll (även reglaget) i `GroupLabel`-stil, titel och undertext i listrad och postkort på samma sätt (NFR-17), en valmarkering i färg- och emojivalet, en dagsmarkering i datumremsa och kalender, en spårfärg för framsteg, en nedtoning för det inaktiva och ikonstorlekar ur en skala (DSN-3) *(4.0)*. |
| NFR-10 | ~~Spara-knappar byggs med den delade komponenten `SaveButton` och är inaktiverade tills formuläret har osparade, giltiga ändringar (dirty-state — jämfört mot senast laddade/sparade värde, inte bara fältvalidering). Försök att navigera bort (tillbaka-knapp eller systemets back) med osparade ändringar visar en bekräftelsedialog (`UnsavedChangesBackHandler`) med möjlighet att spara, kasta ändringarna eller avbryta. `SaveButton` använder appens gröna "positiv"-signal (Emerald400/900) som container-/textfärg i aktivt läge, samma i ljust och mörkt tema.~~ *(4.0 – ersatt av ramen `EntityEditScreen` + `EditorState`:)* Redigeringsskärmar byggs med `EntityEditScreen` och `EditorState`: "Spara" i toppraden är inaktiv tills formuläret har osparade, giltiga ändringar (dirty-state — jämfört mot senast laddade/sparade värde, inte bara fältvalidering). Försök att navigera bort (tillbaka-knapp eller systemets back) med osparade ändringar visar "Släng ändringar?" med valen **Släng** och **Fortsätt redigera** – dialogen sparar inte; man sparar med "Spara" i toppraden. |
| NFR-12 | Ett formulär ska navigera vidare först när sparandet är **klart** — aldrig starta en skrivning och stänga skärmen samtidigt, eftersom ViewModel:ens scope då kan avbryta skrivningen mitt i. |
| NFR-13 | Släppt app ska inte logga något till logcat (loggning strippas i release), och persisterad användardata får aldrig ingå i ett loggmeddelande. |
| NFR-14 | Diagram ska ha en talbar sammanfattning för skärmläsare, och interaktiva ytor ska hålla minst 48 dp i båda riktningarna – även knappar inuti en sammansatt kontroll, som stegarens − och + (`QuantityStepper`), är själva 48 dp *(4.0)*. |
| NFR-11 | Skärmar med textinmatning ska hålla det fokuserade fältet synligt ovanför skärmtangentbordet, så inget inmatningsfält skyms medan man skriver. ~~IME-inset hanteras centralt i den delade `DagbokenScaffold` (`contentWindowInsets` inkluderar `WindowInsets.ime`).~~ *(4.0 – ersatt:)* IME-inset hanteras centralt i ramen `EntityEditScreen` (`imePadding`). |
| NFR-15 | Ett **postkort** — ett kort som representerar en sparad post (historikpost, aktivitet, dos, incheckning, episod, recept) — byggs med den delade `DagbokenEntryCard` och har ett enhetligt gestmönster: **tryck** öppnar posten (detaljskärm om posten har en, annars redigering) och expanderar aldrig; **långtryck** visar samma kontextmeny som `⋮`; **svep från höger till vänster** begär radering, som alltid bekräftas med den delade `ConfirmDialog` — kortet fjädrar tillbaka tills dialogen svarat; **svep från vänster till höger** är oanvänd, reserverad riktning. Ingen åtgärd får vara nåbar enbart via svep. **Sektionskort** (panel, formulär, diagram) och **navigationskort** har varken svep, långtryck eller kontextmeny. Ytorna migreras stegvis till komponenten; tills en yta är migrerad gäller beteendet som beskrivs i dess eget avsnitt. **Undantag:** ett chip-kort som är en loggningsknapp (vid behov-favoriterna, HEM-11) behåller tryck = utför åtgärden, och exponerar hela kontextmenyn via långtryck. |
| NFR-16 | Ett postkorts trailing-innehåll renderas i ordningen status-/värdechip, anteckningsikon, expandera-chevron, kontextmeny (`⋮`) — varje del utelämnas när den saknas, men ordningen är fast. Kontextmenyn följer ordningen Redigera, kontextspecifika val, Ta bort (sist, i error-färg), och varje menypost har en ikon. Expandering av detaljer sker via chevron-knappen, aldrig genom tryck på kortet. Ett korts vänsteraccent är reserverad för status (energifärg, aktiv/inaktiv, pågående) — aldrig dekoration. Ett postkort får ha **en** direktkontroll i trailing-läget för en tillståndsväxling som används ofta (t.ex. receptets aktiv-reglage); den placeras först av trailing-innehållet och dubbleras som menyval. Har kortet en sådan direktkontroll står titeln ensam på en egen rad i hela kortets bredd, och undertexten och trailing-innehållet delar raden under (en dokumenterad variant av `DagbokenEntryCard`). *(4.0)* |
| NFR-17 | **Listrader** inuti ett sektionskort (checklistrader, inställningsrader) är inte kort och följer en egen radstandard: tryck på **hela raden** utför radens primära åtgärd (markera tagen/otagen, öppna screeningformuläret, byt namn, favoritmarkera) — inte bara en liten ikon; en trailing-ikon som speglar tillståndet är en ren indikator och läses inte upp separat, och radens tillstånd exponeras med `Role` och `stateDescription`. Har raden fler åtgärder än den primära ligger de i en kontextmeny på långtryck och `⋮`, byggd med samma menykomponent och ordning som kortens. Högst **en** inline-direktkontroll per rad. Svep används aldrig på rader — det är förbehållet postkort. Radens titel visas på högst två rader och undertexten hel – likadant i postkortet (NFR-16). En rad som öppnar en annan skärm har en pil längst till höger, och ett läge som "Pausat" eller "Arkiverad" står som pill på en nedtonad rad *(4.0)*. |
| NFR-18 | Ett **sektionskort** (NFR-15) får vara **ihopfällbart** och byggs då med den delade `Foldout` (regel 4): **hela titelraden** växlar utfällt läge, med en chevron som roterar som tillståndsindikator. Att tryck expanderar är tillåtet här — till skillnad från postkortet (NFR-16) — eftersom sektionskortet inte har någon konkurrerande primär åtgärd; samma princip som listradens "tryck på hela raden" (NFR-17). Titelraden håller minst 48 dp i höjd och exponeras med `Role` och `stateDescription` (utfälld/ihopfälld); chevronen är en ren indikator och läses inte upp separat. Åtgärdsetiketten delas med postkortets chevron, så samma gest heter samma sak i hela appen. Innehåll som saknar mening utan kortets innehåll — t.ex. en periodväljare — visas först i utfällt läge. |
| NFR-19 | **CI-budget:** en PR kostar högst ca 8 Actions-minuter; tester körs bara när koden de testar ändrats (paths-filter), lint och release-bygge körs veckovis och vid release, instrumenttester på emulator bara när berörda sökvägar ändrats. Enda obligatoriska statuskontrollen är `ci-ok` *(4.0)*. |
| NFR-20 | Nytt synligt utseende eller beteende visas som **mockup** i Design-canvasen och godkänns innan kod skrivs; PR:en visar Roborazzi-skärmdumpar bredvid mockupen *(4.0)*. |

---

## 14. Kända begränsningar / framtida arbete

| ID | Notering |
|----|----------|
| ~~FUT-1~~ | ~~**Hälsa-fliken** är endast en inaktiverad platshållare (ej implementerad).~~ *(borttaget — Hälsa implementerad via Health Connect, se §19)* |
| FUT-2 | `sheetsConfig` (Google Sheets-koppling) finns i inställningslagret men är inte exponerat i UI. |
| FUT-3 | Backup-worker kan inte hantera Drive-auktorisering som kräver UI (returnerar success utan att ladda upp). |

---

## 15. Sjukdomar (SJ) *(4.0: nås via Dagbok – filtret Sjukdom och episoddetaljen, HIST-9 – samt kortet på Idag, HEM-12; 3.x: Hantera → Sjukdomar, HANT-3)*

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
| SJ-11 | En incheckning kan **redigeras** i efterhand — tryck på incheckningskortet öppnar samma formulär förifyllt (datum, tid, svårighetsgrad, symptom, anteckning). Ändringen skriver över samma post: `id` och ursprunglig `timestamp` bevaras, och `somatiska` räknas om från de redigerade symptomen. |
| SJ-12 | Episodkortet på detaljskärmen kan **redigeras** — tryck på kortet öppnar redigering av typ, startdatum och anteckning. Redigering skapar ingen ny incheckning. Episoden raderas från listan, inte inifrån detaljvyn. |
| SJ-13 | Sjukdomsytans kort följer kortstandarden (NFR-15/NFR-16): episodkortet i listan öppnar detaljskärmen vid tryck och har Redigera i sin meny, incheckningskorten öppnar redigering, och svep från höger till vänster raderar efter bekräftelse. Anteckningar visas med anteckningsikonen och status med chip ("Pågår"/"Avslutad"). |

---

## 16. Dagbok-flik (enhetlig tidslinje, HIST) *(4.0: fliken heter Dagbok; 3.x: Historik)*

> Del av UX-omtaget #84 (etapp 2, nåbar via bottennavigeringen sedan etapp 4 — se §3 NAV-7).

| ID | Krav |
|----|------|
| HIST-1 | Dagbok-fliken visar alla posttyper (mående, aktivitet, tagen dos, händelse, sjukdomsincheckning samt episodens start och slut) i ett enda kronologiskt flöde, grupperat per dag med etiketterna Idag, Igår och veckodag + datum. En dospost är en faktiskt **tagen** dos (HIST-7) *(4.0 – 3.x: fem posttyper utan episodens start/slut)*. |
| HIST-2 | Poster kan filtreras per typ med filterchips; minst en typ måste vara aktiv (samma regel som HIS-1). |
| HIST-3 | Tryck på en post navigerar till dess befintliga redigerings-/detaljskärm (ingen ny redigeringslogik i Historik-ytan själv). För en medicinpost redigerar detta endast den enskilda tagningen (MED-15), inte receptet eller favoriten. |
| HIST-4 | ~~Historik-ytan skriver inte till någon datakälla — ren läsvy över befintliga repositories.~~ *(ändrat, se HIST-5 — #105)* |
| HIST-5 | En post i Historik följer kortstandarden (NFR-15): långtryck och `⋮` öppnar samma meny med **Redigera** och **Ta bort**, och svep från höger till vänster begär radering. Radering kräver alltid bekräftelsedialog och anropar samma repository-metod som respektive domänskärm redan använder. |
| HIST-6 | Historik kan växlas mellan listvy och kalendervy (delad komponent `DagbokenCalendar`). I kalendervyn markeras dagar med minst en post; tryck på en dag visar postens/posternas för det datumet. Långtryck-radering (HIST-5) fungerar identiskt i båda vyerna. Kalendern markerar dagarna som datumremsan (HEM-14): en punkt under dagar med poster, idag med den solgula punkten med ring och den valda dagen fylld teal i radens form; TalkBack läser datum, "idag" och "har poster" *(4.0 – tidigare: teal ring kring idag och vald dag som fylld cirkel)*. |
| HIST-8 | Historik läser ett begränsat fönster bakåt (ett år) i stället för hela databasen. En "Visa äldre poster"-rad längst ned utökar fönstret med ytterligare ett år i taget. |
| HIST-7 | Historik-ytans medicinposter visar endast doser som faktiskt är **tagna** (`tagen`, ej överhoppad). Planerade/kommande, aldrig tagna och överhoppade doser (MED-3) visas inte — de hör hemma i Idag-checklistan (MED-1/MED-13). Tidsetiketten är tagningstidpunkten (MED-14), inte den schemalagda tiden. |
| HIST-9 | Filtret **Sjukdom** visar episoder och incheckningar; tryck på en episod öppnar episoddetaljen (SJ-11–SJ-13) som underskärm. Det är sjukdomsytans hem i 4.0 – ingen separat sjukdomslista *(4.0)*. |

---

## 17. Trender-yta (enhetliga diagram, TRD)

> Del av UX-omtaget #84 (etapp 3, nåbar via bottennavigeringen sedan etapp 4 — se §3
> NAV-7). `DiagramScreen`/`SymptomDiagramScreen` (tidigare DIA-1..5) är borttagna —
> Trender-ytan är nu den enda vägen till aktivitets- och symptomdiagram.

| ID | Krav |
|----|------|
| TRD-1 | Trender-ytan delar upp diagrammen i egna kategorier, var och en med egen serieväljare: **Energi (dag)** (TRD-8, ingen väljare — alltid synlig), **Energi per tillfälle** (Frukost/Lunch/Kvällsmat/Läggdags), **Stress & belastning** (Stress/Somatiska/Återhämtande/Energitjuv), **Symptom** (dynamiska symptomserier) samt **Steg** och **Vilopuls** (TRD-11, Health Connect, ingen väljare) längst ner. *(#141: tidigare en gemensam serieväljare för alla serier — ett diagram för alla gav en gemensam y-skala som gjorde enskilda serier oläsliga.)* |
| TRD-2 | Inom en kategori kan flera serier väljas och overlagras i samma diagram, på en skala beräknad **enbart över den kategorins** aktiva värden (se TRD-7) — inte alltid nollankrad, och inte längre delad med andra kategoriers serier (#141). |
| TRD-3 | Varje diagram har en **egen** periodväljare — en dropdown (7 dagar / 14 dagar / Månad / 3 månader / Allt), placerad i diagramkortets övre högra hörn — och styrs individuellt, oberoende av de andra diagrammens period (#149). *(#144: ersatte de fyra fasta periodknapparna 7/14/30/90 dagar med en dropdown; "Allt" är nytt — ingen nedre datumgräns. #149: bytte den då gemensamma periodväljaren mot en per diagram.)* |
| TRD-4 | Trender-ytan skriver inte till någon datakälla — ren läsvy. |
| TRD-5 | Nås från bottennavigeringen samt via en genväg från Idag-skärmens energikort (§4, HEM-7). |
| TRD-6 | Linjediagrammens linjer renderas som mjuka kurvor med en gradientfyllning under linjen i seriens färg; axeletiketterna följer appens ljusa/mörka tema (tillräcklig kontrast i båda lägena). |
| TRD-7 | Varje diagrams y-axel skalas smart efter dess egna aktiva värdens min/max (med marginal, avrundat till läsbara gränser) i stället för att alltid utgå från 0, så variationer i ett smalt värdeband blir läsbara. Axelns gränser och samtliga gridlinjer/etiketter är alltid heltal — steget är minst 1 och avrundas till 1/2/5 × 10^n — även när underliggande data är beräknad (t.ex. dagsgenomsnitt). Gäller alla diagram i appen (delad `computeSmartYAxis`, regel 4). |
| TRD-8 | **Energi (dag)** visas som ett eget intervall-/spannstapeldiagram (`IntervalBarChart`, regel 4 — inte Vicos finansiella candlestick-layer): en stapel per dag från dagens lägsta till högsta loggade screeningenergi, med dagsvärdet (samma uträkning som Idag-diagrammet, HEM-7, delad `computeDailyEnergyStats`) markerat på stapeln. Dagsvärdena förbinds med en mjuk bezier-kurva (samma S-kurve-teknik som Vicos `PointConnector.cubic()`, TRD-6) som bryts vid en dag utan data. Visas alltid när minst en dag har en screening, ingen serieväljare. |
| TRD-9 | Varje diagram (Trender och Idag) visar alltid sitt faktiska datas lägsta och högsta värde som text under diagrammet (delad `MinMaxCaption`, regel 4); decimaler skrivs med svenskt decimalkomma ("6,4") i text, axlar och skärmläsarbeskrivning *(4.0 – 3.x: ~~punkt~~)* — oberoende av hur y-axelns rutnät råkar skala sig eller hur axeln avrundas till heltal (TRD-7). `IntervalBarChart` (TRD-8) ritar dessutom horisontella värdelinjer vid varje jämnt steg mellan lägsta och högsta axelvärde (delad `computeSmartYAxis`, regel 4) så mellanliggande värden går att avläsa utan att gissa. |
| TRD-10 | Trenders diagram stöder tvåfingerzoom och panorering horisontellt, samma känsla på samtliga diagram — linjediagrammen (`LineChart`) via Vico 3:s inbyggda zoom-/scrollstöd, stapeldiagrammen (`IntervalBarChart`, `StackedBarChart`) via en egen gest-hantering där ett **enfingersdrag bara panorerar när diagrammet är inzoomat** och draget går i sidled, så att sidan runt omkring annars rullar som vanligt; panoreringen går aldrig förbi periodens första eller sista dag. Varje diagram är **helt utzoomat (hela den valda perioden synlig)** som standard, oavsett antal datapunkter (`initialZoom = Zoom.Content`). Zoom/panorering nollställs när **det diagrammets egna** period (TRD-3) eller dess data byts. Gäller inte Idag-diagrammen (fasta 7-dagarsvyer utan periodval) *(4.0 – 3.x: ~~linjediagrammen var `LineChartCanvas` på Vico 2; stapeldiagrammens drag tog alla enfingersdrag~~)*. |
| TRD-11 | Trender-ytan visar **Steg** och **Vilopuls** som egna diagram (Health Connect, samma källa som Idag-hälsokortet HEM-15/HLS-7), periodväljbara via sin egen periodväljare (TRD-3) — till skillnad från Idag-kortets sparklines som alltid visar senaste 7 dagarna. Vilopulsdiagrammet har en **serieväljare** med **vilopuls** och **dygnssnittspuls** (båda bpm, samma skala). Kräver kopplad och behörig Health Connect-källa; tom period visar samma tomlägesmönster som övriga diagram (#146). Övriga hälsomått har egna diagram, se TRD-15. |
| TRD-12 | Trenders period- och serieväljare delar en kompakt, platssnål dropdown-triggerknapp (`CompactDropdownButton`, regel 4) — mindre padding/ikon/typografi än standardknappen, så sex periodväljare (TRD-3) och tre serieväljare (TRD-1) får plats utan att tränga ut korttexten (#149). |
| TRD-13 | Varje visad dataserie ritar en streckad linjär trendlinje (minsta kvadrat-anpassning över periodens punkter), alltid synlig och utan användarval — **solgul** när diagrammet visar en serie, i **seriens färg** när det visar flera, så att trenderna går att skilja åt *(4.0 – 3.x: ~~alltid i seriens färg~~)*. Med en serie står riktningen även som text ("Trend uppåt/nedåt/oförändrad") under diagrammet. Gäller alla diagram i appen — Trenders sex diagram och Idag-kortens sparklines (delad `computeTrendLine`, regel 4). Kräver minst två kända punkter; dagar utan data hoppas över utan att förskjuta lutningen. Legend markerar den streckade linjen som trend, och `IntervalBarChart`s skärmläsarbeskrivning anger trendens riktning. |
| TRD-14 | Trenders diagramkort är **ihopfällbara sektionskort** (NFR-18) och **stängda som standard** — kortet visar då bara sin titel och en chevron, och komponerar inte sitt diagram alls. Diagrammet, serieväljaren, `MinMaxCaption`, legenden **och periodväljaren** (TRD-3) visas först i utfällt läge; en periodväljare utan sitt diagram säger ingenting. Utfällningen gäller per diagram och påverkar inte de andra korten. Tillståndet hålls i vyn (`TrenderViewModel`) och persisteras inte — Trender öppnas alltid med samtliga kort stängda. Motivet är att ytan rymmer ett tiotal diagram och att varje utfällt kort kostar en full diagramkomposition (#189). |
| TRD-15 | Trender visar utöver Steg och Vilopuls (TRD-11) egna diagram för klockdatans övriga mått (HLS-12): **Sömn** (total, djup, REM, lätt, vaken — valbara serier i timmar), **Sömnkvalitet** (poängen och dess sex delkomponenter, HLS-10/HLS-13, alla 0–100), **Träning** (passtid i minuter), **Aktiva kalorier**, **Sträcka** (km), **Syremättnad** (%) och **Blodtryck** (systoliskt/diastoliskt, mmHg). **Ett diagram per enhet** — serier med olika enheter delar aldrig y-skala, eftersom den mindre serien då blir en platt linje längs botten (jämför TRD-2). Varje diagram följer TRD-3/6/7/9/10/12/13/14 som övriga. Ett hälsodiagrams data läses **först när kortet fälls ut** (TRD-14) och behålls när det fälls ihop; diagram som visar samma period delar en läsning. Dagar utan mätning ritas som luckor, aldrig som nollor (HLS-12), och en period utan data visar samma tomlägesmönster som övriga diagram (#146). |
| TRD-16 | **Sömnstadier** visas som ett eget **staplat stapeldiagram** (delad `StackedBarChart` i `ui/diagram/`, regel 4): en stapel per natt, delad nedifrån och upp i djupsömn, REM, lätt sömn och vaken tid, så nätternas **sammansättning** går att jämföra och inte bara deras längd — två lika långa nätter kan ha helt olika arkitektur, vilket syns i stapeln men inte i fyra överlagrade linjer. Diagrammet har ingen serieväljare; segmenten är hela poängen. Y-axeln skalas över staplarnas **totalhöjd** (TRD-7) och **börjar alltid på noll** — en stapel är en längd, och en axel ovanför noll skulle kapa de nedersta segmenten *(4.0 – 3.x: ~~axeln utan noll, med staplarna ritade från axelns botten~~)* — med värdelinjer vid varje jämnt steg (TRD-9), och trendlinjen (TRD-13) ritas över totalen. Ett stadium som saknas en natt tar **ingen höjd** i stapeln och skjuter inte upp stadierna ovanför — annars skulle en natt utan REM-mätning se ut att ha mer djupsömn än den hade. Stapelns total är **tiden i säng** (vaken tid ingår), medan Sömn-diagrammets "Total" är sömnlängden. Stadiernas färger är desamma som i Sömn-diagrammets linjeserier (TRD-15). Skärmläsarbeskrivningen anger antal nätter, kortaste och längsta natt, dominerande stadium och trendens riktning. Zoom och panorering som övriga diagram (TRD-10). |
| TRD-17 | Trender har ett **jämförelsediagram** ("Jämför") där två eller flera valfria serier ur hela appen kan överlagras i samma diagram — klockdata (HLS-12/HLS-13) och loggade dagboksserier om vartannat, t.ex. sömnkvalitet mot energi eller vilopuls mot stress. Eftersom serierna har olika enheter **indexeras varje serie 0–100 mot sitt eget min/max inom perioden**; en rak överlagring skulle platta ut den mindre serien mot botten (steg ligger runt 10 000, energi på 0–10). Y-axeln visar index, inte enheter. De verkliga värdena får inte gömmas: legenden anger varje series faktiska lägsta och högsta värde **med enhet** för perioden. En serie med konstanta värden ritas som mittlinje i stället för att divideras med noll, och dagar utan data förblir luckor — aldrig nollor. Serierna behåller sina färger från sina egna diagram, och etiketterna är kvalificerade så de står för sig själva utanför sitt eget diagram ("Sömnlängd", inte "Total"). Datumaxeln är gemensam och sammanhängande för perioden, så en gles dagboksserie blir luckor och inte en hoptryckt axel. Diagrammet visar tomläge tills minst två serier med data valts. Data läses först när kortet fälls ut (TRD-14), och sömnkvaliteten bara när någon valt den. Diagrammet gör **ingen** statistisk korrelation och drar inga slutsatser om orsakssamband — det visar två kurvor bredvid varandra, tolkningen är användarens. Övrigt följer TRD-3/6/10/12/13/14. |
| TRD-18 | Varje linjediagram i Trender har ett tillval **"Föregående period"** som lägger den föregående, lika långa perioden som en **nedtonad** kurva — **grå** när diagrammet visar en serie, annars i seriens färg — ovanpå den nuvarande *(4.0 – 3.x: ~~alltid i samma färg~~)*. Punkterna placeras på samma x-index som den nuvarande perioden (dag 1 mot dag 1), eftersom det är formerna och inte datumen som jämförs. Legenden får en rad per period — föregående märks "(föregående)" — och trendlinjen (TRD-13) ritas för båda, så lutningsskillnaden går att avläsa; `MinMaxCaption` och skärmläsarbeskrivningen täcker båda periodernas värden. Tillvalet är **av som standard**: nuläget är det som ska synas först. Vid periodvalet "Allt" (TRD-3) finns ingen föregående period och tillvalet visas inte alls, hellre än som en död kontroll. För hälsodiagrammen läses båda perioderna i **ett** svep (dubbla periodens längd), så jämförelsen inte kostar en andra läsning. **Undantagna är tre diagram**: Energi (dag) (TRD-8) och Sömnstadier (TRD-16) ritar staplar, och två uppsättningar staplar i samma x-position går inte att läsa av; Jämför (TRD-17) överlagrar redan flera indexerade serier, och en dubblering av dem gör diagrammet oläsligt. |
| TRD-19 | Trender delar diagrammen i tre **grupper** valda med segmentknapp högst upp: **Mående** (Energi per dag, Energi per tillfälle, Stress och belastning, Symptom, Händelser och sjukdom), **Klocka** (Steg, Vilopuls, Sömn, Sömnstadier, Sömnkvalitet, Träning, Kalorier, Sträcka, Syremättnad, Blodtryck) och **Jämför** (TRD-17). Inom en grupp gäller TRD-14 (ihopfällbara kort, stängda som standard) *(4.0 – 3.x: alla kort i en lista)*. |
| TRD-20 | Under **Klocka** finns Health Connect-statusen: kopplad/ej kopplad, saknade behörigheter (HLS-14) och Hälsa idag med klockans alla mått (HLS-8). Det ersätter Hälsa-skärmen under Hantera (HLS-6) *(4.0)*. |

---

## 18. Hantera-yta (bibliotek/konfiguration, HANT) *(hela avsnittet borttaget 4.0 – ersatt av inställningsarket NAV-9, fliken Mediciner §21, Dagbok HIST-9 och Trender TRD-20)*

> Del av UX-omtaget #84 (etapp 4). Fjärde bottennavflik — samlar tidigare `Inställningar`
> (sektionerna nedan återanvänds oförändrade) med två nya navigeringskort till
> sjukdomshantering och recept/scheman, som tidigare nåddes via egna bottenflikar.

| ID | Krav |
|----|------|
| HANT-1 | ~~Hantera-ytan nås som fjärde bottennavflik (se NAV-7, §3) — visar inte tillbakapil, till skillnad från tidigare `Inställningar` som var en underliggande skärm.~~ *(borttaget 4.0 – NAV-8/NAV-9)* |
| HANT-2 | ~~Sektionerna Konto, Import, Tema, Notiser, Aktivitetstyper, Symptom, Vid behov-mediciner, Händelsetyper och Om appen återanvänds oförändrade från tidigare `Inställningar` (samma `DagbokenCard`/`SectionHeader`-uppbyggnad).~~ *(borttaget 4.0 – sektionerna ligger i inställningsarket NAV-9, vid behov-mediciner i MEDF-3)* |
| HANT-3 | ~~Ett nytt navigeringskort **Sjukdomar** öppnar sjukdomshantering (lista/avsluta episoder) som en underliggande skärm med tillbakapil.~~ *(borttaget 4.0 – HIST-9)* |
| HANT-4 | ~~Ett nytt navigeringskort **Recept & scheman** öppnar receptschemat (samma innehåll som tidigare Mediciner-flikens Schema-flik, §6.2) som en underliggande skärm med tillbakapil.~~ *(borttaget 4.0 – MEDF-1)* |
| HANT-5 | ~~På bred skärm (≥360dp) visas sektionerna i en sidopanel; på smal skärm i en scrollbar kolumn — samma responsiva mönster som tidigare `Inställningar`. Sidopanelen är själv vertikalt scrollbar så samtliga sektionsikoner går att nå oavsett skärmhöjd (#146).~~ *(borttaget 4.0 – arket är en kolumn; bred skärm hanteras av ramarna)* |
| HANT-6 | ~~Rader i Hantera följer radstandarden (NFR-17): på en alternativ-/symptomrad byter tryck namn på alternativet, och långtryck eller `⋮` ger menyn (Byt namn, Ta bort); stjärnan är radens enda inline-direktkontroll. På en vid behov-rad växlar tryck var som helst på raden favoritmarkeringen.~~ *(borttaget 4.0 – radstandarden NFR-17 gäller i inställningsarkets listor och i Mediciner)* |

---

## 19. Hälsa (Health Connect, HLS)

> Hälsodata från Galaxy Watch 7 synkas via Samsung Health till **Health Connect** och läses
> read-only på telefonen. Spike #56 fastställde vägvalet (Health Connect framför Samsung Health
> Data SDK: Maven-beroende som bygger i CI, leverantörsneutralt, ingen Samsung-partner/-licens);
> #57 implementerade skärmen. Vägvalet omprövades 2026-08 (#180) och står fast — Samsungs Data SDK
> distribueras fortfarande bara som AAR utanför Maven, kräver registrering av paketnamn och
> signatur, och exponerar varken HRV eller stress. Läsningen breddades i stället till fler
> Health Connect-typer, se HLS-8.

| ID | Krav |
|----|------|
| HLS-1 | Hälsodata hämtas via **Health Connect** (`androidx.health.connect:connect-client`), read-only. Ingen Samsung-partner/-licens krävs vid sideload. |
| HLS-2 | Kärndatapunkter: **steg** (`StepsRecord`, dagens summa — när flera källor skrivit steg, t.ex. telefonens pedometer och Galaxy Watch via Samsung Health, summeras stegen **per källa** och den mest kompletta (högsta) källans summa väljs; aldrig en summering över källor (dubbelräkning) eller `COUNT_TOTAL`s per-tidslucke-dedup, som tappade steg när källorna inte överlappade och gjorde att appen visade färre steg än den bärbara enheten), **puls** (`HeartRateRecord`, dagens snitt), **sömn** (`SleepSessionRecord`, senaste natten — används dessutom som filter för vilopulsskattningen, se HLS-7) och **vilopuls** (`RestingHeartRateRecord`, senaste värdet). Övriga datapunkter, inklusive aktiva kalorier, se HLS-8. |
| HLS-3 | Kärnläsbehörigheterna är scopade hälsobehörigheter (`android.permission.health.READ_STEPS`, `READ_HEART_RATE`, `READ_RESTING_HEART_RATE`, `READ_SLEEP`) med runtime-samtycke via Health Connects behörighetsflöde. Ej beviljad behörighet visar en tydlig vy med "Ge åtkomst"-knapp utan krasch. Appen deklarerar en behörighets-rationale-handler i manifestet (`SHOW_PERMISSIONS_RATIONALE` för Android ≤13, `VIEW_PERMISSION_USAGE`-alias med hälso-kategorin för Android 14+) — annars visar Health Connect ingen samtyckesdialog. |
| HLS-4 | Saknas Health Connect på enheten (`HealthConnectClient.getSdkStatus()` = `SDK_UNAVAILABLE`/`SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`) visas en tydlig uppmaning (installera/uppdatera) med knapp till Health Connect i stället för hälsodata. |
| HLS-5 | Hälsodata persisteras **inte** – varken i Firestore eller i exporten (BCK-12/BCK-13) – Health Connect/Samsung Health äger och backar upp datan; Dagboken läser live *(4.0 – 3.x: inte i Drive-backup)*. |
| HLS-6 | Hälsa idag med klockans alla mått nås under **Trender → Klocka** (TRD-20), med Health Connect-status och behörighetsrad (HLS-14) överst. Datapunkter visas som `StatPill` (regel 4); saknad datapunkt visar "—" *(4.0 – 3.x: navigeringskort i Hantera, HANT)*. |
| HLS-7 | **Idag-skärmen** visar ett kompakt hälsokort med **steg** och **vilopuls** som `StatPill` (regel 4) för den dag som är vald i Idag-checklistans datumnavigering (HEM-14) — byter värde när användaren bläddrar till en annan dag, "—" om Health Connect saknar data för den dagen (t.ex. utanför det hämtade 7-dagarsfönstret), när Health Connect är tillgängligt och behörighet beviljad. Vilopulsen (både dagens och tidigare dagars) tas i första hand från senaste `RestingHeartRateRecord`; saknas den (t.ex. Galaxy Watch via Samsung Health som inte skriver posten) **skattas** den i stället från periodens `HeartRateRecord`-prover så att kortet visar en vilopuls i stället för "—". Skattningen görs enbart på de **vakna** proverna: prover som ligger inom en `SleepSessionRecord` sållas bort först (sömnfönstren läses för hela perioden med startgränsen ett dygn bakåt, så att en session som korsar midnatt exkluderas från båda dygnen), eftersom sömnpulsen ligger under den verkliga vilopulsen och annars utgjorde hela lågänden när klockan bars på natten — appen visade då flera slag lägre vilopuls än Health Connect (#154). På de vakna proverna tas medelvärdet av den lägsta 5-percentilen ≈ den lägsta ihållande pulsen; ett enda artefaktlågt prov drar inte ner värdet. Saknas vakna prover helt (klockan bars bara under natten) används hela provmängden hellre än "—". Vilopulsen persisteras inte utan räknas om vid varje läsning (HLS-5), så en ändrad skattning rättar även redan visade historiska dagar så långt Health Connects rådata räcker. Stegtrend och vilopulstrend för senaste 7 dagarna visas i stället i det gemensamma trenddiagrammet, se HEM-17. Saknas Health Connect eller behörighet visas en diskret "Koppla hälsa"-rad som djuplänkar till Hälsa-skärmen (§18) — **ingen** behörighetsbegäran sker på Idag. Kortet laddas fristående och blockerar inte Idag-renderingen. |
| HLS-8 | Hälsa-skärmen visar utöver kärndatapunkterna, grupperade under `SectionHeader` (regel 4): **sömnstadier** för senaste natten (`SleepSessionRecord.stages` — djupsömn, REM, lätt sömn och vaken tid; `STAGE_TYPE_SLEEPING` räknas som lätt sömn och `AWAKE_IN_BED`/`OUT_OF_BED` som vaken, `UNKNOWN` ignoreras; stadieraderna visas bara när natten faktiskt har stadier), **träning idag** (`ExerciseSessionRecord` — antal pass och sammanlagd tid), **aktiva kalorier** (`ActiveCaloriesBurnedRecord`), **sträcka idag** (`DistanceRecord`, kilometer med en decimal i användarens talformat), **syremättnad** (`OxygenSaturationRecord`, snitt över samma 24-timmarsfönster som sömnen eftersom SpO2 mäts under natten) och **blodtryck** (`BloodPressureRecord`, senaste mätningen inom sju dagar — mäts sporadiskt). Kalorier och sträcka väljs **per källa** enligt samma princip som stegen i HLS-2, eftersom telefonen och klockan skriver samma dygn var för sig. **Träningspass** följer inte den regeln: de är diskreta händelser, inte dygnssummor, så två källor kan hålla olika pass. De dedupliceras därför på **tidsöverlapp** — överlappande pass är samma händelse och reduceras till det längsta av dem, medan pass som inte överlappar räknas var för sig oavsett källa. Ett källval per dygn kastade i stället bort allt utom en källas pass. Dessa behörigheter (`READ_EXERCISE`, `READ_ACTIVE_CALORIES_BURNED`, `READ_DISTANCE`, `READ_OXYGEN_SATURATION`, `READ_BLOOD_PRESSURE`) är **valfria**: bara kärnbehörigheterna i HLS-3 avgör om skärmen visar data, och en nekad valfri behörighet gör att just den datapunkten visas som "—" utan att låsa skärmen eller ge felvy — men skärmen säger då **vilka** mått det gäller och erbjuder att begära åtkomsten, se HLS-14. Datapunkter Samsung Health inte skriver till Health Connect (HRV, stress, hudtemperatur, EKG, VO2max) ingår inte — de går inte att nå via något Samsung-API, se spike #56. |
| HLS-9 | Appen begär `android.permission.health.READ_HEALTH_DATA_HISTORY`. Utan den lämnar Health Connect bara ut data från de 30 dagarna före att behörigheten beviljades, vilket tystade Trenders steg- och vilopulsdiagram (TRD-11) för längre perioder trots att perioderna går upp till ett år. Behörigheten är valfri på samma sätt som HLS-8: nekas den fungerar appen som förut med 30 dagars fönster, och att den saknas syns på Hälsa-skärmen enligt HLS-14. |
| HLS-10 | Hälsa-skärmen visar en **sömnkvalitetspoäng 0–100** för senaste natten, sammanvägd av sex delkomponenter med publicerade gränsvärden: **sömnlängd** (vikt 25 %, full poäng 7–8,5 h enligt AASM/SRS-konsensus), **sömneffektivitet** (20 %, full vid ≥90 %, noll vid 75 % — 85 % är gränsen för kliniskt störd sömn och ska därför inte hamna högt på skalan), **regelbundenhet** (20 %, spridningen i sömnens mittpunkt över 14 nätter — full vid ≤30 min; sömnregelbundenhet var en starkare dödlighetsprediktor än sömnlängd i UK Biobank), **djupsömn** (15 %, mot ålders- och könsnorm, se HLS-11, med nollpunkt på halva normens nedre gräns — 0 % vore en orimlig nollpunkt eftersom ingen med fungerande sensor hamnar där, och en natt långt under normen fick då fortfarande halva poängen), **REM** (12 %, 18–25 % av sömntiden) och **vaken tid efter insomnande** (8 %, åldersjusterad full poäng, noll vid 90 min — en och en halv timme vaken är en dålig natt oavsett ålder, så åldersjusteringen sitter i var full poäng slutar, inte i var skalan bottnar). Vikterna följer mätningarnas tillförlitlighet: stadieklassning på konsumentklockor når bara macro-F1 0,26–0,69 mot polysomnografi, så djupsömn och REM väger tillsammans mindre än en tredjedel. Delkomponenter som inte går att räkna ut (natt utan stadier, för få nätter för regelbundenhet) **faller bort och vikterna normaliseras om** i stället för att natten straffas för något som aldrig mättes. Mittpunktsspridningen räknas cirkulärt runt dygnet, så tider strax före och efter midnatt ligger nära varandra. Låg syremättnad (<90 % i snitt) och sovpuls ≥5 bpm över den egna baslinjen visas som **egna varningsrader**, inte som poängavdrag. Poängen persisteras inte (HLS-5) utan räknas om vid varje läsning, och presenteras uttryckligen som ett hälsomått för egen uppföljning — inte en medicinsk bedömning. |
| HLS-11 | Under **inställningsarket → Profil** anges **födelseår** och **kön** (man/kvinna/ej angivet). Enda syftet är åldersnormerna i HLS-10: andelen djupsömn sjunker linjärt med åldern fram till ~60 år och brantare hos män, och vaken tid efter insomnande stiger ~10 min per decennium, så en 55-årings natt får inte mätas mot en 25-årings arkitektur. Utan födelseår visas ingen poäng utan en uppmaning att fylla i det — en poäng mot fel norm vore missvisande. Kön som inte angetts använder ett mellanvärde. Båda värdena ingår i Drive-backupens `settings` (BCK-10) och överlever en rundtur; äldre backupar utan fälten lämnar inställningen orörd. Födelseåret valideras mot samma spann som åldern (120 år bakåt till innevarande år) *(4.0)*. |
| HLS-12 | Hälsodata läses även som **dagshistorik**: för en vald period ger appen ett värde per dygn för samtliga datapunkter i HLS-2 och HLS-8 — steg, dygnssnittspuls, vilopuls, sömnlängd, sömnstadier, träningspass och passtid, aktiva kalorier, sträcka, syremättnad och blodtryck. Per-källa-principen i HLS-2/HLS-8 tillämpas **per dygn** (mest kompletta källan vinner, aldrig en summering över källor); träningspassen dedupliceras i stället på tidsöverlapp inom dygnet, enligt HLS-8. Varje posttyp läses **en gång** över hela perioden och fördelas per dygn i efterhand — en läsning per dag och typ blir tusentals anrop mot Health Connect över ett år. En post hör till det dygn dess starttid faller på; en **natt** dateras efter sömnsessionens slut, så en session över midnatt hör till morgonens datum, och flera sessioner samma natt reduceras till den längsta. Ett dygn utan mätning ger **ingen** datapunkt (lucka), aldrig noll — en nolla vore ett påstående om ett dygn som aldrig mättes. Historiken persisteras inte (HLS-5) utan läses om vid varje visning, och når bara så långt bakåt som `READ_HEALTH_DATA_HISTORY` (HLS-9) tillåter. Steg- och vilopulsdiagrammen (TRD-11) läser samma kärnhistorik, så de aldrig kan visa andra värden än hälsohistoriken. |
| HLS-13 | **Sömnkvalitetspoängen** (HLS-10) beräknas inte bara för senaste natten utan **per natt** över historiken (HLS-12). Regelbundenhetskomponenten räknas **rullande**: varje natt bedöms mot mittpunktsspridningen över de nätter som slutar med den (samma 14-nättersfönster som HLS-10), inte mot ett enda värde för hela perioden — annars skulle en natt i mars bedömas mot hur regelbunden sömnen var i augusti. Sovpulsen räknas per natt medan den vakna baslinjen den jämförs mot delas av perioden. Nätter som inte går att bedöma — saknat födelseår (HLS-11) eller natt utan sömndata — ger en **lucka**, inte en nolla. Övriga delkomponenter, vikter och normaliseringsregler är oförändrade från HLS-10. |
| HLS-14 | En **valfri** behörighet (HLS-8, HLS-9) som inte är beviljad — nekad, eller aldrig begärd därför att den tillkom efter att användaren gav sitt samtycke — visas på Hälsa-skärmen som en egen rad högst upp: vilka mått som saknar åtkomst, och ett tryck som öppnar samtyckesdialogen för **hela** behörighetsuppsättningen (Health Connect visar en dialog för det som ännu inte besvarats). Raden är en klickbar `StatPill` (regel 4) med `Role.Button` och minst 48 dp tryckyta, och renderas inte alls när ingenting saknas. Utan detta går ett mått utan åtkomst inte att skilja från ett mått utan data — båda visas som "—" — och den som gav samtycke före 3.20.0 hade ingen väg tillbaka till dialogen, eftersom den bara nåddes från behörighetsläget som kärnbehörigheterna (HLS-3) styr. Kan behörighetsläget inte läsas visas ingen rad alls: hellre ingen varning än en som inte stämmer. |

---

## 20. Widget (WID) *(hela avsnittet borttaget, #177)*

> Appen hade tre hemskärmswidgets byggda med `androidx.glance` — mediciner, screening och
> vid behov. De togs bort i sin helhet tillsammans med Glance-beroendet: tryckmekaniken
> krävde flera omtag (3.15.0–3.15.4) och landade i att knapparna byggdes på `CheckBox`
> eftersom det var den enda mekanism som mätbart fungerade i releasebygget, och Glance gick
> inte att använda som avsett (`GlanceTheme` olöslig, #156; `ColorProvider(resId)` ett
> internt API som lint underkänner, #175). Underhållskostnaden stod inte i proportion till
> nyttan. All loggning sker numera i appen.

| ID | Krav |
|----|------|
| WID-1 | ~~Medicinwidgeten visar dagens återstående schemalagda doser med sammanfattningsrubrik.~~ *(borttaget, #177)* |
| WID-2 | ~~En medicindos kan bockas av/på direkt från medicinwidgeten.~~ *(borttaget, #177)* |
| WID-3 | ~~Screening kan loggas stegvis från en egen widget (energi → stress → symptom).~~ *(borttaget, #177)* |
| WID-4 | ~~Widgetarna uppdateras efter varje skrivning som kan ändra dagens vyer.~~ *(borttaget, #177)* |
| WID-5 | ~~Widgetarna är på svenska (ÖV-6).~~ *(borttaget, #177)* |
| WID-6 | ~~Varje widget ritar en egen opak bakgrund med explicita textfärger som följer systemets ljusa/mörka läge.~~ *(borttaget, #177)* |
| WID-7 | ~~Appens widgets är uppdelade per handling och kan läggas till oberoende av varandra.~~ *(borttaget, #177)* |
| WID-8 | ~~En vid behov-dos kan loggas som tagen direkt från vid behov-widgeten.~~ *(borttaget, #177)* |


---

## 21. Mediciner-flik (MEDF) *(4.0)*

> Fjärde bottennavflik (NAV-8). Samlar allt som rör medicinbiblioteket; själva dagens
> doser bockas av på Idag (§4, §6.1) och loggade doser läses i Dagbok (§16).

| ID | Krav |
|----|------|
| MEDF-1 | Fliken (stor topprad med avataren, NAV-9) visar **Recept och scheman** med antalet aktiva som pill ("3 aktiva"): recepten vars period inte passerats, efter namn, som postkort (REC-13) med namn och dos som titel, tidpunkter · upprepning · "tills vidare" som undertext, aktiv-reglaget som direktkontroll (REC-5) – ett pausat recept nedtonat med grå accent, ett aktivt med teal – och pills för dagens gällande dos med höjningen inom parentes ("Idag 75 mg (+25)", REC-12), höjningens dagar, perioden ("Period 28 sep – 7 okt", REC-7) och "Slutar idag"/"Slutar i morgon" (sista dosdagen, NOT-12). Utan recept och vid behov-mediciner visas ett tomt läge med Nytt recept och Ny vid behov-medicin. Innehållet motsvarar 3.x Recept & scheman (§6.2). *(4.0 – tidigare lydelse: "Fliken visar **Recept och scheman**: aktiva recept som postkort (NFR-15) med namn, dos, tidpunkter, upprepning och period, aktiv-reglaget som direktkontroll (REC-5), dagens gällande dos med höjning inom parentes (REC-12) och periodetikett (REC-7/REC-8). Innehållet motsvarar 3.x Recept & scheman (§6.2).")* |
| MEDF-2 | En **banner** överst (varningskort med pil) visar periodslut som inträffar idag eller i morgon, idag först – recept vars sista dosdag är den dagen ("Kåvepenin slutar i morgon.") och pågående doshöjningar som tar slut med dosen efteråt ("Höjningen av Sertralin slutar i morgon – sedan 50 mg."), beräknade som NOT-12 – och tryck öppnar det första receptet. *(4.0 – tidigare lydelse: "En banner överst visar periodslut som inträffar idag eller i morgon (NOT-12) och pågående doshöjningar som tar slut, med genväg till receptet.")* |
| MEDF-3 | Sektionen **Vid behov** listar alla vid behov-mediciner efter namn med antalet, namn och dos som titel, kylperiod och dagsgräns som undertext (FAV-4, FAV-5), stjärnan som inline-direktkontroll för favoritmarkering (FAV-2, FAV-8, SET-10) och pil till formuläret. *(4.0 – tidigare lydelse: "Sektionen **Vid behov-mediciner** listar alla vid behov-mediciner med kylperiod och dagsgräns som undertext och stjärnan som inline-direktkontroll för favoritmarkering (FAV-2, SET-10).")* |
| MEDF-4 | Lägg till-knappen **Nytt recept** med **Ny vid behov-medicin** i pilens meny (`AddSplitButton`) finns bara här (NAV-10) och öppnar respektive formulär (REC-1, FAV-1) på `EntityEditScreen`. Receptformuläret byggs i etapp 5.2c; tills dess öppnar Nytt recept, receptkortet, menyns Redigera och bannern en underskärm "Snart här". *(4.0 – tidigare lydelse: "Knapparna **Nytt recept** och **Ny vid behov-medicin** finns bara här (NAV-10) och öppnar respektive formulär (REC-1, FAV-1) på `EntityEditScreen`.")* |
| MEDF-5 | **Avslutade recept** (REC-8: periodens slut passerat, även ett aktivt som dosgenereringen ännu inte avslutat) ligger ihopfällda längst ned med antal (NFR-18), senast avslutade först; utfällda visas de nedtonade med grå accent och pill "Avslutat {datum}" och kan förlängas och återaktiveras i receptformuläret (*planerad*, etapp 5.2c). *(4.0 – tidigare lydelse: "**Avslutade recept** (REC-8) ligger ihopfällda längst ned med antal; utfällda visar de slutdatum och kan förlängas och återaktiveras.")* |
| MEDF-6 | Länken **Logga en dos i efterhand** öppnar dosformuläret med valfritt datum och klockslag (MED-16, FAV-10). |

---

## 22. Ombyggnad och migrering (OMB) *(4.0)*

> Villkor nummer ett för ombyggnaden: **ingen data får tappas.** Se ARKITEKTUR.md → Migrering.

| ID | Krav |
|----|------|
| OMB-1 | Appen 3.27.0 fryses som tagg och branch `legacy`. 4.0 byggs på `master` med samma `applicationId`, så den installeras som uppdatering och Room-filen finns kvar på enheten. |
| OMB-2 | Vid **första start** av 4.0 på en enhet med en 3.x-databas läser appen Room-filen (read-only legacy-läsare) och skriver alla poster till Firestore i batchar. Skärmen visar antal per entitet före och efter och användaren bekräftar innan appen öppnas. Room-filen raderas **aldrig** automatiskt. |
| OMB-3 | Konverteraren `BackupJson` (v1 och v2, inklusive arvsfälten `anteckning` på posterna och `tidpunkt` på receptet) → 4.0-dokument ligger i `:core` och täcks av en fixtur där **varje** fält är satt. Test: konvertera → exportera → fältvis jämförelse. Varje genererat dokument valideras mot `TextLimits` och intervallen i `firestore.rules`; ryms något inte **stoppar** konverteraren med en rapport (dokument och fält, aldrig innehållet) – den kapar eller hoppar aldrig över ett värde. Samma mappning och validering används av legacy-läsaren (OMB-2) och legacyimporten (BCK-14). Utfallet är antingen alla dokument (`Converted`, med rapport över antal per samling och varningar) eller ett stopp (`Stopped`) som listar **alla** fel – aldrig en del. Enda undantaget från stopp är en anteckning vars post inte finns i backupen: den räknas som varning i rapporten (ARKITEKTUR.md → Migrering, punkt 1). |
| OMB-4 | **Grind före etapp 3 och före första release:** en riktig 3.x-backup konverteras, importeras via `tools/db import.mjs`, exporteras igen och jämförs fältvis med originalet. Noll skillnader krävs, och konverterarens validering mot `TextLimits` och rules-intervallen (OMB-3) ska passera utan stopp – admin-importen går förbi rules, så valideringen är det som garanterar att appen kan spara om varje dokument. Konverteringen körs med `./gradlew :core:convertLegacyBackup --args="--in <3.x-backup.json> --out <export.json> --user <uid>"` (exitkod 0 = filen skrevs, 1 = stopp utan fil, 2 = fel argument eller befintlig utfil utan `--force`), rapporten skrivs utan innehåll. Importen i grinden görs mot ett tomt scratch-uid, eftersom `import.mjs` skriver dokumenten som de är och inte slår ihop med befintliga (ARKITEKTUR.md → Migrering, punkt 3). |
| OMB-5 | Saknas Room-fil (ny enhet) erbjuder första starten import från en 3.x Drive-backup eller en lokal JSON (BCK-14), eller att börja tomt. Migreringsskärmen kan vara startdestination (NAV-6). |
| OMB-6 | Varje krav i denna lista som inte är struket är **paritetschecklista**: första release av 4.0 görs först när alla är uppfyllda och bockade av i PR-beskrivningarna. |

---

## 23. Designspråk (DSN) *(4.0)*

> Vald känsla **I · Papper och teal** (canvas, rad 2d). Tokens i `ui/theme`; värden och motiv i
> ARKITEKTUR.md → Designspråk och skill `ui-style`.

| ID | Krav |
|----|------|
| DSN-1 | Ljust tema: pappersyta, vita kort utan kantlinje, **teal** som primärfärg (kryss, knappar, aktiv flik), **solgul** som gör-något-färg (plusknapp, "Snart", dagens punkt med ring i datumremsa, kalender och diagram, framstegsraden när dagen är klar), **terrakotta** för varning (försenat, periodslut). Varje textbärande färg klarar 4,5:1 mot sin bakgrund. Alla reglage är färgade ur energiskalan med det delade `ValueSlider`: där högre är bättre (energi, sömnkvalitet, aktivitetens energi) går spåret från rött till grönt, där högre är sämre (stress, symptom, smärta) från grönt till rött; värdet visas alltid också som text och som tonad pill (Låg/Medel/Hög resp. Lätt/Måttlig/Svår) – färgen bär aldrig informationen ensam *(4.0)*. |
| DSN-2 | Typografi: **Fraunces** (serif) för rubriker, **Figtree** för brödtext; typsnitten bundlas i appen (OFL) och laddas aldrig ned. Namngivna stilar i `AppTypography`; aldrig `fontSize` i feature-kod. |
| DSN-3 | Form och avstånd: kort 22 dp, chips och knappar helt rundade, ark 28 dp upptill, en vald dag i datumremsa och kalender i radens form (18 dp); avstånd bara ur `Spacing`, ikonstorlekar bara ur `IconSize`; tryckytor minst 48 dp. Det som är inaktivt, avstängt eller inte går att välja tonas ned med en och samma nedtoning *(4.0)*. |
| DSN-4 | Rörelse: fjädrande kryss som tonar raden, framstegsrad som fylls animerat, delad skärmövergång; belöningsläget enligt HEM-19. Rörelse förstärker och blockerar aldrig. |
| DSN-5 | Mörkt tema härleds med samma roller på djup skogsgrön botten; kontrast kontrolleras i båda lägena och varje komponent har Roborazzi-referens ljust + mörkt. Teman: ljust, mörkt, auto (SET-1). |
| DSN-6 | Designspråket skiljer sig avsiktligt från ReseApotekets (ingen korall/aprikos, ingen Nunito, serif-rubriker, luftigare kort) även om komponentkatalogen delas. |
| DSN-7 | Appikonen **"Bladet"**: adaptiv ikon med teal bakgrund och ett pappersark med tre textrader och en solgul trendkurva; ett enfärgat lager för temaikoner (Android 13+). Ikonens färger är resurser lika med temats *(4.0, ersätter 3.x-ikonen)*. |
