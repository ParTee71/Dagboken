package se.partee71.dagboken.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.Handelse
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.SjukdomsIncheckning
import se.partee71.dagboken.domain.usecase.SymptomUtils

/**
 * HAN-9: byter man namn på ett alternativ ska redan loggade poster följa med. Tidigare
 * ändrades bara listan i inställningarna, så historiken låg kvar på det gamla namnet.
 */
@RunWith(AndroidJUnit4::class)
class RenameOptionMigrationTest {

    private lateinit var db: AppDatabase
    private lateinit var aktiviteter: AktiviteterRepository
    private lateinit var handelser: HandelserRepository
    private lateinit var sjukdomar: SjukdomarRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val notes = NoteRepository(db.noteDao())
        aktiviteter = AktiviteterRepository(db.aktivitetDao(), notes, context)
        handelser = HandelserRepository(db.handelseDao(), notes)
        sjukdomar = SjukdomarRepository(db.sjukdomsEpisodDao(), db.sjukdomsIncheckningDao(), notes)
    }

    @After fun tearDown() { db.close() }

    private fun aktivitet(id: String, namn: String, symptom: String = "") = Aktivitet(
        id = id, timestamp = "2026-01-15T09:00:00Z", datum = "2026-01-15", tid = "09:00",
        aktivitet = namn, energy = 5, stress = 2, somatiska = 0, symptom = symptom,
        aterhamtande = false, energitjuv = false, type = "aktivitet", spentTime = null,
    )

    @Test fun renamingAnAktivitetUpdatesLoggedEntries() = runTest {
        aktiviteter.save(aktivitet("a1", "Promenad"))
        aktiviteter.save(aktivitet("a2", "Jobb"))

        aktiviteter.renameAktivitet("Promenad", "Långpromenad")

        val all = aktiviteter.all.first().associateBy { it.id }
        assertEquals("Långpromenad", all.getValue("a1").aktivitet)
        assertEquals("Jobb", all.getValue("a2").aktivitet)
    }

    @Test fun renamingASymptomRewritesTheEncodedScoreString() = runTest {
        aktiviteter.save(aktivitet("a1", "Promenad", SymptomUtils.encode(mapOf("Huvudvärk" to 4, "Yrsel" to 2))))

        aktiviteter.renameSymptom("Huvudvärk", "Migrän")

        val scores = SymptomUtils.decode(aktiviteter.all.first().single().symptom)
        assertEquals(mapOf("Migrän" to 4, "Yrsel" to 2), scores)
    }

    /**
     * Namnet ligger som delsträng i en kodad lista, så en ren textersättning skulle
     * kunna träffa ett annat symptom som råkar innehålla samma text.
     */
    @Test fun renamingASymptomDoesNotTouchSimilarlyNamedSymptoms() = runTest {
        aktiviteter.save(
            aktivitet("a1", "Promenad", SymptomUtils.encode(mapOf("Värk" to 3, "Huvudvärk" to 5))),
        )

        aktiviteter.renameSymptom("Värk", "Kroppsvärk")

        val scores = SymptomUtils.decode(aktiviteter.all.first().single().symptom)
        assertEquals(mapOf("Kroppsvärk" to 3, "Huvudvärk" to 5), scores)
    }

    @Test fun renamingASymptomAlsoUpdatesSjukdomsIncheckningar() = runTest {
        sjukdomar.saveEpisod(
            SjukdomsEpisod(id = "e1", typ = "Förkylning", startDatum = "2026-01-10", slutDatum = "", timestamp = 1),
        )
        sjukdomar.saveIncheckning(
            SjukdomsIncheckning(
                id = "i1", episodId = "e1", datum = "2026-01-11", tid = "20:00",
                svarighetsgrad = 5, symptom = SymptomUtils.encode(mapOf("Hosta" to 3)),
                somatiska = 0, timestamp = 2,
            ),
        )

        sjukdomar.renameSymptom("Hosta", "Rethosta")

        val scores = SymptomUtils.decode(sjukdomar.allIncheckningar.first().single().symptom)
        assertEquals(mapOf("Rethosta" to 3), scores)
    }

    @Test fun renamingAHandelseTypUpdatesLoggedEvents() = runTest {
        handelser.save(
            Handelse(
                id = "h1", timestamp = "2026-01-15T13:00:00Z", datum = "2026-01-15", tid = "13:00",
                typ = "Yrsel", svarighetsgrad = 4, varaktighetMinuter = 10, triggers = "", atgarder = "",
            ),
        )

        handelser.renameTyp("Yrsel", "Ostadighet")

        assertEquals("Ostadighet", handelser.all.first().single().typ)
    }

    @Test fun renamingSomethingWithNoLoggedEntriesIsANoOp() = runTest {
        aktiviteter.save(aktivitet("a1", "Promenad"))

        aktiviteter.renameAktivitet("Finns inte", "Nytt namn")

        assertEquals("Promenad", aktiviteter.all.first().single().aktivitet)
    }
}
