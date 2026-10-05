# Dagboken – projektets grundregler

> Hälsodagbok för mående, aktiviteter, mediciner och sjukdomar – **4.0 under ombyggnad**.
> Android/Kotlin · Compose (Material 3 Expressive) · Navigation 3 · MVVM · Hilt · Firestore · Firebase Auth.
> Kravspecifikation: [KRAVLISTA.md](KRAVLISTA.md) · Arkitektur och ombyggnadsplan: [ARKITEKTUR.md](ARKITEKTUR.md) (ADR-001)

Den här filen laddas automatiskt vid **varje** uppgift och gäller alltid, för alla
ändringar, oavsett storlek. De detaljerade reglerna ligger som skills i
`.claude/skills/` – den här filen är kontraktet som binder ihop dem.

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
höjs bara vid release (skill **release**).
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

---

## Processregel: mockup före ny GUI (skill **mockup**)

Utvecklingen sker mest från telefonen, och en release för att "se hur det blev" är dyr.

- Ny skärm, ny delad komponent eller tydligt ändrat utseende/beteende → visa först en
  **mockup** för användaren (Design-canvas-artifact; ombyggnadens canvas med struktur,
  datamodell och känslan Papper och teal: https://claude.ai/artifact/MP2fPHimTk8tAmKkpr3K5t)
  och vänta på ok innan koden skrivs (NFR-20).
- Mockupen byggs av **katalogens komponenter och mönster** (regel 4), i appens designspråk,
  med riktiga svenska texter och realistisk men påhittad data – aldrig användarens riktiga
  hälsodata. Ett element som redan används i appen återanvänds på samma sätt som tidigare –
  det är ingen designfråga. Bara en verklig öppen fråga om något helt nytt → två varianter
  sida vid sida. Något som saknas i katalogen märks "NY KOMPONENT".
- Länken läggs i issuet under rubriken **Design**.
- I PR:en visas resultatet med **Roborazzi-skärmdumpar** (ljust + mörkt) bredvid mockupen.
- Tumregel: syns förändringen i en tumnagel → mockup först. Padding, ordval eller
  ikonbyte → skärmdumpsdiffen i PR:en räcker.

→ Skills: **mockup**, **ui-style**, **refine-issue**, **implement-issue**.

---

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

## Innan du anser dig klar (slutkontroll)

- [ ] **Datasäkerhet:** nya/ändrade fält i modell, codec, rules, samlingslista och rundturstest; 3.x-fält har sin plats i konverteraren och dess fixtur. (1)
- [ ] **Tester:** tillagda/uppdaterade på alla berörda nivåer; kontraktstester för skärmar som använder ramarna. (2)
- [ ] **Krav:** KRAVLISTA.md (och ev. README/version) speglar ändringen; krav-ID:n i PR-beskrivningen. (3)
- [ ] **Enhetligt:** inga råa M3-komponenter, egna interaktionsmönster, hårdkodade stilvärden eller kopierad logik; `UiConsistencyTest` och `cpdCheck` gröna. (4)
- [ ] **CI-budget:** inga nya dyra steg i PR-flödet; byggtidseffekt angiven vid nya beroenden. (5)
- [ ] **GUI:** mockup godkänd och skärmdumpar bifogade i PR:en.
- [ ] **Granskning:** agenten `granskare` och `/code-review` har körts på diffen – och igen på rättningarna efter dem (skill **implement-issue**, steg 8).

---

## PR-flöde

- **En PR i taget.** Öppna inte nästa förrän den föregående är mergad (eller uttryckligen
  stängd/pausad av användaren). Gäller `implement-issue` och manuellt arbete.
- **Kontrollera mergen via GitHub** (`pull_request_read`) innan nästa issue påbörjas –
  inte via antagande. Först när den är mergad startas arbetsbranchen om från `origin/master`.
- PR:er går mot `master`. Kör testerna lokalt först (skill **testing-strategy**), pusha sedan –
  GitHub Actions bekräftar.
- **Prenumerera automatiskt** på PR-aktivitet (`subscribe_pr_activity`) så fort en PR
  skapas, utan att fråga. Bevaka tills den är mergad eller stängd: fixa CI-fel, hantera
  granskningskommentarer, schemalägg check-in. Vid rött CI: låt agenten `ci-doktor`
  diagnostisera. Avsluta prenumerationen när PR:en är klar.
- **Delegera på rätt nivå** (ARKITEKTUR.md → Agenter): huvudsessionen orkestrerar och granskar;
  agenten `byggare` tar portar, komponenter, skärmar och CI-filer, agenten `arkitekt` datamodell,
  codecs, konverteraren, rules och migrering. Går ett steg fel två gånger i rad flyttas det upp en nivå.

## Bygg & test

```bash
./gradlew :core:test                    # domän, codecs, konverterare, motorer (sekunder, ingen Android SDK)
./gradlew :app:testDebugUnitTest        # ViewModel, Robolectric-UI, UiConsistencyTest
./gradlew :app:verifyRoborazziDebug     # jämför skärmdumpar mot referenser
./gradlew :app:recordRoborazziDebug     # spela in referenser (bara vid avsiktlig designändring)
./gradlew cpdCheck                      # copy-paste-detektor
./gradlew :app:compileDebugKotlin       # kompilera
./gradlew :core:convertLegacyBackup --args="--in tools/db/backup-3x.json --out tools/db/export-4.json --user <uid>"   # 3.x-backup → 4.0-export (OMB-4), rapport utan innehåll
npx --prefix tools/db firebase emulators:exec --only firestore --project demo-dagboken "npm --prefix tools/db test"   # rules + rundtur (Java 21)
node --test '.github/scripts/*.test.mjs' && node --test '.claude/hooks/test/*.test.mjs'                             # dokumentkontroller + hooktest
node tools/db/stats.mjs                 # databasöversikt (kräver FIREBASE_SERVICE_ACCOUNT)
node tools/db/query.mjs doses           # läs en samling
```

Kör det du ändrat lokalt innan varje push – även i fjärr-/telefonsessioner (skill
**testing-strategy**, "Innan du anser dig klar"). **Gradle går att köra i sessionen när Android
SDK finns i `/opt/android-sdk`** (session-start-hooken säger om den finns); annars får CI
bekräfta – säg det i PR:en. Kommandona gäller även i Android Studio.

## Arkitektur i korthet

```
Compose → ViewModel (StateFlow<UiState>) → Repository → FirestoreCollection<T> → Firestore (offline-cache, users/{uid})
                                                         ↑ DocCodec<T> i :core
```

`:core` är ren Kotlin/JVM (modeller, codecs, motorer, diagrammatematik, 3.x-konverteraren);
`:app` är Android-appen med fyra flikar (Idag · Dagbok · Trender · Mediciner), inställningsark
bakom avataren och plusknapp som loggar. Utseende och gemensamt beteende finns bara i
`ui/theme`, `ui/components`, `ui/diagram` och `ui/common`; Firestore bara i `data/firestore`; felmappning en
gång i `data/common`. Struktur, datamodell, lager, migrering och etapper står i
[ARKITEKTUR.md](ARKITEKTUR.md) (enda källan).

## Databasåtkomst från sessionen

Läs med `tools/db` (skill **db-access**, agent **db-inspektor**). Kräver miljövariabeln
`FIREBASE_SERVICE_ACCOUNT` (läsroll, projektet `dagboken-711d2`) i Claude-miljöns
inställningar – be aldrig användaren klistra in en nyckel i chatten. Projektet läses ur
`app/google-services.json`; `FIREBASE_PROJECT_ID` behövs bara för ett annat projekt.
Skrivningar bara på uttrycklig begäran via `import.mjs`/`migrate.mjs`.

---

## Agenter (`.claude/agents/`)

| Agent | När |
|---|---|
| `byggare` | Portar, komponenter, skärmar på ramarna och CI-filer enligt en klar plan (Opus · medium). |
| `arkitekt` | Datamodell, codecs, 3.x-konverteraren, rules, migrering och arkitekturval (Fable/Opus · high). |
| `granskare` | Före varje PR: granskar diffen mot de fem reglerna och mockup-regeln, rapporterar i en tabell. |
| `ci-doktor` | Vid rött CI: läser loggar, skärmdumpsdiffar och CPD-rapporter, föreslår fix. |
| `testskrivare` | Skriver/utökar tester och fixturer med de delade testhjälparna – när tre eller fler testnivåer berörs. |
| `db-inspektor` | Svarar på frågor om faktisk data i Firestore, endast läsning (Haiku · low). |

## Skills (`.claude/skills/`)

| Skill | När |
|---|---|
| `android-dev` | Baslinje för all Android/Kotlin-utveckling (ladda alltid). |
| `compose-expert` · `kotlin-coroutines` · `kotlin-flows` | Compose-, coroutine- och flow-detaljer. |
| `android-gradle-logic` | Gradle, version catalog, `:core`/`:app`, `cpdCheck`. |
| `firebase-auth` | Google-inloggning (Credential Manager + Firebase), krav på inloggning, `users/{uid}`. |
| `notifications-alarms` | Påminnelser och larm: exakta larm, kanaler, omschemaläggning, schemat ur Firestore-cachen. |
| `accessibility-compose` | TalkBack, tryckytor, dynamisk text. |
| `data-privacy-security` | Hälsodata, loggning, EU-region, hemligheter, notiser, minify. |
| **`data-safety-backup`** | Regel 1 – codecs, schemaversion, export/import, rundtur och 3.x-konverteraren. |
| **`testing-strategy`** | Regel 2 – testnivåer, kontraktstester, skärmdumpar. |
| **`requirements-kravlista`** | Regel 3 – hålla KRAVLISTA.md aktuell, paritetschecklistan. |
| **`shared-ui-components`** | Regel 4 – komponenter, ramar, byggstenar, förbud, kontroller. |
| **`ci-budget`** | Regel 5 – vad som körs när i CI. |
| `ui-style` | Designspråket Papper och teal (Material 3 Expressive). |
| `mockup` | Ny skärm, komponent eller ändrat utseende: mockup före kod. |
| `diagram` | Diagrammen i `ui/diagram` och diagrammatematiken i `:core/engine` (TRD-6…18, HEM-7, HLS-10/11/13). |
| `firestore-data-layer` | `FirestoreCollection`, `DocCodec`, repositories, `UserSession`, offline, rules. |
| `db-access` | Läsa databasen från en Claude-session. |
| `refine-issue` | Förfina en idé till ett planerat issue (med Design och Återbruk). |
| `implement-issue` | Genomföra ett issue hela vägen till PR enligt reglerna (med merge-kontroll först). |
| `release` | Endast vid uttrycklig release. |
| `firebase-security-rules-auditor` | *Googles (Firebase).* Granska `firestore.rules` – vid varje rules-ändring. |
| `android-intent-security` | *Googles (Android).* Granska exporterade komponenter, notisers `PendingIntent` och delningens Intents. |
| `navigation-3` | *Googles (Android).* Referens för Navigation 3: djuplänkar, flera stackar, scener. |

Googles skills är oförändrade kopior (källa, commit och licens i deras `SOURCE.md`); där de
krockar med projektets regler gäller projektets. Planerad skill (ARKITEKTUR.md): `health-connect`
tillkommer i etapp 6.

> Reglerna gäller både i Claude-appen på telefonen och i Claude i Android Studio – båda
> läser denna fil och `.claude/`.
