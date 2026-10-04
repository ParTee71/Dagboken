package se.partee71.dagboken.data.firestore

import se.partee71.dagboken.core.schema.CollectionNames

/**
 * Alla Firestore-sökvägar – enda stället i appen (ARKITEKTUR.md → Datamodell). Allt ligger under
 * `users/{uid}`. Namnen är `CollectionNames` i `:core`; `tools/db/lib/collections.mjs` speglar dem och
 * ett test håller alla tre lika (skill data-safety-backup).
 */
object Paths {
    // Namnen kommer från :core (CollectionNames) – konverteraren och exportformatet använder samma.
    const val USERS = CollectionNames.USERS
    const val SETTINGS = CollectionNames.SETTINGS
    const val OPTIONS = CollectionNames.OPTIONS
    const val PRESCRIPTIONS = CollectionNames.PRESCRIPTIONS
    const val PRN_MEDICINES = CollectionNames.PRN_MEDICINES
    const val DOSES = CollectionNames.DOSES
    const val SCREENINGS = CollectionNames.SCREENINGS
    const val ACTIVITIES = CollectionNames.ACTIVITIES
    const val EVENTS = CollectionNames.EVENTS
    const val ILLNESS_EPISODES = CollectionNames.ILLNESS_EPISODES
    const val CHECKINS = CollectionNames.CHECKINS

    /** Samlingarna direkt under `users/{uid}`, i Datamodell-tabellens ordning. */
    val USER_COLLECTIONS: List<String> = CollectionNames.USER_COLLECTIONS

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
