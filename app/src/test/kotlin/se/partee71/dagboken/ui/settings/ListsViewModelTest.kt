package se.partee71.dagboken.ui.settings

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionIds
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.repository.DefaultOptionsRepository
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.ArchiveEvent
import se.partee71.dagboken.ui.common.EditorEffect
import se.partee71.dagboken.ui.common.ListUiState

/** Listor (SET-5, SET-6, SET-9, SET-11, DAT-9): val av lista, stjärna, arkivera/ångra, nytt och namnbyte. */
class ListsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val factory = FakeCollectionFactory()
    private val options = factory.options()
    private val repository = DefaultOptionsRepository(factory)

    private val promenad = Option("activity-promenad", OptionKind.ACTIVITY, "Promenad", sortOrder = 1)
    private val yoga = Option("activity-yoga", OptionKind.ACTIVITY, "Yoga", sortOrder = 2)
    private val huvudvark = Option("symptom-huvudvark", OptionKind.SYMPTOM, "Huvudvärk")

    private suspend fun seed() = options.batch(listOf(promenad, yoga, huvudvark)).getOrThrow()

    @Test
    fun `listan visar den valda sorten`() = runTest(main.dispatcher) {
        seed()
        val vm = ListsViewModel(repository)
        vm.state.test {
            assertEquals(ListUiState.Content(listOf(promenad, yoga)), expectMostRecentItem())
            vm.onEvent(ListsEvent.KindChosen(OptionKind.SYMPTOM))
            assertEquals(ListUiState.Content(listOf(huvudvark)), awaitItem())
            vm.onEvent(ListsEvent.KindChosen(OptionKind.EVENT))
            assertEquals(ListUiState.Empty, awaitItem())
        }
        assertEquals(OptionKind.EVENT, vm.kind.value)
    }

    @Test
    fun `stjärnan växlar favorit och svep arkiverar med Ångra (DAT-9)`() = runTest(main.dispatcher) {
        seed()
        val vm = ListsViewModel(repository)
        vm.state.test {
            expectMostRecentItem()
            vm.onEvent(ListsEvent.FavoriteToggled(yoga))
            assertEquals(listOf(promenad, yoga.copy(favorite = true)), (awaitItem() as ListUiState.Content).items)

            vm.archive.onEvent(ArchiveEvent.Archive(promenad.id, promenad.name))
            assertEquals(listOf(yoga.copy(favorite = true)), (awaitItem() as ListUiState.Content).items)
            assertEquals("Promenad", vm.archive.undo.value?.name)

            vm.archive.onEvent(ArchiveEvent.Undo)
            assertEquals(listOf(promenad, yoga.copy(favorite = true)), (awaitItem() as ListUiState.Content).items)
        }
        assertFalse(options.get(promenad.id).getOrThrow()!!.archived, "arkiveras, raderas aldrig")
    }

    @Test
    fun `ett nytt alternativ får id ur namnet, dubbletter går inte att spara`() = runTest(main.dispatcher) {
        seed()
        val vm = OptionEditViewModel(repository, OptionKind.ACTIVITY, id = null)
        assertTrue(vm.isNew)
        assertFalse(vm.editor.state.value.canSave)

        vm.onEvent(OptionEditEvent.NameChanged(" promenad "))
        assertEquals(R.string.option_name_duplicate, vm.editor.state.value.errorFor(OPTION_NAME))
        vm.onEvent(OptionEditEvent.NameChanged("  "))
        assertEquals(R.string.option_name_missing, vm.editor.state.value.errorFor(OPTION_NAME))

        vm.onEvent(OptionEditEvent.NameChanged("Cykling"))
        vm.editor.effects.test {
            vm.onEvent(OptionEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val id = OptionIds.of(OptionKind.ACTIVITY, "Cykling")
        assertEquals(Option(id, OptionKind.ACTIVITY, "Cykling", sortOrder = 3), options.get(id).getOrThrow())
    }

    @Test
    fun `Spara väntar på listan och dubbletten syns när listan kommer`() = runTest(main.dispatcher) {
        seed()
        val scope = TestUserScope(uid = null) // listan är inte läst än
        val vm = OptionEditViewModel(DefaultOptionsRepository(FakeCollectionFactory(store = factory.store, scope = scope)), OptionKind.ACTIVITY, id = null)
        vm.onEvent(OptionEditEvent.NameChanged("Promenad"))
        assertFalse(vm.editor.state.value.canSave, "inte innan dubbletter går att se")
        assertEquals(null, vm.editor.state.value.errorFor(OPTION_NAME))

        scope.uid.value = "uid-test"
        assertEquals(R.string.option_name_duplicate, vm.editor.state.value.errorFor(OPTION_NAME))
        vm.onEvent(OptionEditEvent.NameChanged("Cykling"))
        assertTrue(vm.editor.state.value.canSave)
    }

    @Test
    fun `namnbyte behåller id så att loggade poster visar det nya namnet (SET-11)`() = runTest(main.dispatcher) {
        seed()
        val vm = OptionEditViewModel(repository, OptionKind.ACTIVITY, promenad.id)
        assertEquals("Promenad", vm.editor.state.value.value)
        assertEquals(promenad, vm.stored.value)

        vm.onEvent(OptionEditEvent.NameChanged("Yoga"))
        assertEquals(R.string.option_name_duplicate, vm.editor.state.value.errorFor(OPTION_NAME))
        vm.onEvent(OptionEditEvent.NameChanged("PROMENAD"))
        assertEquals(null, vm.editor.state.value.errorFor(OPTION_NAME), "eget namn i annan stil är ingen dubblett")

        vm.onEvent(OptionEditEvent.NameChanged("Långpromenad"))
        vm.onEvent(OptionEditEvent.Save)

        assertEquals(promenad.copy(name = "Långpromenad"), options.get(promenad.id).getOrThrow())
        assertEquals(3, options.getAll().getOrThrow().size, "inget nytt alternativ")
    }

    @Test
    fun `ett alternativ arkiveras och återställs i formulärets meny`() = runTest(main.dispatcher) {
        seed()
        val vm = OptionEditViewModel(repository, OptionKind.ACTIVITY, yoga.id)
        vm.editor.effects.test {
            vm.onEvent(OptionEditEvent.Archive)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertTrue(options.get(yoga.id).getOrThrow()!!.archived)

        val again = OptionEditViewModel(repository, OptionKind.ACTIVITY, yoga.id)
        assertEquals(true, again.stored.value?.archived)
        again.onEvent(OptionEditEvent.Restore)
        assertFalse(options.get(yoga.id).getOrThrow()!!.archived)
    }

    @Test
    fun `Ångra som skulle ge en aktiv dubblett nekas och visas som Finns redan i listan`() = runTest(main.dispatcher) {
        seed()
        val vm = ListsViewModel(repository)
        vm.state.test {
            expectMostRecentItem()
            vm.archive.onEvent(ArchiveEvent.Archive(promenad.id, promenad.name))
            options.upsert(Option("annan-promenad", OptionKind.ACTIVITY, "promenad", sortOrder = 9)).getOrThrow()
            vm.archive.onEvent(ArchiveEvent.Undo)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(R.string.option_name_duplicate, vm.archive.failure.value?.message)
        assertTrue(options.get(promenad.id).getOrThrow()!!.archived, "inget skrivet")
        vm.archive.onEvent(ArchiveEvent.ErrorShown)
        assertEquals(null, vm.archive.failure.value)
    }

    @Test
    fun `Återställ som skulle ge en aktiv dubblett nekas och visas som Finns redan i listan`() = runTest(main.dispatcher) {
        options.batch(listOf(yoga.copy(archived = true), Option("ny-yoga", OptionKind.ACTIVITY, "Yoga"))).getOrThrow()
        val vm = OptionEditViewModel(repository, OptionKind.ACTIVITY, yoga.id)
        vm.editor.effects.test {
            vm.onEvent(OptionEditEvent.Restore)
            val failed = awaitItem() as EditorEffect.Failed
            assertEquals(R.string.option_name_duplicate, failed.message)
        }
        assertTrue(options.get(yoga.id).getOrThrow()!!.archived)
    }

    @Test
    fun `ett läsfel står kvar när listan kommer, och Spara skapar inget nytt`() = runTest(main.dispatcher) {
        seed()
        val unreadable = object : OptionsRepository by repository {
            override suspend fun get(id: String): Result<Option?> = Result.failure(DataError.Offline)
        }
        val vm = OptionEditViewModel(unreadable, OptionKind.ACTIVITY, promenad.id)
        assertEquals(DataError.Offline, vm.editor.state.value.loadError)

        options.upsert(Option("ny", OptionKind.ACTIVITY, "Cykling")).getOrThrow() // listan ändras
        vm.onEvent(OptionEditEvent.NameChanged("Långpromenad"))
        vm.onEvent(OptionEditEvent.Save)

        assertEquals(DataError.Offline, vm.editor.state.value.loadError)
        assertEquals(setOf(promenad.id, yoga.id, huvudvark.id, "ny"), options.getAll().getOrThrow().map { it.id }.toSet())
    }

    /** Listan som testet styr: ett värde eller ett fel; "Försök igen" lyssnar på nytt. */
    private class ControlledList(initial: Result<List<Option>>) {
        val result = MutableStateFlow(initial)
        val flow: Flow<List<Option>> = result.map { it.getOrThrow() }
    }

    private fun withList(list: ControlledList) = object : OptionsRepository by repository {
        override fun observe(kind: OptionKind): Flow<List<Option>> = list.flow
    }

    @Test
    fun `ett bestående fel i listan visas som läsfel, och Försök igen läser om`() = runTest(main.dispatcher) {
        val list = ControlledList(Result.failure(DataError.PermissionDenied))
        val vm = OptionEditViewModel(withList(list), OptionKind.ACTIVITY, id = null)
        assertEquals(DataError.PermissionDenied, vm.editor.state.value.loadError)

        list.result.value = Result.success(listOf(promenad))
        vm.onEvent(OptionEditEvent.Retry)
        assertEquals(null, vm.editor.state.value.loadError)
        assertFalse(vm.editor.state.value.loading)
        assertEquals(null, vm.editor.state.value.errorFor(OPTION_NAME), "ett tomt nytt formulär visar inget fel")
        vm.onEvent(OptionEditEvent.NameChanged("Promenad"))
        assertEquals(R.string.option_name_duplicate, vm.editor.state.value.errorFor(OPTION_NAME))
    }

    @Test
    fun `listfelet står kvar fast alternativet självt gick att läsa`() = runTest(main.dispatcher) {
        seed()
        val list = ControlledList(Result.failure(DataError.PermissionDenied))
        val vm = OptionEditViewModel(withList(list), OptionKind.ACTIVITY, promenad.id)
        assertEquals(DataError.PermissionDenied, vm.editor.state.value.loadError)
        assertEquals(promenad, vm.stored.value, "alternativet lästes")

        list.result.value = Result.success(listOf(promenad, yoga))
        vm.onEvent(OptionEditEvent.Retry)
        assertEquals(null, vm.editor.state.value.loadError)
        assertEquals("Promenad", vm.editor.state.value.value)
    }

    @Test
    fun `det inskrivna namnet står kvar när listan läses om efter ett fel`() = runTest(main.dispatcher) {
        val list = ControlledList(Result.success(listOf(promenad)))
        val vm = OptionEditViewModel(withList(list), OptionKind.ACTIVITY, id = null)
        vm.onEvent(OptionEditEvent.NameChanged("Cykling"))

        list.result.value = Result.failure(DataError.Offline)
        assertEquals(DataError.Offline, vm.editor.state.value.loadError)
        list.result.value = Result.success(listOf(promenad))
        vm.onEvent(OptionEditEvent.Retry)

        assertEquals(null, vm.editor.state.value.loadError)
        assertEquals("Cykling", vm.editor.state.value.value)
        assertTrue(vm.editor.state.value.canSave)
    }

    @Test
    fun `ett lagrat namn som visar sig vara en dubblett när listan kommer visar felet direkt`() = runTest(main.dispatcher) {
        options.batch(listOf(promenad, Option("annan", OptionKind.ACTIVITY, "promenad"))).getOrThrow()
        val list = ControlledList(Result.success(emptyList()))
        val vm = OptionEditViewModel(withList(list), OptionKind.ACTIVITY, promenad.id)
        assertEquals(null, vm.editor.state.value.errorFor(OPTION_NAME))
        list.result.value = Result.success(options.getAll().getOrThrow())
        assertEquals(R.string.option_name_duplicate, vm.editor.state.value.errorFor(OPTION_NAME))
    }
}
