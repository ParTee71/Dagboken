package se.partee71.dagboken.core.legacy

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.junit.Test
import se.partee71.dagboken.core.engine.ensureDoses
import se.partee71.dagboken.core.engine.isPrescribed
import se.partee71.dagboken.core.engine.moveTarget
import se.partee71.dagboken.core.engine.prescriptionRef
import se.partee71.dagboken.core.engine.syncDoses
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus

/**
 * Konverterade 3.x-doser och receptkopplingen (MED-4, MED-15, REC-10, OMB-3): en dos med receptets id-form
 * `recept_{id}_{datum}_{tidpunkt}` räknas som receptets – med `receptId` (3.x-fixturerna) eller utan (äldre data,
 * t.ex. webbversionen). Utan kopplingen ska allt bete sig som med den: samma recept, ingen dubbelgenerering i
 * `ensureDoses`/`syncDoses`, och flytten ger tillbaka kopplingen – så att ingen dos tappas eller dubbleras.
 */
class MigratedDosesTest {
    private val zone = TimeZone.of("Europe/Stockholm")

    private fun converted(name: String) =
        assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(LegacyFixtures.backup(name), LegacyFixtures.UID)).data

    /** Fixturens doser utan `prescriptionId` på receptdoserna – som 3.x-data där bara id:t bär receptet. */
    private fun List<Dose>.unlinked() = map { if (it.id.startsWith("recept_")) it.copy(prescriptionId = null) else it }

    @Test
    fun `receptkopplingen ur id-formen är densamma som 3x receptId för varje dos i fixturerna`() {
        for (name in listOf("backup-v2", "backup-v1")) {
            val doses = converted(name).doses
            assertTrue(doses.any { it.prescriptionId != null } && doses.any { it.prescriptionId == null }, "$name har båda sorterna")
            for ((linked, bare) in doses.zip(doses.unlinked())) {
                assertEquals(linked.prescriptionId, linked.prescriptionRef, "$name ${linked.id}")
                assertEquals(linked.prescriptionId, bare.prescriptionRef, "$name ${linked.id} utan receptId")
                assertEquals(linked.prescriptionId != null, bare.isPrescribed)
            }
        }
    }

    @Test
    fun `dosgenereringen skapar aldrig en dos som redan finns – med eller utan receptId (MED-4)`() {
        for (name in listOf("backup-v2", "backup-v1")) {
            val data = converted(name)
            val prescriptions = data.prescriptions.map { it.copy(active = true) }
            for (date in data.doses.mapNotNull { it.date }.distinct()) {
                val linked = data.doses.filter { it.date == date }
                val bare = linked.unlinked()
                val created = ensureDoses(prescriptions, linked, date, today = date, zone = zone).create
                assertEquals(created, ensureDoses(prescriptions, bare, date, today = date, zone = zone).create, "$name $date")
                assertTrue(created.none { dose -> linked.any { it.id == dose.id } }, "$name $date: ingen dubblett")
                for (p in prescriptions) {
                    val withLink = p.syncDoses(linked, date, zone)
                    val withoutLink = p.syncDoses(bare, date, zone)
                    assertEquals(withLink.create, withoutLink.create, "$name $date ${p.id}")
                    assertEquals(withLink.update.map { it.id }, withoutLink.update.map { it.id })
                    assertEquals(withLink.delete.map { it.id }, withoutLink.delete.map { it.id })
                    assertTrue(withLink.create.none { dose -> linked.any { it.id == dose.id } }, "$name $date ${p.id}: ingen dubblett")
                }
            }
        }
    }

    @Test
    fun `en planerad dos utan receptId följer sitt recept i synken och får kopplingen och måldagens id när den flyttas (REC-10, MED-15, MED-4)`() {
        val data = converted("backup-v2")
        val linked = data.doses.first { it.prescriptionId != null }.copy(status = DoseStatus.PLANNED, takenAt = null)
        val bare = listOf(linked).unlinked().single()
        val owner = data.prescriptions.single { it.id == linked.prescriptionId }
        val date = checkNotNull(linked.date)
        // Avaktiverat recept: dagens planerade dos tas bort – också den som bara har receptet i id:t (REC-5).
        assertEquals(listOf(bare), owner.copy(active = false).syncDoses(listOf(bare), date, zone).delete)
        // Ett annat recept äger den aldrig, och den flyttade dosen bär kopplingen i fältet.
        data.prescriptions.filter { it.id != owner.id }.forEach { assertTrue(it.copy(active = false).syncDoses(listOf(bare), date, zone).isEmpty) }
        val moved = bare.moveTarget(bare.copy(date = date.plus(1, DateTimeUnit.DAY))) { "slumpat" }
        assertEquals(owner.id, moved.prescriptionId)
        // Måldagens datumbundna id: dosgenereringen ser den flyttade dosen och skapar ingen till.
        assertEquals(emptyList(), ensureDoses(listOf(owner.copy(active = true)), listOf(moved), checkNotNull(moved.date), checkNotNull(moved.date), zone).create.filter { it.slot == moved.slot })
    }
}
