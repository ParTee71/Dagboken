package se.partee71.dagboken.ui.common

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.R
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.testing.MainDispatcherRule

/** Formulär i ett ark (NFR-10): öppna, ändra, spara, fel och spärren under sparningen – en gång för alla ark. */
class EditorSheetTest {

    @get:Rule
    val main = MainDispatcherRule()

    private data class Form(val name: String, val count: Int = 0)

    private var saved = 0
    private val written = mutableListOf<Pair<Form?, Form>>()

    private fun CoroutineScope.sheet() = EditorSheet<Form, String>(this) { saved++ }

    private val EditorSheet<Form, String>.shown get() = checkNotNull(current.value)

    /** Sparat: arket ska döljas, och när skärmen dolt det stängs det. */
    private fun EditorSheet<Form, String>.assertClosing() {
        assertTrue(shown.closing.value, "sparat – arket döljs")
        close()
        assertNull(current.value)
    }

    @Test
    fun `ett nytt objekt får sparas oförändrat, ett sparat först när det ändrats – sammanhanget följer arket`() = runTest(main.dispatcher) {
        val sheet = backgroundScope.sheet()
        sheet.open(null, Form("ny"), "Lunch")
        assertTrue(sheet.shown.editor.state.value.canSave)
        assertFalse(sheet.shown.unsaved.value, "ingen fråga om att slänga")
        assertEquals("Lunch", sheet.shown.context)

        sheet.open(Form("lagrad"), Form("lagrad"), "Kvällsmat")
        val open = sheet.shown
        assertFalse(open.editor.state.value.canSave)
        sheet.save { loaded, value -> written += loaded to value; Result.success(Unit) }
        runCurrent()
        assertTrue(written.isEmpty(), "Spara är inte aktiv")
        sheet.update { it.copy(count = 2) }
        sheet.update { it.copy(count = it.count + 1) }
        assertEquals(Form("lagrad", 3), open.editor.value, "varje ändring på det aktuella värdet")
        assertTrue(open.editor.state.value.canSave)
    }

    @Test
    fun `sparat döljer och stänger arket och anropar onSaved, fel visas i arket som står kvar`() = runTest(main.dispatcher) {
        val sheet = backgroundScope.sheet()
        sheet.open(null, Form("ny"), "")
        val open = sheet.shown
        sheet.save { _, _ -> Result.failure(DataError.Offline) }
        runCurrent()
        assertSame(open, sheet.current.value)
        assertEquals(DataError.Offline, open.error.value?.error)
        assertEquals(Failure(DataError.Offline).message, open.error.value?.message, "samma text som EntityEditScreen")
        assertEquals(0, saved)
        sheet.update { it.copy(count = 1) }
        assertNull(open.error.value, "en ändring tar bort felet")

        sheet.save { loaded, value -> written += loaded to value; Result.success(Unit) }
        runCurrent()
        sheet.assertClosing()
        assertEquals(1, saved)
        assertEquals(listOf<Pair<Form?, Form>>(null to Form("ny", 1)), written)
    }

    @Test
    fun `en ny post med sparfel förblir osparad tills den sparats eller slängts – också ändrad fram och tillbaka`() = runTest(main.dispatcher) {
        val sheet = backgroundScope.sheet()
        sheet.open(null, Form("ny"), "")
        val new = sheet.shown
        runCurrent()
        assertFalse(new.unsaved.value)
        sheet.save { _, _ -> Result.failure(DataError.Offline) }
        runCurrent()
        runCurrent()
        assertTrue(new.unsaved.value, "det som skulle sparas finns ingen annanstans")
        sheet.update { it.copy(count = 1) }
        sheet.update { it.copy(count = 0) }
        assertNull(new.error.value)
        assertFalse(new.editor.state.value.isDirty)
        runCurrent()
        assertTrue(new.unsaved.value, "klistrig: frågar fortfarande vid stängning")

        sheet.open(Form("lagrad"), Form("lagrad"), "")
        val stored = sheet.shown
        sheet.update { it.copy(count = 1) }
        sheet.save { _, _ -> Result.failure(DataError.Offline) }
        runCurrent()
        sheet.update { it.copy(count = 0) }
        runCurrent()
        assertFalse(stored.unsaved.value, "oförändrad sparad post – inget att slänga")
    }

    @Test
    fun `Spara markerar formuläret som sparande direkt – inte stängbart, ändringsbart eller sparbart igen förrän klart`() = runTest(main.dispatcher) {
        val sheet = backgroundScope.sheet()
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        sheet.open(null, Form("ny"), "")
        val open = sheet.shown
        sheet.save { _, _ -> writes++; gate.await(); Result.success(Unit) }
        // Utan runCurrent: läget är satt innan save() returnerar.
        assertTrue(open.editor.state.value.saving)
        assertFalse(open.canDismiss())

        sheet.close()
        sheet.update { it.copy(count = 9) }
        sheet.save { _, _ -> writes++; Result.success(Unit) }
        runCurrent()
        assertSame(open, sheet.current.value, "stängs inte under sparningen")
        assertEquals(Form("ny"), open.editor.value)
        assertEquals(1, writes)

        gate.complete(Unit)
        runCurrent()
        sheet.assertClosing()
        assertEquals(1, saved)
    }

    @Test
    fun `ett nytt ark ersätter det som visas`() = runTest(main.dispatcher) {
        val sheet = backgroundScope.sheet()
        sheet.open(null, Form("första"), "")
        sheet.update { it.copy(count = 1) }
        sheet.open(null, Form("andra"), "")
        assertEquals(Form("andra"), sheet.shown.editor.value)
        sheet.close()
        assertNull(sheet.current.value)
    }

    @Test
    fun `en ändring markerar fältet som rört, så att dess fel syns`() = runTest(main.dispatcher) {
        val sheet = backgroundScope.sheet()
        val validator = Validator<Form> { if (it.name.isBlank()) mapOf("name" to R.string.error_unknown) else emptyMap() }
        sheet.open(Form("lagrad"), Form("lagrad"), "", validator)
        val editor = sheet.shown.editor
        sheet.update("count") { it.copy(count = 1) }
        assertNull(editor.state.value.errorFor("name"))
        sheet.update("name") { it.copy(name = "") }
        assertEquals(R.string.error_unknown, editor.state.value.errorFor("name"))
    }
}
