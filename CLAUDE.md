# Dagboken – projektets grundregler

> Hälsodagbok för mående, aktiviteter, mediciner och sjukdomar – **4.0 under ombyggnad**.
> Android/Kotlin · Compose (Material 3 Expressive) · Navigation 3 · MVVM · Hilt · Firestore · Firebase Auth.
> Kravspecifikation: [KRAVLISTA.md](KRAVLISTA.md) · Arkitektur och ombyggnadsplan: [ARKITEKTUR.md](ARKITEKTUR.md) (ADR-001)

Den här filen gäller vid **varje** ändring, oavsett storlek, både på telefonen och i Android
Studio; detaljerna ligger som skills i `.claude/skills/`.

**Svarslängd:** håll chattsvar minimala. Inga sammanfattningar, ingen upprepning av vad
som gjordes, inga rubriker/listor om inte nödvändigt. Kod, commits och PR-beskrivningar
skrivs normalt.

**Ombyggnaden:** `master` bygger Dagboken 4.0 på ReseApotekets grund (`:core` + `:app`,
Firestore under `users/{uid}`, fyra flikar, designspråket Papper och teal). 3.27.0 ligger på
branchen `legacy`. Etapper, modell och effort per steg står i [ARKITEKTUR.md](ARKITEKTUR.md);
villkor nummer ett är att **ingen data får tappas** (KRAVLISTA §22, OMB).

---

## De fem icke-förhandlingsbara reglerna

Vid **varje** ändring ska du, innan du anser arbetet klart, gå igenom alla fem.

### 1. Datasäkerhet – ingen data får tappas
All data i Firestore ska överleva en `tools/db export → import`-rundtur, och appen ska
tolerera okända fält och äldre `schemaVersion`. Nytt fält eller ny samling → default i
`:core`-modellen, tolerant `DocCodec`, `firestore.rules`, samlingslistan i
`tools/db/lib/collections.mjs` och rundturstest – i samma ändring. Varje fält i 3.x-schemat
har en plats i 4.0-modellen; konverteraren från `BackupJson` bevisas med en fixtur där varje
fält är satt och med grinden OMB-4.
→ Skill: **data-safety-backup**. Krav: BCK-, DAT- och OMB-serierna.

### 2. Tester på alla nivåer
Ingen beteendeändring utan tester på rätt nivå: `:core` (ren JUnit), ViewModel
(Fake-repositories + Turbine), Compose via Robolectric i JVM, Roborazzi-skärmdumpar,
rules- och rundturstester mot Firebase-emulatorn. Befintliga tester uppdateras, tas
aldrig bort för att "bli gröna". **Flaky tester är buggar** – hitta grundorsaken och gör
testet deterministiskt, kör aldrig bara om.
→ Skill: **testing-strategy**. Krav: NFR-6.

### 3. Kraven hålls aktuella
Ändrar du synligt beteende uppdateras [KRAVLISTA.md](KRAVLISTA.md) i samma PR – nytt krav
får nästa lediga ID, ändrat behåller sitt ID, borttaget stryks med `~~…~~ *(borttaget)*`,
och det som ändras i ombyggnaden märks *(4.0)*. Gemensamma beteenden beskrivs en gång
(NFR-15–18, NAV) och refereras. Kravlistan är ombyggnadens paritetschecklista (OMB-6): varje
PR bockar av sina krav-ID:n. README följer med när funktionsomfånget ändras; `versionName`
höjs i funktions-PR:en (se PR-flöde); själva releasen görs via skill **release**.
→ Skill: **requirements-kravlista**.

### 4. Enhetligt och utan dubbelkod
**Samma typ av element ser likadant ut och beter sig likadant överallt, och varje sak
finns på ett ställe.**

- **Utseende:** varje elementtyp (knapp, kort, listrad, kryssrad, reglage, textfält, rubrik,
  dialog, bottom sheet, pill, progress, tomt tillstånd, diagram …) har **exakt en** komponent i
  `ui/components/` (diagrammen i `ui/diagram/`). Feature-kod anropar aldrig Material 3-komponenter direkt och hårdkodar
  aldrig utseende (`Color(…)`, `RoundedCornerShape(…)`, `fontSize`, `TextStyle`, literala dp
  för hörn/höjd). Färg, form, typografi och avstånd kommer från `ui/theme` (`AppColors`,
  `AppTypography`, `AppShapes`, `Spacing`) i designspråket Papper och teal.
- **Beteende:** varje interaktionsmönster finns **en gång**: listskärm (laddning, tomt,
  fel, lista, lägg till) = `EntityListScreen`; redigering (validering, spara aktiveras
  först när giltigt och ändrat, "Släng ändringar?" vid bakåt, fel som snackbar) =
  `EntityEditScreen` + `EditorState`; postkort, listrader och ihopfällbara sektionskort enligt
  NFR-15–18; permanent radering = alltid `ConfirmDialog`; felmeddelanden =
  `DataError.toMessage()`; skärmbyten = `navigation/Transitions`.
