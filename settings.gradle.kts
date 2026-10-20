pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Det delade UI-biblioteket android-ui (skill android-gradle-logic, "GitHub Packages (android-ui)").
        maven("https://maven.pkg.github.com/ParTee71/android-ui") {
            name = "androidUi"
            credentials {
                username = providers.environmentVariable("GPR_USER")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.environmentVariable("GPR_TOKEN")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
            content { includeGroup("se.partee71.ui") }
        }
    }
}

rootProject.name = "Dagboken"
include(":core", ":app")
