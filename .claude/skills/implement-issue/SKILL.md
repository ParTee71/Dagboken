---
name: implement-issue
description: Implement a GitHub issue, bug fix, feature, or chore in Dagboken (4.0) end to end — check that the previous PR is merged, research, reuse shared parts, change code following the project's patterns, add tests at every level, update requirements and the data chain, self-review with the granskare agent, then commit, push, open a PR and watch it. Use when the user wants to "implement issue #N", "fix this bug", "build this feature", "do this ticket", "pick up #N", "kör #N", "kör nästa", "driv på", or hands off from refine-issue.
---

# Genomföra ett issue (bugg · funktion · underhåll)

Motsvarigheten till `refine-issue`: driver en ändring från issue till PR enligt de fem
reglerna i [CLAUDE.md](../../../CLAUDE.md). Hoppa inte över steg – reglerna är definitionen
av klart.

> **Tester körs lokalt före push, CI bekräftar** (skill `testing-strategy`, "Innan du anser dig
> klar"). Tester i `tools/db` körs bara mot Firebase-emulatorn, aldrig mot den riktiga databasen.

## Steg 0 – Föregående PR och branch (alltid först)

En PR i taget, och sessionen har normalt en enda arbetsbranch.

1. Hämta den senaste PR:en från arbetsbranchen med `mcp__github__list_pull_requests`
   (owner `ParTee71`, repo `Dagboken`, head `<branch>`, state `all`) och
   `mcp__github__pull_request_read`. **Lita aldrig på ett antagande eller på att någon sagt
   "mergad" – fråga GitHub.**
2. **Mergad** → `git fetch origin master && git checkout -B <branch> origin/master`. Avsluta
   bevakningen av den gamla PR:en (`unsubscribe_pr_activity`) och ta bort dess check-in.
3. **Öppen** → säg direkt till användaren att den inte är mergad och att nytt arbete annars
   hamnar i samma PR. Vänta på besked, eller förbered arbetet i en lokal commit utan push.
   **Återställ aldrig branchen från `master` innan mergen bekräftats** – det tappar
   PR:ens commits lokalt.
4. Ingen PR alls → skapa branchen från `origin/master`.

## Steg 1 – Förstå arbetet

- Hämta issuet **och dess kommentarer**: `mcp__github__issue_read` (owner `ParTee71`, repo
  `Dagboken`, metod `get` och `get_comments`). Acceptanskriterier, Design- och
  Återbruk-avsnitt är kontraktet; kommentarer med rubriken "Tillägg" ändrar eller
  kompletterar det och gäller före issuetexten.
- Oklart eller riskabelt omfång → kör `refine-issue` först eller bekräfta omfånget.
- Klassa: bugg, funktion eller underhåll.

## Steg 2 – Mockup (ny GUI)

Kräver issuet ny eller tydligt ändrad GUI (tabellen i skill `mockup`) och det finns ingen
godkänd mockup under **Design** → gör mockupen, visa den och **vänta på ok** innan kod.

## Steg 3 – Återskapa (bugg) / sikta (funktion)

- **Bugg:** exakt återskapande, förväntat mot faktiskt, hitta felande kodväg. Reproduktionen
  blir ett test som faller.
- **Funktion/underhåll:** exakt var ändringen görs och vilket synligt utfall som betyder klart.

## Steg 4 – Hitta befintliga mönster och delade delar

- Slå upp komponenter, ramar och byggstenar i skill `shared-ui-components`. Lista vilka
  som används.
- Saknas något delat → planera det som **egen commit före** featuren.
- Data → skill `data-safety-backup` och `firestore-data-layer`. Larm → `notifications-alarms`.
- Ombyggnadssteg → etappen i `ARKITEKTUR.md`; 3.x-förlagan på branchen `legacy`, förlagan från
  ReseApoteket i dess repo. Paritetskraven (OMB-6) listas i planen.
- Mappa till KRAVLISTA-ID:n och befintliga tester.

## Steg 5 – Planera

Kort plan: filer, ordning, tester, datakedjan, krav. Arkitektoniskt betydande eller
tvetydigt → bekräfta med `AskUserQuestion` innan kod.

**Delegera på rätt nivå** (ARKITEKTUR.md → Agenter): portar, komponenter, skärmar och CI-filer
till agenten `byggare`; datamodell, codecs, konverteraren, rules och migrering till agenten
`arkitekt`; tester på tre eller fler nivåer till `testskrivare`. Huvudsessionen orkestrerar och
granskar och byter aldrig modell själv. Går ett steg fel två gånger i rad flyttas det upp en nivå.

## Steg 6 – Implementera (tester samtidigt, inte efter)

`Compose → ViewModel(StateFlow<UiState>, sealed events) → Repository → FirestoreCollection`,
Hilt, fel mappas i datalagret.

- **Bugg:** regressionstest som faller först, sedan minsta ändringen som gör det grönt.
- **Funktion:** delade delar först (egen commit), sedan featuren ovanpå. En listskärm är
  en `EntityListScreen`, ett formulär en `EntityEditScreen` + `EditorState`.
- **Persisterad data:** hela kedjan i samma ändring.
- Svenska UI-strängar i `strings.xml`; återkommande texter finns redan – återanvänd.
- Ingen loggning av hälsodata; semantik och tryckytor i komponenterna.
- Hooken `regel4-check` säger till direkt om ett förbjudet mönster skrivs – rätta innan du går vidare.

## Steg 7 – Tester på varje berörd nivå

Enligt skill `testing-strategy`: kör kontraktstesterna för skärmar på ramarna, testa bara
det unika; uppdatera – ta aldrig bort eller försvaga – befintliga tester; nya komponenter
får skärmdump ljust + mörkt och plats i `ComponentGallery`.

## Steg 8 – Självgranskning

Kör agenten **`granskare`** på diffen och åtgärda varje ⚠️/❌ i dess tabell. Spåra varje
acceptanskriterium till kod eller test. Kan du inte det är du inte klar.

Rör ändringen logik i `:core`, datalagret, `firestore.rules`, `tools/db` eller en delad
ram/komponent – eller är diffen större än ett par filer – kör dessutom den inbyggda
**`/code-review`** på diffen, *efter* `granskare` och innan push. `granskare` kontrollerar
reglerna; `/code-review` letar buggar. Verifiera varje fynd och åtgärda de verkliga. Rena
dokument- och strängändringar behöver den inte.

Ändras koden efter granskningen (rättningar av fynden) granskas rättningarna igen – `granskare`
och `/code-review` på det som ändrats – innan push. En PR går aldrig ut med kod som ingen
granskning sett.

## Steg 9 – Commit och push

- Konventionella meddelanden, gärna svenska: `feat(idag): …`, `fix(doser): …`,
  `test(...)`, `docs(...)`, `chore(...)`. Små, sammanhängande commits. `Closes #N` i den
  sista. Avsluta med de commit-trailers som sessionen anger.
- Kör kontrollerna lokalt (skill `testing-strategy`) och pusha först när de är gröna.
- `git push -u origin <branch>` (försök igen med backoff vid nätverksfel).

## Steg 10 – Pull request och bevakning

- Mot `master`. Titel `<område>: <sammanfattning>`. Innehåll: vad som ändrats, hur det
  uppfyller de fem reglerna, kravrader som bockas av (paritetschecklistan, OMB-6), testplan,
  `Closes #N`, och för GUI: mockup-länk + skärmdumpar (ljust/mörkt).
- **Prenumerera direkt** (`subscribe_pr_activity`) och schemalägg en check-in.
- Rött CI → agenten **`ci-doktor`** diagnostiserar; fixa grundorsaken och pusha. Kör aldrig
  bara om.
- Har användaren bett om automatisk merge när den är grön: kontrollera att alla
  kontroller på senaste commit är gröna och att PR:en är konfliktfri, merga med
  `merge_pull_request` (metod `merge`, `expectedHeadSha`), avsluta bevakningen och gå
  tillbaka till steg 0 för nästa issue.

## Flera issues

Ett i taget, var och en med egen PR. Blanda aldrig orelaterade ändringar i samma branch.

## Anti-mönster

- Påbörja nästa issue utan att ha frågat GitHub om föregående PR är mergad.
- Kod för ny GUI utan godkänd mockup.
- Bugfix utan regressionstest.
- Persisterad ändring utan codec, rules, samlingslista och rundtur – eller ett 3.x-fält utan plats i 4.0.
- En lokal variant av en delad komponent eller ram.
- Publik `fun` på en ViewModel när projektet använder sealed events + `onEvent()`.
- Ta bort eller `@Ignore`:a ett rött test.