- **Kod i alla lager:** ingen kopierad logik. Firestore via generisk
  `FirestoreCollection<T>` + `DocCodec<T>`; listtillstånd via `asListUiState()`;
  beräkningar (doser, kylperiod, periodslut, dagens energi, diagrammatematik) och 3.x-konverteraren
  i `:core`. Två ställen som gör samma sak → bryt ut *innan* det tredje dyker upp.
  Komposition (hjälpklasser, extensions, parametrar) före djupa basklasser. Gäller även
  tester, byggfiler, CI-flöden och `tools/db`.
- **Behövs en variant?** Utöka den delade delen med en parameter vars default bevarar
  nuvarande utseende och beteende. Aldrig en lokal kopia. Ny elementtyp/nytt mönster →
  ny delad del med test, skärmdump och plats i `ComponentGallery`.
- **Kontrolleras automatiskt:** hooken `regel4-check` ger besked direkt efter varje
  filändring; `UiConsistencyTest` (Konsist) och `cpdCheck` (copy-paste-detektor) fäller
  bygget. Förbjudna mönster finns i en fil (`ui-forbidden.txt`), undantag bara via
  `ui-allowlist.txt` med motivering och användarens ok i PR:en.
- **Gäller även dokumentationen:** varje regeltabell står i en skill; andra filer länkar.

→ Skills: **shared-ui-components**, **ui-style**, **firestore-data-layer**.

### 5. CI-budget
En PR får inte kosta mer än ~8 Actions-minuter, plus instrumenttesterna på Android-emulator
när PR:en rör koden de testar – release kör dem inte igen för samma kod. Inget lint eller
release-bygge i PR-flödet utan uttryckligt beslut; de körs på begäran, veckovis och vid
release. Nya beroenden eller moduler får inte fördubbla byggtiden – ange effekten i PR:en.
(3.x kostade 35–45 minuter per PR; den gamla `instrumented.yml` i varje PR är pensionerad.)

**Tester körs bara när koden de testar har ändrats.** Ett snabbt jobb avgör utifrån ändrade
sökvägar vilka testjobb som körs; Gradles build cache hoppar dessutom över oförändrade
testtasks. På `master` körs allt utom instrumenttesterna, och dokumentkontrollerna (skill-tabeller
mot kod och workflow, länkar, hooktest) körs alltid. Att slippa köra ett test är aldrig ett
skäl att inte skriva det.
→ Skill: **ci-budget** – enda källan för vad som körs när; kontrolleras mot `android.yml`. Krav: NFR-19.

## Processregel: mockup före ny GUI (skill **android:mockup**)

Utvecklingen sker mest från telefonen, och en release för att "se hur det blev" är dyr. Ny
skärm, ny delad komponent eller tydligt ändrat utseende/beteende → visa först en **mockup**
(Design-canvas-artifact av katalogens komponenter, med påhittad data – aldrig riktig hälsodata)
och vänta på ok innan koden skrivs (NFR-20). Tumregel: syns förändringen i en tumnagel →
mockup först; padding, ordval eller ikonbyte → skärmdumpsdiffen i PR:en räcker. Hur mockupen
byggs står i pluginens skill **android:mockup**; mallens länk, datan, var länken läggs och
skärmdumparna i PR:en i skill **android-projekt**.

## Innan du skriver ny kod

1. **Sök efter befintligt mönster.** Komponent, ram, `FirestoreCollection`,
   `EditorState`, use case, motor eller testhjälpare – finns det, använd det.
   `Grep` i `ui/components/`, `ui/common/`, `data/common/`, `data/firestore/` och `core/` först.
   Vid en port: läs förlagan (3.x på branchen `legacy`, eller ReseApoteket) i sin helhet.
2. **Följ arkitekturen** även om en genväg vore enklare: MVVM, `StateFlow<UiState>`,
   sealed events, repository som single source of truth, Hilt-DI, fel mappas i datalagret.
3. **Svenska** i allt användarvänt (UI-strängar i `strings.xml`, återkommande texter en
   gång); kod och identifierare på engelska; commit-meddelanden får vara svenska.
4. **Integritet:** allt i dagboken är hälsodata (GDPR art. 9). Logga aldrig innehåll eller
   PII, persistera aldrig Health Connect-data (HLS-5), checka aldrig in nycklar (`*.jks`,
   service accounts). `app/google-services.json` är incheckad klientkonfiguration, ingen
   hemlighet. → skill **data-privacy-security**.

## Innan du anser dig klar

Gå igenom de fem reglerna och mockup-regeln. Agenten `granskare` gör det på diffen och
rapporterar i en tabell (skill **implement-issue**, steg 8).

## PR-flöde

- **En PR i taget.** Öppna inte nästa förrän den föregående är mergad (eller uttryckligen
  stängd/pausad av användaren). Gäller `implement-issue` och manuellt arbete.
