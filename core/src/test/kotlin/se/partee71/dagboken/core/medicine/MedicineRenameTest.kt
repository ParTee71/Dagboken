package se.partee71.dagboken.core.medicine

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.TextLimits

/** Namn, styrka och form ur Läkemedelsverkets lista på en export (REC-1, FAV-1): förslaget, kartan och tillämpningen. */
class MedicineRenameTest {

    private val catalog = MedicineCatalog.parse(
        listOf(
            "# updated 2026-10-04",
            "Alvedon\t500 mg\tFilmdragerad tablett",
            "Bricanyl Turbuhaler\t0,5 mg/dos\tInhalationspulver",
            "Levaxin\t100 mikrogram\tTablett",
            "Metformin Teva\t500 mg\tFilmdragerad tablett",
        ).joinToString("\n"),
    )

    private val ts = Instant.parse("2026-09-21T08:12:45.000001Z")
    private val future = mapOf("framtidaFält" to mapOf("okänd" to true, "lista" to listOf(1L, "två", null)), "andel" to 0.25)

    private fun doc(path: String, vararg fields: Pair<String, Any?>): ExportFormat.Document =
        ExportFormat.Document("users/$path", mapOf(*fields) + future + ("updatedAt" to ts))

    private fun boost(id: String, dose: String, unit: String) =
        mapOf("id" to id, "start" to "2026-09-01", "end" to null, "dose" to dose, "unit" to unit)

    private val export = listOf(
        ExportFormat.Document("users/u1", mapOf("schemaVersion" to 1L, "createdAt" to ts)),
        doc("u1/settings/app", "theme" to mapOf("mode" to "dark")),
        doc(
            "u1/prescriptions/r1", "name" to "levaxin", "strength" to "", "form" to null, "dose" to "200", "unit" to "mcg",
            "slots" to listOf("morning"), "boosts" to listOf(boost("b1", "100", "mcg")), "active" to true, "createdAt" to ts, "note" to "Fastande",
        ),
        doc("u1/prescriptions/r2", "name" to "Metformin", "dose" to "750", "unit" to "mg", "boosts" to listOf(boost("b2", "250", "mg"))),
        doc("u1/prnMedicines/p1", "name" to "alvedon", "dose" to "1000", "unit" to "mg", "favorite" to true, "minHoursBetween" to 6L),
        doc("u1/prnMedicines/p2", "name" to "Okänt preparat", "dose" to "1", "unit" to "st"),
        doc("u1/prnMedicines/p3", "name" to "Bricanyl", "dose" to "2", "unit" to "st"),
        doc("u1/doses/d1", "name" to "levaxin", "strength" to "", "dose" to "200", "unit" to "mcg", "prescriptionId" to "r1", "prnId" to null, "takenAt" to ts, "note" to "Med frukost"),
        doc("u1/doses/recept_r1_2026-09-21_Morgon", "name" to "levaxin", "dose" to "200", "unit" to "mcg", "status" to "planned"),
        doc("u1/doses/d3", "name" to "alvedon", "dose" to "1000", "unit" to "mg", "prnId" to "p1"),
        doc("u1/doses/d4", "name" to "alvedon", "dose" to "500", "unit" to "mg", "prescriptionId" to null, "prnId" to null),
        doc("u1/doses/d5", "name" to "Ipren", "dose" to "400", "unit" to "mg"),
        doc("u1/doses/d6", "name" to "alvedon", "dose" to "500", "unit" to "mg", "prescriptionId" to "annat"),
        doc("u2/prnMedicines/p1", "name" to "alvedon", "dose" to "500", "unit" to "mg"),
        doc("u2/doses/d7", "name" to "alvedon", "dose" to "500", "unit" to "mg"),
        doc("u1/illnessEpisodes/e1/checkins/c1", "name" to "alvedon"),
    )

    private val byPath = export.associateBy { it.path }

    private fun row(
        collection: String,
        id: String,
        oldName: String,
        newName: String = "",
        strength: String = "500 mg",
        oldDose: String = "1000",
        oldUnit: String = "mg",
        newDose: String = "",
        newUnit: String = "",
        uid: String = "u1",
        form: String = "tablet",
        from: List<ExportFormat.Document> = export,
    ): MedicineRename.Row {
        // Styrka, form och höjningar som de står i exporten raden tillämpas på – annars är raden inaktuell.
        val stored = from.singleOrNull { it.path == "users/$uid/$collection/$id" }?.data.orEmpty()
        return MedicineRename.Row(
            uid, collection, id, oldName, newName, strength, form, oldDose, oldUnit, newDose, newUnit, 0,
            oldStrength = stored["strength"] as? String ?: "", oldForm = stored["form"] as? String ?: "",
            oldBoosts = MedicineRename.fingerprint(stored["boosts"]),
        )
    }

