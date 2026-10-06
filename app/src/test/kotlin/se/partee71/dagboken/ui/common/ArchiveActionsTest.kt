package se.partee71.dagboken.ui.common

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.components.UndoRequest

/** Den delade arkiveringslogiken för listor (NFR-3) – listornas egna tester visar den i sitt sammanhang. */
class ArchiveActionsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val written = mutableListOf<Pair<String, Boolean>>()
    private var result: Result<Unit> = Result.success(Unit)

    private fun actions(undoFormat: Int = R.string.archived_format) = ArchiveActions(
        CoroutineScope(main.dispatcher),
        setArchived = { id, archived ->
            written += id to archived
            result
        },
        undoFormat = undoFormat,
    )

    @Test
    fun `arkivera ger Ångra, och Ångra återställer en gång`() = runTest {
        val archive = actions()
        archive.onEvent(ArchiveEvent.Archive("a", "Promenad"))
        assertEquals(UndoRequest("a", "Promenad"), archive.undo.value)
        archive.onEvent(ArchiveEvent.Undo)
        archive.onEvent(ArchiveEvent.Undo)
        assertNull(archive.undo.value)
        assertEquals(listOf("a" to true, "a" to false), written)
    }

    @Test
    fun `ett skrivfel visas som meddelande och ger inget Ångra`() = runTest {
        val archive = actions()
        result = Result.failure(DataError.PermissionDenied)
        archive.onEvent(ArchiveEvent.Archive("a", "Promenad"))
        assertEquals(DataError.PermissionDenied, archive.failure.value?.error)
        assertEquals(R.string.error_permission_denied, archive.failure.value?.message)
        assertNull(archive.undo.value)
        archive.onEvent(ArchiveEvent.ErrorShown)
        assertNull(archive.failure.value)
    }

    @Test
    fun `två fel i rad är två händelser, med egen text där den finns`() = runTest {
        val archive = ArchiveActions(CoroutineScope(main.dispatcher), { _, _ -> result }, errorMessage = { if (it is IllegalStateException) R.string.option_name_duplicate else null })
        archive.failure.test {
            assertNull(awaitItem())
            result = Result.failure(DataError.Offline)
            archive.onEvent(ArchiveEvent.Archive("a", "Promenad"))
            archive.onEvent(ArchiveEvent.Archive("b", "Yoga"))
            assertEquals(DataError.Offline, awaitItem()?.error)
            assertEquals(DataError.Offline, awaitItem()?.error, "samma fel igen visas igen")
            result = Result.failure(IllegalStateException("dubblett"))
            archive.onEvent(ArchiveEvent.Archive("c", "Simning"))
            val custom = awaitItem()
            assertEquals(DataError.Unknown to R.string.option_name_duplicate, custom?.error to custom?.message)
        }
    }

    @Test
    fun `arkiverade visas sist och bara när de visas`() = runTest {
        val archive = actions()
        val all = flowOf(listOf(Option("a", OptionKind.ACTIVITY, "Promenad", archived = true), Option("b", OptionKind.ACTIVITY, "Yoga")))
        assertEquals(listOf("Yoga"), archive.visible(all).first().map { it.name })
        archive.onEvent(ArchiveEvent.ToggleArchived)
        assertTrue(archive.showingArchived.value)
        assertEquals(listOf("Yoga", "Promenad"), archive.visible(all).first().map { it.name })
        archive.onEvent(ArchiveEvent.ToggleArchived)
        assertFalse(archive.showingArchived.value)
    }

    @Test
    fun `samma mekanik döljer en rad med ett eget meddelande, och andra fel visas likadant`() {
        // Vilket formaterat meddelande som helst; appen har ännu ingen egen "dölj"-text.
        val hiding = actions(R.string.color_format)
        hiding.onEvent(ArchiveEvent.Archive("p", "Städning"))
        assertEquals(UndoRequest("p", "Städning", R.string.color_format), hiding.undo.value)
        hiding.report(DataError.Offline)
        assertEquals(DataError.Offline, hiding.failure.value?.error)
    }
}