- **Kontrollera mergen via GitHub** (`pull_request_read`) innan nästa issue påbörjas –
  inte via antagande. Först när den är mergad startas arbetsbranchen om från `origin/master`.
- PR:er går mot `master`. Kör testerna lokalt först (skill **testing-strategy**), pusha sedan –
  GitHub Actions bekräftar.
- **Förbered releasen i funktions-PR:en.** Varje PR som ändrar appens beteende höjer
  versionen i samma ändring (semver, `versionCode`, `version.properties`, README) och skriver
  den i PR-beskrivningen; ren dokument-, test- eller CI-ändring höjer inte. Detaljerna står i
  skill **release**, "Versionshöjning i funktions-PR:en". Själva releasen körs bara på
  uttrycklig begäran.
- **Prenumerera automatiskt** på PR-aktivitet (`subscribe_pr_activity`) så fort en PR
  skapas, utan att fråga. Bevaka tills den är mergad eller stängd: fixa CI-fel, hantera
  granskningskommentarer, schemalägg check-in. Vid rött CI: låt agenten `ci-doktor`
  diagnostisera. Avsluta prenumerationen när PR:en är klar.
- **Delegera på rätt nivå** (ARKITEKTUR.md → Agenter): huvudsessionen orkestrerar och granskar;
  agenten `byggare` tar portar, komponenter, skärmar och CI-filer, agenten `arkitekt` datamodell,
  codecs, konverteraren, rules och migrering. Går ett steg fel två gånger i rad flyttas det upp en nivå.
- **Modell efter uppgift** för sessionen och varje agent, Opus som standard ([.claude/README.md](.claude/README.md)
  → Modellval). Varje issue anger **Agent · modell · effort** (skill **refine-issue**) och körs så.

## Bygg & test

```bash
./gradlew :core:test                    # domän, codecs, konverterare, motorer (sekunder, ingen Android SDK)
./gradlew :app:testDebugUnitTest        # ViewModel, Robolectric-UI, UiConsistencyTest
./gradlew :app:verifyRoborazziDebug     # jämför skärmdumpar mot referenser
./gradlew :app:recordRoborazziDebug     # spela in referenser (bara vid avsiktlig designändring)
./gradlew cpdCheck                      # copy-paste-detektor
./gradlew :app:compileDebugKotlin       # kompilera
node --test '.github/scripts/*.test.mjs' && node --test '.claude/hooks/test/*.test.mjs'                             # dokumentkontroller + hooktest
node tools/db/stats.mjs                 # databasöversikt (kräver FIREBASE_SERVICE_ACCOUNT)
node tools/db/query.mjs doses           # läs en samling
```

Kör det du ändrat lokalt innan varje push, även i telefonsessioner. Emulatortester,
3.x-konverteraren, läkemedelsmatchningen och Android SDK i sessionen (alltid ok att
installera) står i skill **testing-strategy**, "Innan du anser dig klar".

## Arkitektur i korthet

```
Compose → ViewModel (StateFlow<UiState>) → Repository → FirestoreCollection<T> → Firestore (offline-cache, users/{uid})
                                                         ↑ DocCodec<T> i :core
```

`:core` är ren Kotlin/JVM (modeller, codecs, motorer, diagrammatematik, 3.x-konverteraren);
`:app` är Android-appen med fyra flikar (Idag · Dagbok · Trender · Mediciner). Utseende och gemensamt beteende finns bara i
`ui/theme`, `ui/components`, `ui/diagram` och `ui/common`; Firestore bara i `data/firestore`; felmappning en
gång i `data/common`. Struktur, datamodell, lager, migrering och etapper står i
[ARKITEKTUR.md](ARKITEKTUR.md) (enda källan).

## Databasåtkomst från sessionen

Läs med `tools/db` (skill **db-access**, agent **db-inspektor**); nyckeln ligger i miljön som
`FIREBASE_SERVICE_ACCOUNT` – be aldrig användaren klistra in en nyckel i chatten. Skrivningar
bara på uttrycklig begäran via `import.mjs`/`migrate.mjs`.

## Agenter och skills

Agenterna (`byggare`, `arkitekt`, `granskare`, `ci-doktor`, `testskrivare`, `db-inspektor`)
och alla skills, med när de används, listas i [.claude/README.md](.claude/README.md). Modell
och effort står i varje agents frontmatter (ARKITEKTUR.md → Agenter).

**Kortrapport:** agenternas svar hamnar i huvudsessionens kontext och kostar där. Varje agent
svarar bara med mallen i sin fil – inga referat av det den läst, ingen upprepning av uppdraget,
högst ~15 rader om inte fynden kräver mer.

**Lång session:** vid komprimering av sammanhanget: behåll listan över ändrade filer, issue- och PR-nummer,
testkommandon som körts med resultat, och öppna ❌ från granskningen.