    /** Ändringarna i en ändringsfil (`updates`). */
    private fun updatesOf(text: String): List<ExportFormat.Document> =
        Json.parseToJsonElement(text).jsonObject.getValue(ExportFormat.UPDATES).jsonArray.map { element ->
            val doc = element.jsonObject
            @Suppress("UNCHECKED_CAST")
            ExportFormat.Document(doc.getValue("path").jsonPrimitive.content, ExportFormat.fromJson(doc.getValue("data")) as Doc)
        }

    private fun json(doc: Doc) = ExportFormat.toJson(doc)

    private fun applied(rows: List<MedicineRename.Row> = MedicineRename.suggest(export, catalog), documents: List<ExportFormat.Document> = export) =
        MedicineRename.apply(documents, rows)

    private fun MedicineRename.Applied.fields(path: String): Doc? = documents.singleOrNull { it.path == "users/$path" }?.data

    // ---- Förslaget ----

    @Test
    fun `förslaget - en rad per medicin och användare, första träffen eller första ordet, dosen omräknad när styrkans enhet är dosens`() {
        assertEquals(
            listOf(
                MedicineRename.Row("u1", "prescriptions", "r1", "levaxin", "Levaxin", "100 mikrogram", "tablet", "200", "mcg", "2", "tablett", 2),
                MedicineRename.Row("u1", "prescriptions", "r2", "Metformin", "Metformin Teva", "500 mg", "tablet", "750", "mg", "1,5", "tablett", 0),
                MedicineRename.Row("u1", "prnMedicines", "p1", "alvedon", "Alvedon", "500 mg", "tablet", "1000", "mg", "2", "tablett", 2),
                MedicineRename.Row("u1", "prnMedicines", "p2", "Okänt preparat", oldDose = "1", oldUnit = "st"),
                MedicineRename.Row("u1", "prnMedicines", "p3", "Bricanyl", "Bricanyl Turbuhaler", "0,5 mg/dos", "inhaler", "2", "st", "2", "st", 0),
                MedicineRename.Row("u2", "prnMedicines", "p1", "alvedon", "Alvedon", "500 mg", "tablet", "500", "mg", "1", "tablett", 1),
            ),
            MedicineRename.suggest(export, catalog).map { it.copy(oldBoosts = "") },
        )
        val rows = MedicineRename.suggest(export, catalog)
        assertEquals(MedicineRename.fingerprint(byPath.getValue("users/u1/prescriptions/r1").data["boosts"]), rows[0].oldBoosts)
        assertEquals(12, rows[0].oldBoosts.length)
        assertEquals("", rows[2].oldBoosts, "vid behov-medicinen har inga höjningar")
        val firstWord = MedicineRename.suggest(listOf(doc("u1/prnMedicines/p9", "name" to "Alvedon brus", "dose" to "500", "unit" to "mg")), catalog)
        assertEquals("Alvedon", firstWord.single().newName)
    }

    @Test
    fun `bästa träffen är den vars styrka är den lagrade dosen, annars den första`() {
        val alvedon = MedicineCatalog.parse(
            listOf("Alvedon\t250 mg\tTablett", "Alvedon\t500 mg\tFilmdragerad tablett", "Alvedon\t665 mg\tTablett", "Alvedon\t24 mg/ml\tOral suspension").joinToString("\n"),
        )
        assertEquals("500 mg", MedicineRename.bestMatch(alvedon, "Alvedon", "500", "mg")?.strength)
        assertEquals("665 mg", MedicineRename.bestMatch(alvedon, "alvedon", "665", "MG")?.strength)
        assertEquals("250 mg", MedicineRename.bestMatch(alvedon, "Alvedon", "1000", "mg")?.strength, "ingen styrka = dosen → första")
        assertEquals("250 mg", MedicineRename.bestMatch(alvedon, "Alvedon", "1 tablett", "st")?.strength)
        assertEquals("500 mg", MedicineRename.bestMatch(alvedon, "Alvedon brus", "500", "mg")?.strength, "också på första ordet")
        assertNull(MedicineRename.bestMatch(alvedon, "Ipren", "400", "mg"))
        assertEquals("500 mg", MedicineRename.suggest(listOf(doc("u1/prnMedicines/p", "name" to "Alvedon", "dose" to "500", "unit" to "mg")), alvedon).single().strength)
    }

