---
name: android-gradle-logic
description: Use when setting up or refactoring Android Gradle build logic in Dagboken — convention plugins, composite builds, version catalogs, and shared build configuration across the :core and :app modules. Svenska trigger-ord: gradle, bygg, byggfil, beroende, lägga till bibliotek, versionskatalog, modul, :core, :app, plugin, byggtid, cpdCheck, version.properties, compileSdk, AGP.
---

# Android Gradle Build Logic

Build logic lives in **one place** and every version in the version catalog – never
copy-pasted configuration across modules. The real setup (versions, SDK levels, where the
shared configuration lives, why) is in `gradle/libs.versions.toml`, the root
`build.gradle.kts` and KRAVLISTA TP-1 (minSdk 30, targetSdk 35, compileSdk 37; AGP 9,
Kotlin 2.4, Gradle 9); the motivation for thresholds such as the CPD token count is written
next to them (and in `ARKITEKTUR.md` → "Byggkonfiguration" once that section exists). This
skill describes the rules, not the numbers.

## Version catalog rules

- Every dependency and plugin version is in `gradle/libs.versions.toml`; build files use
  `libs.…` only.
- BOMs keep families in sync: Compose BOM for Compose, Firebase BOM for Firebase (no
  `-ktx` artefacts). A library pinned outside its BOM (e.g. `material3` for the Expressive
  APIs, proven by `VerifiedApisTest`) gets a comment in the catalog saying why.
- AGP 9 has built-in Kotlin: `:app` does **not** apply `org.jetbrains.kotlin.android`. The
  Kotlin version comes from the `kotlin-jvm` plugin declared in the root `plugins {}`.
- AGP aligns `androidTest` dependencies with the app's resolved versions. When a test
  library needs a newer version than the app resolves ("Cannot find a version … strictly …"),
  raise it in the `constraints {}` block in `app/build.gradle.kts` with a comment naming the
  test library – never by disabling the alignment.
- No JVM toolchain: Java 17 is set via `sourceCompatibility`/`targetCompatibility` and
  Kotlin's `jvmTarget`, so any newer JDK can build.
- If more modules are ever added, move the shared configuration from the root
  `build.gradle.kts` to a `build-logic/` convention plugin (included with
  `pluginManagement { includeBuild("build-logic") }`) – still one place.

## This Project's Gradle Setup (Dagboken 4.0)

Two modules (ARKITEKTUR.md → Lager och moduler), replacing the single-module 3.x build (Room, DataStore, Drive):

| Module | Plugins | Rule |
|---|---|---|
| `:core` | `kotlin("jvm")`, `kotlin("plugin.serialization")` | Pure Kotlin/JVM. **No Android or Firebase dependencies**, ever – that is what keeps `:core:test` fast and runnable without an Android SDK. Models, codecs, engines, chart math and the 3.x `BackupJson` converter live here. Tests: `junit`, `kotlin-test`. |
| `:app` | `android.application` (AGP 9 has built-in Kotlin – no `kotlin.android`), `kotlin.compose`, `ksp`, `hilt`, `kotlin.serialization`, `google-services` (only when `app/google-services.json` exists), `roborazzi` | `implementation(project(":core"))`. Material 3 Expressive, Navigation 3, Firestore, Hilt, WorkManager, Health Connect (read-only). The legacy reader in `data/legacy` (OMB-2, etapp 3) is the only code that may read the 3.x Room file – no Room anywhere else. |

### Shared configuration lives in one place (rule 4)
Java 17 (no toolchain), Kotlin compiler flags, test timeout and test logging are configured **once** – in the root `build.gradle.kts` (`subprojects {}` with `plugins.withId {}`) – never copy-pasted into both module files. Move it to a `build-logic` convention plugin only if more modules are added.

### Root-level tasks
- `cpdCheck` (copy-paste detector, PMD CPD for Kotlin via PMD's CLI as a `JavaExec` task – the `de.aaschmid.cpd` plugin does not work with Gradle 9) over `core/src`, `app/src/main`, `app/src/test`, `app/src/sharedTest`, `app/src/androidTest`, wired into `check`.
- Build performance flags in `gradle.properties`: `org.gradle.caching=true`, `org.gradle.configuration-cache=true`, `org.gradle.parallel=true` (skill `ci-budget`).

### AGP / Kotlin
- Versions as in TP-1; a version bump that changes SDK levels updates TP-1 in the same PR (skill `requirements-kravlista`).
- Use `kotlin.plugin.compose` (separate plugin from Kotlin 2.0+).
- KSP for annotation processing (Hilt) – KAPT is not used.

## Checklist

- [ ] All versions in `libs.versions.toml` – no hardcoded versions in build files
- [ ] Firebase uses the BOM to keep auth/firestore versions in sync
- [ ] `:core` has no Android/Firebase dependency
- [ ] Shared build settings exist once; module files only contain what is unique to the module
- [ ] `google-services` plugin applied only in `app/build.gradle.kts`. In Dagboken `app/google-services.json` is **checked in** (since 3.x; Firebase client config for `dagboken-711d2`, not a secret) – CI needs no secret for it
- [ ] Builds still compile if the JSON is removed: a stub resource with `default_web_client_id` and a Firebase config for the demo project `demo-dagboken` (`app/src/authStub/res`, added only when the JSON is missing), so auth code compiles and the app starts; sign-in simply fails at runtime there
- [ ] New dependency or plugin: build-time impact stated in the PR (skill `ci-budget`)
