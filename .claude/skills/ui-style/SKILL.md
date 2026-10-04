---
name: ui-style
description: Dagbokens designspråk i 4.0 – känslan "I · Papper och teal" (Material 3 Expressive): lugn dagbokskänsla med luft, en tydlig gör-något-färg och belöning när dagen är klar. Ladda denna när du bygger eller ändrar tema, färger, typografi, former, rörelse, texter, ikoner, tomma tillstånd, belöningsläget eller en komponents utseende, och tillsammans med skill mockup. Trigger-ord: design, stil, utseende, färg, palett, tema, mörkt läge, ljust läge, papper, teal, solgul, terrakotta, typsnitt, font, Fraunces, Figtree, serif, rundad, hörn, kort, animation, rörelse, fjädrande, Expressive, konfetti, belöning, allt klart, framstegsrad, ikon, emoji, text, formulering, tomt tillstånd, kontrast, snyggt.
---

# Designspråket · I "Papper och teal"

**Känsla:** en lugn dagbok med luft. Pappersyta och vita kort utan kantlinje, **en** tydlig
gör-något-färg, och en liten belöning när dagen är klar. Appen används varje dag och ofta när
man mår dåligt – den ska vara stillsam, tydlig och aldrig stressa.

**Var saker står (enda källor):**
- Värden och motiv → `ARKITEKTUR.md` → "Designspråk" (beslut 10 i ADR-001) och KRAVLISTA §23 (DSN-1…6).
- Referens: Design-canvasen https://claude.ai/artifact/MP2fPHimTk8tAmKkpr3K5t (känsla I, rad 2d,
  och nyckelskärmarna). Mockupens värden är en approximation – **koden i `ui/theme` är facit**,
  med kontrasten uträknad.
- Vilken komponent eller ram som används för vad → skill `shared-ui-components`.
- Exakta Expressive- och Navigation 3-API:er → `VerifiedApisTest` (`app/src/test`), som kompilerar och kör dem, och versionskatalogen `gradle/libs.versions.toml` – gissa aldrig ett API-namn.
- Tokens i kod → `ui/theme` (`AppColors`, `AppTypography`, `AppShapes`, `Spacing`). Feature-kod
  hårdkodar aldrig färg, form, typografi eller avstånd (regel 4, NFR-9).

## Tema

- `MaterialExpressiveTheme` med `MotionScheme.expressive()`. **Ingen dynamic color** – appen har
  en egen identitet (3.x-inställningen "dynamisk färg" följer inte med).
- Lägen: ljust, mörkt och **auto** som växlar på klockslag (SET-1, DSN-5).

| Roll | Ljust | Användning |
|---|---|---|
| Yta | papper `#FBF7EE` | bakgrund på alla skärmar |
| Kort | vit, utan kantlinje | alla sektions- och postkort (`AppCard`) |
| Primär | teal `#0B6E66` | kryss, knappar, aktiv flik, idag-chippet i datumremsan |
| Gör-något | solgul `#F5B631` med mörk text | plusknappen, "Snart", punkten för idag, framstegsraden när dagen är klar |
| Varning | terrakotta (`#B85C38` i mockupen, mörkare i koden för kontrast) | försenat, periodslut |
| Text / dämpad | `#1C1A14` / `#6B655A` | brödtext / undertext och avbockat |
| Energiskala | låg `#B5443A` · mitt `#C98A1B` · hög `#2F7D5B` | diagram, energichips, postkortets vänsteraccent (NFR-16) |

- **Kontrast:** varje färg som bär text klarar **4,5:1** mot sin bakgrund (DSN-1) – räkna efter vid
  varje ny nyans, även i mörkt tema. Solgult bär alltid mörk text.
- **Mörkt tema** härleds med samma roller på djup skogsgrön botten (DSN-5); varje komponent har
  Roborazzi-referens ljust + mörkt.
- **En gör-något-färg:** solgult används sparsamt – bara för det användaren ska göra härnäst eller
  för belöningen. Teal är "det här är valt/klart".
- **Accent = status, aldrig dekoration** (NFR-16): energifärg, aktiv/inaktiv, pågående.

## Typografi (`AppTypography`)

