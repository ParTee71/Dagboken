package se.partee71.dagboken.domain.model

data class SjukdomsEpisod(
    val id: String,
    val typ: String,
    val startDatum: String,
    val slutDatum: String,
    val incheckningar: List<SjukdomsIncheckning> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
)

val SjukdomsEpisod.pagaende: Boolean get() = slutDatum.isBlank()

fun SjukdomsEpisod.varaktighetDagar(): Int? {
    if (slutDatum.isBlank()) return null
    return try {
        val start = java.time.LocalDate.parse(startDatum)
        val slut  = java.time.LocalDate.parse(slutDatum)
        (java.time.temporal.ChronoUnit.DAYS.between(start, slut) + 1).toInt()
    } catch (_: Exception) { null }
}
