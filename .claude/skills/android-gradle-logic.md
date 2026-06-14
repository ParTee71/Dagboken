---
name: android-gradle-logic
description: Use when setting up or refactoring Android Gradle build logic — convention plugins, composite builds, version catalogs, and shared build configuration across modules.
---

# Android Gradle Build Logic

Centralise build logic in reusable Convention Plugins instead of copy-pasting `build.gradle.kts` configuration across modules.

## Project Structure

```
root/
├── build-logic/
│   ├── convention/
│   │   ├── src/main/kotlin/
│   │   │   ├── AndroidApplicationConventionPlugin.kt
│   │   │   ├── AndroidLibraryConventionPlugin.kt
│   │   │   └── AndroidComposeConventionPlugin.kt
│   │   └── build.gradle.kts
│   └── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── app/
│   └── build.gradle.kts
└── settings.gradle.kts
```

## Step 1: Include `build-logic` as a Composite Build

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

## Step 2: Configure `build-logic/settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "build-logic"
include(":convention")
```

## Step 3: Write Convention Plugins

### Application Plugin

```kotlin
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<ApplicationExtension> {
                compileSdk = 35
                defaultConfig {
                    minSdk = 26
                    targetSdk = 35
                }
            }

            extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
                jvmToolchain(21)
            }
        }
    }
}
```

### Compose Plugin

```kotlin
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            val extension = extensions.getByType<CommonExtension<*, *, *, *, *, *>>()
            extension.buildFeatures.compose = true
        }
    }
}
```

## Version Catalog Best Practices

```toml
# gradle/libs.versions.toml
[versions]
agp = "8.13.2"
kotlin = "2.0.21"
compileSdk = "35"

[libraries]
# Use BOM for Firebase to keep versions in sync
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth-ktx" }  # version from BOM

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

## This Project's Current Gradle Setup

This is a **single-module** app — no convention plugins yet. Current setup:
- `gradle/libs.versions.toml` — version catalog with all deps
- `build.gradle.kts` (project) — `alias(libs.plugins.X) apply false` declarations
- `app/build.gradle.kts` — all plugins + dependencies for the single module

### Current plugins in `app/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}
```

### When to add convention plugins
Only worth adding if the project grows to 3+ modules. For a single-module app, keep the current simple setup.

## AGP Compatibility

Current project uses AGP 8.13.2. Key rules:
- `jvmToolchain(21)` — target Java 21
- Use `kotlin.plugin.compose` (separate plugin from Kotlin 2.0+) — do NOT use `buildFeatures.compose = true` alone without the compose compiler plugin
- KSP for annotation processing (Hilt, Room) — KAPT is deprecated

## Checklist

- [ ] All versions in `libs.versions.toml` — no hardcoded versions in build files
- [ ] Firebase uses BOM to keep auth/storage/firestore versions in sync
- [ ] `exportSchema = true` in Room `@Database` and `schemas/` dir committed to git
- [ ] `google-services` plugin applied in `app/build.gradle.kts` (not project level)
- [ ] `google-services.json` in `app/` directory
