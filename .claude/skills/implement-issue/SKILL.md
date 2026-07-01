---
name: implement-issue
description: Implement a GitHub issue, bug fix, feature, or chore in Dagboken end to end — research, plan, change code following the project's patterns, add tests at every level, update requirements and the backup chain, then commit, push, and open a PR. Use when the user wants to "implement issue #N", "fix this bug", "build this feature", "do this ticket", "pick up #N", or hands off from refine-issue. Replaces the old fix-bug flow; handles bugs, features, and chores alike.
---

# Implement an Issue (bug · feature · chore)

This is the "do the work" counterpart to `refine-issue`. It drives a change from a ticket
or description all the way to a pushed branch with a PR, honouring the four non-negotiable
rules in [CLAUDE.md](../../../CLAUDE.md). Don't skip steps to "save time" — the rules are the
definition of done, not optional polish.

> **Tests run in GitHub Actions, not in the session.** From the phone/web there is no
> Android SDK and `dl.google.com` may be blocked — do not try to run `./gradlew`. Reason
> about correctness, write the tests, push, and let CI run them (see CLAUDE.md). In Android
> Studio you can run them locally.

## Step 1 — Understand the work

- If given an issue number, fetch it: `mcp__github__issue_read (repo: partee71/dagboken, issue_number: N)`.
  Read its acceptance criteria and Definition of Done — those are your contract.
- If it's a loose description and the scope is unclear or risky, consider running
  `refine-issue` first (or at least restate scope and confirm) before writing code.
- Classify: **bug**, **feature**, or **chore** — it changes the workflow below slightly.

## Step 2 — Reproduce (bugs) / pin the target (features)

- **Bug:** establish the exact reproduction and the expected vs actual behaviour. Find the
  faulty code path before changing anything. You will encode the repro as a failing test.
- **Feature/chore:** identify the precise insertion point and the user-visible outcome that
  marks "done".

## Step 3 — Research existing patterns (before writing code)

Mandatory — the project values consistency over cleverness.

- Open a sibling ViewModel / screen / repository and copy its shape (state exposure, events,
  error mapping). See `android-dev`'s existing-pattern audit.
- UI? Find the shared component to reuse/extend (`shared-ui-components`) — don't fork a new variant.
- Data? Note Room entities, DAOs, and whether a migration is needed (`room-migrations`).
- Map the touched code to `KRAVLISTA.md` requirement IDs and to existing tests.

## Step 4 — Plan the change

Write a short internal plan (and share it if the change is non-trivial): files to touch, the
order, the tests to add, the migration + backup-chain edits, and the requirement rows to
update. For anything architecturally significant or ambiguous, confirm with the user via
`AskUserQuestion` before coding.

## Step 5 — Branch

Work on the designated development branch (see CLAUDE.md / task instructions); create it if
missing. Never commit straight to `master`.

## Step 6 — Implement (tests alongside, not after)

Follow the architecture: `Compose → ViewModel(StateFlow<UiState>) → Repository → Room/DataStore/Drive`,
Hilt DI, errors mapped in the repository layer.

- **Bug:** first add a **regression test that fails** (reproduces the bug), then make the
  minimal change that turns it green. Don't expand scope while you're in there.
- **Feature:** build behind the existing patterns; reuse shared components; keep ViewModels
  testable (inject dispatchers).
- **Persisted data changed?** Do the full data-safety chain in the *same* change
  (`data-safety-backup` + `room-migrations`): entity → migration (+ committed `schemas/*.json`)
  → `BackupJson` (with default) → `BackupMapper` → assembly in `DriveBackupRepository`.
- Keep diffs focused. Match surrounding style. UI strings in Swedish via `strings.xml`.
- Don't log health data / PII (`data-privacy-security`); add `contentDescription` etc. for new UI (`accessibility-compose`).

## Step 7 — Tests at every touched level (rule 2)

- **Unit** (`app/src/test`): ViewModel/domain/use-case/mapper logic, fakes over mocks for the
  data layer (`FakeNoteDao` pattern), Turbine for flows.
- **Instrumented** (`app/src/androidTest`): Compose UI behaviour; Room DAO; `MigrationXYTest`
  for schema changes; backup round-trip for persisted-data changes.
- Update — never delete or weaken — existing tests the change affects. If you add a method to
  a DAO interface, update its Fake too (or the build breaks).

## Step 8 — Self-review against the four rules (slutkontroll)

Run the CLAUDE.md final checklist:
- [ ] **Data safety** — persisted changes are in the backup chain + round-trip test. (rule 1)
- [ ] **Tests** — added/updated at every touched level. (rule 2)
- [ ] **Requirements** — `KRAVLISTA.md` (and README/version if scope changed) updated. (rule 3)
- [ ] **Reuse** — no duplicate component. (rule 4)
- [ ] **Architecture** — follows established patterns; privacy + a11y respected.

Trace each issue acceptance-criterion to the code/test that satisfies it. If you can't,
you're not done.

## Step 9 — Commit & push

- Conventional, **Swedish-OK** commit messages scoped to the change:
  `feat(backup): inkludera X i backup`, `fix(med): …`, `test(...)`, `chore(...)`.
- Small, coherent commits over one giant blob. Include the commit trailers required by the
  task/CLAUDE.md instructions.
- Push the development branch: `git push -u origin <branch>` (retry with backoff on network errors).

## Step 10 — Pull request (only when the user wants one)

Do **not** open a PR unless asked. When you do:
- Target `master`. Title `<area>: <imperative summary>`.
- Body: what changed, how it maps to the four rules, the test plan, and `Closes #N` to link
  the issue. Mirror any PR template if the repo has one.
- Opening the PR triggers GitHub Actions (`android.yml` + `instrumented.yml`) — that's where
  the tests actually run. Offer to watch the PR (`subscribe_pr_activity`) and drive CI green.

## Multiple issues

Implement them **one at a time**, each its own focused commit set (and PR if requested).
Don't batch unrelated changes into one branch — it muddies review and CI.

## Anti-patterns

- Fixing a bug without a regression test.
- Changing persisted data without the backup chain + migration (silent data loss).
- Adding a public ViewModel `fun` when the project uses sealed events + `onEvent()`.
- A new component that duplicates a shared one.
- Deleting/`@Ignore`-ing a failing test to "go green".
- Trying to run `./gradlew` in a remote/phone session instead of trusting CI.