    @Test
    fun `dos per enhet - samma enhet och jämn kvot, annars inget förslag`() {
        assertEquals("1", MedicineRename.perUnit("500", "mg", "500 mg"))
        assertEquals("0,5", MedicineRename.perUnit("250", "MG", "500 mg"))
        assertEquals("1", MedicineRename.perUnit("50", "µg", "50 mikrogram"))
        assertEquals("1,5", MedicineRename.perUnit("0,75", "mg", "0.5 mg"))
        assertNull(MedicineRename.perUnit("100", "mg", "300 mg"), "100/300 går inte jämnt ut")
        assertNull(MedicineRename.perUnit("500", "mg", "1 g"), "annan enhet")
        assertNull(MedicineRename.perUnit("2", "st", "0,5 mg/dos"))
        assertNull(MedicineRename.perUnit("1 tablett", "mg", "500 mg"), "dosen är inget tal")
        assertNull(MedicineRename.perUnit("500", "mg", "0 mg"))
        assertNull(MedicineRename.perUnit("500", "mg", ""))
    }

    @Test
    fun `kartan skrivs med rubrikrad och uid och läses tillbaka likadant`() {
        val rows = MedicineRename.suggest(export, catalog)
        val text = MedicineRename.renderMap(rows)
        assertEquals(MedicineRename.HEADER.joinToString("\t"), text.lines().first())
        assertEquals("uid", MedicineRename.HEADER.first())
        assertEquals(rows.size + 1, text.lines().count { it.isNotEmpty() })
        assertEquals(rows, MedicineRename.parseMap(text))
        assertEquals(rows, MedicineRename.parseMap(text.replace("\n", "\r\n")), "rättad i ett Windows-program")
    }

    @Test
    fun `en trasig karta stoppar med radnummer, aldrig innehåll`() {
        val header = MedicineRename.HEADER.joinToString("\t")
        fun error(vararg lines: String) = assertFailsWith<IllegalArgumentException> { MedicineRename.parseMap(lines.joinToString("\n")) }.message!!
        fun line(doses: String = "0") = "u1\tprnMedicines\tp1\tAlvedon\tAlvedon\t500 mg\ttablet\t1\tmg\t1\tmg\t$doses\t\t\t"
        val ok = line()
        assertTrue("rad 1" in error("namn\tnytt"))
        assertTrue(error(header, "u1\tprnMedicines\tp1\tAlvedon").let { "rad 2" in it && "Alvedon" !in it })
        assertTrue("okänd samling" in error(header, ok.replace("prnMedicines", "doses")))
        assertTrue(error(header, ok.replace("tablet", "spray")).let { "okänd form" in it && "spray" !in it })
        assertTrue("inte ett tal" in error(header, line(doses = "x")))
        assertTrue("rad 3" in error(header, ok, ok), "samma medicin två gånger")
        assertEquals(2, MedicineRename.parseMap(listOf(header, ok, ok.replaceFirst("u1", "u2")).joinToString("\n")).size, "samma id hos två användare")
    }

    // ---- Tillämpningen ----

    @Test
    fun `bara ändrade fält i berörda dokument kommer ut - andra och okända fält skrivs aldrig`() {
        val result = applied()
        assertEquals(
            listOf(
                "u1/prescriptions/r1", "u1/prescriptions/r2", "u1/prnMedicines/p1", "u1/prnMedicines/p3",
                "u1/doses/d1", "u1/doses/recept_r1_2026-09-21_Morgon", "u1/doses/d3", "u1/doses/d4", "u2/prnMedicines/p1", "u2/doses/d7",
            ).map { "users/$it" },
            result.documents.map { it.path },
        )
        for (out in result.documents) {
            val allowed = if ("/doses/" in out.path) setOf("name", "strength") else setOf("name", "strength", "form", "dose", "unit", "boosts")
            assertTrue(out.data.keys.isNotEmpty() && allowed.containsAll(out.data.keys), "${out.path}: ${out.data.keys}")
            val before = byPath.getValue(out.path).data
            for ((key, value) in out.data) assertTrue(json(mapOf(key to before[key])) != json(mapOf(key to value)), "${out.path}.$key är oförändrat men skrivs")
        }
        assertEquals(setOf("name", "strength", "form"), result.fields("u1/prnMedicines/p3")?.keys, "dos och enhet oförändrade skrivs inte")
        assertEquals(listOf(5, 5, 1, 0, 0, 0), with(result) { listOf(medicines, doses, skipped, stale, doseKept, ambiguousDoses) })
    }

