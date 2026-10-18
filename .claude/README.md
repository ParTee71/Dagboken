# Claude-uppsättningen i Dagboken

Översikt över agenter och skills i `.claude/`. Reglerna står i [CLAUDE.md](../CLAUDE.md);
den här filen läses vid behov, så att CLAUDE.md – som laddas vid varje uppgift – hålls kort.
Dokumentkontrollen (`.github/scripts/check-links.mjs`) kräver att tabellerna listar exakt det
som finns i `.claude/agents/` och `.claude/skills/`.

## Agenter (`.claude/agents/`)

| Agent | När |
|---|---|
| `byggare` | Portar, komponenter, skärmar på ramarna och CI-filer enligt en klar plan (Opus · medium). |
| `arkitekt` | Datamodell, codecs, 3.x-konverteraren, rules, migrering och arkitekturval (Opus · high). |
| `granskare` | Före varje PR: granskar diffen mot de fem reglerna och mockup-regeln, rapporterar i en tabell. |
| `ci-doktor` | Vid rött CI: läser loggar, skärmdumpsdiffar och CPD-rapporter, föreslår fix. |
| `testskrivare` | Skriver/utökar tester och fixturer med de delade testhjälparna – när tre eller fler testnivåer berörs. |
| `db-inspektor` | Svarar på frågor om faktisk data i Firestore, endast läsning (Haiku · low). |

## Modellval

Modellen väljs efter uppgiftens komplexitet – för sessionen och för varje agent. **Opus är
standard.** Frontmatterns modell är agentens standard; huvudsessionen anger `model` när
agenten startas, efter uppgiften. Sessionen byter själv via `/model`, eller låter en billigare
agent ta rutindelarna.

| Uppgift | Modell |
|---|---|
| Uppslag och läsning – loggar, data, sökningar | Haiku |
| Små avgränsade ändringar – en fil, en komponent, ett test | Sonnet |
| Flera lager, datamodell, migrering, backup och granskning | Opus |
| Väldigt komplexa issues – anges då i issuets **Agent · modell · effort** | Fable |

Skills `refine-issue` och `implement-issue` sätter och följer **Agent · modell · effort** per
issue utifrån den här tabellen.

## Skills (`.claude/skills/`)

| Skill | När |
|---|---|
| `android-projekt` | Dagbokens delar av pluginens `android:`-skills (se nedan) – ladda alltid tillsammans med dem; mockupmallen och mockupens data och skärmdumpar. |
| `notifications-alarms` | Påminnelser och larm: exakta larm, kanaler, omschemaläggning, schemat ur Firestore-cachen. |
| `data-privacy-security` | Hälsodata, loggning, EU-region, hemligheter, notiser, minify. |
| **`data-safety-backup`** | Regel 1 – codecs, schemaversion, export/import, rundtur och 3.x-konverteraren. |
| **`testing-strategy`** | Regel 2 – testnivåer, kontraktstester, skärmdumpar. |
| **`requirements-kravlista`** | Regel 3 – hålla KRAVLISTA.md aktuell, paritetschecklistan. |
| **`shared-ui-components`** | Regel 4 – komponenter, ramar, byggstenar, förbud, kontroller. |
| **`ci-budget`** | Regel 5 – vad som körs när i CI. |
| `ui-style` | Designspråket Papper och teal (Material 3 Expressive). |
| `diagram` | Diagrammen i `ui/diagram` och diagrammatematiken i `:core/engine` (TRD-6…18, HEM-7, HLS-10/11/13). |
| `firestore-data-layer` | `FirestoreCollection`, `DocCodec`, repositories, `UserSession`, offline, rules. |
| `db-access` | Läsa databasen från en Claude-session. |
| `refine-issue` | Förfina en idé till ett planerat issue (med Design och Återbruk). |
| `implement-issue` | Genomföra ett issue hela vägen till PR enligt reglerna (med merge-kontroll först). |
| `release` | Endast vid uttrycklig release. |

Elva generella skills kommer från pluginen `android@claude-android` och heter `android:<namn>`
i sessionen: `android-dev` (baslinje, ladda alltid), `compose-expert`, `kotlin-coroutines`,
`kotlin-flows`, `navigation-3`, `accessibility-compose`, `android-gradle-logic`, `firebase-auth`,
`mockup` (mockup före ny GUI) samt Googles `android-intent-security` (manifest, notisers
`PendingIntent`, delning) och `firebase-security-rules-auditor` (vid varje ändring i `firestore.rules`).
Pluginen är generell; Dagbokens delar och nuvarande avvikelser står i `android-projekt`, och där
de krockar gäller projektets regler. Planerad skill (ARKITEKTUR.md): `health-connect` tillkommer i etapp 6.

## Hooks (`.claude/hooks/`) och inställningar

Hur hookarna, behörigheterna och SDK-skriptet fungerar står i README, avsnittet
[Utveckling via Claude](../README.md#utveckling-via-claude).

## Gemensam plugin

`.claude/settings.json` slår på pluginen `android@claude-android` från det privata repot
[ParTee71/claude-android](https://github.com/ParTee71/claude-android) (`extraKnownMarketplaces`
+ `enabledPlugins`), som bär de generella Android-skillsen ovan. Lokalt (Android Studio, CLI)
installeras den därifrån. Molnsessioner får den via miljöns setup-skript, som klonar repot till
`/home/user/claude-android`. Repospecifikt hör aldrig hemma i pluginen – det står i `android-projekt`.
