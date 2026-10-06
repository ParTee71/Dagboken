package se.partee71.dagboken.ui.common

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.dagboken.R
import se.partee71.dagboken.data.common.DataError

/** Formulärlogiken bakom `EntityEditScreen` (NFR-2) – testas en gång här, inte per skärm. */
class EditorStateTest {

    private data class Form(val name: String = "", val note: String = "")

    private val validator = Validator<Form> { form -> if (form.name.isBlank()) mapOf(NAME to R.string.error_unknown) else emptyMap() }

    private fun editor(initial: Form = Form()) = EditorState(initial, validator)

    @Test
    fun `ett nytt formulär är oändrat och kan inte sparas, och felet syns inte än`() {
        val state = editor().state.value
        assertFalse(state.isDirty)
        assertFalse(state.isValid)
        assertFalse(state.canSave)
        assertNull(state.errorFor(NAME))
    }

    @Test
    fun `ett fältfel syns först när fältet har ändrats`() {
        val editor = editor()
        editor.update(NOTE) { it.copy(note = "Faktor 50") }
        assertNull(editor.state.value.errorFor(NAME))
        editor.update(NAME) { it.copy(name = "") }
        assertEquals(R.string.error_unknown, editor.state.value.errorFor(NAME))
    }

    @Test
    fun `spara aktiveras när formuläret är giltigt och ändrat`() {
        val editor = editor()
        editor.update(NAME) { it.copy(name = "Solkräm") }
        assertTrue(editor.state.value.canSave)
        editor.update(NAME) { it.copy(name = "") }
        assertFalse(editor.state.value.canSave)
    }

    @Test
    fun `att ändra tillbaka till det sparade gör formuläret oändrat`() {
        val editor = editor(Form("Solkräm"))
        editor.update(NAME) { it.copy(name = "Solkräm 50") }
        editor.update(NAME) { it.copy(name = "Solkräm") }
        assertFalse(editor.state.value.isDirty)
    }

    @Test
    fun `ogiltigt sparförsök visar alla fel och skriver inget`() = runTest {
        val editor = editor()
        var writes = 0
        editor.save { writes++; Result.success(Unit) }
        assertEquals(0, writes)
        assertEquals(R.string.error_unknown, editor.state.value.errorFor(NAME))
    }

    @Test
    fun `lyckad sparning ger Done och ett oändrat formulär`() = runTest {
        val editor = editor()
        editor.update(NAME) { it.copy(name = "Solkräm") }
        editor.effects.test {
            var saved: Form? = null
            editor.save { saved = it; Result.success(Unit) }
            assertEquals(EditorEffect.Done, awaitItem())
            assertEquals(Form("Solkräm"), saved)
        }
        assertFalse(editor.state.value.isDirty)
        assertFalse(editor.state.value.saving)
    }

    @Test
    fun `sparfel ger Failed och formuläret står kvar ändrat`() = runTest {
        val editor = editor()
        editor.update(NAME) { it.copy(name = "Solkräm") }
        editor.effects.test {
            editor.save { Result.failure(DataError.Offline) }
            assertEquals(EditorEffect.Failed(DataError.Offline), awaitItem())
        }
        assertTrue(editor.state.value.isDirty)
        assertTrue(editor.state.value.canSave)
    }

    @Test
    fun `load sätter det lagrade värdet och avslutar laddningen`() {
        val editor = EditorState(Form(), validator, loading = true)
        assertTrue(editor.state.value.loading)
        assertFalse(editor.state.value.canSave)
        editor.update(NAME) { it.copy(name = "") }
        editor.load(Form("Plåster"))
        val state = editor.state.value
        assertFalse(state.loading)
        assertFalse(state.isDirty)
        assertEquals(Form("Plåster"), state.value)
        assertNull(state.errorFor(NAME))
    }

