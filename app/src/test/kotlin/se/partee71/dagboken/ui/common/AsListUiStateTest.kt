package se.partee71.dagboken.ui.common

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.ui.common.AppErrors
import se.partee71.dagboken.ui.common.dataError
import se.partee71.ui.common.ListUiState
import se.partee71.ui.common.asListUiState

class AsListUiStateTest {

    @Test
    fun `laddar, sedan innehåll eller tomt`() = runTest {
        flowOf(emptyList<String>(), listOf("a")).asListUiState(AppErrors).test {
            assertEquals(ListUiState.Loading, awaitItem())
            assertEquals(ListUiState.Empty, awaitItem())
            assertEquals(ListUiState.Content(persistentListOf("a")), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `fel blir Error med DataError, okända fel som Unknown`() = runTest {
        flow<List<String>> { throw DataError.PermissionDenied }.asListUiState(AppErrors).test {
            assertEquals(ListUiState.Loading, awaitItem())
            assertEquals(DataError.PermissionDenied, ((awaitItem()) as ListUiState.Error).error.dataError)
            awaitComplete()
        }
        flow<List<String>> { error("x") }.asListUiState(AppErrors).test {
            awaitItem()
            assertEquals(DataError.Unknown, ((awaitItem()) as ListUiState.Error).error.dataError)
            awaitComplete()
        }
    }
}
