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
        // Kodhast först, ParTee71 som reserv: före flytten svarar Kodhast 404 och Gradle går vidare,
        // efter flytten svarar Kodhast och den gamla adressen tillfrågas aldrig (claude-android #80).
        listOf("Kodhast", "ParTee71").forEach { owner ->
            maven("https://maven.pkg.github.com/$owner/android-ui") {
                name = "androidUi$owner"
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
}

rootProject.name = "Dagboken"
include(":core", ":app")
