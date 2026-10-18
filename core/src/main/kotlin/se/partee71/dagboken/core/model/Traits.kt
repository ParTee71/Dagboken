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
 * En post med graderade symptom (DAT-6) – måendelogg, aktivitet och incheckning: [symptoms], det bevarade 3.x-värdet
 * [legacySomatic] och summan [somatic] som gäller. **Enda** stället för regeln om 3.x `somatiska`.
 */
interface HasSymptoms<T : HasSymptoms<T>> {
    val symptoms: List<SymptomScore>

    /**
     * 3.x `somatiska` när det inte är summan av [symptoms] – bara importerad 3.x-data (namnlista utan poäng, eget värde).
     * `null` = summan gäller.
     */
    val legacySomatic: Int?

    /** Somatiska: [legacySomatic] när 3.x-värdet bevarats, annars summan av poängen – det 3.x Trender visade. */
    val somatic: Int get() = legacySomatic ?: symptoms.somatic

    /** Posten med [symptoms] och [legacySomatic] satta (dataklassens `copy`). */
    fun copySymptoms(symptoms: List<SymptomScore>, legacySomatic: Int?): T

    /** Nya symptom: 3.x-värdet gäller bara så länge symptomen är oförändrade (3.x räknade om summan vid sparning). */
    fun withSymptoms(symptoms: List<SymptomScore>): T = copySymptoms(symptoms, legacySomatic.takeIf { symptoms == this.symptoms })
}

/**
 * En post med loggade känslor (SCR-7, DAT-14) – måendeloggen: [feelings] ur [FeelingCatalog], högst
 * [FeelingCatalog.MAX_FEELINGS], i vald ordning. Okända tokens bevaras ([Feeling]).
 */
interface HasFeelings<T : HasFeelings<T>> {
    val feelings: List<Feeling>

    /** Posten med [feelings] satta (dataklassens `copy`). */
    fun withFeelings(feelings: List<Feeling>): T
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
