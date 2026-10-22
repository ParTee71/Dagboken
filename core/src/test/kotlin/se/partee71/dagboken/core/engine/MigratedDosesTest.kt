package se.partee71.dagboken.core.engine

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.junit.Test
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.DoseCodec
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.PrescriptionCodec

/**
 * Receptdoser från 3.x i kontot (MED-4, MED-15, REC-10): 3.x-konverteraren (borttagen i 4.9.0) gav dem 3.x-id:t
 * `recept_{id}_{datum}_{tidpunkt}`, och en dos med den id-formen räknas som receptets – med `prescriptionId` eller utan
 * (äldre data, t.ex. webbversionen). Utan kopplingen ska allt bete sig som med den: samma recept, ingen dubbelgenerering i
 * `ensureDoses`/`syncDoses`, och flytten ger tillbaka kopplingen – så att ingen lagrad dos tappas eller dubbleras.
 * Testdata = `tools/db/test/fixtures/user.json` (syntetisk).
 */
class MigratedDosesTest {
    private val zone = TimeZone.of("Europe/Stockholm")

    private class Stored(val doses: List<Dose>, val prescriptions: List<Prescription>)

    /** Fixturens doser och recept genom sina codecs, som de ligger i kontot. */
    private val stored: Stored = ExportFormat.decode(File("../tools/db/test/fixtures/user.json").readText()).let { documents ->
        fun <T> of(collection: String, decode: (String, Map<String, Any?>) -> T) = documents.filter { it.path.split('/').size == 4 && CollectionNames.collectionOf(it.path) == collection }
            .map { decode(it.path.substringAfterLast('/'), it.data) }
        Stored(of(CollectionNames.DOSES, DoseCodec::decode), of(CollectionNames.PRESCRIPTIONS, PrescriptionCodec::decode))
    }

    /** Doserna utan `prescriptionId` på receptdoserna – som 3.x-data där bara id:t bär receptet. */
    private fun List<Dose>.unlinked() = map { if (it.id.startsWith("recept_")) it.copy(prescriptionId = null) else it }

    @Test
    fun `receptkopplingen ur id-formen är densamma som prescriptionId för varje dos i fixturen`() {
        val doses = stored.doses
        assertTrue(doses.any { it.prescriptionId != null && it.id.startsWith("recept_") } && doses.any { it.prescriptionId == null }, "fixturen har båda sorterna")
        for ((linked, bare) in doses.zip(doses.unlinked())) {
            assertEquals(linked.prescriptionId, linked.prescriptionRef, linked.id)
            assertEquals(linked.prescriptionId, bare.prescriptionRef, "${linked.id} utan prescriptionId")
            assertEquals(linked.prescriptionId != null, bare.isPrescribed)
        }
    }

    @Test
    fun `dosgenereringen skapar aldrig en dos som redan finns – med eller utan receptId (MED-4)`() {
        val prescriptions = stored.prescriptions.map { it.copy(active = true) }
        for (date in stored.doses.mapNotNull { it.date }.distinct()) {
            val linked = stored.doses.filter { it.date == date }
            val bare = linked.unlinked()
            val created = ensureDoses(prescriptions, linked, date, today = date, zone = zone, reminders = ReminderSettings()).create
            assertEquals(created, ensureDoses(prescriptions, bare, date, today = date, zone = zone, reminders = ReminderSettings()).create, "$date")
            assertTrue(created.none { dose -> linked.any { it.id == dose.id } }, "$date: ingen dubblett")
            for (p in prescriptions) {
                val withLink = p.syncDoses(linked, date, zone, reminders = ReminderSettings())
                val withoutLink = p.syncDoses(bare, date, zone, reminders = ReminderSettings())
                assertEquals(withLink.create, withoutLink.create, "$date ${p.id}")
                assertEquals(withLink.update.map { it.id }, withoutLink.update.map { it.id })
                assertEquals(withLink.delete.map { it.id }, withoutLink.delete.map { it.id })
                assertTrue(withLink.create.none { dose -> linked.any { it.id == dose.id } }, "$date ${p.id}: ingen dubblett")
            }
        }
    }

    @Test
    fun `en planerad dos utan receptId följer sitt recept i synken och får kopplingen och måldagens id när den flyttas (REC-10, MED-15, MED-4)`() {
        val data = stored
        val linked = data.doses.first { it.prescriptionId != null }.copy(status = DoseStatus.PLANNED, takenAt = null)
        val bare = listOf(linked).unlinked().single()
        val owner = data.prescriptions.single { it.id == linked.prescriptionId }
        val date = checkNotNull(linked.date)
        // Avaktiverat recept: dagens planerade dos tas bort – också den som bara har receptet i id:t (REC-5).
        assertEquals(listOf(bare), owner.copy(active = false).syncDoses(listOf(bare), date, zone, reminders = ReminderSettings()).delete)
        // Ett annat recept äger den aldrig, och den flyttade dosen bär kopplingen i fältet.
        data.prescriptions.filter { it.id != owner.id }.forEach { assertTrue(it.copy(active = false).syncDoses(listOf(bare), date, zone, reminders = ReminderSettings()).isEmpty) }
        val moved = bare.moveTarget(bare.copy(date = date.plus(1, DateTimeUnit.DAY))) { "slumpat" }
        assertEquals(owner.id, moved.prescriptionId)
        // Måldagens datumbundna id: dosgenereringen ser den flyttade dosen och skapar ingen till.
        assertEquals(emptyList(), ensureDoses(listOf(owner.copy(active = true)), listOf(moved), checkNotNull(moved.date), checkNotNull(moved.date), zone, reminders = ReminderSettings()).create.filter { it.slot == moved.slot })
    }
}
