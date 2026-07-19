import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

private val buildTimestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())

private val versionNameOverride = findProperty("versionNameOverride") as String?

private val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "se.partee71.dagboken"
    compileSdk = 36

    defaultConfig {
        applicationId = "se.partee71.dagboken"
        minSdk = 30
        targetSdk = 35
        versionCode = 35
        versionName = versionNameOverride ?: "3.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    val releaseStorePassword = localProps.getProperty("signing.storePassword")
        ?: System.getenv("SIGNING_STORE_PASSWORD")
    val releaseKeyPassword = localProps.getProperty("signing.keyPassword")
        ?: System.getenv("SIGNING_KEY_PASSWORD")
    val hasSigningCredentials = releaseStorePassword != null && releaseKeyPassword != null

    signingConfigs {
        if (hasSigningCredentials) {
            create("release") {
                storeFile     = file("dagboken.jks")
                storePassword = releaseStorePassword
                keyAlias      = localProps.getProperty("signing.keyAlias")
                    ?: System.getenv("SIGNING_KEY_ALIAS")
                    ?: "dagboken"
                keyPassword   = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasSigningCredentials) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                output.outputFileName =
                    "dagboken-${variant.versionName}-${buildTimestamp}.apk"
            }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/*.SF"
            excludes += "META-INF/*.DSA"
            excludes += "META-INF/*.RSA"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        // Each test method runs in its own fresh instrumentation process, so
        // process-wide state (DataStore file, leaked coroutine collectors) can't
        // bleed between tests — see #112 for background on this class of flake.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

tasks.withType<Test> {
    // Bounds any single hanging test (e.g. a runBlocking call stuck on an
    // unmocked network/IO call) instead of letting it silently eat the whole
    // job's timeout budget with no indication of which test was responsible.
    timeout.set(Duration.ofMinutes(3))
    testLogging {
        events("started", "passed", "skipped", "failed")
    }
}

dependencies {
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.compose.ui.text.googlefonts)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Image loading
    implementation(libs.coil.compose)

    // Historik kalendervy (HIST-6)
    implementation(libs.kizitonwose.calendar.compose)

    // Diagram (Trender + Home-sparkline), regel 4 (#110)
    implementation(libs.vico.compose.m3)

    // Health Connect – hälsodata från Galaxy Watch via Samsung Health (epic #54, spike #56)
    implementation(libs.androidx.health.connect)

    // Google Drive / Auth (play-services-auth still needed for Identity.getAuthorizationClient)
    implementation(libs.google.auth.play.services)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    // Credential Manager (modern Google Sign-In)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)

    // WorkManager + Hilt
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit.ext)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestUtil(libs.androidx.test.orchestrator)
}
