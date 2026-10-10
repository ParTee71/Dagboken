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
        doc("u2/doses/d7", "name" to "alvedon", "dose" to "500", "unit" to "mg"),
        doc("u1/illnessEpisodes/e1/checkins/c1", "name" to "alvedon"),
    )

    private val byPath = export.associateBy { it.path }

    // ---- Förslaget ----

    @Test
    fun `förslaget - första träffen, annars första ordet, och dosen räknas om bara när styrkans enhet är dosens`() {
        val rows = MedicineRename.suggest(export, catalog)
        assertEquals(
            listOf(
                MedicineRename.Row("prescriptions", "r1", "levaxin", "Levaxin", "100 mikrogram", "tablet", "200", "mcg", "2", "tablett", 2),
                MedicineRename.Row("prescriptions", "r2", "Metformin", "Metformin Teva", "500 mg", "tablet", "750", "mg", "1,5", "tablett", 0),
                MedicineRename.Row("prnMedicines", "p1", "alvedon", "Alvedon", "500 mg", "tablet", "1000", "mg", "2", "tablett", 2),
                MedicineRename.Row("prnMedicines", "p2", "Okänt preparat", oldDose = "1", oldUnit = "st"),
                MedicineRename.Row("prnMedicines", "p3", "Bricanyl", "Bricanyl Turbuhaler", "0,5 mg/dos", "inhaler", "2", "st", "2", "st", 0),
            ),
            rows,
        )
        // "Okänt" ger ingen träff; ett namn med fler ord prövas på första ordet.
        val firstWord = MedicineRename.suggest(listOf(doc("u1/prnMedicines/p9", "name" to "Alvedon brus", "dose" to "500", "unit" to "mg")), catalog)
        assertEquals("Alvedon", firstWord.single().newName)
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
    fun `kartan skrivs med rubrikrad och läses tillbaka likadant`() {
        val rows = MedicineRename.suggest(export, catalog)
        val text = MedicineRename.renderMap(rows)
        assertEquals(MedicineRename.HEADER.joinToString("\t"), text.lines().first())
        assertEquals(rows.size + 1, text.lines().count { it.isNotEmpty() })
        assertEquals(rows, MedicineRename.parseMap(text))
        assertEquals(rows, MedicineRename.parseMap(text.replace("\n", "\r\n")), "rättad i ett Windows-program")
    }

    @Test
    fun `en trasig karta stoppar med radnummer, aldrig innehåll`() {
        val header = MedicineRename.HEADER.joinToString("\t")
        fun error(vararg lines: String) = assertFailsWith<IllegalArgumentException> { MedicineRename.parseMap(lines.joinToString("\n")) }.message!!
        assertTrue("rad 1" in error("namn\tnytt"))
        assertTrue(error(header, "prnMedicines\tp1\tAlvedon").let { "rad 2" in it && "Alvedon" !in it })
        assertTrue("okänd samling" in error(header, "doses\td1\tA\tB\t\t\t1\tmg\t1\tmg\t0"))
        assertTrue(error(header, "prnMedicines\tp1\tAlvedon\tAlvedon\t500 mg\tspray\t1\tmg\t1\tmg\t0").let { "okänd form" in it && "spray" !in it })
        assertTrue("inte ett tal" in error(header, "prnMedicines\tp1\tAlvedon\tAlvedon\t500 mg\ttablet\t1\tmg\t1\tmg\tx"))
    }

    // ---- Tillämpningen ----

    @Test
    fun `bara mappade dokument kommer ut, och alla andra och okända fält är oförändrade`() {
        val applied = MedicineRename.apply(export, MedicineRename.suggest(export, catalog))
        assertEquals(
            listOf(
                "users/u1/prescriptions/r1", "users/u1/prescriptions/r2", "users/u1/prnMedicines/p1", "users/u1/prnMedicines/p3",
                "users/u1/doses/d1", "users/u1/doses/recept_r1_2026-09-21_Morgon", "users/u1/doses/d3", "users/u1/doses/d4",
            ),
            applied.documents.map { it.path },
        )
        val medicineFields = setOf("name", "strength", "form", "dose", "unit", "boosts")
        for (out in applied.documents) {
            val before = byPath.getValue(out.path).data
            val allowed = if ("/doses/" in out.path) setOf("name") else medicineFields
            assertEquals(json(before - allowed), json(out.data - allowed), out.path)
            assertTrue(out.data.keys.containsAll(before.keys), "${out.path} tappar inga fält")
        }
        assertEquals(listOf(4, 4, 1, 0, 0, 0), with(applied) { listOf(medicines, doses, skipped, stale, doseKept, ambiguousDoses) })
    }

    @Test
    fun `medicinen får namn, styrka, form och omräknad dos - receptets höjningar räknas om med samma faktor`() {
        val out = MedicineRename.apply(export, MedicineRename.suggest(export, catalog)).documents.associateBy { it.path }
        val r1 = out.getValue("users/u1/prescriptions/r1").data
        assertEquals(listOf("Levaxin", "100 mikrogram", "tablet", "2", "tablett"), listOf(r1["name"], r1["strength"], r1["form"], r1["dose"], r1["unit"]))
        assertEquals(listOf(boost("b1", "1", "tablett")), r1["boosts"])
        val r2 = out.getValue("users/u1/prescriptions/r2").data
        assertEquals(listOf("1,5", "tablett", listOf(boost("b2", "0,5", "tablett"))), listOf(r2["dose"], r2["unit"], r2["boosts"]))
        val p3 = out.getValue("users/u1/prnMedicines/p3").data
        assertEquals(listOf("Bricanyl Turbuhaler", "0,5 mg/dos", "inhaler", "2", "st"), listOf(p3["name"], p3["strength"], p3["form"], p3["dose"], p3["unit"]))
    }

    @Test
    fun `doserna får bara nytt namn - kopplade via recept, receptdosens id eller prnId, och okopplade med samma namn`() {
        val out = MedicineRename.apply(export, MedicineRename.suggest(export, catalog)).documents.associateBy { it.path }
        assertEquals("Levaxin", out.getValue("users/u1/doses/d1").data["name"])
        assertEquals("Levaxin", out.getValue("users/u1/doses/recept_r1_2026-09-21_Morgon").data["name"])
        val renamed = listOf(row("prnMedicines", "p1", "alvedon", newName = "Alvedon Novum"))
        val doses = MedicineRename.apply(export, renamed).documents.map { it.path }
        // d6 hör till ett annat recept, d5 heter något annat, d7 är en annan användare, c1 är ingen dos.
        assertEquals(listOf("users/u1/prnMedicines/p1", "users/u1/doses/d3", "users/u1/doses/d4"), doses)
        val d4 = MedicineRename.apply(export, renamed).documents.single { it.path.endsWith("/d4") }.data
        assertEquals(json(byPath.getValue("users/u1/doses/d4").data + ("name" to "Alvedon Novum")), json(d4))
        assertFalse("strength" in d4, "dosen får inte styrkan")
    }

    @Test
    fun `går en höjning inte jämnt ut lämnas receptets dos, enhet och höjningar orörda`() {
        val third = row("prescriptions", "r2", "Metformin", newName = "Metformin Teva", newDose = "1", newUnit = "tablett", oldDose = "750", oldUnit = "mg")
        val applied = MedicineRename.apply(export, listOf(third))
        val r2 = applied.documents.single().data
        assertEquals(listOf("Metformin Teva", "750", "mg"), listOf(r2["name"], r2["dose"], r2["unit"]))
        assertEquals(byPath.getValue("users/u1/prescriptions/r2").data["boosts"], r2["boosts"])
        assertEquals(1, applied.doseKept)
        // Utan höjningar byts dosen även när den inte är ett tal.
        val noBoosts = listOf(doc("u1/prescriptions/r9", "name" to "D-vitamin", "dose" to "1 tablett", "unit" to "st", "boosts" to emptyList<Any>()))
        val d = MedicineRename.apply(noBoosts, listOf(row("prescriptions", "r9", "D-vitamin", newName = "D-vitamin", newDose = "1", newUnit = "tablett", oldDose = "1 tablett", oldUnit = "st")))
        assertEquals(listOf("1", "tablett", emptyList<Any>()), d.documents.single().data.let { listOf(it["dose"], it["unit"], it["boosts"]) })
        // Oförändrad dos och enhet (eller tomma nya) rör varken dos eller höjningar.
        val same = MedicineRename.apply(export, listOf(row("prescriptions", "r2", "Metformin", newName = "Metformin Teva", oldDose = "750", oldUnit = "mg")))
        assertEquals(listOf("750", "mg"), same.documents.single().data.let { listOf(it["dose"], it["unit"]) })
    }

    @Test
    fun `tomt nytt namn hoppas över, en saknad eller ändrad medicin är inaktuell`() {
        val applied = MedicineRename.apply(
            export,
            listOf(
                row("prnMedicines", "p1", "alvedon"),
                row("prnMedicines", "p1", "Alvedon forte", newName = "Alvedon"),
                row("prnMedicines", "saknas", "alvedon", newName = "Alvedon"),
                row("prnMedicines", "p1", "alvedon", newName = "Alvedon", oldDose = "500"),
            ),
        )
        assertEquals(emptyList(), applied.documents)
        assertEquals(listOf(1, 3), listOf(applied.skipped, applied.stale))
    }

    @Test
    fun `en okopplad dos vars namn får olika nya namn lämnas orörd`() {
        val both = export + doc("u1/prescriptions/r5", "name" to "alvedon", "dose" to "1000", "unit" to "mg")
        val applied = MedicineRename.apply(
            both,
            listOf(
                row("prnMedicines", "p1", "alvedon", newName = "Alvedon"),
                row("prescriptions", "r5", "alvedon", newName = "Alvedon Novum", oldDose = "1000"),
            ),
        )
        assertFalse(applied.documents.any { it.path.endsWith("/d4") })
        assertEquals(1, applied.ambiguousDoses)
    }

    @Test
    fun `ett ändrat fält som bryter mot rules stoppar med sökväg och fält, aldrig värdet`() {
        val long = "x".repeat(TextLimits.SHORT + 1)
        val error = assertFailsWith<IllegalArgumentException> {
            MedicineRename.apply(export, listOf(row("prnMedicines", "p1", "alvedon", newName = "Alvedon", strength = long)))
        }.message!!
        assertTrue("users/u1/prnMedicines/p1" in error && "strength" in error && long !in error, error)
    }

    // ---- Kommandoraden ----

    @Test
    fun `kommandoraden - förslag och tillämpning på en exportfil, stdout bara antal`() {
        val dir = Files.createTempDirectory("match").toFile()
        val input = dir.resolve("export.json").apply { writeText(ExportFormat.encode(ts, 1, export)) }
        val tsv = dir.resolve("medicines.tsv").apply { writeText("Alvedon\t500 mg\tFilmdragerad tablett\nLevaxin\t100 mikrogram\tTablett") }
        val map = dir.resolve("map.tsv")
        val out = dir.resolve("renamed.json")
        val (suggestCode, suggestOut) = cli("--in", input.path, "--catalog", tsv.path, "--suggest", map.path)
        assertEquals(MatchMedicinesCli.EXIT_OK, suggestCode)
        assertEquals(5, MedicineRename.parseMap(map.readText()).size)
        val (applyCode, applyOut) = cli("--in", input.path, "--map", map.path, "--out", out.path)
        assertEquals(MatchMedicinesCli.EXIT_OK, applyCode)
        val written = ExportFormat.decode(out.readText())
        assertTrue(written.isNotEmpty() && written.all { it.path in byPath })
        assertTrue(out.readText().contains("\"exportedAt\": \"2026-09-21T08:12:45.000Z\""))
        for (text in listOf(suggestOut, applyOut)) {
            assertTrue(listOf("alvedon", "levaxin", "metformin", "bricanyl").none { it in text.lowercase() }, "inga namn på stdout: $text")
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
    ) = MedicineRename.Row(collection, id, oldName, newName, strength, "tablet", oldDose, oldUnit, newDose, newUnit, 0)

    private fun json(doc: Doc) = ExportFormat.toJson(doc)
}
