import com.android.build.api.dsl.ApplicationExtension
import java.time.Duration
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.roborazzi) apply false
}

// Gemensam byggkonfiguration – enda stället (skill android-gradle-logic, regel 4).
// Modulfilerna innehåller bara det som är unikt för modulen.
val javaVersion = JavaVersion.VERSION_17

subprojects {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }
    }
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            compileOptions {
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
            }
        }
    }
    tasks.withType<Test>().configureEach {
        // Ett test som hänger får inte äta upp PR-budgeten (~8 min, skill ci-budget): tasken
        // avbryts efter 4 min, och den sista STARTED-raden utan resultat i loggen namnger testet.
        timeout.set(Duration.ofMinutes(4))
        testLogging {
            events("started", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

// Copy-paste-detektor (regel 4): PMD CPD för Kotlin via PMD:s CLI. Gradle-pluginen
// de.aaschmid.cpd fungerar inte med Gradle 9. Tröskeln motiveras i ARKITEKTUR.md →
// "Byggkonfiguration" och sänks/höjs bara med motivering där.
val cpdMinimumTokens = 80
val cpdClasspath = configurations.create("cpdClasspath")
dependencies {
    add(cpdClasspath.name, libs.pmd.cli)
    add(cpdClasspath.name, libs.pmd.kotlin)
}
val cpdSources = fileTree(rootDir) {
    include("core/src/**/*.kt", "app/src/main/**/*.kt", "app/src/test/**/*.kt", "app/src/sharedTest/**/*.kt", "app/src/androidTest/**/*.kt")
    exclude("**/*Preview.kt")
}

val cpdCheck = tasks.register<JavaExec>("cpdCheck") {
    group = "verification"
    description = "Hittar kopierad Kotlin-kod (PMD CPD, minst $cpdMinimumTokens tokens)."
    val fileList = layout.buildDirectory.file("cpd/files.txt")
    val report = layout.buildDirectory.file("reports/cpd/cpd.txt")
    val root = rootDir
    val tokens = cpdMinimumTokens
    val sources: FileCollection = cpdSources
    val result = executionResult
    workingDir = root
    classpath = cpdClasspath
    mainClass.set("net.sourceforge.pmd.cli.PmdCli")
    inputs.files(sources).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(report)
    outputs.cacheIf { true }
    isIgnoreExitValue = true
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "cpd", "--language", "kotlin", "--minimum-tokens", "$tokens",
            "--file-list", fileList.get().asFile.path,
            "--format", "text", "--report-file", report.get().asFile.path,
        )
    })
    doFirst {
        fileList.get().asFile.apply {
            parentFile.mkdirs()
            writeText(sources.files.map { it.relativeTo(root).path }.sorted().joinToString("\n"))
        }
        report.get().asFile.parentFile.mkdirs()
    }
    doLast {
        // PMD: 0 = inga dubbletter, 4 = dubbletter hittade, annat = verktygsfel.
        val exit = result.get().exitValue
        if (exit != 0 && exit != 4) throw GradleException("cpdCheck: PMD avslutades med kod $exit (se loggen ovan).")
        if (exit == 4) {
            val text = report.get().asFile.takeIf { it.exists() }?.readText().orEmpty()
            throw GradleException(
                "cpdCheck: kopierad kod hittad (regel 4, skill shared-ui-components). Bryt ut till en " +
                    "delad byggsten i stället för att höja tröskeln.\n$text",
            )
        }
    }
}

tasks.named("check") { dependsOn(cpdCheck) }