    @Test
    fun `en rad som inte ändrar något ger inget dokument`() {
        val same = row("prnMedicines", "p1", "alvedon", newName = "alvedon", strength = "", form = "")
        assertEquals(emptyList(), applied(listOf(same)).documents)
    }

    @Test
    fun `medicinen får namn, styrka, form och omräknad dos - receptets höjningar räknas om med samma faktor`() {
        val result = applied()
        assertEquals(
            mapOf(
                "name" to "Levaxin", "strength" to "100 mikrogram", "form" to "tablet", "dose" to "2", "unit" to "tablett",
                "boosts" to listOf(boost("b1", "1", "tablett")),
            ),
            result.fields("u1/prescriptions/r1"),
        )
        val r2 = result.fields("u1/prescriptions/r2")!!
        assertEquals(listOf("1,5", "tablett", listOf(boost("b2", "0,5", "tablett"))), listOf(r2["dose"], r2["unit"], r2["boosts"]))
    }

    @Test
    fun `doserna får bara medicinens nya namn och styrka - kopplade via recept, receptdosens id eller prnId, och okopplade med samma namn`() {
        val result = applied()
        assertEquals(mapOf("name" to "Levaxin", "strength" to "100 mikrogram"), result.fields("u1/doses/d1"))
        assertEquals(mapOf("name" to "Levaxin", "strength" to "100 mikrogram"), result.fields("u1/doses/recept_r1_2026-09-21_Morgon"))
        // Gamla namnet bar styrkan: den flyttar till fältet strength, dos och enhet orörda.
        val levaxin50 = listOf(
            doc("u1/prescriptions/r", "name" to "Levaxin 50", "dose" to "1", "unit" to "st"),
            doc("u1/doses/d", "name" to "Levaxin 50", "dose" to "1", "unit" to "st", "prescriptionId" to "r"),
        )
        val renamed = applied(listOf(row("prescriptions", "r", "Levaxin 50", newName = "Levaxin", strength = "50 mikrogram", oldDose = "1", oldUnit = "st", from = levaxin50)), levaxin50)
        assertEquals(mapOf("name" to "Levaxin", "strength" to "50 mikrogram"), renamed.fields("u1/doses/d"))
        // d6 hör till ett annat recept, d5 heter något annat, d7 är en annan användare, c1 är ingen dos.
        val novum = applied(listOf(row("prnMedicines", "p1", "alvedon", newName = "Alvedon Novum")))
        assertEquals(listOf("u1/prnMedicines/p1", "u1/doses/d3", "u1/doses/d4").map { "users/$it" }, novum.documents.map { it.path })
    }

    @Test
    fun `kartans rader gäller sin användare - samma id hos en annan användare rörs inte`() {
        val u2 = applied(listOf(row("prnMedicines", "p1", "alvedon", newName = "Alvedon", oldDose = "500", uid = "u2")))
        assertEquals(listOf("users/u2/prnMedicines/p1", "users/u2/doses/d7"), u2.documents.map { it.path })
    }

    @Test
    fun `går en höjning inte jämnt ut lämnas receptets dos, enhet och höjningar orörda`() {
        val third = row("prescriptions", "r2", "Metformin", newName = "Metformin Teva", newDose = "1", newUnit = "tablett", oldDose = "750", oldUnit = "mg")
        val result = applied(listOf(third))
        assertEquals(mapOf("name" to "Metformin Teva", "strength" to "500 mg", "form" to "tablet"), result.fields("u1/prescriptions/r2"))
        assertEquals(1, result.doseKept)
        // Utan höjningar byts dosen även när den gamla inte är ett tal.
        val noBoosts = listOf(doc("u1/prescriptions/r9", "name" to "D-vitamin", "dose" to "1 tablett", "unit" to "st", "boosts" to emptyList<Any>()))
        val d = applied(listOf(row("prescriptions", "r9", "D-vitamin", newName = "D-vitamin", newDose = "1", newUnit = "tablett", oldDose = "1 tablett", oldUnit = "st", from = noBoosts)), noBoosts)
        assertEquals(listOf("1", "tablett", null), d.fields("u1/prescriptions/r9")!!.let { listOf(it["dose"], it["unit"], it["boosts"]) })
        // Oförändrad dos och enhet (eller tomma nya) rör varken dos eller höjningar.
        val same = applied(listOf(row("prescriptions", "r2", "Metformin", newName = "Metformin Teva", oldDose = "750", oldUnit = "mg")))
        assertEquals(setOf("name", "strength", "form"), same.fields("u1/prescriptions/r2")?.keys)
    }

