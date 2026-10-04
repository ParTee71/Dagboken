# Källa

Kopia av Googles officiella skill **`navigation-3`** – ändras inte här, så att den kan bytas mot en
ny version rakt av.

| | |
|---|---|
| Repo | https://github.com/android/skills |
| Sökväg | `navigation/navigation-3` |
| Commit | `42dc2270e960` (2026-09-25) |
| Licens | Apache 2.0 – [LICENSE.txt](LICENSE.txt) |

**Projektets regler går före.** Skillen är generell referens; där den krockar med
[CLAUDE.md](../../../CLAUDE.md) eller projektets skills gäller de – här: skill `compose-expert` (vår `AppBackStack` med en stack per flik, `navigation/Transitions`, Hilt via `hiltViewModel`) och skill `android-dev`.

Uppdatera: klona repot, kopiera katalogen på nytt (behåll denna fil och LICENSE.txt), ändra
commit och datum ovan och kör `node .github/scripts/check-links.mjs`.
