---
name: requirements-kravlista
description: Dagbokens kravregel (regel 3) — KRAVLISTA.md ska alltid spegla appens faktiska beteende. Ladda denna ALLTID när du lägger till, ändrar eller tar bort synligt beteende, en funktion, en skärm, en inställning eller ett UI-flöde. Trigger-ord: krav, kravlista, KRAVLISTA, specifikation, requirement, feature, funktion, beteende, versionsbump, ändra UI, ta bort funktion.
---

# Hålla kraven aktuella (KRAVLISTA.md)

**Regel:** Varje ändring av användarsynligt beteende ska speglas i
[KRAVLISTA.md](../../KRAVLISTA.md) i **samma** ändring/PR. Kraven är projektets
sanning om vad appen ska göra — kod och krav får aldrig glida isär.

## Format och konventioner (följ exakt)

KRAVLISTA.md är numrerade tabeller per avsnitt. Varje krav har ett **stabilt ID** med
prefix per område:

`ÖV` översikt · `TP` teknisk plattform · `NAV` navigation · `HEM` hem · `AKT`/`SCR`/`HIS`
aktivitet/screening/historik · `MED`/`REC`/`FAV` mediciner · `DIA` diagram · `AUTH` konto
· `BCK` backup · `NOT` notiser · `SET` inställningar · `DAT` datamodell · `NFR`
icke-funktionellt · `FUT` framtida · `SJ` sjukdomar.

| Du gör | Så här uppdaterar du |
|---|---|
| **Nytt beteende** | Lägg en ny rad i rätt tabell med **nästa lediga ID** i den serien (t.ex. `MED-8`). Återanvänd aldrig ett gammalt ID. |
| **Ändrat beteende** | Redigera kravtexten på det befintliga ID:t. Behåll ID:t. |
| **Borttaget beteende** | Stryk men **behåll raden** med genomstrykning + notis: `~~Visa **stat-pills**: …~~ *(borttaget)*`. Ta inte bort raden helt — ID:t ska förbli spårbart. |
| **Helt nytt område** | Lägg ett nytt numrerat avsnitt och välj ett nytt ID-prefix. |

Exempel som redan finns i filen: `HEM-3`, `SET-3` (strukna med `~~…~~ *(borttaget)*`).

## Spårbarhet till kod och tester

- Krav som rör persisterad data (`BCK-2`, `SJ-7`, m.fl.) hänger ihop med skill
  `data-safety-backup` — uppdaterar du backup-omfånget, uppdatera `BCK-2`.
- Krav märkta med testbarhet (`NFR-6`) hänger ihop med skill `testing-strategy`.
- När du lägger ett krav, se till att det finns (eller skapas) test som bevisar det.

## Följdartefakter vid större ändringar

- **README.md** — uppdatera funktionslistan och versionshistorik-tabellen om en
  funktion tillkommer/försvinner.
- **Version** — en ny funktion eller borttagning motiverar ofta en versionsbump.
  Själva releasen görs separat via skill `release` (bara på uttrycklig begäran), men
  notera i commit/PR att kravet ändrats.
- **ATGARDSLISTA.md** — bocka av eller lägg till punkter om ändringen rör en pågående
  åtgärdspunkt.

## Checklista

- [ ] Rätt tabell och ID-serie hittad.
- [ ] Nytt ID tillagt / befintligt redigerat / struket med `~~…~~ *(borttaget)*`.
- [ ] Inga ID:n återanvända eller helt raderade.
- [ ] README/version uppdaterad om funktionsomfånget ändrats.
- [ ] Det finns test som bevisar det nya/ändrade kravet (regel 2).