    @Test
    fun `tomt nytt namn hoppas över, en saknad eller ändrad medicin är inaktuell`() {
        val result = applied(
            listOf(
                row("prnMedicines", "p1", "alvedon"),
                row("prnMedicines", "p1", "Alvedon forte", newName = "Alvedon"),
                row("prnMedicines", "saknas", "alvedon", newName = "Alvedon"),
                row("prnMedicines", "p1", "alvedon", newName = "Alvedon", oldDose = "500"),
                row("prnMedicines", "p1", "alvedon", newName = "Alvedon", uid = "u3"),
            ),
        )
        assertEquals(emptyList(), result.documents)
        assertEquals(listOf(1, 4), listOf(result.skipped, result.stale))
    }

    @Test
    fun `tom styrka eller form i kartan lämnar de lagrade - doserna får den lagrade styrkan`() {
        val stored = listOf(
            doc("u1/prescriptions/r", "name" to "levaxin", "strength" to "50 mikrogram", "form" to "tablet", "dose" to "1", "unit" to "st"),
            doc("u1/doses/d", "name" to "levaxin", "strength" to "", "dose" to "1", "unit" to "st", "prescriptionId" to "r"),
        )
        val result = applied(listOf(row("prescriptions", "r", "levaxin", newName = "Levaxin", strength = "", form = "", oldDose = "1", oldUnit = "st", from = stored)), stored)
        assertEquals(mapOf("name" to "Levaxin"), result.fields("u1/prescriptions/r"))
        assertEquals(mapOf("name" to "Levaxin", "strength" to "50 mikrogram"), result.fields("u1/doses/d"))
    }

    @Test
    fun `en medicin vars styrka, form eller höjningar ändrats sedan förslaget är inaktuell`() {
        val rows = MedicineRename.suggest(export, catalog)
        fun changed(field: String, value: Any?) = export.map { if (it.path == "users/u1/prescriptions/r1") ExportFormat.Document(it.path, it.data + (field to value)) else it }
        for ((field, value) in listOf("strength" to "100 mikrogram", "form" to "capsule", "boosts" to listOf(boost("b1", "150", "mcg")))) {
            val result = applied(rows, changed(field, value))
            assertNull(result.fields("u1/prescriptions/r1"), field)
            assertNull(result.fields("u1/doses/d1"), "$field: dess doser rörs inte heller")
            assertEquals(1, result.stale, field)
        }
        assertEquals(0, applied(rows).stale)
    }

    @Test
    fun `fingeravtrycket beror på höjningarnas innehåll, inte på nycklarnas ordning`() {
        val a = listOf(mapOf("id" to "b1", "dose" to "1", "unit" to "mg"))
        val b = listOf(mapOf("unit" to "mg", "dose" to "1", "id" to "b1"))
        assertEquals(MedicineRename.fingerprint(a), MedicineRename.fingerprint(b))
        assertTrue(MedicineRename.fingerprint(a) != MedicineRename.fingerprint(listOf(mapOf("id" to "b1", "dose" to "2", "unit" to "mg"))))
        assertEquals("", MedicineRename.fingerprint(null))
        assertTrue(MedicineRename.fingerprint(emptyList<Any>()).isNotEmpty(), "en tom lista är inte samma som saknad")
    }

