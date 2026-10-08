package se.partee71.dagboken.core.model

/**
 * Ett graderat symptom på en post (DAT-6): alternativet ([optionId] i `options`, kind
 * `symptom`), poängen 0–10 och fritexten vid "Övrigt" (AKT-6). Ersätter 3.x wire-formatet
 * `Namn:Poäng,…`, där fritexten låg i namnet: `Övrigt (fritext):Poäng`.
 */
data class SymptomScore(
    val optionId: String = "",
    /**
     * Poängen 0–10; `null` = symptomet utan poäng – bara från 3.x, där en symptomtext helt utan `:poäng` (en
     * namnlista) inte gick att läsa (OMB-3). Räknas som 0 i summan och ger ingen punkt i trenderna; 4.0 skapar aldrig `null`.
     */
    val score: Int? = 0,
    /** Egen beskrivning vid "Övrigt" (AKT-6); `null` för vanliga symptom. */
    val customText: String? = null,
)

/** Summan av poängen – 3.x `somatiska`, som i 4.0 beräknas och aldrig persisteras (DAT-6). */
val List<SymptomScore>.somatic: Int get() = sumOf { it.score ?: 0 }
