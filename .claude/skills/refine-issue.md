---
name: refine-issue
description: Turn a rough idea, bug report, or feature request into a well-refined, planned GitHub issue for Dagboken — clarify intent, research the codebase for affected areas, define scope and acceptance criteria grounded in the project's four non-negotiable rules, then create or update the issue on GitHub after confirmation. Use when the user wants to "create an issue", "update an issue", "plan a feature", "write up a bug", "refine this idea into a ticket", or similar. Pairs with the implement-issue skill, which does the work this skill plans.
---

# Refine & Plan a New Issue

Take a half-formed idea and turn it into a GitHub issue another contributor (or a future
Claude session running `implement-issue`) could pick up and finish without further
questions.

Work through the steps in order. Keep the conversation tight — batch clarifying questions,
don't interrogate. **Do not create or update the issue until Step 6.**

The Definition of Done (Step 4) is anchored in the four non-negotiable rules in
[CLAUDE.md](../../CLAUDE.md). A refined issue must make those rules concrete for *this*
change — that is the main thing this skill adds over a plain bug report.

### New vs existing issue

At the very start, check if the user provided an issue number or URL:
- **No number** → follow all steps, create a new issue at Step 6.
- **Issue number given** (e.g. `/refine-issue #42`) → fetch it first with
  `mcp__github__issue_read`, show the current title/body, then refine and **update** it at
  Step 6 instead of creating.

---

## Step 1 — Capture the raw idea

Restate the user's request in one or two sentences so they can confirm you understood it.
Classify it:

| Type | Signals |
|---|---|
| **Feature** | new screen, new capability, "add", "support", "allow users to…" |
| **Bug** | "broken", "crashes", "wrong", "doesn't work", reproduction steps |
| **Chore / tech-debt** | refactor, dependency bump, build/CI, cleanup, no user-visible change |

If the idea may duplicate existing work, search first and surface close matches:
```
mcp__github__search_issues  (repo: partee71/dagboken, query: <keywords>)
```

---

## Step 2 — Clarify intent (batch the questions)

Ask only what you genuinely cannot infer from the request or the codebase. Use
`AskUserQuestion` when there are concrete choices. Typical gaps:

- **Feature**: who is it for, what problem does it solve, what does "done" look like, any UI expectations?
- **Bug**: exact steps to reproduce, expected vs actual, device/Android version, frequency, any logcat/stack trace?
- **Chore**: motivation (perf, maintainability, security), blast radius?

Don't re-ask what the user already answered. Skip this step entirely if the request is
already unambiguous.

---

## Step 3 — Research the codebase

Locate the parts of the app this issue would touch. This grounds the plan in reality and
catches hidden complexity. Use `Grep` / `Glob` / `Read`:

- Which screen(s), ViewModel(s), repository(ies), Room entities, or workers are involved?
- Is there an existing pattern for this kind of change? (See `android-dev`'s existing-pattern audit.)
- **Data model / persistence** → does it add or change a persisted field/entity? Then it
  needs a Room migration (`room-migrations`) **and** backup-chain coverage (`data-safety-backup`).
- **UI** → is there a shared component in `ui/components/` / `ui/diagram/` to reuse
  (`shared-ui-components`)? Any accessibility impact (`accessibility-compose`)?
- **Notifications/alarms** → touchpoints in `AlarmScheduler`/receivers (`notifications-alarms`)?
- **Privacy/security** → does it log, store, or transmit health data (`data-privacy-security`)?
- Auth (`FirebaseAuthRepository`), backup (`DriveBackupRepository`/`BackupWorker`), DI (Hilt) touchpoints?
- **Which requirements** in `KRAVLISTA.md` does it touch? Find the ID(s) (e.g. `ÖV-2`, `BCK-2`) or note a **new** ID is needed.
- **Which tests** cover the affected code today? Check `app/src/test` (unit) and `app/src/androidTest` (instrumented).

Note the concrete file paths you find — they go into the issue.

---

## Step 4 — Define scope & Definition of Done

Draw the boundary explicitly:

- **In scope** — what this issue delivers.
- **Out of scope / follow-ups** — what it deliberately does NOT include (prevents scope creep).
- **Acceptance criteria** — a checklist of observable, testable outcomes, each verifiable by
  running the app or a test, e.g. `- [ ] Tapping Save persists the entry and it survives app restart`.

### Definition of Done — the four rules, made concrete

Every issue **must** carry these, each tied to a non-negotiable rule. If one genuinely
doesn't apply, say why in the issue rather than dropping it silently.

1. **Data safety (rule 1)** — if the change adds/alters persisted data: the field/entity is
   added to `BackupJson` + `BackupMapper` + assembly in `DriveBackupRepository`, a Room
   migration is written, and a **backup→restore round-trip test** asserts the new data.
   (`data-safety-backup`, `room-migrations`)
2. **Tests at all levels (rule 2)** — unit (`app/src/test`) for ViewModel/domain/mapper
   logic; instrumented (`app/src/androidTest`) for Compose UI and Room/migration. A bug fix
   adds a **regression test that fails before the fix and passes after**. (`testing-strategy`)
3. **Requirements updated (rule 3)** — `KRAVLISTA.md` reflects the change: new requirement
   row (next ID in the section) for new behaviour, amended row for changed behaviour, or
   `~~…~~ *(borttaget)*` for removed behaviour. (`requirements-kravlista`)
4. **Component reuse (rule 4)** — any UI uses/extends the shared components rather than a new
   variant. (`shared-ui-components`)

Plus, where relevant: **privacy** (no health data in logs; storage only local/Drive
appDataFolder — `data-privacy-security`), **accessibility** for new UI
(`accessibility-compose`), and `ATGARDSLISTA.md` updated if it resolves a tracked item.

If the work is large, propose splitting it and ask whether to file separate issues or a
parent with sub-issues (`mcp__github__sub_issue_write`). Each split keeps its own DoD.

---

## Step 5 — Draft the issue

Assemble the body using this template. Fill every section; drop one only if it genuinely
doesn't apply (and consider saying so).

```markdown
## Summary
<one paragraph: what and why>

## Motivation / Problem
<the user need or bug impact>

## Proposed approach
<technical plan grounded in Step 3 — name real files/classes>
1. …
2. …

## Affected areas
- `app/src/main/.../FooViewModel.kt`
- `app/src/main/.../FooRepository.kt`

## Data safety (backup/restore)
<persisted fields added/changed and how they enter the backup chain + migration; or "no persisted data change">

## Requirements (KRAVLISTA.md)
- <new or amended requirement ID(s) and the exact row text to add/change>

## Test plan
- **Unit** (`app/src/test`): <which test class(es), what cases>
- **Instrumented** (`app/src/androidTest`): <which screen/DAO/migration test(s), what cases>

## Acceptance criteria
- [ ] <behaviour 1, observable/testable>
- [ ] Shared components reused where applicable (no duplicate UI variant)
- [ ] Data round-trips through backup→restore (if persisted data changed)
- [ ] `KRAVLISTA.md` updated (new/amended/struck requirement row)
- [ ] Unit tests added/updated and passing (`app/src/test`)
- [ ] Instrumented tests added/updated and passing (`app/src/androidTest`)

## Out of scope
- …

## Risks / Open questions
- <hidden complexity, migration risk, or "none">
```

For a **bug**, swap "Proposed approach / Motivation" for **Steps to reproduce**, **Expected
behaviour**, **Actual behaviour**, and **Environment**, and require the regression test in
acceptance criteria.

Show the full drafted title and body. Propose a concise title
(`<area>: <imperative summary>`, e.g. `Backup: surface last-sync timestamp on settings screen`).

**Wait for explicit confirmation. Ask whether to apply labels** (fetch real ones via repo
labels if unsure).

---

## Step 6 — Create or update the issue

Only after confirmation:

**New:** `mcp__github__issue_write (method: create, repo: partee71/dagboken, title, body, labels?)`
**Existing:** `mcp__github__issue_write (method: update, repo: partee71/dagboken, issue_number: <N>, title?, body?, labels?)`

When updating, replace the full body with the refined version — do not append. Keep existing
labels unless the user wants them changed. Report the issue number and URL. For a split,
create the parent first, then each sub-issue with `mcp__github__sub_issue_write`.

---

## Step 7 — Offer next steps

Briefly offer (don't auto-run):
- **Implement it now** → hand off to the `implement-issue` skill.
- File the out-of-scope items as follow-up issues.
- Leave it as a planned ticket for later.
