package se.partee71.dagboken.core.schema

/**
 * Dataformatets version (`users/{uid}.schemaVersion`, skill data-safety-backup).
 * Höjs vid betydelseändring: omdöpt fält, ändrad enhet eller struktur, eller ett nytt
 * enum-värde/en ny variant som en äldre app inte kan tolka. Ett nytt valfritt fält med
 * default höjer den inte.
 */
object Schema {
    /**
     * Historik:
     * - 1: första formatet i Dagboken 4.0 (ARKITEKTUR.md → Datamodell).
     */
    const val CURRENT_VERSION = 1

    /** Version för ett användardokument utan `schemaVersion` – det första formatet, aldrig "senaste". */
    const val FIRST_VERSION = 1

    /** Nyare data än appen förstår: läs, men skriv ingenting och be om uppdatering. */
    fun isNewerThanApp(version: Int): Boolean = version > CURRENT_VERSION

    /**
     * Versionen ur användardokumentets råa `schemaVersion`. Saknas den, eller är den under
     * [FIRST_VERSION] (ett trasigt värde som 0), gäller det första formatet.
     */
    fun versionOf(raw: Any?): Int = (raw as? Number)?.toIntClamped()?.coerceAtLeast(FIRST_VERSION) ?: FIRST_VERSION
}
