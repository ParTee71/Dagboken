package se.partee71.dagboken.data.firestore

/**
 * Alla Firestore-sökvägar – enda stället i appen (ARKITEKTUR.md → Datamodell). Allt ligger under
 * `users/{uid}`. Speglas i `tools/db/lib/collections.mjs`; ett test håller listorna lika
 * (skill data-safety-backup).
 */
object Paths {
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

    /** Undersamlingar per föräldersamling. */
    val SUBCOLLECTIONS: Map<String, List<String>> = mapOf(ILLNESS_EPISODES to listOf(CHECKINS))

    fun user(uid: String) = "$USERS/$uid"

    /** En samling direkt under användaren, t.ex. `users/{uid}/doses`. */
    fun collection(uid: String, name: String) = "${user(uid)}/$name"

    fun settings(uid: String) = collection(uid, SETTINGS)

    fun options(uid: String) = collection(uid, OPTIONS)

    fun prescriptions(uid: String) = collection(uid, PRESCRIPTIONS)

    fun prnMedicines(uid: String) = collection(uid, PRN_MEDICINES)

    fun doses(uid: String) = collection(uid, DOSES)

    fun screenings(uid: String) = collection(uid, SCREENINGS)

    fun activities(uid: String) = collection(uid, ACTIVITIES)

    fun events(uid: String) = collection(uid, EVENTS)

    fun illnessEpisodes(uid: String) = collection(uid, ILLNESS_EPISODES)

    fun checkins(uid: String, episodeId: String) = "${illnessEpisodes(uid)}/$episodeId/$CHECKINS"
}