    @Test
    fun `loadFrom laddar det lagrade, och ett saknat eller oläsbart värde blir läsfel`() = runTest {
        val editor = EditorState(Form(), validator, loading = true)
        editor.loadFrom({ Result.success(Form("Solkräm")) }) { it.copy(note = "förval") }
        assertEquals(Form("Solkräm", "förval"), editor.state.value.value)
        assertFalse(editor.state.value.loading)

        val missing = EditorState(Form(), validator, loading = true)
        missing.loadFrom({ Result.success(null) })
        assertEquals(DataError.NotFound, missing.state.value.loadError)

        val offline = EditorState(Form(), validator, loading = true)
        offline.loadFrom({ Result.failure(DataError.Offline) })
        assertEquals(DataError.Offline, offline.state.value.loadError)
    }

    @Test
    fun `run för arkivera och radera ger Done eller Failed`() = runTest {
        val editor = editor(Form("Solkräm"))
        editor.effects.test {
            editor.run { Result.success(Unit) }
            assertEquals(EditorEffect.Done, awaitItem())
            editor.run { Result.failure(DataError.PermissionDenied) }
            assertEquals(EditorEffect.Failed(DataError.PermissionDenied), awaitItem())
        }
    }

    @Test
    fun `ett andra sparförsök medan det första pågår skriver inte två gånger`() = runTest {
        val editor = editor()
        editor.update(NAME) { it.copy(name = "Solkräm") }
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val first = launch { editor.save { writes++; gate.await(); Result.success(Unit) } }
        testScheduler.runCurrent()
        assertTrue(editor.state.value.saving)
        editor.save { writes++; Result.success(Unit) }
        gate.complete(Unit)
        first.join()
        assertEquals(1, writes)
    }

    @Test
    fun `spara medan det lagrade värdet laddas eller inte gick att läsa skriver inget`() = runTest {
        val editor = EditorState(Form(), validator, loading = true)
        editor.update(NAME) { it.copy(name = "Platshållare") }
        var writes = 0
        editor.save { writes++; Result.success(Unit) }
        assertTrue(editor.state.value.loading, "laddningen avbryts inte av ett sparförsök")
        editor.loadFailed(DataError.Offline)
        editor.save { writes++; Result.success(Unit) }
        assertEquals(DataError.Offline, editor.state.value.loadError)
        assertEquals(0, writes)
    }

    @Test
    fun `text som skrivs medan sparningen pågår är osparad och stänger inte formuläret (NFR-2)`() = runTest {
        val editor = editor()
        editor.update(NAME) { it.copy(name = "Solkräm") }
        val gate = CompletableDeferred<Unit>()
        editor.effects.test {
            val saving = launch { editor.save { gate.await(); Result.success(Unit) } }
            testScheduler.runCurrent()
            editor.update(NOTE) { it.copy(note = "SPF 50") }
            gate.complete(Unit)
            saving.join()

            assertTrue(editor.state.value.isDirty, "anteckningen är inte sparad")
            assertTrue(editor.state.value.canSave)
            expectNoEvents()
        }
    }

    @Test
    fun `arkivera eller radera stänger alltid, även om något ändrats under tiden`() = runTest {
        val editor = editor(Form("Solkräm"))
        val gate = CompletableDeferred<Unit>()
        editor.effects.test {
            val deleting = launch { editor.run { gate.await(); Result.success(Unit) } }
            testScheduler.runCurrent()
            editor.update(NOTE) { it.copy(note = "skrivet under raderingen") }
            gate.complete(Unit)
            deleting.join()
            assertEquals(EditorEffect.Done, awaitItem())
        }
    }

    @Test
    fun `ett läsfel avslutar laddningen och Försök igen laddar om`() {
        val editor = EditorState(Form(), validator, loading = true)
        editor.loadFailed(DataError.Offline)
        assertFalse(editor.state.value.loading)
        assertEquals(DataError.Offline, editor.state.value.loadError)
        assertFalse(editor.state.value.canSave)
        editor.reload()
        assertTrue(editor.state.value.loading)
        assertNull(editor.state.value.loadError)
        editor.load(Form("Plåster"))
        assertNull(editor.state.value.loadError)
    }

    private companion object {
        const val NAME = "name"
        const val NOTE = "note"
    }

