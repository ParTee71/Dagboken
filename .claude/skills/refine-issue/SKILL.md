---
name: refine-issue
description: Turn a rough idea, bug report, or feature request into a well-refined, planned GitHub issue for Dagboken (4.0) — clarify intent, research the codebase, define scope and acceptance criteria grounded in the project's five non-negotiable rules, make a mockup for new GUI, and create or update the issue on GitHub after confirmation. Use when the user wants to "create an issue", "update an issue", "plan a feature", "write up a bug", "refine this idea into a ticket", or plan a step of the 4.0 rebuild (etapp). Svenska trigger-ord: skapa issue, nytt issue, uppdatera issue, planera, förfina, skriv upp en bugg, idé, önskemål, ticket. Pairs with implement-issue.
---

# Förfina och planera ett issue

Gör en halvfärdig idé till ett GitHub-issue som en annan session (med `implement-issue`)
kan genomföra utan fler frågor.

Gå igenom stegen i ordning. Samla frågor, förhör inte. **Skapa eller uppdatera inte
issuet förrän i steg 7.**

Definition of Done (steg 5) vilar på de fem reglerna i [CLAUDE.md](../../../CLAUDE.md).
Ett förfinat issue gör reglerna konkreta för *just den här* ändringen.

### Nytt eller befintligt issue
- **Inget nummer** → alla steg, skapa nytt issue i steg 7.
- **Nummer angivet** → hämta med `mcp__github__issue_read` (owner `ParTee71`, repo
  `Dagboken`), visa titel och innehåll, förfina och **uppdatera** i steg 7.

---

## Steg 1 – Fånga idén

Återge begäran i en eller två meningar. Klassa den:

| Typ | Signaler |
|---|---|
| **Funktion** | ny skärm, ny förmåga, "lägg till", "stöd för" |
| **Bugg** | "fel", "kraschar", "räknar fel", "fungerar inte" |
| **Underhåll** | refaktorering, beroende, bygg/CI, städning, ingen synlig ändring |

Sök efter dubbletter först: `mcp__github__search_issues` (owner `ParTee71`, repo `Dagboken`).

## Steg 2 – Klargör avsikt

Fråga bara det som inte går att härleda ur begäran eller koden. `AskUserQuestion` vid
konkreta val. Hoppa över steget om begäran redan är entydig.

## Steg 3 – Undersök koden

- Vilka skärmar, ViewModels, repositories, codecs, `:core`-funktioner berörs?
- **Återbruk (regel 4):** vilka komponenter, ramar och byggstenar ur tabellerna i skill
  `shared-ui-components` används? Behövs något **nytt delat**? Då byggs det först.
- **Data (regel 1):** nytt eller ändrat persisterat fält eller ny samling? Då krävs hela
  kedjan i skill `data-safety-backup`.
- **Ombyggnaden:** vilken etapp i `ARKITEKTUR.md` hör det till, och vilken modell/effort
  anger etapptabellen (byggare/arkitekt)? Rör det 3.x-paritet: vilka krav i kravlistan är
  paritetspunkter (OMB-6) och var ligger 3.x-koden på branchen `legacy`?
- **Beräkning:** berörs en regel som räknas (doser, kylperiod, periodslut, energi, diagram)?
  Den ligger i `:core` och står i kravlistan (skill `requirements-kravlista`).
- **Larm:** berörs påminnelser eller notiser? Skill `notifications-alarms`.
- **Integritet:** loggas, lagras eller exporteras hälsodata? Skill `data-privacy-security`.
- **Krav (regel 3):** vilka ID:n i KRAVLISTA.md berörs, eller behövs nya?
- **Tester (regel 2):** vilka tester finns i dag, vilka nivåer berörs?
- **CI (regel 5):** nytt beroende eller nytt steg?

Anteckna konkreta filsökvägar.

## Steg 4 – Mockup (endast ny GUI)

Om ändringen är synlig enligt tabellen i skill `mockup`: bygg mockupen nu, visa länken
och vänta på ok innan issuet skapas. Länken går in under **Design**.

## Steg 5 – Omfång och Definition of Done

- **Inom omfång** – vad issuet levererar.
- **Utanför omfång** – vad det medvetet inte gör.
- **Acceptanskriterier** – observerbara, testbara punkter.

### Definition of Done – de fem reglerna konkret
1. **Datasäkerhet** – modell med default, codec, rules, `collections.mjs`, rundturstest, och vid
   3.x-data konverteraren med fixtur (OMB-3); eller "ingen persisterad ändring".
2. **Tester** – nivåer och klasser; kontraktstester för skärmar på ramarna; bugg →
   regressionstest som faller före fixen.
3. **Krav** – ID:n att lägga till, ändra eller stryka.
4. **Enhetligt** – vilka delade delar som används, vad nytt som byggs delat;
   `UiConsistencyTest` och `cpdCheck` gröna.
5. **CI-budget** – nya beroenden och deras byggtidseffekt; eller "ingen påverkan".

Plus **mockup** (Design) för ny GUI, **integritet** och **tillgänglighet** där det är relevant.
Gäller en regel inte, skriv varför. Stort arbete → föreslå uppdelning i under-issues
(`mcp__github__sub_issue_write`), var och en med egen DoD.

## Steg 6 – Utkast

```markdown
## Sammanfattning
<vad och varför>

## Design
<mockup-länk, eller "ingen GUI-ändring">

## Beteende / Föreslaget upplägg
1. …

## Återbruk (regel 4)
- Ramar: …
- Komponenter: …
- Data/`:core`: …
- Nytt delat som byggs först: … (eller "inget")
- Feature-kod: …

## Berörda filer
- `app/src/main/…`

## Datasäkerhet
<fält/samling och kedjan, eller "ingen persisterad ändring">

## Krav (KRAVLISTA.md)
- <ID och radtext>

## Testplan
- `:core`: …
- `app/src/test`: … (inkl. kontraktstester, skärmdumpar)
- `tools/db`: …

## Acceptanskriterier
- [ ] <beteende>
- [ ] Mockup godkänd och skärmdumpar i PR:en (om GUI)
- [ ] Datakedjan komplett och rundturstest grönt (om data)
- [ ] KRAVLISTA uppdaterad
- [ ] Tester tillagda/uppdaterade och gröna
- [ ] `UiConsistencyTest` och `cpdCheck` gröna

## Utanför omfång
- …

## Risker / Öppna frågor
- …
```

För **buggar**: byt Föreslaget upplägg mot **Steg för att återskapa**, **Förväntat**,
**Faktiskt**, **Miljö**, och kräv regressionstestet.

Titel: `<område>: <sammanfattning i imperativ>`. Visa hela utkastet och **vänta på
bekräftelse**. Fråga om etiketter (`funktion`, `arkitektur`, `ai-regler`, `ombyggnad`, `prio:1–3`, `epic`).

## Steg 7 – Skapa eller uppdatera

- **Nytt:** `mcp__github__issue_write` (method `create`, owner `ParTee71`, repo `Dagboken`,
  title, body, labels; `parent_issue_number` om det hör till en epic).
- **Befintligt:** `method: update` med hela den förfinade texten – lägg inte till i slutet.

Rapportera nummer och länk.

## Steg 8 – Nästa steg

Erbjud kort: genomför nu (`implement-issue`), skapa uppföljande issues för det som låg
utanför omfånget, eller låt det ligga planerat.
