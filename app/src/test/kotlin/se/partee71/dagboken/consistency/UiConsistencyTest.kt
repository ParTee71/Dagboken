package se.partee71.dagboken.consistency

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.Test

/**
 * Regel 4 i bygget (skill shared-ui-components). Mönstren står bara i `ui-forbidden.txt`,
 * undantagen bara i `ui-allowlist.txt` – samma filer som hooken `regel4-check` läser.
 */
class UiConsistencyTest {

    private val repoRoot = File("").absoluteFile.let { if (it.name == "app") it.parentFile else it }
    private val patterns = Regel4.parsePatterns(Regel4.readResource("ui-forbidden.txt"))

    // Lat, så att en trasig rad ger ett tydligt fel i testet i stället för i klassens konstruktor.
    private val allowlist by lazy { Regel4.parseAllowlist(Regel4.readResource("ui-allowlist.txt")) }
    private val examples = Regel4.parseSections(Regel4.readResource("ui-forbidden-examples.txt"))
    private val production = Konsist.scopeFromProduction(moduleName = "app", sourceSetName = "main")

    private val featureFile = "app/src/main/kotlin/se/partee71/dagboken/ui/today/TodayRow.kt"
    private val componentFile = "app/src/main/kotlin/se/partee71/dagboken/ui/components/X.kt"
    private val diagramFile = "app/src/main/kotlin/se/partee71/dagboken/ui/diagram/X.kt"

    @Test
    fun `mönsterfilen har alla scope och en ersättning per mönster`() {
        assertEquals(setOf("feature", "firestore", "expressive", "vico", "dispatchers", "events"), patterns.keys)
        patterns.values.flatten().forEach { assertTrue(it.replacement.isNotBlank(), it.regex.pattern) }
    }

    @Test
    fun `ingen kod i app-src-main använder förbjudna mönster`() {
        val hits = production.files.flatMap { file ->
            val rel = File(file.path).relativeTo(repoRoot).invariantSeparatorsPath
            Regel4.findViolations(rel, file.text, patterns, allowlist).map { hit ->
                "  $rel:${hit.line}  `${hit.match.trim()}`  → använd ${hit.replacement}"
            }
        }
        if (hits.isNotEmpty()) {
            fail(
                "Regel 4: förbjudna mönster (skill shared-ui-components).\n" + hits.joinToString("\n") +
                    "\nUndantag bara via ui-allowlist.txt med motivering och användarens ok."
            )
        }
    }

    @Test
    fun `skärmar stänger sig med popIfTop, aldrig pop (NAV-11)`() {
        // Bara back stacken själv och systemets bakåt i AppNavHost går tillbaka utan nyckel.
        val allowed = setOf("AppBackStack.kt", "AppNavHost.kt")
        val hits = production.files
            .filter { File(it.path).name !in allowed && Regex("\\.pop\\(\\)").containsMatchIn(it.text) }
            .map { File(it.path).relativeTo(repoRoot).invariantSeparatorsPath }
        assertEquals(emptyList(), hits, "använd backStack.popIfTop(key) – ett andra anrop får inte stänga skärmen under")
    }

    @Test
    fun `ramarna för textinmatning lägger tangentbordets inset på den scrollande ytan (NFR-11)`() {
        // På synfältet krymper tangentbordet den scrollande ytan, så att det fokuserade fältet scrollas fram ovanför
        // det. Efter verticalScroll blir insetet bara utfyllnad i innehållet, och ett fält längst ned kan stå kvar under
        // tangentbordet. Kontrollen gäller modifier-kedjan i koden (kommentarer strippade), inte bara radordningen.
        // Ramarna kommer från android-ui (*(android-ui)* i shared-ui-components), som prövar kedjan i sin egen kod;
        // här prövas en ram som appen har själv – en lokal kopia av en bibliotekets ram får samma krav.
        val chains = mapOf(
            // Samma kedja: imePadding() direkt före verticalScroll.
            "EntityEditScreen" to Regex("""\.imePadding\(\)\s*\.\s*verticalScroll\("""),
            // Knappradsvarianten: ytan med imePadding() omsluter direkt den scrollande kolumnen.
            "AppBottomSheet" to Regex("""\.imePadding\(\)\s*\)\s*\{\s*Column\(\s*Modifier[^{}]*?\.verticalScroll\(""")
        )
        // verticalScroll(…) följt av imePadding() senare i samma kedja, också efter andra led: `.verticalScroll(s).padding(x).imePadding()`.
        val call = """\((?:[^()]|\([^()]*\))*\)"""
        val afterScroll = Regex("""verticalScroll$call(?:\s*\.\s*\w+$call)*\s*\.\s*imePadding\(""")
        val fromLibrary = libraryRows()
        for ((frame, chain) in chains) {
            val file = production.files.firstOrNull { it.name == frame && it.packagee?.name?.endsWith("ui.components") == true }
            if (file == null) {
                assertTrue(frame in fromLibrary, "$frame saknas i ui/components och är inte markerad *(android-ui)* i shared-ui-components")
                continue
            }
            val text = stripComments(file.text)
            assertTrue(chain.containsMatchIn(text), "$frame: imePadding() ska ligga på den scrollande ytans synfält, före verticalScroll")
            assertFalse(afterScroll.containsMatchIn(text), "$frame: imePadding() efter verticalScroll är utfyllnad i innehållet")
        }
    }

