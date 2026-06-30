---
name: accessibility-compose
description: Tillgänglighet (a11y) i Dagbokens Compose-UI — appen används dagligen för hälsologgning och ska fungera med TalkBack, stor text och tillräckliga tryckytor. Ladda denna när du bygger eller ändrar UI: ikoner, knappar, klickbara ytor, bilder, sliders, kort, fält. Trigger-ord: tillgänglighet, accessibility, a11y, TalkBack, contentDescription, semantics, touch target, tryckyta, skärmläsare, dynamisk text, kontrast, mergeDescendants.
---

# Tillgänglighet i Compose

En hälsodagbok används av alla — inklusive med skärmläsare, förstorad text och nedsatt
motorik. Tillgänglighet är ett kvalitetskrav, inte en extrafunktion. Projektet använder
redan `contentDescription` på många ställen; den här skillen kodifierar regeln. Bygg på
de delade komponenterna (skill `shared-ui-components`) så blir a11y konsekvent.

## Regler

### 1. contentDescription — meningsbärande vs dekorativt
- **Meningsbärande** ikon/bild (en `IconButton`, statusikon, avatar) ska ha en
  beskrivande, **svensk** `contentDescription` (helst från `strings.xml`).
- **Rent dekorativ** grafik ska ha `contentDescription = null` så att TalkBack hoppar
  över den — fyll inte skärmläsaren med brus.
- Upprepa inte synlig text i `contentDescription` på samma nod (dubbelläsning).

### 2. Tryckytor minst 48dp
Alla klickbara element ska ha minst **48×48dp** tryckyta (Material-riktlinje). Små ikoner
ska använda `IconButton`/`Modifier.minimumInteractiveComponentSize()` eller padding —
inte en naken 24dp-ikon med `clickable`.

### 3. Stöd dynamisk textstorlek
Text och `lineHeight` i **`sp`**, inte `dp`. Lås inte höjder så att förstorad text
klipps. Testa layouten med stor systemtextstorlek.

### 4. Gruppera och semantik
- Sammansatta kort/rader som är **en** logisk enhet: `Modifier.semantics(mergeDescendants = true)`
  så TalkBack läser dem i ett svep.
- Ge tillstånd semantik: t.ex. `stateDescription` ("tagen"/"ej tagen") för medicin-toggeln,
  `Role.Button`/`Role.Checkbox` där det är otydligt.
- Sliders/graderingar (`SliderRow`, `SymptomLogCard`) ska kommunicera värde och etikett —
  utöka den delade komponenten om semantiken saknas, fixa inte per anropare.

### 5. Färg är aldrig ensam bärare av information
Energifärg, "försenat"-status osv. ska också ha text/ikon — färgblinda och skärmläsare
ska få samma information. Säkra rimlig kontrast mot bakgrund i båda teman (ljust/mörkt).

## Tester (regel 2)

Compose-UI-test kan assertera semantik utan emulator-TalkBack:
```kotlin
composeTestRule.onNodeWithContentDescription("Ta bort").assertHasClickAction()
composeTestRule.onNodeWithText("Tagen").assertIsDisplayed()
```
Lägg/utöka sådana assertions när du ändrar en interaktiv komponent. För nya delade
komponenter, verifiera att en meningsbärande `contentDescription` finns.

## Checklista

- [ ] Meningsbärande ikoner/bilder har svensk `contentDescription`; dekorativa har `null`.
- [ ] Klickbara element ≥ 48dp tryckyta.
- [ ] Text i `sp`; layouten tål förstorad text.
- [ ] Logiska grupper merge:ade; tillstånd har `stateDescription`/`Role` vid behov.
- [ ] Ingen information enbart via färg; rimlig kontrast i ljust och mörkt tema.
