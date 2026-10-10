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

// Grinden OMB-4 (ARKITEKTUR.md → Migrering, punkt 3): konverterar en 3.x-backup till en fil som
// `node tools/db/import.mjs --dry-run` godtar. Rapporten skrivs till stdout utan innehåll.
//   ./gradlew :core:convertLegacyBackup --args="--in <3.x-backup.json> --out <export.json> --user <uid>"
tasks.register<JavaExec>("convertLegacyBackup") {
    group = "migration"
    description = "Konverterar en 3.x-backup (BackupJson) till 4.0-exportformat (OMB-3, OMB-4)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("se.partee71.dagboken.core.legacy.ConvertBackupMainKt")
    workingDir = rootDir // sökvägarna i --args anges från repots rot
}

// Recept och vid behov-mediciner får namn, styrka och form ur Läkemedelsverkets lista (REC-1, FAV-1), i två
// steg på en `tools/db export`-fil. Stdout får bara antal (MedicineRename, MatchMedicinesMain.kt).
//   ./gradlew :core:matchMedicines --args="--in <export.json> --catalog app/src/main/assets/medicines.tsv --suggest <map.tsv>"
//   ./gradlew :core:matchMedicines --args="--in <export.json> --map <map.tsv> --out <renamed.json>"
tasks.register<JavaExec>("matchMedicines") {
    group = "migration"
    description = "Föreslår och tillämpar namn, styrka och form ur Läkemedelsverkets lista på en export (REC-1, FAV-1)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("se.partee71.dagboken.core.medicine.MatchMedicinesMainKt")
    workingDir = rootDir // sökvägarna i --args anges från repots rot
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
