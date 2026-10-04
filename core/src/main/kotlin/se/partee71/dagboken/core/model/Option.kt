package se.partee71.dagboken.core.model

import java.security.MessageDigest
import java.text.Normalizer
import java.util.HexFormat
import java.util.Locale

/**
 * `users/{uid}/options/{id}` – ett alternativ i någon av listorna aktivitet, symptom eller
 * händelsetyp (DAT-9). Poster refererar alternativet med `optionId`, så ett namnbyte syns i
 * historiken (SET-11). Ett använt alternativ arkiveras i stället för att raderas.
 */
data class Option(
    override val id: String,
    /** Vilken lista alternativet hör till. */
    val kind: OptionKind = OptionKind.ACTIVITY,
    /** Namnet som visas. */
    val name: String = "",
    /** Stjärnmärkt: visas som en-tryck-chip (SET-5, SET-9). */
    val favorite: Boolean = false,
    /** Plats i listan (3.x: listans ordning). */
    override val sortOrder: Int = 0,
    /** Dold i formulären men kvar för poster som refererar det. */
    override val archived: Boolean = false,
) : Identified, Sortable, Archivable

enum class OptionKind(override val wire: String) : WireEnum {
    ACTIVITY("activity"),
    SYMPTOM("symptom"),
    EVENT("event"),
}

/**
 * Dokument-id för alternativ (DAT-13). 3.x-alternativen hade inga id:n (de var namn i listor), så
 * konverteraren och 4.0 ger dem samma deterministiska id ur lista och namn – en upprepad import
 * skriver över i stället för att dubblera, och ett namn ger alltid samma id.
 */
object OptionIds {
    /** Längsta läsbara del (slug) i id:t. */
    const val MAX_SLUG = 32

    /** Antal hex-tecken ur SHA-256 i id:t. */
    const val HASH_LENGTH = 6

    /**
     * `"${kind.wire}-${slug}-${hash}"`. slug = [name] NFD-normaliserat med alla kombinerande tecken
     * borttagna (`å`→`a`, `ä`→`a`, `ö`→`o`, `é`→`e`) och gement, varje tecken utom `a–z` och `0–9`
     * ersatt med `-`, bindestreck hopslagna och trimmade, högst [MAX_SLUG] tecken (tom om inget
     * tecken återstår: `"activity--1a2b3c"`). hash = de [HASH_LENGTH] första
     * hex-tecknen av SHA-256 över UTF-8-byten i det **exakta** namnet, så att `"Promenad"`,
     * `"promenad"` och `"Promenad "` – samma slug – får olika id.
     */
    fun of(kind: OptionKind, name: String): String = "${kind.wire}-${slug(name)}-${hash(name)}"

    internal fun slug(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD).replace(COMBINING, "").lowercase(Locale.ROOT)
            .replace(NOT_SLUG, "-").replace(DASHES, "-").trim('-')
            .take(MAX_SLUG).trimEnd('-')

    private fun hash(name: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))).take(HASH_LENGTH)

    private val COMBINING = Regex("\\p{Mn}+")
    private val NOT_SLUG = Regex("[^a-z0-9]")
    private val DASHES = Regex("-{2,}")
}
