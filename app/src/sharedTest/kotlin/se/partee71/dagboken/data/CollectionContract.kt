package se.partee71.dagboken.data

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.common.dataError
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.testing.StuckTestTimeout

/**
 * Kontraktet för `EntityCollection` (skill firestore-data-layer). Körs mot `FakeCollection` i
 * JVM i varje PR och mot riktig `FirestoreCollection` + Firebase-emulatorn som instrumenttest –
 * så att fake och verklighet bevisligen beter sig lika. Körs mot provmodellen [ContractItem]
 * tills appens egna samlingar finns (etapp 2).
 */
abstract class CollectionContract {

    /** En ny, tom och skrivbar användare per test, med rå åtkomst förbi codecen. */
    interface Environment {
        val uid: String

        /** Tiden samlingarnas klocka anger. */
        val now: Instant

        /** En samling med samlingarnas regler – samma som `CollectionTable` skapar i appen. */
        fun <T : Identified> collection(codec: DocCodec<T>, name: String, path: (uid: String?) -> String): EntityCollection<T>

        /** Skriver ett dokument som det är, som en annan appversion skulle göra. */
        suspend fun writeRaw(path: String, id: String, doc: Doc)

        /** Läser ett dokument som det är lagrat (tidpunkter som [Instant]). */
        suspend fun readRaw(path: String, id: String): Doc?

        /** Användarens data får ett nyare format än appen. */
        fun makeUserNewerThanApp()

        /** Ingen användare är inloggad. */
        fun signOut()
    }

    /**
     * Uppställningen i [createEnvironment] blockerar (mot emulatorn: inloggning och första
     * skrivningen); regeln fäller ett test som fastnar där eller i `@After`, i JVM och på enhet.
     */
    @get:Rule(order = StuckTestTimeout.OUTERMOST)
    val stuckTestTimeout = StuckTestTimeout.rule()

    protected abstract fun createEnvironment(): Environment

    private lateinit var env: Environment
    private lateinit var items: EntityCollection<ContractItem>
    private val itemsPath get() = Paths.options(env.uid)

    @Before
    fun setUpEnvironment() {
        env = createEnvironment()
        items = env.collection(ContractItemCodec, Paths.OPTIONS) { uid -> Paths.options(uid ?: throw DataError.NotSignedIn) }
    }

    private fun contract(block: suspend () -> Unit) = runBlocking { withTimeout(TIMEOUT_MS) { block() } }

    private suspend fun <T> Flow<T>.awaitMatching(predicate: (T) -> Boolean): T = first(predicate)

    @Test
    fun upsert_syns_i_observe_sorterat_pa_sortOrder() = contract {
        items.upsert(ContractItem("b", "Yoga", sortOrder = 2)).getOrThrow()
        items.upsert(ContractItem("a", "Promenad", sortOrder = 1)).getOrThrow()
        val list = items.observe().awaitMatching { it.size == 2 }
        assertEquals(listOf("a", "b"), list.map { it.id })
    }

    @Test
    fun observe_och_get_pa_id_ger_dokumentet_eller_null_for_okant_id() = contract {
        items.upsert(ContractItem("a", "Promenad")).getOrThrow()
        assertEquals("Promenad", items.observe("a").awaitMatching { it != null }?.name)
        assertEquals("Promenad", items.get("a").getOrThrow()?.name)
        assertNull(items.get("finns-inte").getOrThrow())
        assertNull(items.observe("finns-inte").first())
    }

    @Test
    fun upsert_pa_samma_id_ersatter_kanda_falt() = contract {
        items.upsert(ContractItem("a", "Promenad", kind = "activity")).getOrThrow()
        items.upsert(ContractItem("a", "Huvudvärk", kind = "symptom")).getOrThrow()
        val stored = items.observe("a").awaitMatching { it?.name == "Huvudvärk" }
        assertEquals("symptom", stored?.kind)
    }

    @Test
    fun delete_tar_bort_dokumentet() = contract {
        items.upsert(ContractItem("a", "Promenad")).getOrThrow()
        items.observe().awaitMatching { it.size == 1 }
        items.delete("a").getOrThrow()
        items.observe().awaitMatching { it.isEmpty() }
        assertNull(env.readRaw(itemsPath, "a"))
    }

