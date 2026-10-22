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
 * En post med loggade känslor (SCR-7, AKT-13, DAT-14) – måendeloggen och aktiviteten: [feelings] ur [FeelingCatalog], högst
 * [FeelingCatalog.MAX_FEELINGS], i vald ordning, och per vald känsla en frivillig styrka ([feelingStrengths], SCR-9) och
 * kommentar ([feelingNotes], SCR-10), nycklade på [Feeling.token]. Okända tokens och styrkeord bevaras ([Feeling]).
 * **Enda** stället för reglerna om hur styrka och kommentar följer valen ([FeelingValues]).
 */
interface HasFeelings<T : HasFeelings<T>> {
    val feelings: List<Feeling>

    /** Token → styrkeord ([FeelingStrength.wire]); bara valda känslor, saknad nyckel = ingen styrka (SCR-9). */
    val feelingStrengths: Map<String, String>

    /** Token → kommentar, inte tom; bara valda känslor (SCR-10, DAT-15). Lagras trimmad (codecen). */
    val feelingNotes: Map<String, String>

    /** Posten med alla tre satta (dataklassens `copy`). */
    fun copyFeelings(feelings: List<Feeling>, strengths: Map<String, String>, notes: Map<String, String>): T

    /**
     * Nya val: styrka och kommentar följer ett val som förfinas på sin plats; ett ord som släcks nollställer styrkan men
     * behåller kommentaren, och en borttagen känsla tar med sig båda (SCR-9, SCR-10).
     */
    fun withFeelings(feelings: List<Feeling>): T = copyFeelings(
        feelings,
        FeelingValues.carry(this.feelings, feelings, feelingStrengths, keepWhenCoarser = false),
        FeelingValues.carry(this.feelings, feelings, feelingNotes, keepWhenCoarser = true)
    )

    /** Den kända styrkan på [feeling]; `null` = ingen (eller ett okänt ord från en nyare app). */
    fun strengthOf(feeling: Feeling): FeelingStrength? = FeelingStrength.of(feelingStrengths[feeling.token])

    /** [feeling] ett steg starkare i långtryckets cykel (SCR-9); en känsla som inte är vald ändras inte. */
    fun withNextStrength(feeling: Feeling): T = update(feeling) {
        val next = FeelingStrength.next(strengthOf(feeling))
        copyFeelings(feelings, if (next == null) feelingStrengths - feeling.token else feelingStrengths + (feeling.token to next.wire), feelingNotes)
    }

    fun noteOf(feeling: Feeling): String = feelingNotes[feeling.token].orEmpty()

    /**
     * Kommentaren till [feeling] som den skrivs (codecen trimmar när den lagras, så att ett mellanslag går att skriva); en
     * tom text tas bort (SCR-10). En känsla som inte är vald ändras inte.
     */
    fun withNote(feeling: Feeling, note: String): T = update(feeling) {
        copyFeelings(feelings, feelingStrengths, if (note.isBlank()) feelingNotes - feeling.token else feelingNotes + (feeling.token to note))
    }

    /** [change] för en vald känsla; en känsla som inte är vald ger en oförändrad kopia. */
    private fun update(feeling: Feeling, change: () -> T): T = if (feeling in feelings) change() else copyFeelings(feelings, feelingStrengths, feelingNotes)
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
