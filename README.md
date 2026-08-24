# Dagboken – Android

Hälsodagbok för att logga aktiviteter, daglig screening (energi, stress, symptom) och mediciner, med diagram, påminnelser och molnbackup via Google Drive.

**Kravspecifikation:** [KRAVLISTA.md](KRAVLISTA.md) · **Utvecklingsregler:** [CLAUDE.md](CLAUDE.md)

> **Bidrar du (eller en AI-assistent) med kod?** Läs [CLAUDE.md](CLAUDE.md) först. Den
> samlar projektets fyra icke-förhandlingsbara regler som gäller vid varje ändring:
> datasäkerhet (backup/restore), tester på alla nivåer, aktuell kravlista och återbruk
> av delade UI-komponenter. Detaljer ligger som skills i [`.claude/skills/`](.claude/skills/).

---

## Funktioner

- **Aktivitetsloggning** — välj aktivitetstyp (favoriter som chips, övriga via dropdown), tagga som återhämtande/energitjuv, logga energi, stress och duration
- **Daglig screening** — energi- och stressnivåer (0–10), symptomloggning med svårighetsgrad, koppling till måltidshändelse
- **Symptom & aktivitetstyper** — konfigurerbara listor med favoriter, döp om och ta bort; favoriter visas framträdande i loggvyn
- **Mediciner** — schemalagda doser (dagliga, veckodagar, intervall), engångsdoser, vid-behovs-favoriter med kyldownregler och maxdos per dag
- **Händelselogg** — logga hälsohändelser med typ, svårighetsgrad, varaktighet, triggers och åtgärder
- **Diagram** — energi- och stresstrender över valbar tidsperiod
- **Påminnelser** — medicinnotiser 15 min i förväg, konfigurerbara screeningpåminnelser per måltidstillfälle
- **Backup** — automatisk daglig säkerhetskopiering till Google Drive, import från backup-fil eller Drive
- **Tema** — mörkt/ljust/auto med konfigurerbar dag- och kvällstid

---

## Kom igång

### Förutsättningar

- Senaste Android Studio (stable channel)
- JDK 17
- Android SDK API 35 (compileSdk), minSdk 30

### Bygg och kör

```bash
git clone https://github.com/ParTee71/Dagboken.git
cd Dagboken
./gradlew :app:assembleDebug
```

Öppna projektet i Android Studio och kör på en enhet eller emulator (API 30+).

### Google Services

Appen kräver en `google-services.json` från Firebase Console (Firebase Auth + Google Sign-In). Placera filen i `app/`. Filen är git-ignorerad och delas inte i repot.

---

## Releasebygge och signering

Releasebygget kräver en keystore och lösenord via `local.properties` (git-ignorerad) eller miljövariabler:

**local.properties:**
```properties
signing.storePassword=<lösenord>
signing.keyAlias=dagboken
signing.keyPassword=<lösenord>
```

**Miljövariabler (CI):**
```
SIGNING_STORE_PASSWORD
SIGNING_KEY_ALIAS
SIGNING_KEY_PASSWORD
```

Keystorefilen `dagboken.jks` placeras i `app/` och är git-ignorerad.

---

## Arkitektur

```
app/
├── data/
│   ├── auth/          FirebaseAuthRepository – Google Sign-In via Credential Manager
│   ├── datastore/     PreferencesRepository – inställningar, teman, screeningtider, symptom/aktivitetstyper
│   ├── migration/     DriveBackupRepository – import/export mot Google Drive
│   ├── repository/    AktiviteterRepository, MedicinerRepository, HandelserRepository
│   └── room/          Room-databas, DAOs, entiteter
├── di/                Hilt-moduler (AppModule, DatabaseModule, …)
├── domain/
│   ├── model/         Domänmodeller (Aktivitet, Medicin, Recept, Favorit, Handelse, …)
│   └── usecase/       EnsureTodayEntriesUseCase, DosLimitUseCase, CheckCooldownUseCase, …
├── notifications/     AlarmScheduler, påminnelsemottagare (medicin + screening)
├── ui/
│   ├── aktiviteter/   LoggaTab, ScreeningTab, HistorikTab – loggning och screening
│   ├── components/    Delade komponenter: SymptomLogCard, Foldout, GradientSliderRow, DateTimeRow, …
│   ├── diagram/       Trenddiagram (LineChartCanvas)
│   ├── handelser/     Händelselogg – logga hälsohändelser med triggers och åtgärder
│   ├── home/          Hemskärm med dagens mediciner och screeningstatus
│   ├── mediciner/     Mediciner, recept, favoriter och vid-behov-doser
│   ├── migration/     Importguide från Google Drive-backup
│   ├── settings/      Inställningar, tema, notiser, symptomtyper, aktivitetstyper
│   └── theme/         Material 3-tema (färger, typografi, animationer)
└── worker/            BackupWorker – schemalagd Drive-backup
```

**Stack:** Kotlin · Jetpack Compose + Material 3 · MVVM · Hilt · Room · DataStore · Firebase Auth · Google Drive API · WorkManager · kotlinx.serialization

