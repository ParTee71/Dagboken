# Källa

Kopia av Googles officiella skill **`firebase-security-rules-auditor`** – ändras inte här, så att den kan bytas mot en
ny version rakt av.

| | |
|---|---|
| Repo | https://github.com/firebase/agent-skills |
| Sökväg | `skills/firebase-security-rules-auditor` |
| Commit | `de359da26586` (2026-10-01) |
| Licens | Apache 2.0 – [LICENSE.txt](LICENSE.txt) |

**Projektets regler går före.** Skillen är generell referens; där den krockar med
[CLAUDE.md](../../../CLAUDE.md) eller projektets skills gäller de – här: skill `firestore-data-layer` (regler under `users/{uid}`, bara ägaren `request.auth.uid == uid`, tolerans för okända fält enligt BCK-9) och skill `data-safety-backup`.

Uppdatera: klona repot, kopiera katalogen på nytt (behåll denna fil och LICENSE.txt), ändra
commit och datum ovan och kör `node .github/scripts/check-links.mjs`.
