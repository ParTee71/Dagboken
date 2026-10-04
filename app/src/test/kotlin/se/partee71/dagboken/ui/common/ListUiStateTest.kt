package se.partee71.dagboken.ui.common

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.dagboken.data.common.DataError

class AsListUiStateTest {

    @Test
    fun `laddar, sedan innehåll eller tomt`() = runTest {
        flowOf(emptyList<String>(), listOf("a")).asListUiState().test {
            assertEquals(ListUiState.Loading, awaitItem())
            assertEquals(ListUiState.Empty, awaitItem())
            assertEquals(ListUiState.Content(listOf("a")), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `fel blir Error med DataError, okända fel som Unknown`() = runTest {
        flow<List<String>> { throw DataError.PermissionDenied }.asListUiState().test {
            assertEquals(ListUiState.Loading, awaitItem())
            assertEquals(ListUiState.Error(DataError.PermissionDenied), awaitItem())
            awaitComplete()
        }
        flow<List<String>> { error("x") }.asListUiState().test {
            awaitItem()
            assertEquals(ListUiState.Error(DataError.Unknown), awaitItem())
            awaitComplete()
        }
    }
}