- **Fraunces 500/600** (serif) för rubriker: skärmtitel, dagens rubrik ("Lördag 4 oktober"),
  sektionsrubriker, stora tal i sammanfattningskortet.
- **Figtree** för brödtext, rader, chips och knappar.
- Typsnitten bundlas i `res/font/` (SIL Open Font License 1.1) och laddas **aldrig** ned – appen
  ser likadan ut offline (DSN-2). Licenstexten följer med appen; ett nytt typsnitt läggs till där.
- Namngivna stilar (`screenTitle`, `headline`, `sectionTitle`, `bigNumber`, `itemTitle`,
  `itemSubtitle`, `body`, `quantity`, `pill`, `caption`, `button`). Aldrig `fontSize`,
  `TextStyle` eller `MaterialTheme.typography` i feature-kod.
- Siffror som betyder något (energi, antal doser, steg) står stora och tydliga; enheter i dämpad text.

## Former och avstånd (`AppShapes`, `Spacing`)

- Kort **22 dp**, chips och knappar **helt rundade** (`pill`), datumremsans chip **18 dp**, ark
  **28 dp** upptill (`sheet`), rader 18 dp, fält 14 dp (DSN-3).
- `Spacing`: `xs 4 · s 8 · m 12 · l 16 · xl 24 · xxl 32` dp – enda tillåtna avstånden.
- **Luftigare än ReseApoteket:** mer avstånd mellan kort (`l`) än inuti dem (`m`); ett kort per
  ämne hellre än täta listor.
- Tryckytor minst **48 dp** i båda riktningarna (NFR-14).

## Rörelse och belöning

- **Fjädrande kryss** (Expressive spring) som tonar raden till dämpad avbockad stil.
- **Framstegsraden** fylls animerat när doser och mående loggas.
- **Belöningsläget** (HEM-19, DSN-4): när alla dagens doser och aktiverade måendetillfällen är
  klara byter rubriken till "Allt klart för idag", framstegsraden blir solgul, `Confetti` faller
  **en gång** per dag och ett grönt sammanfattningskort (`DayDoneCard`) visar snittenergi,
  jämförelse med igår och dagar i rad.
- Skärmbyten: samma rörelse överallt, definierad en gång i `navigation/Transitions`.
- Rörelse förstärker och blockerar **aldrig** – inga väntetider för att se en animation, och
  systemets "ta bort animationer" respekteras.

## Beteende hör till stilen

Samma bekräftelser, samma ångra-snackbar, samma tomma tillstånd, samma felmeddelanden och samma
kortgester överallt – de kommer från ramarna och komponenterna i `shared-ui-components` och
kort-/radstandarden NFR-15–18, aldrig från en enskild skärm.

## Texter

- Korta, vardagliga, svenska och vänliga: "Logga nu", "Allt klart för idag", "Hur mår du?",
  "Snart", "Visa äldre".
- Inga medicinska förkortningar utan förklaring; doser med enhet ("50 µg", "2 tabletter").
- Återkommande texter finns en gång i `strings.xml` och används av ramarna.
- Tomma tillstånd: en mening om vad som saknas + en primärknapp som löser det.

## Ikoner och emoji

- **Linjeikoner 24 dp** i kontroller (knappar, flikar, menyer): `res/drawable/ic_*`, rundad linje i
  Material Symbols-rutnätet – samma uppsättning som canvasen. **Aldrig emoji som knappikon.**
- Emoji bara i innehåll där användaren själv valt dem, och i firandet.

## Skillnad mot ReseApoteket (DSN-6)

Komponentkatalogen delas, utseendet inte: ingen korall/aprikos, ingen Nunito, serif-rubriker,
luftigare kort, inga personfärger. Kopiera aldrig ReseApotekets färg- eller typografivärden.

## Tillgänglighet

Enligt skill `accessibility-compose` – semantiken ligger i komponenterna; diagram har talbar
sammanfattning (NFR-14).

## Skärmdumpar

Varje komponent har Roborazzi-referens i ljust och mörkt; en designändring uppdaterar
referenserna i samma PR (skill `testing-strategy`).

## Expressive-opt-in

Opt-in för experimentella Expressive-API:er görs bara i `ui/theme` och `ui/components`
(`@file:OptIn`), så att feature-koden inte sprider annotationer.