    @Test
    fun update_skriver_bara_de_valda_falten_och_skapar_inget() = contract {
        items.upsert(ContractItem("a", "Promenad", kind = "activity")).getOrThrow()
        // En annan enhet har bytt sort; den här skriver bara namnet ur sin äldre kopia.
        items.upsert(ContractItem("a", "Promenad", kind = "event")).getOrThrow()
        items.update(ContractItem("a", "Lång promenad", kind = "activity"), setOf(ContractItemCodec.NAME)).getOrThrow()
        val updated = items.observe("a").awaitMatching { it?.name == "Lång promenad" }
        assertEquals("event", updated?.kind)
        items.update(ContractItem("borta", "Borta"), setOf(ContractItemCodec.NAME))
        assertNull(env.readRaw(itemsPath, "borta"))
        assertTrue(items.update(ContractItem("a", "Promenad"), setOf("finnsInte")).isFailure, "ett okänt fält är ett fel, inte en tyst no-op")
    }

    @Test
    fun updateAll_skriver_bara_faltet_i_alla() = contract {
        items.upsert(ContractItem("a", "Promenad", kind = "activity")).getOrThrow()
        items.upsert(ContractItem("b", "Yoga", kind = "activity")).getOrThrow()
        items.updateAll(listOf(ContractItem("a", "Annat", kind = "event"), ContractItem("b", "Annat", kind = "event")), setOf(ContractItemCodec.KIND)).getOrThrow()
        val updated = items.observe().awaitMatching { list -> list.all { it.kind == "event" } }
        assertEquals(listOf("Promenad", "Yoga"), updated.map { it.name })
    }

    @Test
    fun update_ersatter_en_map_helt_och_skriver_alltid_updatedAt() = contract {
        items.upsert(ContractItem("a", "Levaxin", schedule = ContractSchedule.Daily(2))).getOrThrow()
        items.update(ContractItem("a", "Annat namn", schedule = ContractSchedule.Interval(3)), setOf(ContractItemCodec.SCHEDULE)).getOrThrow()
        val updated = items.observe("a").awaitMatching { it?.schedule is ContractSchedule.Interval }!!
        assertEquals("Levaxin", updated.name)
        val raw = env.readRaw(itemsPath, "a")!!
        assertEquals(
            (ContractItemCodec.encode(updated)[ContractItemCodec.SCHEDULE] as Map<*, *>).keys,
            (raw[ContractItemCodec.SCHEDULE] as Map<*, *>).keys,
            "inga kvarblivna nycklar från det gamla schemat",
        )

        env.writeRaw(itemsPath, "b", mapOf("name" to "Yoga"))
        items.update(ContractItem("b", "Annat namn", kind = "event"), setOf(ContractItemCodec.KIND)).getOrThrow()
        val written = items.observe("b").awaitMatching { it?.kind == "event" }!!
        assertEquals("Yoga", written.name)
        assertEquals(env.now, written.updatedAt, "updatedAt skrivs alltid")
    }

    @Test
    fun setArchived_vaxlar_arkiverat_utan_att_rora_andra_falt() = contract {
        items.upsert(ContractItem("a", "Promenad")).getOrThrow()
        items.setArchived("a", true).getOrThrow()
        assertEquals(true, items.observe("a").awaitMatching { it?.archived == true }?.archived)
        items.setArchived("a", false).getOrThrow()
        assertEquals("Promenad", items.observe("a").awaitMatching { it?.archived == false }?.name)
    }

    @Test
    fun okant_falt_fran_en_nyare_app_bevaras_vid_upsert() = contract {
        env.writeRaw(itemsPath, "a", mapOf("name" to "Promenad", "framtidaFält" to "grön"))
        val item = items.observe("a").awaitMatching { it != null }!!
        items.upsert(item.copy(name = "Promenad B")).getOrThrow()
        items.observe("a").awaitMatching { it?.name == "Promenad B" }
        assertEquals("grön", env.readRaw(itemsPath, "a")?.get("framtidaFält"))
    }

    @Test
    fun tomt_valfritt_falt_forsvinner_vid_upsert() = contract {
        items.upsert(ContractItem("a", "Promenad", note = "I skogen")).getOrThrow()
        items.upsert(ContractItem("a", "Promenad", note = null)).getOrThrow()
        assertNull(items.observe("a").awaitMatching { it?.note == null }?.note)
        assertNull(env.readRaw(itemsPath, "a")?.get("note"))
    }

