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
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.core.schema.DoseCodec
import se.partee71.dagboken.core.schema.OptionCodec
import se.partee71.dagboken.core.schema.PrescriptionCodec
import se.partee71.dagboken.core.schema.PrnMedicineCodec
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.common.dataError
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.testing.StuckTestTimeout

/**
 * Kontraktet för `EntityCollection` (skill firestore-data-layer). Körs mot `FakeCollection` i
 * JVM i varje PR och mot riktig `FirestoreCollection` + Firebase-emulatorn som instrumenttest –
 * så att fake och verklighet bevisligen beter sig lika. Körs mot appens egna samlingar och codecs:
 * recept ([PrescriptionCodec] – nästlat schema med okänd variant, valfritt fält, `createdAt`), vid
 * behov-mediciner ([PrnMedicineCodec] – enkla fält, stora batchar) och alternativ ([OptionCodec] –
 * det som sorteras på `sortOrder` och arkiveras) och doser ([DoseCodec] – villkorade skrivningar och
 * avgränsad läsning på datum).
 *
 * `updatedAt`: ingen av appens modeller har fältet ännu (ARKITEKTUR.md → Datamodell), så regeln för
 * det bevisas mot testmodellen [StampedItem] – och för appens modeller att det **inte** skrivs.
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
    private lateinit var recipes: EntityCollection<Prescription>
    private lateinit var medicines: EntityCollection<PrnMedicine>
    private lateinit var options: EntityCollection<Option>
    private lateinit var doses: EntityCollection<Dose>
    private lateinit var stamped: EntityCollection<StampedItem>
    private val recipesPath get() = Paths.prescriptions(env.uid)
    private val medicinesPath get() = Paths.prnMedicines(env.uid)
    private val optionsPath get() = Paths.options(env.uid)
    private val dosesPath get() = Paths.doses(env.uid)

    @Before
    fun setUpEnvironment() {
        env = createEnvironment()
        recipes = env.collection(PrescriptionCodec, Paths.PRESCRIPTIONS) { uid -> Paths.prescriptions(uid ?: throw DataError.NotSignedIn) }
        medicines = env.collection(PrnMedicineCodec, Paths.PRN_MEDICINES) { uid -> Paths.prnMedicines(uid ?: throw DataError.NotSignedIn) }
        options = env.collection(OptionCodec, Paths.OPTIONS) { uid -> Paths.options(uid ?: throw DataError.NotSignedIn) }
        doses = env.collection(DoseCodec, Paths.DOSES) { uid -> Paths.doses(uid ?: throw DataError.NotSignedIn) }
        stamped = env.collection(StampedItemCodec, Paths.OPTIONS) { uid -> Paths.options(uid ?: throw DataError.NotSignedIn) }
    }

    private fun contract(block: suspend () -> Unit) = runBlocking { withTimeout(TIMEOUT_MS) { block() } }

    private suspend fun <T> Flow<T>.awaitMatching(predicate: (T) -> Boolean): T = first(predicate)

    private fun dose(id: String, date: LocalDate, status: DoseStatus = DoseStatus.PLANNED) = Dose(id, date, name = "Levaxin", status = status)

    private fun option(id: String, name: String = "", sortOrder: Int = 0) = Option(id, OptionKind.ACTIVITY, name, sortOrder = sortOrder)

    @Test
    fun upsert_syns_i_observe_sorterat_pa_sortOrder() = contract {
        options.upsert(option("b", "Yoga", sortOrder = 2)).getOrThrow()
        options.upsert(option("a", "Promenad", sortOrder = 1)).getOrThrow()
        val list = options.observe().awaitMatching { it.size == 2 }
        assertEquals(listOf("a", "b"), list.map { it.id })
    }

    @Test
    fun observe_och_get_pa_id_ger_dokumentet_eller_null_for_okant_id() = contract {
        recipes.upsert(Prescription("a", "Levaxin")).getOrThrow()
        assertEquals("Levaxin", recipes.observe("a").awaitMatching { it != null }?.name)
        assertEquals("Levaxin", recipes.get("a").getOrThrow()?.name)
        assertNull(recipes.get("finns-inte").getOrThrow())
        assertNull(recipes.observe("finns-inte").first())
    }

    @Test
    fun upsert_pa_samma_id_ersatter_kanda_falt() = contract {
        recipes.upsert(Prescription("a", "Levaxin", active = true)).getOrThrow()
        recipes.upsert(Prescription("a", "Sertralin", active = false)).getOrThrow()
        val stored = recipes.observe("a").awaitMatching { it?.name == "Sertralin" }
        assertEquals(false, stored?.active)
    }

    @Test
    fun delete_tar_bort_dokumentet() = contract {
        recipes.upsert(Prescription("a", "Levaxin")).getOrThrow()
        recipes.observe().awaitMatching { it.size == 1 }
        recipes.delete("a").getOrThrow()
        recipes.observe().awaitMatching { it.isEmpty() }
        assertNull(env.readRaw(recipesPath, "a"))
    }

    @Test
    fun update_skriver_bara_de_valda_falten_och_skapar_inget() = contract {
        medicines.upsert(PrnMedicine("a", "Alvedon")).getOrThrow()
        // En annan enhet har bytt namn; den här skriver bara stjärnan ur sin äldre kopia.
        medicines.upsert(PrnMedicine("a", "Panodil")).getOrThrow()
        medicines.update(PrnMedicine("a", "Alvedon", favorite = true), setOf(PrnMedicineCodec.FAVORITE)).getOrThrow()
        val updated = medicines.observe("a").awaitMatching { it?.favorite == true }
        assertEquals("Panodil", updated?.name)
        medicines.update(PrnMedicine("borta", "Borta"), setOf(PrnMedicineCodec.FAVORITE))
        assertNull(env.readRaw(medicinesPath, "borta"))
        assertTrue(medicines.update(PrnMedicine("a", "Alvedon"), setOf("finnsInte")).isFailure, "ett okänt fält är ett fel, inte en tyst no-op")
    }

    @Test
    fun update_tar_bort_falten_i_remove_och_inget_annat() = contract {
        env.writeRaw(medicinesPath, "a", mapOf("name" to "Alvedon", "gammaltFält" to "bort", "framtidaFält" to "kvar"))
        medicines.update(PrnMedicine("a", "Panodil"), setOf("name"), remove = setOf("gammaltFält")).getOrThrow()
        medicines.observe("a").awaitMatching { it?.name == "Panodil" }
        assertEquals(mapOf("name" to "Panodil", "framtidaFält" to "kvar"), env.readRaw(medicinesPath, "a"))
    }

    @Test
    fun confirmed_ger_hela_samlingen() = contract {
        // Skrivet så att servern har dem (en egen skrivning kan ännu ligga i kö – den läses inte här).
        env.writeRaw(medicinesPath, "a", mapOf("name" to "Alvedon"))
        env.writeRaw(medicinesPath, "b", mapOf("name" to "Ipren"))
        assertEquals(listOf("a", "b"), medicines.confirmed().getOrThrow().map { it.id })
    }

    @Test
    fun updateAll_skriver_bara_faltet_i_alla() = contract {
        recipes.upsert(Prescription("a", "Levaxin")).getOrThrow()
        recipes.upsert(Prescription("b", "Sertralin")).getOrThrow()
        recipes.updateAll(listOf(Prescription("a", "Annat", active = false), Prescription("b", "Annat", active = false)), setOf(PrescriptionCodec.ACTIVE)).getOrThrow()
        val updated = recipes.observe().awaitMatching { list -> list.size == 2 && list.none { it.active } }
        assertEquals(listOf("Levaxin", "Sertralin"), updated.map { it.name })
    }

    @Test
    fun update_ersatter_en_map_helt_och_skriver_inget_utanfor_falten() = contract {
        env.writeRaw(recipesPath, "a", mapOf("name" to "Levaxin", SCHEDULE to mapOf("repeat" to "daily", "days" to emptyList<Long>(), "intervalDays" to 2L, "framtidaNyckel" to "grön")))
        recipes.update(Prescription("a", "Annat namn", schedule = Schedule.Repeating(Repeat.INTERVAL, intervalDays = 3)), setOf(SCHEDULE)).getOrThrow()
        val updated = recipes.observe("a").awaitMatching { (it?.schedule as? Schedule.Repeating)?.repeat == Repeat.INTERVAL }!!
        assertEquals("Levaxin", updated.name)
        val raw = env.readRaw(recipesPath, "a")!!
        assertEquals(
            (PrescriptionCodec.encode(updated)[SCHEDULE] as Map<*, *>).keys,
            (raw[SCHEDULE] as Map<*, *>).keys,
            "inga kvarblivna nycklar från det gamla schemat",
        )
        assertEquals(setOf("name", SCHEDULE), raw.keys, "bara fältet skrivs – och updatedAt bara för en modell som har det")

        env.writeRaw(recipesPath, "b", mapOf("name" to "Sertralin"))
        recipes.update(Prescription("b", "Annat namn", active = false), setOf(PrescriptionCodec.ACTIVE)).getOrThrow()
        val written = recipes.observe("b").awaitMatching { it?.active == false }!!
        assertEquals("Sertralin", written.name)
    }

    @Test
    fun merge_skriver_bara_valda_falt_pa_djupet_och_bevarar_resten() = contract {
        val newer = mapOf("repeat" to "interval", "days" to emptyList<Long>(), "intervalDays" to 2L, "framtidaNyckel" to "grön")
        env.writeRaw(recipesPath, "a", mapOf("name" to "Levaxin", "active" to true, SCHEDULE to newer, "framtidaFält" to "kvar"))
        // En äldre kopia med annat namn och upprepning: bara schemats intervall och aktiv skrivs.
        recipes.merge(
            Prescription("a", "Gammalt namn", schedule = Schedule.Repeating(Repeat.DAILY, intervalDays = 3), active = false),
            setOf(listOf(SCHEDULE, "intervalDays"), listOf(PrescriptionCodec.ACTIVE)),
        ).getOrThrow()
        recipes.observe("a").awaitMatching { it?.active == false }
        val raw = env.readRaw(recipesPath, "a")!!
        assertEquals("Levaxin", raw["name"])
        assertEquals("kvar", raw["framtidaFält"], "okänt toppfält")
        assertEquals(mapOf("repeat" to "interval", "days" to emptyList<Long>(), "intervalDays" to 3L, "framtidaNyckel" to "grön"), raw[SCHEDULE], "okänd nyckel i samma map")
        assertFalse("updatedAt" in raw, "updatedAt skrivs bara för en modell som har fältet")
    }

    @Test
    fun merge_skapar_dokumentet_med_bara_de_valda_falten() = contract {
        recipes.merge(Prescription("ny", "Levaxin", schedule = Schedule.Repeating(Repeat.INTERVAL, intervalDays = 4)), setOf(listOf(SCHEDULE, "intervalDays"))).getOrThrow()
        recipes.observe("ny").awaitMatching { it != null }
        assertEquals(mapOf(SCHEDULE to mapOf("intervalDays" to 4L)), env.readRaw(recipesPath, "ny"))
    }

    @Test
    fun merge_med_okant_falt_ar_ett_fel_och_vagras_nar_datan_ar_nyare() = contract {
        val item = Prescription("a", "Levaxin")
        assertTrue(recipes.merge(item, setOf(listOf("finnsInte"))).isFailure)
        assertTrue(recipes.merge(item, setOf(listOf("name", "inuti"))).isFailure, "name är ingen map")
        assertTrue(recipes.merge(item, setOf(listOf("$SCHEDULE.intervalDays"))).isFailure, "en punkt är ingen väg")
        assertNull(env.readRaw(recipesPath, "a"))
        env.makeUserNewerThanApp()
        assertEquals(DataError.UpdateRequired, recipes.merge(item, setOf(listOf(PrescriptionCodec.ACTIVE))).dataError())
        assertNull(env.readRaw(recipesPath, "a"))
    }

    @Test
    fun cached_ger_listan_och_dokumentet_ur_cachen_och_null_for_okant_id() = contract {
        options.upsert(option("b", "Yoga", sortOrder = 2)).getOrThrow()
        options.upsert(option("a", "Promenad", sortOrder = 1)).getOrThrow()
        assertEquals(listOf("a", "b"), options.cached().getOrThrow().map { it.id })
        assertEquals("Yoga", options.cached("b").getOrThrow()?.name)
        assertNull(options.cached("finns-inte").getOrThrow())
    }

    @Test
    fun confirmedFrom_ger_bara_dokument_fran_och_med_vardet() = contract {
        // Skrivet så att servern har dem – läsningen godtar bara serverns svar.
        for ((id, date) in listOf("igar" to "2026-09-20", "idag" to "2026-09-21", "imorgon" to "2026-09-22")) {
            env.writeRaw(dosesPath, id, mapOf("name" to "Levaxin", DoseCodec.DATE to date))
        }
        env.writeRaw(dosesPath, "utan-datum", mapOf("name" to "Okänd dag"))
        val fromToday = doses.confirmedFrom(DoseCodec.DATE, "2026-09-21").getOrThrow()
        assertEquals(listOf("idag", "imorgon"), fromToday.map { it.id }.sorted(), "ett dokument utan fältet kommer inte med")
    }

    @Test
    fun createIfAbsent_skapar_bara_det_som_saknas_och_skriver_aldrig_over() = contract {
        // Tagen på en annan enhet, okänd för anroparens kopia – och ett okänt fält från en nyare app.
        env.writeRaw(dosesPath, "tagen", mapOf("name" to "Levaxin", DoseCodec.STATUS to "taken", "framtidaFält" to "kvar"))
        doses.createIfAbsent(listOf(dose("tagen", LocalDate(2026, 9, 21)), dose("ny", LocalDate(2026, 9, 21)))).getOrThrow()
        doses.observe("ny").awaitMatching { it != null }
        assertEquals(mapOf("name" to "Levaxin", DoseCodec.STATUS to "taken", "framtidaFält" to "kvar"), env.readRaw(dosesPath, "tagen"))
        assertEquals("planned", env.readRaw(dosesPath, "ny")?.get(DoseCodec.STATUS))
        doses.createIfAbsent(emptyList()).getOrThrow()
    }

    @Test
    fun deleteIf_provar_villkoret_mot_det_lagrade_och_skapar_inget() = contract {
        doses.batch(listOf(dose("planerad", LocalDate(2026, 9, 21)), dose("tagen", LocalDate(2026, 9, 21)))).getOrThrow()
        // Anroparens kopia säger "planerad", men en annan enhet har hunnit ta dosen.
        env.writeRaw(dosesPath, "tagen", mapOf("name" to "Levaxin", DoseCodec.STATUS to "taken"))
        doses.deleteIf(listOf("planerad", "tagen", "finns-inte")) { it.status == DoseStatus.PLANNED }.getOrThrow()
        doses.observe("planerad").awaitMatching { it == null }
        assertEquals("taken", env.readRaw(dosesPath, "tagen")?.get(DoseCodec.STATUS))
        assertNull(env.readRaw(dosesPath, "finns-inte"))
        doses.deleteIf(emptyList()) { true }.getOrThrow()
    }

    @Test
    fun updateIf_skriver_bara_falten_dar_det_lagrade_uppfyller_villkoret() = contract {
        doses.batch(listOf(dose("planerad", LocalDate(2026, 9, 21)), dose("tagen", LocalDate(2026, 9, 21)))).getOrThrow()
        // Anroparens kopia säger "planerad", men en annan enhet har hunnit ta dosen – och lagt till ett fält.
        env.writeRaw(dosesPath, "tagen", mapOf("name" to "Levaxin", DoseCodec.STATUS to "taken", "framtidaFält" to "kvar"))
        val renamed = listOf("planerad", "tagen", "finns-inte").map { dose(it, LocalDate(2026, 9, 21)).copy(name = "Levaxin Ny", note = "skrivs inte") }
        doses.updateIf(renamed, setOf(DoseCodec.NAME)) { it.status == DoseStatus.PLANNED }.getOrThrow()
        doses.observe("planerad").awaitMatching { it?.name == "Levaxin Ny" }
        assertNull(env.readRaw(dosesPath, "planerad")?.get("note"), "bara de valda fälten")
        assertEquals(mapOf("name" to "Levaxin", DoseCodec.STATUS to "taken", "framtidaFält" to "kvar"), env.readRaw(dosesPath, "tagen"))
        assertNull(env.readRaw(dosesPath, "finns-inte"), "skapar inget")
        assertTrue(doses.updateIf(renamed, setOf("finnsInte")) { true }.isFailure, "ett okänt fält är ett fel")
    }

    @Test
    fun updatedAt_satts_vid_upsert_update_updateIf_och_merge_och_createdAt_raderas_inte() = contract {
        val created = Instant.fromEpochSeconds(1_700_000_000)
        stamped.upsert(StampedItem("a", "Promenad", createdAt = created)).getOrThrow()
        stamped.upsert(StampedItem("a", "Promenad igen", createdAt = null)).getOrThrow()
        val upserted = stamped.observe("a").awaitMatching { it?.name == "Promenad igen" }!!
        assertEquals(created, upserted.createdAt)
        assertEquals(env.now, upserted.updatedAt)

        env.writeRaw(optionsPath, "b", mapOf("name" to "Yoga"))
        stamped.update(StampedItem("b", "Annat namn", kind = "event"), setOf(StampedItemCodec.KIND)).getOrThrow()
        stamped.observe("b").awaitMatching { it?.kind == "event" }
        assertEquals(mapOf("name" to "Yoga", "kind" to "event", "updatedAt" to env.now), env.readRaw(optionsPath, "b"), "update skriver alltid updatedAt")

        env.writeRaw(optionsPath, "c", mapOf("name" to "Löpning"))
        stamped.updateIf(listOf(StampedItem("c", kind = "event")), setOf(StampedItemCodec.KIND)) { true }.getOrThrow()
        stamped.observe("c").awaitMatching { it?.kind == "event" }
        assertEquals(env.now, env.readRaw(optionsPath, "c")?.get("updatedAt"), "updateIf skriver alltid updatedAt")

        env.writeRaw(optionsPath, "d", mapOf("name" to "Simning", "framtidaFält" to "kvar"))
        stamped.merge(StampedItem("d", "Gammalt namn", kind = "event"), setOf(listOf(StampedItemCodec.KIND))).getOrThrow()
        stamped.observe("d").awaitMatching { it?.kind == "event" }
        assertEquals(mapOf("name" to "Simning", "framtidaFält" to "kvar", "kind" to "event", "updatedAt" to env.now), env.readRaw(optionsPath, "d"))

        stamped.merge(StampedItem("ny", kind = "event"), setOf(listOf(StampedItemCodec.KIND))).getOrThrow()
        stamped.observe("ny").awaitMatching { it != null }
        assertEquals(mapOf("kind" to "event", "updatedAt" to env.now), env.readRaw(optionsPath, "ny"), "merge skapar med fälten och updatedAt")
    }

    @Test
    fun ett_olasbart_dokument_rors_inte_och_faller_inte_de_ovriga_villkorade_skrivningarna() = contract {
        val unreadable = mapOf(StampedItemCodec.NAME to StampedItemCodec.UNREADABLE, "kind" to "activity")
        env.writeRaw(optionsPath, "trasig", unreadable)
        env.writeRaw(optionsPath, "hel", mapOf(StampedItemCodec.NAME to "Promenad", "kind" to "activity"))
        env.writeRaw(optionsPath, "bort", mapOf(StampedItemCodec.NAME to "Yoga", "kind" to "activity"))

        stamped.createIfAbsent(listOf(StampedItem("trasig", "Skriver över"), StampedItem("ny", "Simning"))).getOrThrow()
        stamped.updateIf(listOf(StampedItem("trasig", "Ändrad"), StampedItem("hel", "Ändrad")), setOf(StampedItemCodec.NAME)) { true }.getOrThrow()
        stamped.deleteIf(listOf("trasig", "bort")) { true }.getOrThrow()

        stamped.observe("bort").awaitMatching { it == null }
        assertEquals(unreadable, env.readRaw(optionsPath, "trasig"), "oläsbart: varken skapat över, ändrat eller raderat")
        assertEquals("Simning", env.readRaw(optionsPath, "ny")?.get(StampedItemCodec.NAME))
        assertEquals("Ändrad", env.readRaw(optionsPath, "hel")?.get(StampedItemCodec.NAME))
    }

    @Test
    fun villkorade_skrivningar_vagras_utloggad_och_nar_datan_ar_nyare() = contract {
        doses.upsert(dose("a", LocalDate(2026, 9, 21))).getOrThrow()
        doses.observe("a").awaitMatching { it != null }
        env.makeUserNewerThanApp()
        assertEquals(DataError.UpdateRequired, doses.createIfAbsent(listOf(dose("b", LocalDate(2026, 9, 21)))).dataError())
        assertEquals(DataError.UpdateRequired, doses.deleteIf(listOf("a")) { true }.dataError())
        assertEquals(DataError.UpdateRequired, doses.updateIf(listOf(dose("a", LocalDate(2026, 9, 21))), setOf(DoseCodec.NAME)) { true }.dataError())
        assertNull(env.readRaw(dosesPath, "b"))
        assertEquals("Levaxin", env.readRaw(dosesPath, "a")?.get("name"))
        env.signOut()
        assertEquals(DataError.NotSignedIn, doses.confirmedFrom(DoseCodec.DATE, "2026-09-21").dataError())
    }

    @Test
    fun cached_utloggad_ger_NotSignedIn_direkt() = contract {
        env.signOut()
        assertEquals(DataError.NotSignedIn, recipes.cached().dataError())
        assertEquals(DataError.NotSignedIn, recipes.cached("a").dataError())
    }

    @Test
    fun setArchived_vaxlar_arkiverat_utan_att_rora_andra_falt() = contract {
        options.upsert(option("a", "Promenad")).getOrThrow()
        options.setArchived("a", true).getOrThrow()
        assertEquals(true, options.observe("a").awaitMatching { it?.archived == true }?.archived)
        options.setArchived("a", false).getOrThrow()
        assertEquals("Promenad", options.observe("a").awaitMatching { it?.archived == false }?.name)
    }

    @Test
    fun okant_falt_fran_en_nyare_app_bevaras_vid_upsert() = contract {
        env.writeRaw(medicinesPath, "a", mapOf("name" to "Alvedon", "framtidaFält" to "grön"))
        val item = medicines.observe("a").awaitMatching { it != null }!!
        medicines.upsert(item.copy(name = "Alvedon 500")).getOrThrow()
        medicines.observe("a").awaitMatching { it?.name == "Alvedon 500" }
        assertEquals("grön", env.readRaw(medicinesPath, "a")?.get("framtidaFält"))
    }

    @Test
    fun tomt_valfritt_falt_forsvinner_vid_upsert() = contract {
        medicines.upsert(PrnMedicine("a", "Alvedon", note = "Max 3 g per dygn")).getOrThrow()
        medicines.upsert(PrnMedicine("a", "Alvedon", note = null)).getOrThrow()
        assertNull(medicines.observe("a").awaitMatching { it?.note == null }?.note)
        assertNull(env.readRaw(medicinesPath, "a")?.get("note"))
    }

    @Test
    fun okand_variant_fran_en_nyare_app_bevaras_nar_andra_falt_andras() = contract {
        val newer = mapOf("regel" to "fullmåne", "fas" to 3L)
        env.writeRaw(recipesPath, "a", mapOf("name" to "Nytt", SCHEDULE to newer))
        val item = recipes.observe("a").awaitMatching { it != null }!!
        assertEquals(Schedule.Unknown(newer), item.schedule)
        recipes.upsert(item.copy(note = "Till kvällen")).getOrThrow()
        recipes.observe("a").awaitMatching { it?.note == "Till kvällen" }
        assertEquals(newer, env.readRaw(recipesPath, "a")?.get(SCHEDULE))
    }

    @Test
    fun byte_fran_okand_till_kand_variant_sparar_den_kanda() = contract {
        env.writeRaw(recipesPath, "a", mapOf("name" to "Nytt", SCHEDULE to mapOf("regel" to "fullmåne")))
        val item = recipes.observe("a").awaitMatching { it != null }!!
        val known = Schedule.Repeating(Repeat.CUSTOM, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        recipes.upsert(item.copy(schedule = known)).getOrThrow()
        val stored = recipes.observe("a").awaitMatching { it?.schedule is Schedule.Repeating }
        assertEquals(known, stored?.schedule)
    }

    @Test
    fun createdAt_raderas_inte_av_en_modell_utan_varde() = contract {
        val created = Instant.fromEpochSeconds(1_700_000_000)
        recipes.upsert(Prescription("a", "Levaxin", createdAt = created)).getOrThrow()
        recipes.upsert(Prescription("a", "Levaxin igen", createdAt = null)).getOrThrow()
        val stored = recipes.observe("a").awaitMatching { it?.name == "Levaxin igen" }!!
        assertEquals(created, stored.createdAt)
    }

    @Test
    fun batch_skriver_och_tar_bort_i_ett_svep() = contract {
        medicines.batch(upserts = listOf(PrnMedicine("a", "Alvedon"), PrnMedicine("b", "Ipren"))).getOrThrow()
        medicines.observe().awaitMatching { it.size == 2 }
        medicines.batch(upserts = listOf(PrnMedicine("c", "Loratadin")), deletes = listOf("a")).getOrThrow()
        val list = medicines.observe().awaitMatching { it.map(PrnMedicine::id).toSet() == setOf("b", "c") }
        assertEquals(setOf("b", "c"), list.map { it.id }.toSet())
        assertEquals(setOf("b", "c"), medicines.getAll().getOrThrow().map { it.id }.toSet())
    }

    @Test
    fun skrivning_vagras_nar_anvandarens_data_ar_nyare_an_appen() = contract {
        recipes.upsert(Prescription("a", "Levaxin")).getOrThrow()
        recipes.observe().awaitMatching { it.size == 1 }
        env.makeUserNewerThanApp()
        assertEquals(DataError.UpdateRequired, recipes.upsert(Prescription("a", "Kapad")).dataError())
        assertEquals(DataError.UpdateRequired, recipes.delete("a").dataError())
        assertEquals(DataError.UpdateRequired, recipes.batch(listOf(Prescription("b"))).dataError())
        assertEquals("Levaxin", env.readRaw(recipesPath, "a")?.get("name"))
        assertTrue(Schema.isNewerThanApp(Schema.CURRENT_VERSION + 1))
    }

    @Test
    fun setArchived_skapar_inget_dokument_som_inte_finns() = contract {
        options.setArchived("borta", true)
        assertNull(env.readRaw(optionsPath, "borta"))
    }

    @Test
    fun batch_over_500_skrivningar_delas_och_allt_skrivs() = contract {
        val many = List(501) { PrnMedicine("m%03d".format(it), "Medicin $it") }
        medicines.batch(many).getOrThrow()
        assertEquals(501, medicines.observe().awaitMatching { it.size == 501 }.size)
    }

    @Test
    fun lika_sortOrder_sorteras_pa_id_och_osorterbart_pa_id() = contract {
        options.batch(listOf(option("c"), option("a"), option("b"))).getOrThrow()
        assertEquals(listOf("a", "b", "c"), options.observe().awaitMatching { it.size == 3 }.map { it.id })
        recipes.batch(listOf(Prescription("c"), Prescription("a"), Prescription("b"))).getOrThrow()
        assertEquals(listOf("a", "b", "c"), recipes.observe().awaitMatching { it.size == 3 }.map { it.id })
    }

    @Test
    fun utloggad_ger_skrivning_NotSignedIn_och_lasning_inget() = contract {
        env.signOut()
        assertEquals(DataError.NotSignedIn, recipes.upsert(Prescription("a", "Levaxin")).dataError())
        assertEquals(DataError.NotSignedIn, recipes.get("a").dataError())
    }

    @Test
    fun newId_ger_unika_slumpade_id() = contract {
        val ids = List(50) { recipes.newId() }.toSet()
        assertEquals(50, ids.size)
        assertFalse(ids.any { it.isBlank() })
        assertNotEquals(recipes.newId(), recipes.newId())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

        /** Receptets upprepning i dokumentet (`PrescriptionCodec`). */
        const val SCHEDULE = "schedule"
    }
}
