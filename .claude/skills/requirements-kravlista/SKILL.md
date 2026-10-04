---
name: requirements-kravlista
description: Dagbokens kravregel (regel 3) — KRAVLISTA.md ska alltid spegla appens faktiska beteende, och under ombyggnaden till 4.0 är den paritetschecklistan (OMB-6). Ladda denna ALLTID när du lägger till, ändrar eller tar bort synligt beteende, en funktion, en skärm, en flik, en inställning, en beräkning, ett UI-flöde eller en regel för data/backup/migrering/CI. Trigger-ord: krav, kravlista, KRAVLISTA, specifikation, requirement, feature, funktion, beteende, formel, beräkning, paritet, 4.0, ombyggnad, versionsbump, ändra UI, ta bort funktion, NFR, OMB, DSN, MEDF, HIST, TRD.
---

# Hålla kraven aktuella (KRAVLISTA.md)

**Regel:** varje ändring av användarsynligt beteende – och av icke-funktionella regler
som tester, CI och backup – speglas i [KRAVLISTA.md](../../../KRAVLISTA.md) i **samma**
PR. Kraven är projektets sanning om vad appen ska göra; kod och krav får aldrig glida isär.

## Format och konventioner

KRAVLISTA.md består av numrerade avsnitt med tabeller `| ID | Krav |`. Varje krav har ett
**stabilt ID** med prefix per område:

| Prefix | Område (avsnitt) |
|---|---|
| `ÖV` | Översikt och syfte (§1) |
| `TP` | Teknisk plattform (§2) |
| `NAV` | Navigation – flikar, avatarark, plusknapp (§3) |
| `HEM` | Idag-fliken, inklusive belöningsläget (§4) |
| `AKT` | Logga aktivitet (§5.1) |
| `SCR` | Mående/screening (§5.2) |
| `HIS` | 3.x-historik per flik – ersatt av `HIST` (§5.3, §6.4) |
| `MED` | Mediciner och doser (§6.1) |
| `REC` | Recept och scheman (§6.2) |
| `FAV` | Vid behov-mediciner (§6.3) |
| `DIA` | 3.x-diagram – borttaget, ersatt av `TRD` (§7) |
| `AUTH` | Konto och inloggning (§8) |
| `BCK` | Backup, export och import (§9) |
| `NOT` | Notifikationer och påminnelser (§10) |
| `SET` | Inställningar (§11) |
| `DAT` | Datamodell (§12) |
| `NFR` | Icke-funktionella krav, inklusive kort- och radstandarden NFR-15–18 (§13) |
| `FUT` | Kända begränsningar / framtida arbete (§14) |
| `SJ` | Sjukdomar (§15) |
| `HIST` | Dagbok-fliken – tidslinjen (§16) |
| `TRD` | Trender-fliken (§17) |
| `HANT` | 3.x Hantera-ytan – borttagen 4.0 (§18) |
| `HLS` | Hälsa via Health Connect (§19) |
| `WID` | Widget – borttagen (§20) |
| `MEDF` | Mediciner-fliken (§21) |
| `OMB` | Ombyggnad och migrering (§22) |
| `DSN` | Designspråk (§23) |

| Du gör | Så här |
|---|---|
| **Nytt beteende** | Ny rad i rätt tabell med **nästa lediga ID** i serien (t.ex. `PER-4`). Återanvänd aldrig ett ID. |
| **Ändrat beteende** | Redigera texten på befintligt ID. Behåll ID:t. |
| **Borttaget beteende** | Behåll raden, stryk: `~~Visa **stat-pills**: …~~ *(borttaget)*` – med hänvisning till det som ersätter den när det finns (`*(borttaget 4.0 – NAV-8/NAV-9)*`). |
| **Ändras i ombyggnaden** | Behåll ID:t, skriv 4.0-lydelsen och märk `*(4.0)*`; står 3.x-lydelsen kvar som hänvisning skrivs den `*(4.0 – 3.x: …)*`. Kravlistan på `master` beskriver 4.0; 3.27.0 har sin egen på branchen `legacy`. |
| **Planerat men ej byggt** | Markera `*(planerad)*` sist i raden; ta bort markeringen i PR:en som bygger det. |
| **Helt nytt område** | Nytt numrerat avsnitt och nytt prefix – lägg även prefixet i tabellen ovan. |

## Gemensamma beteenden beskrivs en gång

Beteenden som ramarna och komponenterna ger alla skärmar (redigering med spara och "Släng
ändringar?" NFR-10/NFR-12, postkortets gester NFR-15/16, listraden NFR-17, ihopfällbara
sektionskort NFR-18, designsystemet NFR-9, mockup NFR-20, CI NFR-19) står som **NFR-/NAV-krav
en gång**. Funktionskraven refererar till dem (`Receptkortet följer NFR-15 och NFR-16.`) i
stället för att upprepa texten – regel 4 gäller även kravlistan. NFR-15–18 är mer genomarbetade
än ReseApotekets motsvarigheter och behålls som de är (ARKITEKTUR.md).

## Beräkningar är krav

Regler som räknas – kylperiod och dagsgräns för vid behov (FAV), doshöjningar och periodslut
(REC, NOT), stabila dos-id:n (MED-4, DAT-8), dagens energisnitt och belöningsläget (HEM),
diagrammatematik (TRD) – står i kravlistan **som de implementeras** i `:core`. Ändras en regel
ändras kravraden i samma PR, och testet som bevisar den.

## Paritetschecklistan (OMB-6)

Under ombyggnaden är varje krav som inte är struket en paritetspunkt: första release av 4.0
görs först när alla är uppfyllda. Varje PR bockar av de krav den uppfyller i PR-beskrivningen
(ID-lista) – en kravrad som inte går att peka på i kod och test är inte klar.

## Spårbarhet

- Varje krav ska kunna bevisas av ett test (regel 2). Skriv krav-ID i testnamnet eller en
  kommentar när det inte är uppenbart (`// BER-3`).
- Krav om persisterad data och migrering (DAT, BCK, OMB) hänger ihop med skill `data-safety-backup`.
- Krav om utseende (DSN, NFR-9) hänger ihop med skills `ui-style` och `shared-ui-components`.
- Krav om CI och tester (NFR) hänger ihop med skills `ci-budget` och `testing-strategy`.

## Följdartefakter

- **README.md** – funktionslistan och versionshistoriken när en funktion tillkommer eller försvinner.
- **ARKITEKTUR.md** – när ett krav ändrar ett arkitekturbeslut (enda källan för besluten).
- **Version** – `versionName`/`versionCode` i `version.properties` höjs **bara vid release**,
  aldrig i en funktions-PR. Releasen görs bara på uttrycklig begäran (skill `release`).

## Checklista

- [ ] Rätt tabell och ID-serie.
- [ ] Nytt ID / redigerat / struket med `~~…~~ *(borttaget)*`; `*(4.0)*`-märkt vid ombyggnad; `*(planerad)*` borttaget när det byggts.
- [ ] Inga ID:n återanvända eller raderade.
- [ ] Gemensamma beteenden refererade, inte upprepade.
- [ ] README uppdaterad om funktionsomfånget ändrats (version höjs vid release).
- [ ] Ett test bevisar det nya/ändrade kravet.
- [ ] Kravets ID står i PR-beskrivningen (paritetschecklistan, OMB-6).
