package se.partee71.dagboken.ui.common

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Feeling
import se.partee71.dagboken.core.model.FeelingCatalog

/** Känslornas svenska namn och pilltexten (SCR-7, SCR-8) – ett uppslag per token, "Okänd känsla" för det okända. */
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
}
