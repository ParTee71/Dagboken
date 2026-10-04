package se.partee71.dagboken.core.legacy

/**
 * 3.x:s standardvärden som konverteraren behöver (branchen `legacy`, `data/datastore/PreferencesRepository.kt`).
 * Tidpunkternas och tillfällenas namn och standardtider ligger på [se.partee71.dagboken.core.model.Slot]
 * och [se.partee71.dagboken.core.model.Occasion]; påminnelsernas standardvärden är modellernas defaults.
 */
internal object LegacyDefaults {
    /** 3.x:s fasta alternativ för "annat": aktivitetens fritext (AKT-2) och symptomens fritext (AKT-6). */
    const val OTHER = "Övrigt"

    /** `DEFAULT_AKTIVITET_OPTIONS` – gäller när backupen saknar listan (`null`). */
    val ACTIVITY_OPTIONS = listOf("Promenad", "Jobb", "Möte", "Träning", "Vila", "Mat", "Sällskap", "Läsning")

    /** `DEFAULT_SYMPTOM_OPTIONS`. */
    val SYMPTOM_OPTIONS = listOf("Huvudvärk", "Trötthet", "Yrsel", "Smärta", "Illamående", OTHER)

    /** `DEFAULT_HANDELSE_TYP_OPTIONS`. */
    val EVENT_OPTIONS = listOf(
        "Blodtrycksfall", "Ögonmigrän", "Vita fingrar (Raynaud)", "Plötslig huvudvärk",
        "Allergisk reaktion", "Yrsel", "Hjärtklappning", "Andnöd",
    )

    /** 3.x `NoteTarget`-namnen, i enum-ordningen. */
    const val NOTE_ACTIVITY = "ACTIVITY"
    const val NOTE_SCREENING = "SCREENING"
    const val NOTE_MEDICATION = "MEDICATION"
    const val NOTE_PRESCRIPTION = "RECEPT"
    const val NOTE_PRN = "FAVORIT"
    const val NOTE_EVENT = "EVENT"
    const val NOTE_EPISODE = "SJUKDOM_EPISOD"
    const val NOTE_CHECKIN = "SJUKDOM_INCHECKNING"
    val NOTE_TARGETS = listOf(
        NOTE_ACTIVITY, NOTE_SCREENING, NOTE_MEDICATION, NOTE_PRESCRIPTION, NOTE_PRN, NOTE_EVENT, NOTE_EPISODE, NOTE_CHECKIN,
    )

    /** 3.x `Aktivitet.type`. */
    const val TYPE_SCREENING = "screening"

    /** 3.x `Sex.storageKey` → 4.0 (`SettingsBackup.sex`). */
    const val SEX_MALE = "man"
    const val SEX_FEMALE = "kvinna"
    const val SEX_UNSPECIFIED = "ej_angivet"
}