    @Test
    fun `komponenterna från android-ui anropas med Dagbokens parametrar`() {
        // Där bibliotekets standard skiljer sig från Dagboken anges Dagbokens värde i varje anrop (skill shared-ui-components).
        val required = mapOf(
            "QuantityStepper" to "buttonSize = QuantityStepperDefaults.TouchTargetButtonSize", // 48 dp (NFR-14)
            "EntityEditScreen" to "horizontalMargin = SCREEN_MARGIN",
            "EntityDetailScreen" to "sectionGap = SECTION_GAP",
            "PausableRow" to "pillTone = Tone.Neutral"
        )
        val problems = required.flatMap { (component, argument) ->
            val call = Regex("""(?<![\w.])$component\(""")
            val calls = production.files.flatMap { file ->
                val text = stripComments(file.text)
                call.findAll(text).map { file.name to argumentsOf(text, it.range.last) }.toList()
            }
            if (calls.isEmpty()) {
                listOf("hittade inga anrop till $component")
            } else {
                calls.filterNot { (_, args) -> argument in args }.map { (name, _) -> "$name: $component($argument)" }
            }
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun `Trender är en ren läsvy – inga anrop till repositoryns skrivningar (TRD-4)`() {
        val writes = RepositoryWrites.writes
        assertTrue(setOf("save", "delete", "update", "add", "setArchived").all { it in writes }, "skrivningarna hittades inte: $writes")
        // Kontrollprov: listorna i inställningarna skriver – annars hittar skanningen ingenting alls.
        assertTrue(RepositoryWrites.callsIn("ui.settings").isNotEmpty(), "skanningen hittar inte skrivningarna i ui/settings")
        assertEquals(emptyList(), RepositoryWrites.callsIn("ui.trends"), "Trender skriver inte till någon datakälla (TRD-4)")
    }

    // De delade exemplen körs med tom allowlist utom sektionen "allowlist" – precis som i hooktestet.

    @Test
    fun `delade exempel - tillåtna och förbjudna rader tolkas likadant som i hooken`() {
        examples.getValue("ok").forEach { line ->
            assertEquals(emptyList(), Regel4.findViolations(featureFile, line, patterns, emptyList()), line)
        }
        examples.getValue("stopp").forEach { line ->
            assertTrue(Regel4.findViolations(featureFile, line, patterns, emptyList()).isNotEmpty(), line)
        }
        examples.getValue("komponent-ok").forEach { line ->
            assertEquals(emptyList(), Regel4.findViolations(componentFile, line, patterns, emptyList()), line)
        }
        examples.getValue("diagram-ok").forEach { line ->
            assertEquals(emptyList(), Regel4.findViolations(diagramFile, line, patterns, emptyList()), line)
        }
        examples.getValue("diagram-stopp").forEach { line ->
            assertTrue(Regel4.findViolations(componentFile, line, patterns, emptyList()).isNotEmpty(), line)
        }
    }

    @Test
    fun `delade exempel - scope per sökväg`() {
        examples.getValue("scope").forEach { line ->
            val parts = line.split("\t")
            val expected = parts.getOrElse(1) { "" }.split(",").filter { it.isNotEmpty() }
            assertEquals(expected, Regel4.scopesFor(parts[0]), parts[0])
        }
    }

    @Test
    fun `delade exempel - ett undantag gäller bara sin fil och sin symbol`() {
        examples.getValue("allowlist").forEach { example ->
            val (allowLine, code, expected) = example.split("\t")
            val hits = Regel4.findViolations(featureFile, code, patterns, Regel4.parseAllowlist(allowLine))
            assertEquals(expected, if (hits.isEmpty()) "ok" else "stopp", example)
        }
    }

    @Test
    fun `delade exempel - allowlist-rad utan symbol eller motivering avvisas`() {
        examples.getValue("allowlist-fel").forEach { line ->
            val error = assertFailsWith<IllegalArgumentException>(line) { Regel4.parseAllowlist(line) }
            assertTrue(error.message.orEmpty().startsWith("ui-allowlist.txt rad 1"), error.message)
        }
    }

    @Test
    fun `redigera-ViewModels exponerar EditorState`() {
        production.classes().withNameEndingWith("EditViewModel").forEach { vm ->
            assertTrue(
                vm.properties().any { it.type?.name?.startsWith("EditorState") == true || it.text.contains("= EntryEditor(") },
                "${vm.name} ska exponera en EditorState, direkt eller via sin EntryEditor (ram EntityEditScreen)"
            )
        }
    }

    @Test
    fun `UiState-typer är @Immutable eller @Stable`() {
        // En lista eller karta i tillståndet är ImmutableList/ImmutableMap; annoteringen gör hela typen stabil (skill compose-expert).
        val states = production.classes().filter { it.hasDataModifier && it.name.endsWith("State") && it.resideInPackage("..ui..") } +
            production.interfaces().withNameEndingWith("UiState").filter { it.resideInPackage("..ui..") }
        assertTrue(states.isNotEmpty(), "hittade inga UiState-typer i ui/")
        states.forEach { state ->
            assertTrue(state.hasAnnotationWithName("Immutable", "Stable"), "${state.name} ska vara @Immutable eller @Stable")
        }
    }

    @Test
    fun `komponenttabellen i shared-ui-components stämmer med ui-components, ui-diagram och android-ui`() {
        val inCode = publicComponents("Dokumentdrift")
        val inLibrary = libraryRows()
        val inTable = documentedComponents() - inLibrary
        val missingInTable = inCode - inTable - inLibrary
        assertTrue(missingInTable.isEmpty(), "Lägg till raden i shared-ui-components: $missingInTable")
        val missingInCode = inTable - inCode
        assertTrue(missingInCode.isEmpty(), "Ta bort raden i shared-ui-components eller bygg komponenten: $missingInCode")
        // En rad märkt *(android-ui)* är bibliotekets komponent: ingen lokal kopia i appen, och den finns i biblioteket.
        val copies = inLibrary intersect inCode
        assertTrue(copies.isEmpty(), "Lokal kopia av en komponent från android-ui – använd bibliotekets: $copies")
        val notInLibrary = inLibrary - LibraryComponents.names
        assertTrue(notInLibrary.isEmpty(), "Märkt *(android-ui)* men finns inte i biblioteket: $notInLibrary")
    }

    @Test
    fun `varje publik komponent har skärmdump ljust och mörkt och plats i ComponentGallery`() {
        // Också bibliotekets komponenter: skärmdumparna visar dem i Dagbokens tema, och galleriet visar dem.
        val components = publicComponents("Skärmdump/galleri") + libraryRows() - "ComponentGallery"
        val screenshots = File(repoRoot, "app/src/test/screenshots").list().orEmpty().toSet()
        val gallery = galleryText()
        val problems = components.flatMap { name ->
            listOfNotNull(
                "$name saknar skärmdump ${name}_*_light.png".takeIf { screenshots.none { it.startsWith("${name}_") && it.endsWith("_light.png") } },
                "$name saknar skärmdump ${name}_*_dark.png".takeIf { screenshots.none { it.startsWith("${name}_") && it.endsWith("_dark.png") } },
                "$name saknas i ComponentGallery".takeIf { !Regex("""\b$name\s*[({<]""").containsMatchIn(gallery) }
            )
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private fun galleryText(): String = checkNotNull(production.files.firstOrNull { it.name == "ComponentGallery" }?.text) { "ComponentGallery saknas i ui/components" }

    /**
     * Publika composables i de delade paketen `ui.components` och `ui.diagram`; inga alls betyder att
     * sökningen gått fel och fäller testet.
     */
    private fun publicComponents(checkName: String): Set<String> {
        val names = production.functions()
            .filter { (it.resideInPackage("..ui.components..") || it.resideInPackage("..ui.diagram..")) && it.hasPublicOrDefaultModifier }
            .filter { it.hasAnnotationWithName("Composable") }
            .map { it.name }
            .toSet()
        check(names.isNotEmpty()) { "$checkName: ui.components saknar publika composables" }
        return names
    }

    /** Komponentnamnen i kolumn två i tabellerna "Utseende" och "Beteende" (alla rader). */
    private fun documentedComponents(): Set<String> = tableRows().mapNotNull { componentOf(it) }.toSet()

    /** Raderna märkta *(android-ui)*: komponenten kommer från biblioteket android-ui, inte från appen. */
    private fun libraryRows(): Set<String> = tableRows().filter { LIBRARY_MARK in it }.mapNotNull { componentOf(it) }.toSet()

    private fun componentOf(row: String): String? = row.split("|").getOrNull(2)?.let { Regex("`(\\w+)`").find(it)?.groupValues?.get(1) }

    /**
     * Tabellraderna i "Utseende" och "Beteende". Saknas tabellerna fäller testet – annars skulle dokumentdriften
     * tyst sluta kontrolleras.
     */
    private fun tableRows(): List<String> {
        val skill = File(repoRoot, ".claude/skills/shared-ui-components/SKILL.md").readText()
        val hasTables = Regex("""^## (Utseende|Beteende)""", RegexOption.MULTILINE).findAll(skill).count() == 2
        check(hasTables) { "Dokumentdrift: shared-ui-components saknar tabellerna \"Utseende\" och \"Beteende\"" }
        val tables = Regex("""## (Utseende|Beteende)[^\n]*\n(.*?)(?=\n## )""", RegexOption.DOT_MATCHES_ALL)
            .findAll(skill)
            .joinToString("\n") { it.groupValues[2] }
        return tables.lines().filter { it.startsWith("|") }
    }

    /** Argumenten i anropet vars öppnande parentes står på [open] – till den matchande stängande. */
    private fun argumentsOf(text: String, open: Int): String {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return text.substring(open + 1, i)
            }
        }
        return text.substring(open + 1)
    }

    private companion object {
        const val LIBRARY_MARK = "*(android-ui)*"
    }
}
