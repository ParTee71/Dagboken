---
name: refine-issue
description: Turn a rough idea, bug report, or feature request into a well-refined, planned GitHub issue for Dagboken — clarify intent, research the codebase for affected areas, define scope and acceptance criteria, draft a technical plan, then create the issue on GitHub after confirmation. Use when the user wants to "create an issue", "plan a feature", "write up a bug", "refine this idea into a ticket", or similar.
---

# Refine & Plan a New Issue

Take a half-formed idea and turn it into a GitHub issue another contributor (or a future Claude session) could pick up and implement without further questions.

Work through the steps in order. Keep the conversation tight — batch clarifying questions, don't interrogate. **Do not create the issue until Step 6.**

---

## Step 1 — Capture the raw idea

Restate the user's request in one or two sentences so they can confirm you understood it. Classify it:

| Type | Signals |
|---|---|
| **Feature** | new screen, new capability, "add", "support", "allow users to…" |
| **Bug** | "broken", "crashes", "wrong", "doesn't work", reproduction steps |
| **Chore / tech-debt** | refactor, dependency bump, build/CI, cleanup, no user-visible change |

If the idea is clearly a duplicate of existing work, search first:
```
mcp__github__search_issues  (repo: partee71/dagboken, query: <keywords>)
```
Surface any close matches before continuing.

---

## Step 2 — Clarify intent (batch the questions)

Ask only what you genuinely cannot infer from the request or the codebase. Use `AskUserQuestion` when there are concrete choices. Typical gaps:

- **Feature**: who is it for, what problem does it solve, what does "done" look like, any UI expectations?
- **Bug**: exact steps to reproduce, expected vs actual, device/Android version, frequency, any logcat/stack trace?
- **Chore**: what's the motivation (perf, maintainability, security), what's the blast radius?

If the user has already answered something, don't re-ask it. Skip this step entirely if the request is already unambiguous.

---

## Step 3 — Research the codebase

Locate the parts of the app this issue would touch. This grounds the plan in reality and catches hidden complexity. Use `Grep` / `Glob` / `Read`:

- Which screen(s), ViewModel(s), repository(ies), Room entities, or workers are involved?
- Is there an existing pattern for this kind of change? (See the `android-dev` skill's existing-pattern audit.)
- Any data-model or migration impact (Room schema → migration required)?
- Any auth (`FirebaseAuthRepository`), backup (`DriveBackupRepository`/`BackupWorker`), or DI (Hilt module) touchpoints?

Note the concrete file paths you find — they go into the issue.

---

## Step 4 — Define scope & acceptance criteria

Draw the boundary explicitly:

- **In scope** — what this issue delivers.
- **Out of scope / follow-ups** — what it deliberately does NOT include (prevents scope creep).
- **Acceptance criteria** — a checklist of observable, testable outcomes. Each item should be verifiable by running the app or a test, e.g. `- [ ] Tapping Save persists the entry and it survives app restart`.

If the work is large, propose splitting it into multiple issues and ask the user whether to file them as separate issues or a parent issue with sub-issues (`mcp__github__sub_issue_write`).

---

## Step 5 — Draft the issue

Assemble the body using this template. Fill every section; drop a section only if it genuinely doesn't apply.

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

## Acceptance criteria
- [ ] …
- [ ] …

## Out of scope
- …

## Open questions
- <anything still unresolved, or "none">
```

For a **bug**, swap "Proposed approach / Motivation" for **Steps to reproduce**, **Expected behaviour**, **Actual behaviour**, and **Environment**.

Show the full drafted title and body to the user. Propose a concise title (`<area>: <imperative summary>`, e.g. `Backup: surface last-sync timestamp on settings screen`).

**Wait for explicit confirmation. Ask whether to apply any labels** (fetch real ones with `mcp__github__list_issues` / repo labels if unsure).

---

## Step 6 — Create the issue

Only after confirmation:
```
mcp__github__issue_write  (method: create, repo: partee71/dagboken,
                           title, body, labels?)
```

Report back the issue number and URL. If the user wants the work split, create the parent first, then each sub-issue with `mcp__github__sub_issue_write`.

---

## Step 7 — Offer next steps

Briefly offer (don't auto-run):
- Start implementation now (switch to the `android-dev` skill).
- File the out-of-scope items as follow-up issues.
- Leave it as a planned ticket for later.
