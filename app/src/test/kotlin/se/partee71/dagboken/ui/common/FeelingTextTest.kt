package se.partee71.dagboken.ui.common

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.FeelingEntry
import se.partee71.dagboken.core.engine.FeelingRole
import se.partee71.dagboken.core.engine.FeelingSummary
import se.partee71.dagboken.core.engine.computeFeelingStats
import se.partee71.dagboken.core.model.Feeling
import se.partee71.dagboken.core.model.FeelingCatalog
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.ui.components.GalleryFeelings

/**
 * Känslornas svenska namn och pilltexten (SCR-7, SCR-8) – ett uppslag per token, "Okänd känsla" för det okända – och
 * meningen i ord ur `FeelingSummary` för Idag och Trender (#320) med 0, 1, 2 och 3+ kärnkänslor.
 */
@RunWith(RobolectricTestRunner::class)
class FeelingTextTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun name(core: String, nuance: String? = null, detail: String? = null) = context.getString(feelingName(core, nuance, detail))

    /** Varje ord i katalogen som (kärna, nyans, precisare ord) – kärnor, nyanser och precisare ord. */
    private val words: List<Triple<String, String?, String?>> = FeelingCatalog.cores.flatMap { core ->
        listOf(Triple(core, null, null)) + FeelingCatalog.nuances(core).flatMap { nuance ->
            listOf(Triple(core, nuance, null)) + FeelingCatalog.details(core, nuance).map { Triple(core, nuance, it) }
        }
    }

    @Test
    fun `alla 114 ord i katalogen har ett eget svenskt namn`() {
        val unknown = context.getString(R.string.feeling_unknown)
        val names = words.map { (core, nuance, detail) -> name(core, nuance, detail) }
        assertEquals(114, names.size)
        assertTrue(names.none { it == unknown }, "saknar namn: " + words.filterIndexed { i, _ -> names[i] == unknown })
        assertEquals(names.size, names.toSet().size, "två ord med samma namn")
    }

    @Test
    fun `kärnorna heter Glad, Stark, Lugn, Ledsen, Arg och Rädd i hjulets ordning`() {
        assertEquals(listOf("Glad", "Stark", "Lugn", "Ledsen", "Arg", "Rädd"), FeelingCatalog.cores.map { name(it) })
        assertEquals("Spänd", name("scared", "anxious", "tense"))
    }

    @Test
    fun `ett okänt token heter Okänd känsla`() {
        assertEquals("Okänd känsla", name("bored"))
        assertEquals("Okänd känsla", name("calm", "sleepy"))
        assertEquals("Okänd känsla", name("calm", "grateful", "sleepy"))
        assertEquals("Okänd känsla", name("calm", null, "humble"), "ett precisare ord utan nyans")
    }

    @Test
    fun `pilltexten har de nivåer som finns, och en okänd känsla heter Okänd känsla`() {
        val labels = mutableListOf<String>()
        rule.setContent {
            labels += feelingsLabel(Feeling("calm", "grateful", "humble"))
            labels += feelingsLabel(Feeling("calm", "grateful"))
            labels += feelingsLabel(Feeling("scared"))
            labels += feelingsLabel(Feeling("calm", "sleepy"))
        }
        rule.waitForIdle()
        assertEquals(listOf("Lugn › Tacksam › Ödmjuk", "Lugn › Tacksam", "Rädd", "Okänd känsla"), labels.take(4))
    }

    private fun sentences(kind: FeelingSentenceKind, vararg summaries: FeelingSummary): List<String> {
        val texts = mutableListOf<String>()
        rule.setContent { summaries.forEach { texts += feelingSentence(it, kind) } }
        rule.waitForIdle()
        return texts.take(summaries.size)
    }

    private fun roles(vararg cores: String) = cores.map { FeelingRole(it) }

    private val none = FeelingSummary.EMPTY

    /**
     * Samma strukturer för båda meningarna: 0, 1, 2 och 3+ kärnor, med tillfälle, vana och orörda – och lika många på
     * gränsen: en ensam vanligaste med fler kärnor bakom ("Mest ledsen."), tre som delar förstaplatsen.
     */
    private val cases = listOf(
        none,
        FeelingSummary(roles("calm"), null, emptyList(), coreCount = 1),
        FeelingSummary(roles("sad"), null, emptyList(), coreCount = 3),
        FeelingSummary(roles("glad", "calm", "sad"), null, emptyList(), coreCount = 3),
        FeelingSummary(roles("calm", "glad"), null, emptyList(), coreCount = 2),
        FeelingSummary(roles("calm", "glad"), FeelingRole("scared", occasion = Occasion.LUNCH), emptyList(), coreCount = 3),
        FeelingSummary(roles("calm", "glad"), FeelingRole("scared", nuance = "anxious", occasion = Occasion.BREAKFAST), listOf("strong"), coreCount = 5),
        FeelingSummary(roles("sad", "angry"), FeelingRole("strong"), listOf("glad", "scared"), coreCount = 4)
    )

    @Test
    fun `dagens mening - 0, 1, 2 och 3+ kärnkänslor, tillfället men inte vana eller orörda`() {
        assertEquals(
            listOf(
                "Inga känslor loggade.",
                "Lugn.",
                "Mest ledsen.",
                "Mest glad, lugn och ledsen.",
                "Mest lugn och glad.",
                "Mest lugn och glad. Lite rädd vid lunch.",
                "Mest lugn och glad. Lite rädd efter frukost.",
                "Mest ledsen och arg. Lite stark."
            ),
            sentences(FeelingSentenceKind.DAY, *cases.toTypedArray())
        )
    }

    @Test
    fun `periodens mening - 0, 1, 2 och 3+ kärnkänslor, vana och orörda men inte tillfället`() {
        assertEquals(
            listOf(
                "Inga känslor loggade.",
                "Du bor i Lugn.",
                "Du bor i Ledsen.",
                "Du bor i Glad, Lugn och Ledsen.",
                "Du bor i Lugn och Glad.",
                "Du bor i Lugn och Glad. Rädd är din tredje hemvist.",
                "Du bor i Lugn och Glad. Rädd är din tredje hemvist, nästan alltid som Orolig. Stark har du knappt rört.",
                "Du bor i Ledsen och Arg. Stark är din tredje hemvist. Glad och Rädd har du knappt rört."
            ),
            sentences(FeelingSentenceKind.PERIOD, *cases.toTypedArray())
        )
    }

    @Test
    fun `lika många på andra och tredje plats - bara den vanligaste nämns, ingen lite`() {
        val tie = computeFeelingStats(listOf(FeelingEntry(LocalDate(2026, 10, 14), listOf("sad", "sad", "calm", "glad").map { checkNotNull(Feeling.parse(it)) }))).summary
        assertEquals(listOf("Mest ledsen."), sentences(FeelingSentenceKind.DAY, tie))
    }

    @Test
    fun `en handbyggd struktur som säger en kärna men har två mest kraschar inte`() {
        assertEquals(listOf("Mest lugn och glad."), sentences(FeelingSentenceKind.DAY, FeelingSummary(roles("calm", "glad"), null, emptyList(), coreCount = 1)))
    }

    @Test
    fun `stängda läget följer lika-antal-regeln`() {
        val texts = mutableListOf<String>()
        rule.setContent { cases.forEach { texts += feelingHighlights(it) } }
        rule.waitForIdle()
        assertEquals(
            listOf("Inga känslor i perioden", "Mest lugn", "Mest ledsen", "Mest glad, lugn och ledsen", "Mest lugn och glad", "Mest lugn och glad", "Mest lugn och glad", "Mest ledsen och arg"),
            texts.take(cases.size)
        )
    }

    @Test
    fun `galleriets dag ger mockupens mening`() {
        assertEquals(listOf("Mest glad och lugn. Lite rädd vid lunch."), sentences(FeelingSentenceKind.DAY, GalleryFeelings.day.summary))
    }

    @Test
    fun `galleriets period ger mockupens mening`() {
        assertEquals(
            listOf("Du bor i Lugn och Glad. Rädd är din tredje hemvist, nästan alltid som Orolig. Stark har du knappt rört."),
            sentences(FeelingSentenceKind.PERIOD, GalleryFeelings.period.summary)
        )
    }
}
