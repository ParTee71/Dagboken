package se.partee71.dagboken.ui.common

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.repository.DefaultOptionsRepository

class OptionNamesTest {
    private val factory = FakeCollectionFactory()
    private val options = factory.options()

    @Test
    fun `names ger id till namn för listan, arkiverade med, och följer ett namnbyte`() = runTest(UnconfinedTestDispatcher()) {
        options.batch(
            listOf(
                Option("yrsel", OptionKind.SYMPTOM, "Yrsel"),
                Option("gammal", OptionKind.SYMPTOM, "Gammal", archived = true),
                Option("promenad", OptionKind.ACTIVITY, "Promenad")
            )
        ).getOrThrow()
        val repository = DefaultOptionsRepository(factory)
        repository.names(OptionKind.SYMPTOM).test {
            assertEquals(mapOf("yrsel" to "Yrsel", "gammal" to "Gammal"), awaitItem())
            options.upsert(Option("yrsel", OptionKind.SYMPTOM, "Svindel")).getOrThrow()
            assertEquals(mapOf("yrsel" to "Svindel", "gammal" to "Gammal"), awaitItem())
        }
    }
}
