package se.partee71.dagboken.core.schema

/**
 * Lyfter ett dokument i ett äldre format steg för steg till [Schema.CURRENT_VERSION].
 *
 * Versionen hör till användaren (`users/{uid}`), inte till varje dokument. Därför:
 * - Varje steg ska vara **idempotent** (känna igen redan migrerad data), eftersom samma
 *   dokument kan passera flera gånger innan användardokumentet stämplats om.
 * - `schemaVersion` höjs av en fullständig migrering av alla användarens dokument med
 *   `tools/db/migrate.mjs`, aldrig av en vanlig skrivning. Ändrar inget steg dit några dokument
 *   ([canStamp]) stämplar appen själv användardokumentet när det öppnas, så att en äldre app
 *   genast ber om uppdatering i stället för att skriva tillbaka ett värde den inte förstår.
 * Varje steg speglas i `tools/db/migrate.mjs` och testas i `SchemaMigratorTest`.
 */
object SchemaMigrator {

    /**
     * Ett steg från version `n` till `n + 1` för ett dokument i en samling. [changesData] =
     * `false` för ett steg som bara gör nya värden möjliga (ett nytt enum-värde) och inte ändrar
     * några befintliga dokument.
     */
    class Step(val changesData: Boolean, private val transform: (collection: String, doc: Doc) -> Doc) {
        fun migrate(collection: String, doc: Doc): Doc = transform(collection, doc)

        companion object {
            /** Inget befintligt dokument ändras. */
            val UNCHANGED = Step(changesData = false) { _, doc -> doc }
        }
    }

    /** Nyckel = versionen steget migrerar *från*. */
    internal val steps: Map<Int, Step> = emptyMap() // version 1 är den första – inga steg än


    /**
     * Om ett användardokument i version [from] kan stämplas med [Schema.CURRENT_VERSION] utan att några
     * dokument migreras: det är äldre än appen och inget steg dit ändrar data.
     */
    fun canStamp(from: Int): Boolean =
        from < Schema.CURRENT_VERSION && stepsFrom(from).none { it.changesData }

    fun migrate(from: Int, collection: String, doc: Doc): Doc {
        require(!Schema.isNewerThanApp(from)) {
            "schemaVersion $from är nyare än appens ${Schema.CURRENT_VERSION} – kan inte migreras bakåt"
        }
        return stepsFrom(from).fold(doc) { current, step -> step.migrate(collection, current) }
    }

    /** Stegen från [from] till [Schema.CURRENT_VERSION]; en version under den första (trasigt värde) är det första formatet. */
    private fun stepsFrom(from: Int): List<Step> =
        (from.coerceAtLeast(Schema.FIRST_VERSION) until Schema.CURRENT_VERSION).map { version ->
            checkNotNull(steps[version]) { "Migreringssteg saknas för version $version" }
        }
}
