---
name: release
description: Dagboken release workflow — propose a version bump, confirm, update build.gradle.kts + README, commit the release on master, then trigger release.yml via workflow_dispatch so GitHub Actions creates the tag, builds the signed APK and publishes a GitHub Release. CI-first (works from the phone); local Android Studio build is a documented fallback. Only use when the user explicitly asks to release or ship a new version.
---

# Dagboken Release Workflow

Run the steps in order. After each step report what you did. Stop and explain clearly if
anything fails. **Only run this when the user explicitly asks to release.**

## How a release happens here

- **Build & sign:** GitHub Actions `release.yml` (canonical) — decodes the keystore from
  secrets and builds the signed APK in the cloud, so this works from the phone with no local
  SDK. Building locally in Android Studio is a documented fallback (see end).
- **Publish:** `release.yml` builds the signed APK and **publishes a GitHub Release**
  (auto-generated changelog + APK attached). It has two triggers:
  - pushing a tag `vX.Y.Z`, or
  - `workflow_dispatch` on `master` with `version_name` set and `publish_release` ticked —
    the workflow then **creates the tag itself** (via `action-gh-release`, on the dispatched
    commit) and publishes the Release.
- **Who creates the tag:** use the dispatch path. **Never try to `git push` a tag from an
  agent session** — the network policy blocks `refs/tags/*` and the push fails with
  `error: RPC failed; HTTP 403`. Branch pushes are unaffected. Pushing the tag from the
  user's own machine still works and is a valid manual fallback.
- **Tools:** use the **GitHub MCP** tools (`mcp__github__*`) — there is no `gh` CLI here.
  The build runs in Actions, not in the session — don't run `./gradlew` in a phone/web session.

---

## Step 1 — Read current version

Read `app/build.gradle.kts`: note `versionCode` (int) and `versionName` (string).

---

## Step 2 — Analyse commits and propose a version bump

Find the latest release tag (local tags may be absent — prefer the remote):
```
mcp__github__list_tags        (owner: ParTee71, repo: Dagboken)   # newest vX.Y.Z
mcp__github__get_latest_release (owner: ParTee71, repo: Dagboken)  # or the last published release
```
List commits since that tag: `git log <last-tag>..HEAD --oneline --no-merges`
(or all commits if there is no tag). PRs are normally **squash-merged**, so each line is one
conventional-commit subject (`feat(...)`, `fix(...)`, `chore(...)`) ending in `(#NN)` — that is
your changelog material. A multi-PR feature landed since the last tag (e.g. an epic split into
spike + implementation + follow-up) is still assessed as a whole: the **highest-severity** commit
across the whole range sets the bump (one `feat` → minor, even amid many `chore`/`fix`).

Classify the highest-severity change present:

| Commit keyword / pattern | Bump |
|---|---|
| `BREAKING`, `!:`, major rewrite, removed feature | **major** — x+1.0.0 |
| `feat`, `feature`, new screen, new capability | **minor** — x.y+1.0 |
| `fix`, `chore`, `refactor`, `test`, `docs`, `i18n`, `deps`, polish | **patch** — x.y.z+1 |

Compute new `versionName` (per the bump) and new `versionCode` (= current + 1). Present:
```
Current:  v<old_name>  (versionCode <old_code>)
Proposed: v<new_name>  (versionCode <new_code>)
Reason:   <one sentence>
Commits:  <bullet list>
Proceed with v<new_name>, or a different version?
```
**Wait for explicit confirmation before continuing.**

---

## Step 3 — Update version + README version history

1. Edit `app/build.gradle.kts`: `versionCode = <new>` and `versionName = "<new>"`. Verify the
   edit before committing.
2. **Add a new row to the README "Versionshistorik" table** for `<new_name>`, summarising the
   user-visible changes shipped since the last release (mirror the style of the existing rows:
   one dense sentence per theme, with the `(#NN)` PR/issue refs). Feature PRs land throughout the
   cycle **without** touching this table — so at release time the row is *written here*, not merely
   "confirmed". Read the commits from Step 2 to compose it.
3. Confirm `KRAVLISTA.md` already reflects the shipped behaviour (features update it as they land,
   per `requirements-kravlista`) and that `TP-1`'s SDK levels still match `build.gradle.kts`
   (e.g. `compileSdk`) — fix if a build-config change slipped through without a requirement update.

---

## Step 4 — Land the release commit on master

Releases are published from **master**. If you are on a feature branch, get the version bump
onto master first (open/merge a PR), then release from the master commit. With explicit release
intent the user may approve committing the bump directly to master.

```
git commit -am "Release v<new_name>"
git push origin master            # land the release commit (retry with backoff on network errors)
```

Do **not** create or push a tag here — the workflow creates it in Step 5. A tag push from an
agent session fails with `error: RPC failed; HTTP 403` (the network policy blocks `refs/tags/*`).

---

## Step 5 — Trigger the release build

Dispatch `release.yml` on `master`, which creates the tag and publishes the Release:
```
mcp__github__actions_run_trigger
  owner: ParTee71, repo: Dagboken
  workflow_id: release.yml
  ref: master
  inputs: { version_name: "<new_name>", publish_release: "true" }
```
`version_name` is **without** the leading `v` (the workflow tags `v<new_name>`); `publish_release`
must be the *string* `"true"` — `workflow_dispatch` inputs are sent as strings. The workflow fails
fast if the tag already exists, so a repeated dispatch never overwrites a published Release.

Then follow the run and the published Release:
```
mcp__github__actions_list        (owner: ParTee71, repo: Dagboken)          # find the run
mcp__github__actions_get         (... run_id)                                # poll status
mcp__github__get_job_logs        (... run_id, failed_only: true)             # on failure
mcp__github__get_release_by_tag  (owner: ParTee71, repo: Dagboken, tag: v<new_name>)
```
The workflow builds the signed APK and publishes a GitHub Release (changelog + APK). If it
fails, report the failing step/log. (An artifact-only build without a Release: dispatch the same
workflow with `version_name` only, leaving `publish_release` off.)

**Manual fallback:** from the user's own machine `git tag v<new_name> && git push origin v<new_name>`
triggers the exact same workflow. Only offer this if the dispatch path is unavailable.

---

## Step 6 — Summary

```
Released: Dagboken v<new_name>  (versionCode <new_code>)
Commit:   Release v<new_name>  on master
Tag:      v<new_name>  created by release.yml on the master commit
CI:       <run URL / status>
GitHub:   <Release URL>  (signed APK attached, once built)
```

---

## Fallback — build the signed APK locally (Android Studio)

When CI isn't an option and you have the SDK + keystore locally:
```
./gradlew :app:assembleRelease
```
APK lands in `app/build/outputs/apk/release/` (filename includes a build timestamp). Requires
`dagboken.jks` in `app/` and signing passwords via `local.properties` or the
`SIGNING_*` environment variables. For a published Release, run Step 5 so `release.yml`
attaches the cloud-built APK.
