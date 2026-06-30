# GitHub Copilot – instruktioner för Dagboken

De fullständiga reglerna för det här repot finns i **[../CLAUDE.md](../CLAUDE.md)**.
Följ dem vid varje ändring. Sammanfattning av de fyra icke-förhandlingsbara reglerna:

1. **Datasäkerhet** — all persisterad data måste överleva en backup→restore-rundtur.
   Nya/ändrade fält ska in i `BackupJson` + `BackupMapper` + `DriveBackupRepository`
   och täckas av rundturstest. (`.claude/skills/data-safety-backup.md`)
2. **Tester på alla nivåer** — ingen beteendeändring utan tillagda/uppdaterade enhets-
   och instrumenttester. (`.claude/skills/testing-strategy.md`)
3. **Krav aktuella** — uppdatera `KRAVLISTA.md` i samma ändring.
   (`.claude/skills/requirements-kravlista.md`)
4. **Återanvänd delade komponenter** i `ui/components/` och `ui/diagram/` i stället för
   att bygga nya varianter. (`.claude/skills/shared-ui-components.md`)

Teknik: Kotlin · Jetpack Compose + Material 3 · MVVM · Hilt · Room · DataStore ·
Firebase Auth · Google Drive · WorkManager. Arkitektur och kommandon: se `CLAUDE.md`
och `README.md`. UI-strängar på svenska i `strings.xml`.
