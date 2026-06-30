# AGENTS.md

Det här repots regler och konventioner för AI-agenter och kodassistenter
(Cursor, Codex, Zed, Windsurf, Aider m.fl.) är samlade i **[CLAUDE.md](CLAUDE.md)**.

Läs `CLAUDE.md` först — den innehåller de fyra icke-förhandlingsbara reglerna som
gäller vid **varje** ändring:

1. **Datasäkerhet** — backup/restore får aldrig tappa data.
2. **Tester på alla nivåer** vid varje beteendeändring.
3. **KRAVLISTA.md** hålls aktuell.
4. **Återanvänd delade komponenter** (sliders, diagram, kort …).

Detaljerade regler ligger som vanliga Markdown-filer i [`.claude/skills/`](.claude/skills/)
och kan läsas direkt oavsett verktyg:

- `data-safety-backup.md` — regel 1
- `testing-strategy.md` — regel 2
- `requirements-kravlista.md` — regel 3
- `shared-ui-components.md` — regel 4
- `android-dev.md` m.fl. — arkitektur- och teknikbaslinje

Bygg/test-kommandon och arkitekturöversikt finns i `CLAUDE.md` och `README.md`.