    @Test
    fun `en okopplad dos rörs bara när alla mediciner med dess namn får samma namn och styrka`() {
        val both = export + doc("u1/prescriptions/r5", "name" to "alvedon", "dose" to "1000", "unit" to "mg")
        fun d4(vararg rows: MedicineRename.Row) = applied(rows.toList(), both).let { it.fields("u1/doses/d4") to it.ambiguousDoses }
        val p1 = row("prnMedicines", "p1", "alvedon", newName = "Alvedon", from = both)
        assertEquals(null to 1, d4(p1), "r5 heter fortfarande alvedon")
        assertEquals(null to 1, d4(p1, row("prescriptions", "r5", "alvedon", newName = "Alvedon Novum")), "olika nya namn")
        assertEquals(null to 1, d4(p1, row("prescriptions", "r5", "alvedon", newName = "Alvedon", strength = "1 g")), "olika styrka")
        assertEquals(null to 1, d4(p1, row("prescriptions", "r5", "alvedon", newName = "Alvedon", oldDose = "999")), "r5:s rad är inaktuell")
        assertEquals(mapOf("name" to "Alvedon", "strength" to "500 mg") to 0, d4(p1, row("prescriptions", "r5", "alvedon", newName = "Alvedon")))
    }

    @Test
    fun `ett ändrat fält som bryter mot rules stoppar med sökväg och fält, aldrig värdet`() {
        val long = "x".repeat(TextLimits.SHORT + 1)
        val error = assertFailsWith<IllegalArgumentException> {
            applied(listOf(row("prnMedicines", "p1", "alvedon", newName = "Alvedon", strength = long)))
        }.message!!
        assertTrue("users/u1/prnMedicines/p1" in error && "strength" in error && long !in error, error)
    }

    // ---- Kommandoraden ----

    @Test
    fun `kommandoraden - förslag och ändringsfil för import --update, stdout bara antal`() {
        val dir = Files.createTempDirectory("match").toFile()
        val input = dir.resolve("export.json").apply { writeText(ExportFormat.encode(ts, 1, export)) }
        val tsv = dir.resolve("medicines.tsv").apply { writeText("Alvedon\t500 mg\tFilmdragerad tablett\nLevaxin\t100 mikrogram\tTablett") }
        val map = dir.resolve("map.tsv")
        val out = dir.resolve("renamed.json")
        val (suggestCode, suggestOut) = cli("--in", input.path, "--catalog", tsv.path, "--suggest", map.path)
        assertEquals(MatchMedicinesCli.EXIT_OK, suggestCode)
        assertEquals(6, MedicineRename.parseMap(map.readText()).size)
        val (applyCode, applyOut) = cli("--in", input.path, "--map", map.path, "--out", out.path)
        assertEquals(MatchMedicinesCli.EXIT_OK, applyCode)
        val text = out.readText()
        assertFalse("\"${ExportFormat.DOCUMENTS}\"" in text, "ingen vanlig export – en vanlig import skulle skriva de ofullständiga dokumenten hela")
        val written = updatesOf(text)
        assertTrue(written.isNotEmpty() && written.all { it.path in byPath })
        assertEquals(mapOf("name" to "Levaxin", "strength" to "100 mikrogram"), written.single { it.path == "users/u1/doses/d1" }.data)
        assertTrue(text.contains("\"exportedAt\": \"2026-09-21T08:12:45.000Z\""))
        assertTrue("--update" in applyOut)
        for (output in listOf(suggestOut, applyOut)) {
            assertTrue(listOf("alvedon", "levaxin", "metformin", "bricanyl").none { it in output.lowercase() }, "inga namn på stdout: $output")
        }
        assertEquals(MatchMedicinesCli.EXIT_USAGE, cli("--in", input.path, "--map", map.path, "--out", out.path).first, "utfilen finns")
        assertEquals(MatchMedicinesCli.EXIT_OK, cli("--in", input.path, "--map", map.path, "--out", out.path, "--force").first)
        assertEquals(MatchMedicinesCli.EXIT_USAGE, cli("--in", input.path, "--map", map.path).first)
        assertEquals(MatchMedicinesCli.EXIT_USAGE, cli("--in", input.path, "--catalog", tsv.path, "--map", map.path, "--out", out.path).first)
        assertEquals(MatchMedicinesCli.EXIT_USAGE, cli("--in", dir.resolve("saknas.json").path, "--map", map.path, "--out", dir.resolve("x.json").path).first)
        map.writeText("trasig")
        assertEquals(MatchMedicinesCli.EXIT_FAILED, cli("--in", input.path, "--map", map.path, "--out", out.path, "--force").first)
        dir.deleteRecursively()
    }

    private fun cli(vararg args: String): Pair<Int, String> {
        val out = ByteArrayOutputStream()
        val code = MatchMedicinesCli.run(arrayOf(*args), PrintStream(out, true, "UTF-8"), PrintStream(ByteArrayOutputStream()))
        return code to out.toString("UTF-8")
    }
}