### Datamodell: SymptomOption

Symptom och aktivitetstyper lagras som `List<SymptomOption>` i DataStore med stöd för favoriter:

```kotlin
@Serializable
data class SymptomOption(val name: String, val isFavorite: Boolean = false)
```

DataStore-migrering hanteras transparent: befintliga `List<String>`-värden migreras automatiskt till det nya formatet vid läsning.

---

## CI

GitHub Actions kör vid push/PR mot `master`:
- `:app:compileDebugKotlin`
- `:app:compileDebugUnitTestKotlin`
- `:app:compileDebugAndroidTestKotlin`
- `:app:testDebugUnitTest`

Se [.github/workflows/android.yml](.github/workflows/android.yml).

---

## Versionshistorik

| Version | Innehåll |
|---|---|
| 3.26.1 | Klockans träningspass syntes inte. Två fel: dels visades ett pass aldrig när den valfria behörigheten `READ_EXERCISE` inte var beviljad — vilket den inte var för den som gav sitt samtycke före 3.20.0, då behörigheten ännu inte fanns — och appen hade ingen väg tillbaka till samtyckesdialogen, eftersom den bara nåddes från behörighetsläget som kärnbehörigheterna styr. Måttet visades som ”—”, omöjligt att skilja från ett dygn utan pass. Detsamma gällde aktiva kalorier, sträcka, syremättnad, blodtryck och historiken bortom 30 dagar. Hälsa-skärmen räknar nu upp vilka mått som saknar åtkomst och öppnar dialogen vid tryck (#219, HLS-14). Dels valdes **en enda källa** per dygn för träningspassen — den med längst sammanlagd passtid — varpå övriga källors pass kastades. Passen dedupliceras nu på **tidsöverlapp** i stället: överlappande pass är samma händelse och räknas en gång, medan två pass som inte överlappar räknas var för sig oavsett källa (#220). Per-källa-urvalet är kvar för steg, kalorier och sträcka, där varje källa är en dygnssumma av samma aktivitet och en summering vore dubbelräkning |
| 3.26.0 | **Klockdatan får historik och jämförelser i Trender** (#188). Appen läser nu ett värde per dygn för samtliga Health Connect-mått över hela den valda perioden — sömnlängd och sömnstadier, dygnssnitts- och vilopuls, träningspass, aktiva kalorier, sträcka, syremättnad och blodtryck — med ett enda svep per posttyp i stället för en läsning per dag, så en årsperiod inte blir tusentals anrop (#190, HLS-12). Per-källa-principen tillämpas per dygn, så telefonen och klockan aldrig summeras ihop, och sömnkvalitetspoängen räknas per natt med rullande regelbundenhet i stället för ett enda värde för hela perioden (HLS-13). Ovanpå det ligger fyra nya sätt att läsa datan: **egna historikdiagram** för sömn, sömnkvalitet, träning, kalorier, sträcka, syremättnad och blodtryck (#191, TRD-15), **sömnstadier som staplat stapeldiagram** — en stapel per natt delad i djup, REM, lätt och vaken, så nätternas sammansättning går att jämföra och inte bara deras längd (#193, TRD-16), ett **jämförelsediagram** där klockdata och loggade dagboksserier kan överlagras med varje serie indexerad 0–100 mot sitt eget min/max, eftersom en rak överlagring av poäng och timmar inte säger något (#194, TRD-15/TRD-17), och tillvalet **Föregående period** som lägger den föregående lika långa perioden som en nedtonad kurva på samma x-index — det är formerna och inte datumen som jämförs (#195, TRD-18). Trenders diagramkort är dessutom **ihopfällbara och stängda som standard** (#189, TRD-14, NFR-18): ett stängt kort komponerar inte sitt diagram alls, så skärmen inte längre ritar upp ett dussin diagram man ändå ska scrolla förbi, och hela titelraden växlar utfällt läge — tillåtet på ett sektionskort, till skillnad från postkortet (NFR-16), eftersom det inte har någon konkurrerande primär åtgärd |
| 3.25.2 | Listraderna följer nu en egen radstandard (#203, sista delen av #197): hela medicinraden på Idag är tryckyta för att bocka av dosen — tidigare var bara den lilla ikonen till höger klickbar, trots att raden ser tryckbar ut — och ikonen är numera en ren indikator som skärmläsaren inte läser upp en gång till. Raderna exponerar sitt tillstånd ("Tagen"/"Ej tagen", "Loggad"/"Ej loggad") för TalkBack. I Hantera byter tryck namn på en symptom- eller aktivitetsrad medan Byt namn och Ta bort flyttat till radens kontextmeny, och en vid behov-rad favoritmarkeras genom tryck var som helst på raden (NFR-17, MED-2, HANT-6) |
| 3.25.1 | Vid behov-favoriterna på Idag följer kortstandardens undantag för chip-kort (#202, del av #197): tryck loggar fortfarande en dos direkt, men långtryck öppnar **alltid** kontextmenyn. Tidigare gjorde långtryck olika saker beroende på vilka callbacks anroparen råkade skicka in — meny i ett fall, hoppa direkt till redigering i ett annat — vilket bara var osynligt i dag för att appens enda anropare skickar alla. Menyn byggs nu av samma `EntryAction`-lista och menykomponent som postkortens, så ordning, ikoner och färger inte kan glida isär (HEM-11, NFR-15) |
| 3.25.0 | **Sjukdomsincheckningar går att redigera** (#192, del av #197). En felskriven svårighetsgrad eller symptomgradering krävde tidigare att posten raderades och loggades om — nu öppnar ett tryck på incheckningskortet samma formulär förifyllt, och sparandet skriver över samma post med `id` och ursprunglig tidsstämpel bevarade (SJ-11). Även episodkortet på detaljskärmen går att redigera: rutten fanns sedan tidigare men anropades aldrig från något UI, så typ, startdatum och anteckning gick inte att ändra efter att episoden skapats (SJ-12). Sjukdomsytans tre kort följer nu kortstandarden med meny, svep-radera och anteckningsikon (SJ-13), och de två kopierade svep-wrapparna är borta — inklusive buggen där ett avbrutet raderingsval lämnade kortet osynligt i listan |
| 3.24.4 | Receptkorten i Recept & scheman följer kortstandarden (#201, del av #197): tryck **redigerar** receptet i stället för att fälla ut det — utfällningen ligger på chevron-knappen — och korten får långtrycksmeny, Aktivera/Avaktivera som menyval och svep-radera med bekräftelse (REC-13). Aktiv-reglaget är kvar som direktkontroll och en receptanteckning visas nu med anteckningsikonen i stället för som text längst ned i det utfällda innehållet. Den delade `DagbokenEntryCard` fick två nya valfria slots för detta: en stödrad under undertiteln (periodetikett och dagens doshöjning, som behöver egna färger) och nedtoning av ett inaktivt kort |
| 3.24.3 | Aktivitets- och screeningkorten under loggningsformulären följer kortstandarden (#200, del av #197): tryck **redigerar** posten i stället för att fälla ut symptomen — utfällningen ligger nu på chevron-knappen — och ett kort utan symptom går att trycka på över huvud taget, vilket det inte gjorde förut. Korten får dessutom långtrycksmeny och svep-radera, precis som historikposterna (AKT-10) |
| 3.24.2 | Historikposterna följer kortstandarden (#199, del av #197): posten har nu en `⋮`-knapp som ger samma meny som långtryck, menyn innehåller **Redigera** — tidigare bara "Ta bort", trots att tryck på kortet redan redigerade — och posten kan svepas bort åt vänster med bekräftelse (HIST-5). Kortet byggs med den delade `DagbokenEntryCard`, så all lokal meny- och svep-kod i `HistorikScreen` är borta |
| 3.24.1 | Grunden för en enhetlig kortstandard (#197): den nya delade `DagbokenEntryCard` äger postkortens hela interaktionsmönster — tryck öppnar posten, långtryck ger samma kontextmeny som `⋮`, svep från höger till vänster begär radering och chevron-knappen expanderar detaljer, med fast ordning på trailing-ikoner och menyval (NFR-15, NFR-16). Svepet returnerar `false` från `confirmValueChange`, så kortet fjädrar tillbaka tills bekräftelsedialogen svarat i stället för att animeras bort direkt — det som tidigare kunde lämna ett osynligt "spökkort" i listan när dialogen avbröts. `DagbokenCard` har fått `onClickLabel`/`onLongClickLabel` för skärmläsaren. Rent internt — ingen yta använder komponenten ännu, den migreras en i taget i #199–#203 och #192 |
| 3.24.0 | Kvälls- och nattmedicinen var osynlig hela dagen på Idag: en dos vars schemalagda tid ännu inte nåtts räknades som "kommande" och göms bakom "Visa kommande" (MED-13), så kvällsdosen dök upp först 19:00 och nattdosen 22:00 — den gick inte att se eller bocka av i förväg. Nu göms bara doser som ligger **mer än 3 timmar fram**; en dos vars tid närmar sig listas tillsammans med de aktuella och försenade, märkt "Snart" så att den inte förväxlas med en dos som redan är att ta (MED-1, MED-13). Dessutom: linjediagrammet kunde krascha när en serie tömdes — modellen töms efter kompositionen, så en omritning däremellan slog upp en linje ur en tom lista. Uppföljning till #141, som bara täckte att den gamla linjen faktiskt rensas |
| 3.23.0 | Medicinpåminnelserna hämtade sina tider ur screeningkonfigurationen, så notisen "Dags för medicin – Efter frukost" kom när det var dags för screening — med screeningens etikett, screeningens på/av-reglage och ofta utan någon dos att ta. Varje medicintidpunkt (Morgon, Förmiddag, Lunch, Eftermiddag, Kväll, Natt) har nu ett eget reglage och en egen tid under Hantera → Notifikationer, larmet går 15 minuter före den tidpunkten, notisen listar bara doserna för just den tidpunkten och postas inte alls när inget är kvar att ta (NOT-2, NOT-3, NOT-17, NOT-18). Screeningpåminnelsen tystas nu bara för den måltidshändelse som faktiskt är loggad — tidigare räckte en morgonscreening för att tysta lunch, kvällsmat och läggdags (NOT-19). Medicintiderna följer med i Drive-backupen |
| 3.22.0 | En dosperiod på ett recept är nu en **doshöjning**: värdet läggs till grunddosen i stället för att ersätta den, och anges alltid i receptets egen enhet — enheten kan inte längre väljas separat, så en höjning kan bara göras på samma sorts dos som receptet redan har (REC-9). Överallt där dosen visas visas den totala dosen för dagen: Idag-checklistan, Recept & scheman (dagens dos med höjningen inom parentes) och medicinpåminnelsen, som nu listar dagens ej tagna doser med namn och dos i stället för bara "Glöm inte att ta dina mediciner" (REC-12, NOT-17) |
| 3.21.1 | Sömnkvalitetens nedre svansar var för snälla (HLS-10). En verklig natt med 17 minuters djupsömn, 60 minuter vaken och 88 % effektivitet fick 89 poäng — Samsung Health gav 77 för samma natt. Orsaken var gränsvärdena i botten av skalan, inte vikterna: djupsömnens nollpunkt låg på 0 %, vilket är en omöjlig nollpunkt eftersom ingen med fungerande sensor hamnar där, så 3,9 % djupsömn gav ändå 55 poäng. Nollpunkten ligger nu på halva åldersnormens nedre gräns, vaken tid ger noll vid 90 minuter i stället för 120, och effektiviteten bottnar vid 75 % i stället för 70 % så att den kliniska 85-procentsgränsen inte längre ger 75 poäng. Samma natt ger nu 81. Vikterna och åldersnormerna är oförändrade — bara var skalan bottnar |
| 3.21.0 | **Sömnkvalitet 0–100** på Hälsa-skärmen (HLS-10): en sammanvägd poäng av sömnlängd, sömneffektivitet, regelbundenhet, djupsömn, REM och vaken tid, med gränsvärden hämtade ur publicerad sömnforskning — AASM/SRS för längden, den kliniska 85-procentsgränsen för effektiviteten och Ohayons åldersnormer för stadierna. Vikterna följer hur tillförlitlig varje mätning är: stadieklassning på konsumentklockor når bara macro-F1 0,26–0,69 mot polysomnografi, så djupsömn och REM väger tillsammans mindre än en tredjedel, medan regelbundenheten väger tungt — den var en starkare dödlighetsprediktor än sömnlängd i UK Biobank och bygger bara på tidpunkter, som klockan mäter bra. Spridningen i sömnens mittpunkt räknas cirkulärt runt dygnet, så 23:50 och 00:10 ligger 20 minuter isär och inte tolv timmar. Komponenter som inte går att räkna ut faller bort och vikterna normaliseras om, i stället för att natten straffas för något som aldrig mättes. Låg syremättnad och förhöjd sovpuls visas som egna varningsrader, inte som poängavdrag. Ny sektion **Hantera → Profil** med födelseår och kön (HLS-11), som enbart styr åldersnormerna — djupsömnen sjunker med åldern och brantare hos män — och som följer med i Drive-backupen |
| 3.20.0 | Hälsa-skärmen läser mer från Health Connect (#180): **sömnstadier** för senaste natten (djup, REM, lätt, vaken), **träning idag** (antal pass och sammanlagd tid), **aktiva kalorier**, **sträcka**, **syremättnad** och **blodtryck** (HLS-8). Anledningen är att Samsung Health redan skriver dessa typer från Galaxy Watch 7 till Health Connect — appen läste bara fyra av dem. De nya behörigheterna är valfria: nekas en av dem visas den datapunkten som "—" i stället för att låsa hela skärmen, och bara kärnbehörigheterna (steg, puls, vilopuls, sömn) avgör om skärmen visar data alls. Kalorier, sträcka och träningspass väljs per källa enligt samma princip som stegen, så telefonen och klockan inte dubbelräknas. Appen begär dessutom `READ_HEALTH_DATA_HISTORY` (HLS-9) — utan den lämnade Health Connect bara ut 30 dagar bakåt, vilket tystade Trenders steg- och vilopulsdiagram för längre perioder. Vägvalet Health Connect framför Samsung Health Data SDK omprövades i samma veva och står fast (se §19 och #56) |
| 3.19.1 | Idag-skärmen är uppdelad (#176): `HomeScreen.kt` var 1072 rader och skickade `AktiviteterViewModel`/`MedicinerViewModel` ned i composable-trädet, vilket band varje kort till hela Hilt-grafen. Korten ligger nu i `IdagChecklistCard.kt`, `HealthCards.kt`, `FavoriterRow.kt` och `WeekSummaryCard.kt`, och tar tillstånd + lambdor via `ScreeningFormBinding`/`VidBehovBinding` i stället för ViewModels. `HomeScreen` är enda stället som håller ViewModels. Rent internt — inget synligt beteende ändras |
| 3.19.0 | Hemskärmswidgetarna tas bort helt, tillsammans med Glance-beroendet (#177). De tre widgetarna — mediciner, screening och vid behov — krävde flera omtag för att tryck över huvud taget skulle fungera i releasebygget (3.15.0–3.15.4) och landade i knappar byggda på `CheckBox`, den enda mekanism som mätbart fungerade. Glance gick heller inte att använda som avsett: `GlanceTheme` var olöslig (#156) och `ColorProvider(resId)` visade sig vara ett internt API som lint underkänner (#175). Underhållskostnaden stod inte i proportion till nyttan. All loggning sker nu i appen; WID-1…8 är strukna ur kravlistan. `LogVidBehovDosUseCase` och `BuildScreeningAktivitetUseCase` är kvar — de är fortfarande appens enda skrivvägar för respektive loggning |
| 3.18.0 | Genomgång av data- och UI-lagret, med åtgärder för fynden. Påminnelser: medicinlarmet schemalägger om sig själv och larmen sätts om även efter en appuppdatering, så påminnelser inte längre kan tystna (NOT-14); efter en återställning schemaläggs larmen direkt (NOT-15); notisbehörigheten begärs när en påminnelse slås på, och Hantera visar när notiser eller exakta larm är blockerade (NOT-16). Backup: exportmappningen ligger i `BackupAssembler` och rundturstestas hela vägen domän → JSON → domän, appinställningar ingår i backupen (BCK-10) och sjukdomsdata importeras i samma transaktion som resten. Dataförlust: DataStore-avkodningen kan inte längre tyst ersätta egna alternativ med standardlistan, formulär navigerar först när skrivningen är klar (NFR-12), anteckningar raderas tillsammans med sin post oavsett skärm (DAT-4) och namnbyte på ett alternativ följer med till redan loggade poster (SET-11). Dessutom: Health Connect läser alla sidor i stället för bara den första, widgetarna uppdateras efter varje skrivning och följer systemets tema (WID-4, WID-6), Historik läser ett begränsat fönster bakåt (HIST-8), diagram har skärmläsarbeskrivning och tryckytor håller 48 dp (NFR-14), och loggning strippas i release (NFR-13) |
| 3.17.0 | Recept kan nu gälla under en **period** i stället för bara tills vidare: startdatum plus längd i dagar eller t.o.m.-datum (REC-7). När perioden passerats slutar receptet generera doser och markeras automatiskt som avslutat, utan att raderas (REC-8). Ett recept kan dessutom ha flera **dosperioder** som tillfälligt ersätter grunddosen, t.ex. nedtrappning i flera steg (REC-9) — otagna doser för idag och framåt uppdateras när perioden ändras (REC-10). Dagen innan en period eller dosändring tar slut kommer en påminnelse i medicinkanalen, med konfigurerbar tid under Hantera → Notifikationer (NOT-12, NOT-13). Databasen migreras till version 11 |
| 3.16.0 | Diagram: y-axelns gränser, gridlinjer och etiketter är nu alltid heltal — även när de räknas fram ur beräknad data (t.ex. dagsgenomsnitt) — i stället för att ibland avrundas till decimalsteg som 0,5 (TRD-7, delad `computeSmartYAxis`). Varje visad dataserie ritar nu en streckad linjär trendlinje i seriens färg, i alla diagram i appen (nytt TRD-13, delad `computeTrendLine`) — `IntervalBarChart`s skärmläsarbeskrivning anger trendens riktning och Trenders kategoridiagram har en egen legendrad för den |
| 3.15.4 | **Grundorsaken bakom alla tidigare försök:** widgetarnas skrivningar till Glance-state har aldrig sparats. `updateAppWidgetState(context, glanceId) { prefs -> … }` tar `suspend (MutablePreferences) -> Unit`, så `prefs` *är* objektet som persisteras — men varje action gjorde `prefs.toMutablePreferences().apply { … }`, muterade en kopia och slängde den (lambdans returvärde coercas till `Unit`). Kompilerade rent, men ingen screening- eller vid behov-skrivning nådde disk. `ActionCallback`-erna har alltså körts hela tiden; tryckräknaren visade 0 därför att den använde exakt samma trasiga mönster — instrumentet mätte sig självt trasigt, vilket ledde felsökningen mot tryckmekanismen genom 3.15.0–3.15.3. Alla mutationer ligger nu som rena extensions på `MutablePreferences`, enhetstestade mot `mutablePreferencesOf()` |
| 3.15.3 | Screening- och vid behov-widgetens knappar går äntligen att trycka på: de byggs nu på `CheckBox`, den enda tryckmekanism som mätbart fungerar i release-bygget. Tryckräknaren visade att `GlanceModifier.clickable` aldrig nådde fram till någon `ActionCallback` — varken på `Text` eller `Box`, med eller utan keep-regler för `androidx.glance.**` — medan medicinchecklistans `CheckBox` alltid fungerade. Knapparna ser därför ut som kryssrutor tills en snyggare primitiv är verifierad på samma sätt |
| 3.15.2 | Fix (försök 3, två oberoende orsaker): widgetknappar med `clickable` reagerade inte på tryck alls — keep-regeln i 3.15.0 täckte appens widget-klasser men inte **Glances egna** trampolin-komponenter, som dess biblioteksmanifest deklarerar via klassnamn och R8 därför kunde byta namn på; `CheckBox` gick via en annan intern väg och fungerade, vilket förklarade asymmetrin. Dessutom: i en `LazyColumn` blir varje rad ett RemoteViews-collection-item där per-rad-klick kräver PendingIntent-template — bara första radens kryssruta reagerade. Alla widgetlistor använder nu vanlig `Column` |
| 3.15.1 | Fix (försök 2): screening- och vid behov-widgetens knappar reagerade inte på tryck — klicket satt på en `Text` med `clickable` inklämd mitt i modifier-kedjan, medan medicinwidgetens fungerande `CheckBox` är en riktig RemoteViews-compound-knapp. Knappen byggs nu som en `Box` med `clickable` först i kedjan. Innehåller tillfällig tryckräknare i widgetrubriken för att kunna avgöra var felet sitter om det kvarstår |
| 3.15.0 | Fix: widgetknappar (screening, vid behov) svarade inte på tryck i release-builden — R8 saknade keep-regel för Glances reflektionsbaserade widget-/actionklasser och strippade/döpte om dem tyst. Medicinwidgeten döljer nu avbockade doser direkt i stället för att visa dem kvar avbockade (WID-1). Vid behov-widgeten visar favoriter direkt med en "Fler"-rad som expanderar till alla vid behov-mediciner, favoriter först (WID-8) |
| 3.14.0 | Hemskärmswidgeten delas upp i tre fristående widgets — **mediciner**, **screening** och **vid behov** — som kan läggas till oberoende av varandra (WID-7). Screeningwidgeten visar nu nästa ej loggade screeningtillfälle och sparas under rätt namn så den markerar tillfället som klart i Idag-vyn (tidigare sparades widget-screeningar alltid som det generiska namnet "Screening" och matchade inget tillfälle). Ny **vid behov-widget**: logga en favoritmarkerad dos direkt från hemskärmen, inklusive cooldown-bekräftelse och dagsgräns (WID-8, FAV-2) |
| 3.13.0 | Widget: fixade texten som försvann mot hemskärmstapeten (widgeten ritar nu en egen opak bakgrund med explicita textfärger i stället för att förlita sig på systemets standardfärger, WID-6). Framsidan är omlagd till en kompakt statusvy — screeningstatus samt en medicinsammanfattning ("X/Y tagna", ev. "Z försenade") — den fullständiga checklistan nås med ett tryck och en väg tillbaka (WID-1) |
| 3.12.0 | Hemskärmswidgeten kan nu logga dagens **screening** stegvis (energi → stress → symptom) direkt från hemskärmen, utan att öppna appen — samma mappning som appens screeningformulär (`BuildScreeningAktivitetUseCase`, WID-3, §20). Symptomsteget visar bara favoritmarkerade symptom för att rymmas i widgetytan; redan loggad screening idag visas som bekräftelse i stället för startknappen. Del 2 av #120 (#157) |
| 3.11.0 | Ny **hemskärmswidget** (Glance): visar dagens medicinchecklista och låter mediciner bockas av direkt från hemskärmen utan att öppna appen — samma skrivväg som Idag-checklistan (WID-1/2/4/5, §20). Del 1 av #120 (infrastruktur + medicinavbockning); screeningloggning från widgeten (WID-3) kommer i en uppföljande leverans (#120) |
| 3.10.1 | Fix: vilopulsen (Idag-kortet och Trender-diagrammet) visades flera slag för lågt jämfört med Health Connect — fallback-skattningen räknade på **alla** dygnets `HeartRateRecord`-prover, och med klockan buren på natten bestod hela lågänden av djupsömnsprover. Prover inom en `SleepSessionRecord` sållas nu bort innan den lägsta 5-percentilen beräknas; sömnfönstren läses för hela perioden med startgränsen ett dygn bakåt så att en session som korsar midnatt exkluderas från båda dygnen (HLS-7). Vilopulsen persisteras inte utan räknas om vid varje läsning, så även redan visade historiska dagar rättas (#154) |
| 3.10.0 | Historik: medicinposter visar nu endast faktiskt **tagna** doser i stället för schemalagda/planerade (HIST-7) — tidsetiketten är tagningstidpunkten (`tagenTid`, MED-14), inte receptets slottid. Redigering av en historikpost gäller nu **endast den enskilda tagningen** (datum, tagningstid, dos, enhet, tagen-status) i stället för receptets/favoritens fält (MED-15) — namn och tidpunktsslot visas read-only för receptgenererade doser. Vid behov-doser kan nu loggas **i efterhand** med valfritt datum och klockslag, både från "Ny medicin" och favoritens långtrycksmeny ("Logga i efterhand", FAV-10/MED-16); cooldown och dagsgräns räknas mot den valda tidpunkten. Room-schema v9 → v10 (#152) |
| 3.9.1 | Fix: Trenders linjediagram (`LineChartCanvas`) startade ibland inzoomade i stället för att visa hela den valda perioden — berodde på Vicos standard-zoomnivå, som för dataset med många punkter kunde välja en fast, mer inzoomad nivå. Sätts nu explicit till att alltid visa allt innehåll (`Zoom.Content`) (TRD-10) |
| 3.9.0 | Trender: periodväljaren flyttas från en gemensam kontroll till en **egen väljare per diagram** (övre högra hörnet av varje diagramkort) — alla sex diagram kan nu visa olika perioder samtidigt (TRD-3). Fix: zoom/panorering på linjediagrammen (`LineChartCanvas`) återställdes inte vid periodbyte, trots att det var tänkt (TRD-10). Period- och serieväljarnas dropdown-knappar är nu kompaktare (delad `CompactDropdownButton`, TRD-12). Ny delad `DagbokenCard`-parameter `titleTrailing` för innehåll i kortets övre högra hörn (#149) |
| 3.8.1 | Fix: Hantera-sidopanelens sektionsikoner var inte scrollbara på de flesta telefoner — de nedersta ikonerna gick inte att nå (HANT-5). Fix: Trender-ytan saknade helt diagram för Steg och Vilopuls (Health Connect) — visas nu som egna periodväljbara diagram (TRD-11). Fix: "+"-FAB på Idag saknade ett sätt att logga en fristående screening — nytt snabbval "Logga screening" (HEM-8b) (#146) |
| 3.8.0 | Trender: alla fyra diagram får tvåfingerzoom + panorering, samma känsla överallt — linjediagrammen via Vicos inbyggda zoom, "Energi (dag)" via egen handrullad gest-hantering. De fyra periodknapparna (7/14/30/90 dagar) ersätts av en gemensam dropdown: 7 dagar / 14 dagar / Månad / 3 månader / **Allt** (nytt — ingen nedre datumgräns) (#144) |
| 3.7.1 | Trender: "Energi (dag)"-diagrammets dagsvärden förbinds nu med en mjuk bezier-kurva (samma stil som övriga diagram, TRD-6), och diagrammet ritar horisontella värdelinjer vid jämna steg mellan lägsta och högsta axelvärde (`computeSmartYAxis`, delat steg) så mellanliggande energivärden går att avläsa direkt (#141) |
| — | Fix: fokuserat textfält (t.ex. anteckning) skymdes av tangentbordet på skrollbara redigeringsskärmar — delad `DagbokenScaffold` inkluderar nu `WindowInsets.ime` i `contentWindowInsets` (NFR-11, #145) |
| 3.7.0 | **Trender:** diagrammen delas upp per kategori (Energi (dag) / Energi per tillfälle / Stress & belastning / Symptom) i stället för ett gemensamt diagram med gemensam y-skala — varje kategori skalas nu efter sina egna aktiva serier (#141). Ny "Energi (dag)": ett intervall-/spannstapeldiagram (`IntervalBarChart`) som per dag visar lägsta–högsta loggade screeningenergi med dagsvärdet markerat, delad uträkning (`computeDailyEnergyStats`) med Idag-energidiagrammet. Alla diagram i appen (Trender + Idag) visar nu alltid sitt lägsta och högsta värde som text under diagrammet (`MinMaxCaption`) |
| 3.6.0 | **Idag:** hälsokortet delas upp — steg och vilopuls följer nu checklistans valda dag i stället för att alltid visa dagens datum; stegtrend, vilopulstrend och energitrend slås ihop i ett gemensamt diagramkort (#138/#140). Kommande mediciner (schemalagd tid ej nådd) döljs nu bakom en "Visa kommande"-toggle, analogt med tagna doser (#139). Diagram: y-axeln skalas nu smart efter de aktiva värdenas min/max i stället för att alltid utgå från 0 — gäller Trender (`LineChartCanvas`) och Idag-diagrammen (delad `SparklineChart`). Ett smalt värdeband fyller nu diagramhöjden i stället för att klämmas ihop i toppen (#136/#137) |
| 3.5.1 | Fix: stegantalet på Idag/Hälsa visade färre steg än enheten — `COUNT_TOTAL` de-duplicerade per tidslucka och tappade steg när flera källor (telefon + klocka via Samsung Health) inte överlappade; summeras nu per källa och den mest kompletta källan väljs. Fix: vilopulsskattningen (när `RestingHeartRateRecord` saknas) låg för högt — skattas nu som medelvärdet av lägsta 5-percentilen ≈ lägsta ihållande pulsen (#135) |
| 3.5.0 | Fix: stegantalet på Idag/Hälsa dubbelräknades när flera källor (t.ex. telefon och klocka) skrev överlappande stegposter — läses nu via Health Connects aggregeringsmotor i stället för en rå summering. Nytt: vilopulstrend för senaste 7 dagarna på Idag-hälsokortet, delad `SparklineChart` (#134) |
| 3.4.1 | **Idag:** checklistan för mediciner/screening/vid behov-mediciner grupperas nu visuellt i ett gemensamt kort, hälsokortet flyttat till energisektionen (#130). Fix: vilopuls på Idag skattas från veckans pulsdata (5:e percentilen) när `RestingHeartRateRecord` saknas (t.ex. Galaxy Watch via Samsung Health) i stället för att visa "—" (#131). Fix: energidiagrammet (HEM-7) och stegtrenden (HLS-7) på Idag visar nu värden på både x- och y-axeln — delad `SparklineChart` fick riktiga axlar (#133) |
| ~~3.4.0~~ | *(taggad felaktigt mot en gammal commit av misstag — samma innehåll som 3.4.1 ovan, inget APK publicerat)* |
| 3.3.1 | Fix: "Ge åtkomst" på Hälsa-skärmen öppnar nu Health Connects samtyckesdialog — appen deklarerar den behörighets-rationale-handler som Health Connect kräver (`SHOW_PERMISSIONS_RATIONALE` / `VIEW_PERMISSION_USAGE`), annars hände inget vid tryck (#128) |
| 3.3.0 | **Hälsa (Health Connect):** ny Hälsa-skärm som läser steg, puls och sömn read-only via Health Connect, nås via kort i Hantera (#54/#56/#57); hälsokort på Idag med stegtrend (7 dagar, delad `SparklineChart`) och vilopuls (#124). Stabil `connect-client` 1.1.0, compileSdk 36 (#127). **Idag:** datumnavigering till tidigare dagar för mediciner/screening/händelser (#115). **Diagram:** bättre mörkt tema-kontrast samt mjuka kurvor med gradientfyllning (#125) |
| 3.2.0 | Historik: kalendervy som växlingsbart komplement till listvyn (HIST-6, delad `DagbokenCalendar`-komponent). Trender- och Hem-diagrammen (`LineChartCanvas`, `SparklineChart`) byggs nu på Vico i stället för handrullad Canvas-kod. Spara-knappen (`SaveButton`) är nu grön (`Emerald400`) i stället för tonal (#107) |
| 3.1.0 | Historik: radera poster (aktivitet/screening/medicindos/händelse/sjukdomsincheckning) via långtryck (#105). Generaliserad spara-knapp (`SaveButton`) på alla formulär, inaktiverad tills osparade giltiga ändringar finns (dirty-state); bekräftelsedialog vid navigering bort med osparat (#104) |
| 3.0.0 | UX-omarbetning (#84): uppgiftsorienterad bottennavigering med fyra flikar **Idag / Historik / Trender / Hantera** (ersätter de fem entitetsflikarna). Idag-ytan som handlingsyta med checklistor, enhetlig Historik-yta för alla posttyper, Trender-ytan som slår ihop diagrammen, Hantera-ytan. Polish: notisåtgärder ("Markera tagen"/"Logga nu"), smarta FAB-förval, stegvis svepbar screening, veckosammanfattning på Idag |
| 2.14.1 | Tillgänglighetsfixar (48dp tryckytor, TalkBack-semantik på energireglaget), korrekt tidszonskonvertering för loggade tidsstämplar (medicinnedkylning kunde bli ~2h för lång), avkodad Settings-layout och medicindosloggning, borttagen död kod, städad KRAVLISTA/README |
| 2.14.0 | Info-ikon med läs-only anteckningsdialog på kort som saknade en synlig anteckningsindikator (Aktivitet/Screening, medicindos, favorit, sjukdomsepisod) |
| 2.13.0 | Sjukdomsepisod- och incheckningsanteckning migrerad till det generiska anteckningssystemet (delad `NoteField`) |
| 2.12.0 | Händelse-anteckning migrerad till det generiska anteckningssystemet (delad `NoteField`) |
| 2.11.0 | Medicin/Recept/Favorit-anteckning migrerad till det generiska anteckningssystemet (delad `NoteField`); Favorit-anteckning har nu ett UI-fält |
| 2.10.0 | Anteckning (delad `NoteField`) på Screening- och Aktivitet-formulären |
| 2.9.0 | Vid behov-mediciner kan favoritmarkeras (som aktivitetstyper); endast favoriter visas som chips, resten nås via "Fler"-lista; ny inställningssektion för favoritmarkering |
| 2.2.1 | Symptomloggning med svårighetsgrad och favoriter; aktivitetstyper med favorit-chips och dropdown; OptionSettingsCard för full CRUD av båda listor |
| 2.2.0 | Händelselogg (Handelse) med triggers och åtgärder |
| 2.1.0 | Måltidshändelseväljare i ScreeningTab |
| 2.0.x | Strängextraktion till strings.xml, utökad testtäckning, CI-fixes, Upprepning-enum, IoDispatcher-injektion |
