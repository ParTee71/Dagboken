---
name: release
description: Dagbokens release-flöde — föreslå versionshöjning, bekräfta, uppdatera version.properties och README, landa release-commiten på master och trigga release.yml via workflow_dispatch så att GitHub Actions kör grindarna (tester, lint, instrumenttester), skapar taggen, bygger den signerade APK:n och publicerar en GitHub Release. CI-först (fungerar från telefonen). Använd BARA när användaren uttryckligen ber om en release. Trigger-ord: release, släpp, ny version, publicera, versionshöjning, bygg APK.
---

# Release

Kör stegen i ordning och rapportera efter varje steg. Stanna och förklara om något fallerar.
**Kör bara när användaren uttryckligen ber om en release.**

> **Första releasen av 4.0** görs först när paritetschecklistan är grön (OMB-6) och grinden
> OMB-4 är passerad (en riktig 3.x-backup importerad och jämförd med noll skillnader). 3.27.0
> ligger kvar som tagg och på branchen `legacy` (OMB-1). 4.0 behåller `applicationId`, så den
> installeras som uppdatering ovanpå 3.x och migreringen på enheten (OMB-2) körs vid första start.

## Hur en release går till

- **Snålt med Actions-minuter (skill `ci-budget`):** `release.yml` kör bara det som inte redan
  körts. `build` gör lint och det signerade, minifierade bygget i en Gradle-körning, parallellt
  med instrumenttesterna (`instrumented`) – som hoppas över när samma kod redan testats grönt i
  en PR. `publish` körs när båda är gröna och kräver att CI
  redan är grönt på commiten (master-pushen har kört alla tester, `cpdCheck` och
  dokumentkontroller – de körs inte om). Är CI inte klart än: kör om bara `publish`-jobbet.
- **Bygg och signering:** i Actions; keystore (`dagboken.jks`) och lösenord från secrets
  (`SIGNING_*`). `app/google-services.json` är incheckad och behöver ingen secret. Ingen lokal SDK krävs.
- **Rules:** `publish` deployar `firestore.rules` (secret `FIREBASE_RULES_DEPLOYER`) före
  GitHub Release – bara om de ändrats sedan förra releasen. En publicering vars
  `firestore.rules` skiljer sig från `master` stoppas.
- **Testbygge utan publicering:** `workflow_dispatch` med `publish_release: "false"` ger bara den
  signerade APK:n som artifact – ingen tagg, ingen Release, ingen rules-deploy (kräver då inte
  `FIREBASE_RULES_DEPLOYER`). Används t.ex. för att installera 4.0 ovanpå 3.x och spara kopian (#228).
- **Ett fallerat jobb:** kör om bara det (`rerun_failed_jobs`), aldrig hela releasen.
- **Starta releasen först när CI på `master` är grönt** för merge-commiten – annars stoppar
  `gate` den direkt (det kostar sekunder, inte emulatorminuter).
- Rules jämförs mot förra `v*`-taggen. Skapades en tagg utan att `publish` lyckades, kör
  `rules.yml` med `deploy: true` om rules ändrats.
- **Publicering:** `release.yml` skapar taggen `vX.Y.Z` själv och publicerar en GitHub
  Release med changelog och APK. Två triggers: push av tagg, eller `workflow_dispatch` på
  `master` med `version_name` och `publish_release`.
- **Taggar pushas aldrig från en agentsession** – nätverkspolicyn blockerar `refs/tags/*`
  (HTTP 403). Använd dispatch-vägen.
- **Verktyg:** GitHub MCP (`mcp__github__*`); ingen `gh`.

## Steg 1 – Nuvarande version

Läs `versionCode` och `versionName` i `version.properties`.

## Steg 2 – Föreslå versionshöjning

Senaste tagg: `mcp__github__list_tags` / `mcp__github__get_latest_release` (owner `ParTee71`,
repo `Dagboken`). Commits sedan dess: `git log <tagg>..HEAD --oneline --no-merges`.

| Innehåll | Höjning |
|---|---|
| `BREAKING`, `!:`, borttagen funktion, schemaVersion höjd på ett sätt som kräver ny app | **major** |
| `feat`, ny skärm, ny förmåga | **minor** |
| `fix`, `chore`, `refactor`, `test`, `docs`, beroenden | **patch** |

Ny `versionCode` = nuvarande + 1. Visa:
```
Nuvarande: v<gammal>  (versionCode <gammal>)
Förslag:   v<ny>      (versionCode <ny>)
Skäl:      <en mening>
Commits:   <punktlista>
Kör v<ny>, eller en annan version?
```
**Vänta på bekräftelse.**

## Steg 3 – Uppdatera version, README och krav

1. `version.properties`: `versionCode` och `versionName`. Filen ligger utanför CI-filtren, så
   release-PR:en kör bara dokumentkontrollerna (README ändras också).
2. README "Versionshistorik": ny rad för `<ny>` med användarsynliga ändringar och `(#NN)`-referenser
   (ersätt "under ombyggnad" på 4.0.0-raden vid första releasen).
3. Kontrollera att KRAVLISTA.md speglar det som släpps (inga kvarvarande `*(planerad)*` för
   byggda funktioner) och att TP-kraven matchar SDK-nivåerna i byggfilen.
4. Har `schemaVersion` höjts sedan förra releasen: nämn det tydligt (skill `data-safety-backup`)
   och be användaren göra detta, i den här ordningen, **innan** release-bygget triggas (steg 5):
   1. deploya `firestore.rules` med den nya `maxSchemaVersion()` – kör `rules.yml` med
      `deploy: true` (`publish` deployar dem också, men stämplingen måste vänta på dem);
   2. stämpla användaren: `node tools/db/migrate.mjs --to <N> --dry-run`, sedan utan `--dry-run`.
   Äldre appar visar då "Uppdatera appen" (BCK-15) redan innan den nya appen kan skriva ett värde
   som de skulle läsa fel och skriva tillbaka. Appen stämplar själv bara när inget steg ändrar
   data (`SchemaMigrator.canStamp`) – och bara om de nya rules redan är deployade. Vänta med
   steg 5 tills användaren bekräftat båda.

## Steg 4 – Release-commit på master

Via PR (grön och mergad), eller direkt till `master` om användaren uttryckligen godkänt det.
```
git commit -am "Release v<ny>"
```
Skapa ingen tagg här.

## Steg 5 – Trigga release-bygget

```
mcp__github__actions_run_trigger
  owner: ParTee71, repo: Dagboken
  workflow_id: release.yml, ref: master
  inputs: { version_name: "<ny>", publish_release: "true" }
```
`version_name` utan `v`; `publish_release` som strängen `"true"`. Workflowen vägrar om
taggen redan finns.

Följ körningen: `actions_list` → `actions_get` → vid fel `get_job_logs` (eller agenten
`ci-doktor`) → `get_release_by_tag` för `v<ny>`.

**Manuell reserv:** från användarens egen dator `git tag v<ny> && git push origin v<ny>`.

## Steg 6 – Sammanfattning

```
Släppt:  Dagboken v<ny>  (versionCode <ny>)
Commit:  Release v<ny> på master
Tagg:    v<ny> (skapad av release.yml)
CI:      <länk / status>
GitHub:  <Release-länk> (signerad Dagboken-<ny>-<ÅÅÅÅMMDD-HHMM>.apk)
```

## Reserv – bygga lokalt i Android Studio

`./gradlew :app:assembleRelease` kräver `dagboken.jks` i `app/` och signeringslösenord via
`local.properties` eller `SIGNING_*`-miljövariabler (skill `data-privacy-security`). För en
publicerad Release, kör ändå steg 5.
