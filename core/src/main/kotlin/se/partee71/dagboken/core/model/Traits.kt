package se.partee71.dagboken.core.model

/** Har ett dokument-ID (Firestore-dokumentets ID, lagras inte som fält). */
interface Identified {
    val id: String
}

/** Användarstyrd ordning i listor. */
interface Sortable {
    val sortOrder: Int

    companion object {
        /** Den enda sorteringen på `sortOrder` – listor sorteras aldrig på egen hand (regel 4). */
        val ORDER: Comparator<Sortable> = compareBy { it.sortOrder }
    }
}

/** Kan arkiveras och återställas – samma beteende för alla listbara entiteter. */
interface Archivable {
    val archived: Boolean
}

/**
 * En post i dagboken (mående, aktivitet, händelse, incheckning): dag, skapandetid – satt en gång när posten
 * skapas – och samma post med ett annat id ([withId], för att skriva en ändring på det lästa dokumentets id).
 */
interface Post<T : Post<T>> : Identified {
    val date: kotlinx.datetime.LocalDate?
    val createdAt: kotlin.time.Instant?

    fun withId(id: String): T
}
