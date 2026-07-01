---
name: shared-ui-components
description: Dagbokens återbruksregel (regel 4) — använd de delade, generiska komponenterna i stället för att bygga nya varianter av samma sak. Ladda denna ALLTID när du bygger eller ändrar UI: en slider/sifferreglage, ett diagram/graf, ett kort, en datum/tid-väljare, en duration-väljare, en symptomgradering, en ihopfällbar sektion, en stat-pill eller liknande. Trigger-ord: composable, UI, komponent, slider, reglage, diagram, graf, chart, kort, card, foldout, datum, tid, picker, duration, symptom, wheel, ny skärm.
---

# Återbruk av delade UI-komponenter

**Regel:** Innan du bygger en UI-byggsten — sök i `ui/components/` och `ui/diagram/`.
Finns något som löser samma sak: **använd det, eller utöka det**. Bygg inte en ny,
nästan-likadan variant. Konsistens i utseende och beteende är ett krav, inte en bonus.

Innan ny composable skapas:
```
Grep i app/src/main/kotlin/se/partee71/dagboken/ui/components/ och ui/diagram/
```

## Komponentkatalog (`ui/components/`)

| Komponent | Använd för |
|---|---|
| `SliderRow` | Etiketterad M3-slider med värdesetikett. Standard för **alla** sifferreglage 0–10 o.dyl. Parametrar: `valueRange`, `steps`, `valueLabel`, `valueLabelColor`. |
| `GradientSliderRow` | Slider med färggradient (t.ex. energi −10…+10 med färgkodning). |
| `SymptomLogCard` | Symptomval + gradering 0–10. Samma komponent i aktivitet, screening **och** sjukdomsincheckning (krav SJ-3). |
| `DateTimeRow` | Välja datum + tid. |
| `DurationRow` | Ange tidsåtgång (timmar + minuter). |
| `WheelPicker` | Hjul-väljare (t.ex. tid/tal). |
| `Foldout` | Ihopfällbar/utfällbar sektion (foldout). |
| `DagbokenCard` | Standardkort (yta, padding, hörn) — basen för kortliknande ytor. |
| `NoteField` | Fritext-anteckningsfält. |
| `StatPill` | Liten statistik-pill/etikett. |
| `NoteField`, `AccountBubble`, `AccountBottomSheet` | Anteckning resp. konto-avatar/sheet. |

## Diagram (`ui/diagram/`)

| Komponent | Använd för |
|---|---|
| `LineChartCanvas` | **All** linjediagramsritning (trender energi/stress, sparkline på hem). Bygg inte en ny chart-canvas. |
| `DiagramLayout` / `DiagramScreen` | Diagramskärmens ramverk och seriehantering (visa/dölj serier, tidsintervall). |

Nytt diagrambehov → utöka `LineChartCanvas` (ny serie, ny stil) i stället för att rita
egen `Canvas`.

## När en delad komponent inte räcker

1. **Utöka den först.** Lägg en parameter med ett default som bevarar nuvarande
   beteende (jfr `SliderRow`s `valueLabel`/`valueLabelColor`). Befintliga anropare
   ska inte behöva ändras.
2. **Bara om det är en genuint annan byggsten** skapar du en ny komponent — och då i
   `ui/components/` (eller `ui/diagram/`) så att även den blir delad, inte gömd i en
   feature-mapp.
3. Håll komponenten **stateless**: ta emot `value` + `onValueChange`/callbacks, hoista
   state till anroparen (se skill `compose-expert` och `android-dev`).

## Anti-mönster

- Egen `Slider {}` inline i en skärm i stället för `SliderRow`.
- Ny privat `@Composable` `MyCard` när `DagbokenCard` finns.
- Egen `Canvas`-graf vid sidan av `LineChartCanvas`.
- Kopierad symptom-UI i stället för `SymptomLogCard`.
- En "nästan likadan" komponent i en feature-mapp som borde bo i `ui/components/`.
