package se.partee71.dagboken.data.firestore

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.common.EntityCollection

/**
 * Varje samling i `CollectionTable` skriver till sin sökväg i `Paths` med sin codec – samma tabell
 * som `FirestoreCollectionFactory` använder (skill firestore-data-layer).
 */
class CollectionTableTest {

    private val factory = FakeCollectionFactory()
    private val uid = factory.scope.uid.value!!

    private suspend fun <T : Identified> assertStoredAt(collection: EntityCollection<T>, item: T, path: String, field: String) {
        collection.upsert(item).getOrThrow()
        val raw = assertNotNull(factory.store.read(path, item.id), "${item.id} ska ligga i $path")
        assertNotNull(raw[field], "$path/${item.id} ska ha fältet $field")
        assertEquals(item, collection.get(item.id).getOrThrow())
    }

    @Test
    fun `varje samling har sin sökväg och codec`() = runTest {
        assertStoredAt(factory.settings(), Settings(), Paths.settings(uid), "reminders")
        assertStoredAt(factory.options(), Option("o1", name = "Promenad"), Paths.options(uid), "kind")
        assertStoredAt(factory.prescriptions(), Prescription("r1", name = "Levaxin"), Paths.prescriptions(uid), "period")
        assertStoredAt(factory.prnMedicines(), PrnMedicine("p1", name = "Alvedon"), Paths.prnMedicines(uid), "slot")
        val doseId = DoseIds.prescribed("r1", LocalDate(2026, 9, 21), Slot.MIDMORNING)
        assertStoredAt(factory.doses(), Dose(doseId, status = DoseStatus.TAKEN), Paths.doses(uid), "status")
        assertStoredAt(factory.screenings(), Screening("s1", energy = 6), Paths.screenings(uid), "symptoms")
        assertStoredAt(factory.activities(), Activity("a1", energy = -2), Paths.activities(uid), "energy")
        assertStoredAt(factory.events(), Event("e1", severity = 5), Paths.events(uid), "severity")
        assertStoredAt(factory.illnessEpisodes(), IllnessEpisode("ep1", type = "Förkylning"), Paths.illnessEpisodes(uid), "type")
        assertStoredAt(factory.checkins("ep1"), Checkin("c1", severity = 4), Paths.checkins(uid, "ep1"), "severity")
    }

    @Test
    fun `incheckningar hålls isär per episod`() = runTest {
        factory.checkins("ep1").upsert(Checkin("c1")).getOrThrow()
        assertEquals(null, factory.checkins("ep2").get("c1").getOrThrow())
    }
}
