plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

// ExportFormatTest och FixtureCodecsTest läser tools/db:s testdata, ParityTableTest paritetstabellen i
// ARKITEKTUR.md – en ändring där ska köra om testerna, även när de annars hämtas ur byggcachen.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("tools/db/test/fixtures"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("toolsDbFixtures")
    inputs.file(rootProject.file("ARKITEKTUR.md"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("architecture")
    inputs.file(rootProject.file("firestore.rules"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("firestoreRules")
}
