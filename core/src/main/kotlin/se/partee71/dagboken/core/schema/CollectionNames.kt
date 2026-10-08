package se.partee71.dagboken.core.schema

/**
 * Samlingarnas namn och sökvägar under `users/{uid}` (ARKITEKTUR.md → Datamodell) för `:core`:
 * konverteraren, exportformatet och valideringen bygger sökvägar med dem. `Paths` i `:app` och
 * `tools/db/lib/collections.mjs` har samma namn; `tools/db/test/collections.test.mjs` håller de tre lika.
 */
object CollectionNames {
    const val USERS = "users"
    const val SETTINGS = "settings"
    const val OPTIONS = "options"
    const val PRESCRIPTIONS = "prescriptions"
    const val PRN_MEDICINES = "prnMedicines"
    const val DOSES = "doses"
    const val SCREENINGS = "screenings"
    const val ACTIVITIES = "activities"
    const val EVENTS = "events"
    const val ILLNESS_EPISODES = "illnessEpisodes"
    const val CHECKINS = "checkins"

    /** Samlingarna direkt under `users/{uid}`, i Datamodell-tabellens ordning. */
    val USER_COLLECTIONS: List<String> =
        listOf(SETTINGS, OPTIONS, PRESCRIPTIONS, PRN_MEDICINES, DOSES, SCREENINGS, ACTIVITIES, EVENTS, ILLNESS_EPISODES)

    /** Alla samlingar som har dokument, i rapportordning: användaren, sedan samlingarna och undersamlingen. */
    val ALL: List<String> = listOf(USERS) + USER_COLLECTIONS + CHECKINS

    fun user(uid: String) = "$USERS/$uid"

    /** Ett dokument i en samling direkt under användaren, t.ex. `users/{uid}/doses/{id}`. */
    fun document(uid: String, collection: String, id: String) = "${user(uid)}/$collection/$id"

    /** Incheckningarna under en episod, `users/{uid}/illnessEpisodes/{eid}/checkins`. */
    fun checkins(uid: String, episodeId: String) = "${document(uid, ILLNESS_EPISODES, episodeId)}/$CHECKINS"

    /** En incheckning under sin episod (3.x `episodId` är sökvägen). */
    fun checkin(uid: String, episodeId: String, id: String) = "${checkins(uid, episodeId)}/$id"

    /** Samlingens namn ur en dokumentsökväg (näst sista ledet); `users/{uid}` → [USERS]. */
    fun collectionOf(path: String): String = path.split('/').let { it[it.size - 2] }

    /** Antal dokument per samling i [ALL]-ordning, bara samlingar med dokument – rapportens och granskningens "före". */
    fun countsOf(paths: Collection<String>): Map<String, Int> {
        val counts = paths.groupingBy(::collectionOf).eachCount()
        return ALL.filter { it in counts }.associateWith { counts.getValue(it) }
    }
}
