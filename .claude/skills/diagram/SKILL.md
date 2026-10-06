---
name: diagram
description: Dagbokens diagram – de delade diagrammen i ui/diagram (LineChart, IntervalBarChart, StackedBarChart, SparklineChart, MinMaxCaption, CompactDropdownButton) och diagrammatematiken i :core/engine (smart y-axel, trendlinje, staplar, dagens energi, sömnkvalitet). Ladda denna när du ritar, ändrar eller använder ett diagram, en trend, en axel, en sparkline eller en sömnpoäng, och tillsammans med shared-ui-components. Trigger-ord: diagram, graf, kurva, linjediagram, stapel, staplat, sparkline, trend, trendlinje, y-axel, axel, rutnät, zoom, panorering, Vico, Canvas, luckor, föregående period, Trender, sömnstadier, sömnkvalitet, dagens energi, MinMaxCaption, computeSmartYAxis, computeTrendLine.
---

# Diagram

**Invariant:** ett diagram ritas bara med komponenterna i `app/.../ui/diagram/`, och allt som räknas
(axel, trend, staplar, sammanfattning, zoom, dagens energi, sömnpoäng) räknas bara i
`core/.../core/engine/` med JUnit-tester. Kraven står i [KRAVLISTA.md §17](../../../KRAVLISTA.md#17-trender-yta-enhetliga-diagram-trd)
(TRD-6…TRD-18) och HEM-7, HLS-10, HLS-11, HLS-13, NFR-14 – den här filen sammanfattar dem för
koden; vid skillnad gäller kravlistan. Vilken komponent som är vilken elementtyp står i skill
`shared-ui-components` (enda tabellen).

## Reglerna i korthet (kravlistan är källan)

| Krav | I koden |
|---|---|
| TRD-6 – mjuk kurva med gradientfyllning, axeletiketter i temat | `VicoLinePlot` (kubisk interpolering, `AreaFill` med gradient), etiketter `chartLabelStyle` |
| TRD-7 – y-axeln smart efter värdena, alltid heltal, steg 1/2/5 × 10ⁿ ≥ 1 | `computeSmartYAxis`; diagrammen räknar själva axeln (`chartAxisFor`, `intervalAxisFor`, `stackedAxisFor`) – anroparen skickar aldrig in min/max |
| TRD-8 – energi per dag som spann min–max med dagsvärdet markerat | `IntervalBarChart` + `computeDailyEnergyStats`; visas redan med en dag |
| TRD-9 – lägsta och högsta som text under varje diagram, värdelinjer | `MinMaxCaption` ingår i varje diagram; `gridValuesFor` |
| TRD-10 – tvåfingerzoom och panorering, helt utzoomat från början, nollställs vid periodbyte | Vico `Zoom.Content` i `LineChart`, `ZoomPan` i stapeldiagrammen; nyckel = `xLabels` |
| TRD-12 – kompakt rullgardin för period och serier | `CompactDropdownButton` (menyn är `AppMenu`s) |
| TRD-13 – streckad minsta kvadrat-trend per serie, luckor hoppas över | `computeTrendLine`/`trendSegment`; solgul med en serie, seriens färg med flera |
| TRD-15 – luckor ritas aldrig som nollor | `gapFreeRuns` (kurvan bryts), `null` i alla diagramdata |
| TRD-16 – sömnstadier staplade, saknat segment tar ingen höjd | `StackedBarChart` + `stackBases`; axeln börjar på noll; staplarna ur `sleepStagePoints`, namn och färger i `sleepStageSegments` |
| TRD-17 – Jämför: varje serie 0–100 mot eget min/max, konstant = 50, luckor kvar | `indexSeries`/`compareSeries`/`CompareKey` i `CompareIndex.kt`; `LineChart(axis = COMPARE_AXIS, showCaption = false, footnote = …)`, legenden bär verkligt spann med enhet |
| TRD-11/TRD-15 – klockans serier i måttets enhet, "Allt" kapad vid 365 dagar | `WatchMetric`, `watchSeries`, `sleepQualitySeries`, `TrendRange.cappedDays`/`cappedReadFrom` i `WatchSeries.kt` |
| TRD-18 – föregående period nedtonad på samma x-index, egen trend | `LineChart(previous = …)`; grå med en serie, seriens färg nedtonad med flera |
| NFR-14 – talbar sammanfattning, tryckytor 48 dp | `ChartSemantics.kt`; `CompactDropdownButton` har 48 dp |
| HEM-7 – sparkline med dagens punkt, lägst · högst · idag | `SparklineChart` |
| HLS-10/11/13 – sömnkvalitet per natt med ålders- och könsnorm | `scoreSleepQuality`, `scoreNightlySleep`, `ageFromBirthYear` – ålder och kön som parametrar, aldrig ur en repository; klockdata persisteras aldrig (HLS-5) |

## Så används de

- **För lite data:** under två kända punkter (en dag för `IntervalBarChart`) visar diagrammet själv
  det gemensamma tomma läget "För lite data än" + uppmaning (`emptyHint`) – lägg aldrig ett eget.
- **Data:** ett värde per dag/natt i periodens ordning, `null` där inget finns. Lägg dagsvärden på
  dagarna med `alignTo` (dagens energi). Etiketter (`xLabels`) formateras med `DateFormat`.
- **Färger:** bara ur temat – kurvan är `primary`, trend och dagens punkt `secondary` (solgul),
  energiskalan via `scaleLevel(...).color`, sömnstadierna `AppColors.extended.sleepStages`.
  Stilvärden (mått, alfa) finns bara i `ChartStyle.kt`.
- **Kortet runt:** diagramkort i Trender är `Foldout` i `AppCard` (NFR-18, TRD-14); periodväljaren
  (`CompactDropdownButton`) står i titelraden och visas bara utfälld.
- **Vico** (3.x API, `com.patrykandpatrick.vico:compose`) anropas bara i `VicoLinePlot` – mönstret
  `[vico]` i `ui-forbidden.txt` stoppar det överallt utanför `ui/diagram`. Modellen byggs direkt ur
  datan (ingen `CartesianChartModelProducer`): inga asynkrona transaktioner, samma bild varje gång.

## Tester

- `:core` – `core/src/test/.../engine/` (portade 3.x-tester med samma värden + kantfall med fasta datum).
- Robolectric – `DiagramsTest` (semantik, tomt läge, rullgardin, 48 dp).
- Roborazzi – `DiagramScreenshotTest`, ljust + mörkt per diagram (tomt, ett värde, luckor, trend,
  föregående period, stor text).
