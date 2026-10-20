package se.partee71.dagboken.ui.common

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.ui.common.AppErrors
import se.partee71.dagboken.ui.common.dataError
import se.partee71.ui.common.DetailLoader
import se.partee71.ui.common.DetailUiState
import se.partee71.ui.common.ListEvent
import se.partee71.ui.common.ListLoader
import se.partee71.ui.common.ListUiState
import se.partee71.ui.common.STOP_TIMEOUT_MILLIS

class ListLoaderTest {

    @Test
    fun `försök igen börjar om från laddning och läser källan på nytt`() = runTest(UnconfinedTestDispatcher()) {
        var attempts = 0
        val source = flow {
            attempts++
            if (attempts == 1) throw DataError.Offline
            emit(listOf("Solkräm"))
        }
        val loader = ListLoader(source, backgroundScope, AppErrors)
        loader.state.test {
            assertEquals(DataError.Offline, ((expectMostRecentItem()) as ListUiState.Error).error.dataError)
            loader.onEvent(ListEvent.Retry)
            assertEquals(ListUiState.Content(persistentListOf("Solkräm")), expectMostRecentItem())
        }
        assertEquals(2, attempts)
    }

    @Test
    fun `utan prenumerant går listan tillbaka till laddning - gammal lista visas aldrig`() = runTest(UnconfinedTestDispatcher()) {
        val source = MutableStateFlow(listOf("Morgonpromenad"))
        val loader = ListLoader(source, backgroundScope, AppErrors)
        loader.state.test {
            assertEquals(ListUiState.Content(persistentListOf("Morgonpromenad")), expectMostRecentItem())
        }
        advanceTimeBy(STOP_TIMEOUT_MILLIS + 1)
        assertEquals(ListUiState.Loading, loader.state.value)
    }

    @Test
    fun `detaljen laddar, visar värdet, ger fel för ett saknat dokument och försöker igen`() = runTest(UnconfinedTestDispatcher()) {
        var attempts = 0
        val source = flow<String?> {
            attempts++
            when (attempts) {
                1 -> throw DataError.Offline
                2 -> emit(null)
                else -> emit("Promenad")
            }
        }
        val loader = DetailLoader(source, backgroundScope, AppErrors)
        loader.state.test {
            assertEquals(DataError.Offline, ((expectMostRecentItem()) as DetailUiState.Error).error.dataError)
            loader.retry()
            assertEquals(DataError.NotFound, ((expectMostRecentItem()) as DetailUiState.Error).error.dataError)
            loader.retry()
            assertEquals(DetailUiState.Content("Promenad"), expectMostRecentItem())
        }
    }

    @Test
    fun `activeOnly döljer arkiverade`() = runTest {
        val people = listOf(Option("a", OptionKind.ACTIVITY, "Promenad"), Option("b", OptionKind.ACTIVITY, "Yoga", archived = true))
        activeOnly(people).test {
            assertEquals(listOf("Promenad"), awaitItem().map { it.name })
            awaitComplete()
        }
    }

    private fun activeOnly(people: List<Option>) = flowOf(people).activeOnly()
}
