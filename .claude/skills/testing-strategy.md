---
name: testing-strategy
description: Dagbokens testregel (regel 2) — ingen beteendeändring utan tester på rätt nivå(er). Ladda denna ALLTID när du lägger till, ändrar eller fixar funktionalitet, en ViewModel, ett UseCase, ett repository, en DAO, en composable, en mappning eller en bugg. Trigger-ord: test, tester, JUnit, MockK, Turbine, FakeDao, ViewModelTest, instrument, androidTest, Compose UI-test, createComposeRule, coverage, täckning, regression, TDD.
---

# Teststrategi: tester på alla nivåer

**Regel:** En ändring av synligt beteende är inte klar förrän tester lagts till eller
uppdaterats på varje berörd nivå. Befintliga tester som påverkas **uppdateras** så att
de speglar det nya beteendet — de tas aldrig bort eller försvagas bara för att bli
gröna. Krav: **NFR-6**.

## Nivåer och var de bor

| Nivå | Källkatalog | Verktyg | Testar |
|---|---|---|---|
| Enhet | `app/src/test/kotlin/...` | JUnit, MockK, Turbine, kotlinx-coroutines-test | ViewModels, UseCases, mappers, repository-logik, ren Kotlin. |
| Instrument | `app/src/androidTest/kotlin/...` | AndroidX Test, Compose-test, Room in-memory | Compose-UI, Room-DAO/migrering, riktig Android-runtime. |

Paketstrukturen i testerna **speglar** `main` (t.ex. en ViewModel i
`ui/handelser/` testas i `test/.../ui/handelser/`).

## Vad ska testas när du ändrar X

| Ändring | Lägg till / uppdatera |
|---|---|
| **ViewModel / UiState** | Enhetstest: initialt state, varje event/action → nytt state, fel-state. Observera `StateFlow` med Turbine. Injicera test-dispatcher. |
| **UseCase / domänlogik** | Enhetstest med kantfall (cooldown, dosgräns, idempotens, datum/tid). |
| **Repository** | Enhetstest mot **Fake**-DAO (se `FakeNoteDao`-mönstret), inte mockad Room. Verifiera `Flow`-emission och `Result`-mappning av fel. |
| **DAO / SQL / @Query** | Instrumenttest mot in-memory Room. |
| **Room-schemaändring** | Instrument-`MigrationXYTest` + `exportSchema`. |
| **Backup/restore** | Se skill `data-safety-backup` — rundtur + serialisering + mapper. |
| **Composable / skärm** | Compose-UI-test: rendering, knapp aktiverad/inaktiverad, klick → callback. |
| **Bugfix** | Skriv först ett test som **reproducerar buggen** (faller), fixa sedan. |

## Projektmönster att följa

- **Fake-DAO framför mock för datalagret.** Repositories testas mot in-memory
  `Fake*Dao` som implementerar DAO-interfacet (jfr `FakeNoteDao`). När du lägger en
  metod på ett DAO-interface måste motsvarande Fake uppdateras — annars bryts bygget
  (jfr commit "implementera nya NoteDao-metoder i FakeNoteDao").
- **Injicera dispatcher** (`IoDispatcher`) så tester kan köra på
  `StandardTestDispatcher`/`UnconfinedTestDispatcher`. Kalla aldrig `Dispatchers.IO`
  direkt i testbar kod.
- **Turbine** för `StateFlow`/`Flow`-assertions, inte manuell `collect`.
- **MockK** för samverkande beroenden (repositories i en ViewModel-test). När du lägger
  ett konstruktorberoende på en ViewModel måste alla dess tester få med mocken (jfr
  commit "uppdatera MigrationViewModelTest med HandelserRepository-mock").

## Innan du anser dig klar

```bash
./gradlew :app:testDebugUnitTest               # enhetstester — måste vara gröna
./gradlew :app:compileDebugAndroidTestKotlin   # instrumenttester ska minst kompilera
./gradlew :app:connectedDebugAndroidTest       # kör om emulator finns
```

CI kör enhetstester + kompilering av instrumenttester vid varje PR. Lämna aldrig en
ändring där `main` kompilerar men en Fake/mock i test slutat matcha ett interface.

## Anti-mönster

- Att ta bort eller `@Ignore`:a ett rött test för att "bli klar". Förstå varför det
  blev rött och uppdatera det.
- Att testa implementation i stället för beteende (assertion på interna anrop när
  resultatet räcker).
- Att lägga ny funktion utan något test alls "för att den är liten".
