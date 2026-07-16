---
name: release
description: Dagboken release workflow — propose a version bump, confirm, update build.gradle.kts + README, commit the release on master and create the tag locally, then hand the tag push to the user (the tag push triggers GitHub Actions, which builds the signed APK and publishes a GitHub Release). CI-first (works from the phone); local Android Studio build is a documented fallback. Only use when the user explicitly asks to release or ship a new version.
---

# Dagboken Release Workflow

Run the steps in order. After each step report what you did. Stop and explain clearly if
anything fails. **Only run this when the user explicitly asks to release.**

## How a release happens here

- **Build & sign:** GitHub Actions `release.yml` (canonical) — decodes the keystore from
  secrets and builds the signed APK in the cloud, so this works from the phone with no local
  SDK. Building locally in Android Studio is a documented fallback (see end).
- **Publish:** pushing a tag `vX.Y.Z` triggers `release.yml`, which builds the signed APK and
  **publishes a GitHub Release** (auto-generated changelog + APK attached).
- **Who pushes the tag:** this skill prepares everything and creates the tag **locally**, but
  **does not push it** — it hands the exact push command to the user (and copies the tag to the
  clipboard). The user pushing the tag is what kicks off the build.
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

## Step 4 — Land the release commit on master and create the tag

Releases are published from **master**. If you are on a feature branch, get the version bump
onto master first (open/merge a PR), then tag the master commit. With explicit release intent
the user may approve committing the bump directly to master.

```
git commit -am "Release v<new_name>"
git push origin master            # land the release commit (retry with backoff on network errors)
git tag v<new_name>               # create the tag LOCALLY — do not push it
```

---

## Step 5 — Hand the tag push to the user

**Do not push the tag yourself.** The tag push is the user's action — it triggers `release.yml`.

Copy the tag to the clipboard (best effort — pick what fits the environment):
```
# Windows / Android Studio terminal
Set-Clipboard "v<new_name>"
# macOS:  printf 'v<new_name>' | pbcopy
# Linux:  printf 'v<new_name>' | xclip -selection clipboard   # or wl-copy
```

Then ask the user to push it:
```
Release v<new_name> is committed on master and tagged locally.
Push the tag to build & publish (copied to clipboard):

    git push origin v<new_name>
```
After the user confirms they pushed it, optionally follow the run and the published Release:
```
mcp__github__actions_list        (owner: ParTee71, repo: Dagboken)          # find the run
mcp__github__actions_get         (... run_id)                                # poll status
mcp__github__get_job_logs        (... run_id, failed_only: true)             # on failure
mcp__github__get_release_by_tag  (owner: ParTee71, repo: Dagboken, tag: v<new_name>)
```
The workflow builds the signed APK and publishes a GitHub Release (changelog + APK). If it
fails, report the failing step/log. (An artifact-only build without a Release can be triggered
manually via `mcp__github__actions_run_trigger` with the `version_name` input.)

---

## Step 6 — Summary

```
Released: Dagboken v<new_name>  (versionCode <new_code>)
Commit:   Release v<new_name>  on master
Tag:      v<new_name>  created locally — user pushes `git push origin v<new_name>` to publish
CI:       <run URL / status, once the user has pushed the tag>
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
`SIGNING_*` environment variables. For a published Release, push the matching tag so `release.yml`
attaches the cloud-built APK.
