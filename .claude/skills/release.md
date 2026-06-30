---
name: release
description: Dagboken release workflow — propose a version bump, confirm, update build.gradle.kts, commit and tag, then let GitHub Actions build the signed APK and publish a GitHub Release, and finally upload the APK to Google Drive /Dagboken. CI-first (works from the phone); local Android Studio build is a documented fallback. Only use when the user explicitly asks to release or ship a new version.
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
- **Distribute:** the signed APK is then **uploaded to Google Drive `/Dagboken`** (mandatory).
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
(or all commits if there is no tag).

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

## Step 3 — Update version in build.gradle.kts

Edit `app/build.gradle.kts`: `versionCode = <new>` and `versionName = "<new>"`. Verify the
edit before committing. (Confirm `KRAVLISTA.md`/README version table already reflect the
shipped changes — see `requirements-kravlista`.)

---

## Step 4 — Land the release commit on master and tag it

Releases are published from **master**. If you are on a feature branch, get the version bump
onto master first (open/merge a PR), then tag the master commit. With explicit release intent
the user may approve committing the bump directly to master.

```
git commit -am "Release v<new_name>"
git tag v<new_name>
```

---

## Step 5 — Push the tag → CI builds & publishes

Push master (if it has the new commit) and the tag. **The tag push triggers `release.yml`.**
```
git push origin master          # if the release commit isn't on origin/master yet
git push origin v<new_name>      # triggers the Release Build workflow
```
(Retry pushes with backoff on network errors.)

Watch the run and confirm the Release with GitHub MCP:
```
mcp__github__actions_list   (owner: ParTee71, repo: Dagboken)        # find the run
mcp__github__actions_get    (... run_id)                              # poll status
mcp__github__get_job_logs   (... run_id, failed_only: true)          # on failure
mcp__github__get_release_by_tag (owner: ParTee71, repo: Dagboken, tag: v<new_name>)
```
The workflow builds the signed APK and publishes a GitHub Release (changelog + APK). If it
fails, report the failing step/log and stop. (An artifact-only build without a Release can be
triggered manually via `mcp__github__actions_run_trigger` with the `version_name` input.)

---

## Step 6 — Upload the APK to Google Drive (mandatory)

Get the signed APK — download it from the GitHub Release assets (or the run's
`dagboken-release` artifact). Then upload to Drive `/Dagboken` with the helper (PowerShell,
runs locally / in Android Studio — not from the phone):
```powershell
.\.claude\scripts\drive-upload.ps1 `
    -FilePath "<path-to-downloaded.apk>" `
    -FileName "dagboken-v<new_name>.apk"
```
The script tries, in order: **rclone** (`gdrive` remote) → **Google Drive for Desktop** sync
folder → **REST API** (`~/.dagboken-drive-config.json`), and reports which worked. If it exits
1, no method worked — report its instructions and ask the user to confirm once uploaded
manually. This step is **required** before the release is considered done.

---

## Step 7 — Summary

```
Released: Dagboken v<new_name>  (versionCode <new_code>)
Tag:      v<new_name>  pushed → release.yml
CI:       <run URL / status>
GitHub:   <Release URL>  (signed APK attached)
Drive:    <uploaded to /Dagboken | manual upload pending>
```

---

## Fallback — build the signed APK locally (Android Studio)

When CI isn't an option and you have the SDK + keystore locally:
```
./gradlew :app:assembleRelease
```
APK lands in `app/build/outputs/apk/release/` (filename includes a build timestamp). Requires
`dagboken.jks` in `app/` and signing passwords via `local.properties` or the
`SIGNING_*` environment variables. Then continue with Step 6 (Drive upload) and, if you want a
published Release, push the matching tag so `release.yml` attaches the cloud-built APK.

## Drive upload — first-time setup

If the upload script finds no working method, run the setup helper once:
```powershell
.\.claude\scripts\setup-drive-token.ps1
```
It walks an OAuth device-code flow and saves credentials to `~/.dagboken-drive-config.json`.
Alternatively install rclone and configure a remote named `gdrive` (type `drive`).
