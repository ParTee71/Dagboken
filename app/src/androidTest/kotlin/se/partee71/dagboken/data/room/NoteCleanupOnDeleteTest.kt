package se.partee71.dagboken.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Handelse
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.SjukdomsIncheckning
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase

/**
 * Anteckningarna ligger i en generisk `notes`-tabell utan främmande nyckel till sina
 * ägare, så varje repository måste städa sin egen anteckning vid radering (DAT-4).
 * Städningen låg tidigare i ViewModel-lagret, vilket gjorde att raderingar därifrån
 * (t.ex. Historik) lämnade föräldralösa rader kvar som följde med i varje backup.
 */
@RunWith(AndroidJUnit4::class)
class NoteCleanupOnDeleteTest {

    private lateinit var db: AppDatabase
    private lateinit var notes: NoteRepository
    private lateinit var aktiviteter: AktiviteterRepository
    private lateinit var handelser: HandelserRepository
    private lateinit var mediciner: MedicinerRepository
    private lateinit var sjukdomar: SjukdomarRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        notes = NoteRepository(db.noteDao())
        aktiviteter = AktiviteterRepository(db.aktivitetDao(), notes)
        handelser = HandelserRepository(db.handelseDao(), notes)
        sjukdomar = SjukdomarRepository(db.sjukdomsEpisodDao(), db.sjukdomsIncheckningDao(), notes)
        mediciner = MedicinerRepository(
            db                 = db,
            medicinDao         = db.medicinDao(),
            receptDao          = db.receptDao(),
            favoritDao         = db.favoritDao(),
            noteRepo           = notes,
            ensureTodayEntries = EnsureTodayEntriesUseCase(),
            json               = Json { ignoreUnknownKeys = true },
        )
    }

    @After fun tearDown() { db.close() }

    private suspend fun noteFor(target: NoteTarget, id: String): String =
        notes.observe(target, id).first()

    @Test fun deletingAnAktivitetRemovesItsNote() = runTest {
        val aktivitet = Aktivitet(
            id = "a1", timestamp = "2026-01-15T09:00:00Z", datum = "2026-01-15", tid = "09:00",
            aktivitet = "Promenad", energy = 5, stress = 2, somatiska = 0, symptom = "",
            aterhamtande = false, energitjuv = false, type = "aktivitet", spentTime = null,
        )
        aktiviteter.save(aktivitet)
        notes.save(NoteTarget.ACTIVITY, aktivitet.id, "Fint väder")

        aktiviteter.delete(aktivitet)

        assertEquals("", noteFor(NoteTarget.ACTIVITY, aktivitet.id))
    }

    @Test fun deletingAScreeningRemovesItsScreeningNote() = runTest {
        val screening = Aktivitet(
            id = "s1", timestamp = "2026-01-15T12:00:00Z", datum = "2026-01-15", tid = "12:00",
            aktivitet = "Lunch", energy = 6, stress = 3, somatiska = 1, symptom = "",
            aterhamtande = false, energitjuv = false, type = "screening", spentTime = null,
        )
        aktiviteter.save(screening)
        notes.save(NoteTarget.SCREENING, screening.id, "Kändes bra")

        aktiviteter.delete(screening)

        assertEquals("", noteFor(NoteTarget.SCREENING, screening.id))
    }

    @Test fun deletingAHandelseRemovesItsNote() = runTest {
        val handelse = Handelse(
            id = "h1", timestamp = "2026-01-15T13:00:00Z", datum = "2026-01-15", tid = "13:00",
            typ = "Yrsel", svarighetsgrad = 4, varaktighetMinuter = 10,
            triggers = "", atgarder = "",
        )
        handelser.save(handelse)
        notes.save(NoteTarget.EVENT, handelse.id, "Gick över snabbt")

        handelser.delete(handelse)

        assertEquals("", noteFor(NoteTarget.EVENT, handelse.id))
    }

    @Test fun deletingAMedicinRemovesItsNote() = runTest {
        val medicin = Medicin(
            id = "m1", timestamp = "2026-01-15T07:00:00Z", datum = "2026-01-15", tid = "07:00",
            namn = "Metformin", dos = "500", enhet = "mg", tidpunkt = "Morgon", tagen = true,
        )
        mediciner.saveMedicin(medicin)
        notes.save(NoteTarget.MEDICATION, medicin.id, "Med mat")

        mediciner.deleteMedicin(medicin)

        assertEquals("", noteFor(NoteTarget.MEDICATION, medicin.id))
    }

    @Test fun deletingAFavoritRemovesItsNote() = runTest {
        val favorit = Favorit(
            id = "f1", namn = "Ibuprofen", dos = "400", enhet = "mg", tidpunkt = "Vid behov",
            minTidMellan = 6,
        )
        mediciner.saveFavorit(favorit)
        notes.save(NoteTarget.FAVORIT, favorit.id, "Max 3/dag")

        mediciner.deleteFavorit(favorit)

        assertEquals("", noteFor(NoteTarget.FAVORIT, favorit.id))
    }

    /** Incheckningarna kaskadraderas i databasen — deras anteckningar måste följa med. */
    @Test fun deletingAnEpisodRemovesNotesForItsIncheckningarToo() = runTest {
        val episod = SjukdomsEpisod(
            id = "e1", typ = "Förkylning", startDatum = "2026-01-10", slutDatum = "",
            timestamp = 1,
        )
        val incheckning = SjukdomsIncheckning(
            id = "i1", episodId = "e1", datum = "2026-01-11", tid = "20:00",
            svarighetsgrad = 5, symptom = "", somatiska = 0, timestamp = 2,
        )
        sjukdomar.saveEpisod(episod)
        sjukdomar.saveIncheckning(incheckning)
        notes.save(NoteTarget.SJUKDOM_EPISOD, episod.id, "Feber första dagen")
        notes.save(NoteTarget.SJUKDOM_INCHECKNING, incheckning.id, "Sov dåligt")

        sjukdomar.deleteEpisod(episod)

        assertEquals("", noteFor(NoteTarget.SJUKDOM_EPISOD, episod.id))
        assertEquals("", noteFor(NoteTarget.SJUKDOM_INCHECKNING, incheckning.id))
    }

    @Test fun deletingAnIncheckningRemovesItsNote() = runTest {
        val episod = SjukdomsEpisod(
            id = "e2", typ = "Influensa", startDatum = "2026-02-01", slutDatum = "", timestamp = 1,
        )
        val incheckning = SjukdomsIncheckning(
            id = "i2", episodId = "e2", datum = "2026-02-02", tid = "08:00",
            svarighetsgrad = 7, symptom = "", somatiska = 0, timestamp = 2,
        )
        sjukdomar.saveEpisod(episod)
        sjukdomar.saveIncheckning(incheckning)
        notes.save(NoteTarget.SJUKDOM_INCHECKNING, incheckning.id, "Hög feber")

        sjukdomar.deleteIncheckning(incheckning)

        assertEquals("", noteFor(NoteTarget.SJUKDOM_INCHECKNING, incheckning.id))
        // Episodens egen anteckning ska inte röras.
        assertEquals("", noteFor(NoteTarget.SJUKDOM_EPISOD, episod.id))
    }

    @Test fun deletingOneNoteLeavesOtherEntriesUntouched() = runTest {
        val kvar = Aktivitet(
            id = "a-kvar", timestamp = "2026-01-15T10:00:00Z", datum = "2026-01-15", tid = "10:00",
            aktivitet = "Jobb", energy = 4, stress = 5, somatiska = 0, symptom = "",
            aterhamtande = false, energitjuv = true, type = "aktivitet", spentTime = null,
        )
        val bort = kvar.copy(id = "a-bort", aktivitet = "Möte")
        aktiviteter.save(kvar)
        aktiviteter.save(bort)
        notes.save(NoteTarget.ACTIVITY, kvar.id, "Behålls")
        notes.save(NoteTarget.ACTIVITY, bort.id, "Tas bort")

        aktiviteter.delete(bort)

        assertEquals("Behålls", noteFor(NoteTarget.ACTIVITY, kvar.id))
        assertEquals("", noteFor(NoteTarget.ACTIVITY, bort.id))
    }
}
