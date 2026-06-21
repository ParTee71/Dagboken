---
name: release
description: Dagboken release workflow — propose version bump, confirm, update build.gradle.kts, commit, tag, build signed release APK, upload to Google Drive /Dagboken, create GitHub release. Only use when the user explicitly asks to release or ship a new version.
---

# Dagboken Release Workflow

Run these steps in order. After each step report what you did. Stop and explain clearly if anything fails.

---

## Step 1 — Read current version

Read `app/build.gradle.kts` and note the current `versionCode` (integer) and `versionName` (string).

---

## Step 2 — Analyse commits and propose a version bump

Find the most recent tag:
```
git describe --tags --abbrev=0 2>/dev/null || echo "(no previous tag)"
```

List commits since that tag (or all commits if no tag):
```
git log <last-tag>..HEAD --oneline --no-merges
```
(If no previous tag: `git log --oneline --no-merges`)

Classify the highest-severity commit type present:

| Commit keyword / pattern | Bump |
|---|---|
| `BREAKING`, `!:`, major rewrite, removed feature | **major** — x+1.0.0 |
| `feat`, `feature`, new screen, new capability | **minor** — x.y+1.0 |
| `fix`, `chore`, `refactor`, `test`, `docs`, `i18n`, `deps`, polish | **patch** — x.y.z+1 |

Compute:
- New `versionName` according to the bump
- New `versionCode` = current `versionCode` + 1

Present to the user:
```
Current:  v<old_name>  (versionCode <old_code>)
Proposed: v<new_name>  (versionCode <new_code>)
Reason:   <one sentence>

Commits included:
<bullet list>

Proceed with v<new_name>, or tell me a different version?
```

**Wait for explicit confirmation before continuing.**

---

## Step 3 — Update version in build.gradle.kts

Edit `app/build.gradle.kts`:
- Replace `versionCode = <old>` → `versionCode = <new>`
- Replace `versionName = "<old>"` → `versionName = "<new>"`

Verify the edit looks correct before committing.

---

## Step 4 — Commit and tag

```
git add app/build.gradle.kts
git commit -m "Release v<new_name>"
git tag v<new_name>
```

---

## Step 5 — Build signed release APK

```
./gradlew :app:assembleRelease
```

Find the produced APK (name includes a build timestamp):
```powershell
Get-ChildItem app/build/outputs/apk/release/ -Filter "*.apk" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 -ExpandProperty FullName
```

Note the full path — call it `$apkPath`. If the build fails, show the error and stop.

---

## Step 6 — Upload to Google Drive /Dagboken

Run the upload helper (PowerShell):
```powershell
.\.claude\scripts\drive-upload.ps1 `
    -FilePath "$apkPath" `
    -FileName "dagboken-v<new_name>.apk"
```

The script tries three methods in order and reports which one succeeded:
1. **rclone** with a remote named `gdrive`
2. **Google Drive for Desktop** local sync folder
3. **REST API** using credentials stored in `~/.dagboken-drive-config.json`

If it exits with code 1, no method worked. Report the instructions the script printed and ask the user to confirm once they have uploaded manually — then continue to Step 7.

---

## Step 7 — Push and publish GitHub release

Push the commit and tag:
```
git push origin master
git push origin v<new_name>
```

Build a changelog from commits since the previous tag:
```
git log <prev_tag>..v<new_name> --pretty=format:"- %s" --no-merges
```

Create the GitHub release:
```
gh release create v<new_name> "$apkPath" `
    --title "Dagboken v<new_name>" `
    --notes "<changelog>"
```

---

## Step 8 — Summary

Print a compact summary:
```
Released: Dagboken v<new_name>  (versionCode <new_code>)
APK:      <apk filename>  (<size> MB)
Drive:    <uploaded to gdrive:Dagboken/ | manual upload needed>
GitHub:   <release URL>
Tag:      v<new_name>  pushed to origin
```

---

## Drive upload — first-time setup

If the upload script cannot find any working method, run the setup helper once:
```powershell
.\.claude\scripts\setup-drive-token.ps1
```
It walks through an OAuth device-code flow and saves credentials to
`~/.dagboken-drive-config.json`. After setup, re-run the release from Step 6.

Alternatively, install rclone (`winget install Rclone.Rclone`) and run
`rclone config` to create a remote called `gdrive` of type `drive`.
