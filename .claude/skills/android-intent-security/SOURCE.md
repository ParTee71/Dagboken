# Källa

Kopia av Googles officiella skill **`android-intent-security`** – ändras inte här, så att den kan bytas mot en
ny version rakt av.

| | |
|---|---|
| Repo | https://github.com/android/skills |
| Sökväg | `security/android-intent-security` |
| Commit | `42dc2270e960` (2026-09-25) |
| Licens | Apache 2.0 – [LICENSE.txt](LICENSE.txt) |

**Projektets regler går före.** Skillen är generell referens; där den krockar med
[CLAUDE.md](../../../CLAUDE.md) eller projektets skills gäller de – här: skill `data-privacy-security` (hälsodata, notiser privata på låsskärmen, delning bara via FileProvider) och skill `notifications-alarms` (larmens `PendingIntent`).

Uppdatera: klona repot, kopiera katalogen på nytt (behåll denna fil och LICENSE.txt), ändra
commit och datum ovan och kör `node .github/scripts/check-links.mjs`.
