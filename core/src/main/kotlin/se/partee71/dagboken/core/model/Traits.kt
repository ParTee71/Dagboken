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