    @Test
    fun `Försök igen för något nytt läser inget och laddar inte för evigt`() = runTest {
        val editor = editor()
        val loader = EditorLoader(editor, backgroundScope, read = null)
        loader.retry()
        assertFalse(editor.state.value.loading)
        assertNull(loader.stored.value)
    }

    @Test
    fun `omvalidering och ändringar tar inte bort ett läsfel`() = runTest {
        val editor = EditorState(Form(), validator, loading = true)
        editor.loadFailed(DataError.Offline)

        editor.revalidate()
        assertEquals(DataError.Offline, editor.state.value.loadError)
        editor.update("name") { it.copy(name = "Promenad") }
        assertEquals(DataError.Offline, editor.state.value.loadError)

        var writes = 0
        editor.save { writes++; Result.success(Unit) }
        assertEquals(0, writes, "inget lagrat värde att spara över")
    }

    @Test
    fun `EditorLoader utan projektion lägger det lagrade i formuläret och i stored`() = runTest {
        val editor = EditorState(Form(), validator, loading = true)
        val loader = EditorLoader(editor, backgroundScope, read = { Result.success(Form("Promenad")) })
        testScheduler.runCurrent()
        assertEquals(Form("Promenad"), editor.state.value.value)
        assertEquals(Form("Promenad"), loader.stored.value)
    }

    @Test
    fun `EditorLoader gör ändringen efter varje lyckad läsning, också efter Försök igen, och inte efter ett läsfel`() = runTest {
        val editor = EditorState(Form(), validator, loading = true)
        var result: Result<Form?> = Result.failure(DataError.Offline)
        val loader = EditorLoader(editor, backgroundScope, read = { result }, afterLoad = { editor.update { it.copy(note = "ändrad") } })
        testScheduler.runCurrent()
        assertEquals(DataError.Offline, editor.state.value.loadError)
        assertEquals(Form(), editor.state.value.value)

        result = Result.success(Form("Promenad"))
        loader.retry()
        testScheduler.runCurrent()
        assertEquals(Form("Promenad", note = "ändrad"), editor.state.value.value)
        assertTrue(editor.state.value.isDirty, "ändringen kan sparas")
    }

    @Test
    fun `Försök igen avbryter en pågående läsning – den sena laddas aldrig över den nya och efterarbetet körs en gång`() = runTest {
        val editor = EditorState(Form(), validator, loading = true)
        val slow = CompletableDeferred<Result<Form?>>()
        var reads = 0
        var afterLoads = 0
        val loader = EditorLoader(
            editor,
            backgroundScope,
            read = { if (++reads == 1) slow.await() else Result.success(Form("Ny")) },
            afterLoad = { afterLoads++ },
        )
        testScheduler.runCurrent()
        loader.retry()
        testScheduler.runCurrent()
        assertEquals(Form("Ny"), editor.state.value.value)

        editor.update { it.copy(note = "användarens") }
        slow.complete(Result.success(Form("Gammal")))
        testScheduler.runCurrent()
        assertEquals(Form("Ny", note = "användarens"), editor.state.value.value, "den första läsningen avbröts")
        assertEquals(1, afterLoads)
    }

    @Test
    fun `ett ogiltigt lagrat värde visar sitt fel direkt bara när formuläret ber om det`() {
        val editor = editor()
        editor.load(Form())
        assertTrue(editor.state.value.errors.isEmpty(), "standard: som förut")
        editor.load(Form(), showInvalid = true)
        assertTrue(editor.state.value.errors.isNotEmpty())
    }

    @Test
    fun `ett fel som uppstår först vid omvalidering syns när det lagrade ska visa sina fel`() {
        var taken = emptySet<String>()
        val editor = EditorState(Form(), Validator<Form> { if (it.name in taken) mapOf(NAME to R.string.error_unknown) else emptyMap() })
        editor.load(Form("Promenad"), showInvalid = true)
        assertTrue(editor.state.value.errors.isEmpty())
        taken = setOf("Promenad")
        editor.revalidate()
        assertEquals(R.string.error_unknown, editor.state.value.errorFor(NAME))
    }
}