    @Test
    fun okand_variant_fran_en_nyare_app_bevaras_nar_andra_falt_andras() = contract {
        val newer = mapOf("type" to "VID_FULLMANE", "fas" to 3L)
        env.writeRaw(itemsPath, "a", mapOf("name" to "Nytt", "schedule" to newer))
        val item = items.observe("a").awaitMatching { it != null }!!
        assertEquals(ContractSchedule.Unknown(newer), item.schedule)
        items.upsert(item.copy(note = "Till kvällen")).getOrThrow()
        items.observe("a").awaitMatching { it?.note == "Till kvällen" }
        assertEquals(newer, env.readRaw(itemsPath, "a")?.get("schedule"))
    }

    @Test
    fun byte_fran_okand_till_kand_variant_sparar_den_kanda() = contract {
        env.writeRaw(itemsPath, "a", mapOf("name" to "Nytt", "schedule" to mapOf("type" to "VID_FULLMANE")))
        val item = items.observe("a").awaitMatching { it != null }!!
        items.upsert(item.copy(schedule = ContractSchedule.Daily(2))).getOrThrow()
        val stored = items.observe("a").awaitMatching { it?.schedule is ContractSchedule.Daily }
        assertEquals(ContractSchedule.Daily(2), stored?.schedule)
    }

    @Test
    fun createdAt_raderas_inte_av_en_modell_utan_varde_och_updatedAt_satts_vid_skrivning() = contract {
        val created = Instant.fromEpochSeconds(1_700_000_000)
        items.upsert(ContractItem("a", "Promenad", createdAt = created)).getOrThrow()
        items.upsert(ContractItem("a", "Promenad igen", createdAt = null)).getOrThrow()
        val stored = items.observe("a").awaitMatching { it?.name == "Promenad igen" }!!
        assertEquals(created, stored.createdAt)
        assertEquals(env.now, stored.updatedAt)
    }

    @Test
    fun batch_skriver_och_tar_bort_i_ett_svep() = contract {
        items.batch(upserts = listOf(ContractItem("a", "Promenad"), ContractItem("b", "Yoga"))).getOrThrow()
        items.observe().awaitMatching { it.size == 2 }
        items.batch(upserts = listOf(ContractItem("c", "Städning")), deletes = listOf("a")).getOrThrow()
        val list = items.observe().awaitMatching { it.map(ContractItem::id).toSet() == setOf("b", "c") }
        assertEquals(setOf("b", "c"), list.map { it.id }.toSet())
        assertEquals(setOf("b", "c"), items.getAll().getOrThrow().map { it.id }.toSet())
    }

    @Test
    fun skrivning_vagras_nar_anvandarens_data_ar_nyare_an_appen() = contract {
        items.upsert(ContractItem("a", "Promenad")).getOrThrow()
        items.observe().awaitMatching { it.size == 1 }
        env.makeUserNewerThanApp()
        assertEquals(DataError.UpdateRequired, items.upsert(ContractItem("a", "Kapad")).dataError())
        assertEquals(DataError.UpdateRequired, items.delete("a").dataError())
        assertEquals(DataError.UpdateRequired, items.batch(listOf(ContractItem("b"))).dataError())
        assertEquals("Promenad", env.readRaw(itemsPath, "a")?.get("name"))
        assertTrue(Schema.isNewerThanApp(Schema.CURRENT_VERSION + 1))
    }

    @Test
    fun setArchived_skapar_inget_dokument_som_inte_finns() = contract {
        items.setArchived("borta", true)
        assertNull(env.readRaw(itemsPath, "borta"))
    }

    @Test
    fun batch_over_500_skrivningar_delas_och_allt_skrivs() = contract {
        val many = List(501) { ContractItem("o%03d".format(it), "Alternativ $it") }
        items.batch(many).getOrThrow()
        assertEquals(501, items.observe().awaitMatching { it.size == 501 }.size)
    }

    @Test
    fun lika_sortOrder_sorteras_pa_id() = contract {
        items.batch(listOf(ContractItem("c"), ContractItem("a"), ContractItem("b"))).getOrThrow()
        assertEquals(listOf("a", "b", "c"), items.observe().awaitMatching { it.size == 3 }.map { it.id })
    }

    @Test
    fun utloggad_ger_skrivning_NotSignedIn_och_lasning_inget() = contract {
        env.signOut()
        assertEquals(DataError.NotSignedIn, items.upsert(ContractItem("a", "Promenad")).dataError())
        assertEquals(DataError.NotSignedIn, items.get("a").dataError())
    }

    @Test
    fun newId_ger_unika_slumpade_id() = contract {
        val ids = List(50) { items.newId() }.toSet()
        assertEquals(50, ids.size)
        assertFalse(ids.any { it.isBlank() })
        assertNotEquals(items.newId(), items.newId())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
