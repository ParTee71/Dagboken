package se.partee71.dagboken.consistency

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

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

    @Test
    fun `mönsterfilen har alla scope och en ersättning per mönster`() {
        assertEquals(setOf("feature", "firestore", "expressive"), patterns.keys)
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
                    "\nUndantag bara via ui-allowlist.txt med motivering och användarens ok.",
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
                vm.properties().any { it.type?.name?.startsWith("EditorState") == true },
                "${vm.name} ska exponera en EditorState (ram EntityEditScreen)",
            )
        }
    }

    @Test
    fun `komponenttabellen i shared-ui-components stämmer med ui-components`() {
        val inCode = publicComponents("Dokumentdrift")
        val inTable = documentedComponents()
        val missingInTable = inCode - inTable
        assertTrue(missingInTable.isEmpty(), "Lägg till raden i shared-ui-components: $missingInTable")
        val missingInCode = inTable - inCode
        assertTrue(missingInCode.isEmpty(), "Ta bort raden i shared-ui-components eller bygg komponenten: $missingInCode")
    }

    @Test
    fun `varje publik komponent har skärmdump ljust och mörkt och plats i ComponentGallery`() {
        val components = publicComponents("Skärmdump/galleri") - "ComponentGallery"
        val screenshots = File(repoRoot, "app/src/test/screenshots").list().orEmpty().toSet()
        val gallery = galleryText()
        val problems = components.flatMap { name ->
            listOfNotNull(
                "$name saknar skärmdump ${name}_*_light.png".takeIf { screenshots.none { it.startsWith("${name}_") && it.endsWith("_light.png") } },
                "$name saknar skärmdump ${name}_*_dark.png".takeIf { screenshots.none { it.startsWith("${name}_") && it.endsWith("_dark.png") } },
                "$name saknas i ComponentGallery".takeIf { !Regex("""\b$name\s*[({<]""").containsMatchIn(gallery) },
            )
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private fun galleryText(): String =
        checkNotNull(production.files.firstOrNull { it.name == "ComponentGallery" }?.text) { "ComponentGallery saknas i ui/components" }

    /** Publika composables i `ui.components`; hoppar över testet (med orsak i loggen) så länge det inte finns några. */
    private fun publicComponents(check: String): Set<String> {
        val names = production.functions()
            .filter { it.resideInPackage("..ui.components..") && it.hasPublicOrDefaultModifier }
            .filter { it.hasAnnotationWithName("Composable") }
            .map { it.name }
            .toSet()
        val reason = "$check: hoppar över – ui.components saknar publika composables"
        if (names.isEmpty()) println(reason)
        assumeTrue(reason, names.isNotEmpty())
        return names
    }

    /**
     * Komponentnamnen i kolumn två i tabellerna "Utseende" och "Beteende". Hoppar över testet (med
     * orsak i loggen) så länge skillen saknar tabellerna – den skrivs om för Dagboken 4.0 i etapp 1
     * i samma PR som skelettet; därefter håller testet tabellen och koden lika.
     */
    private fun documentedComponents(): Set<String> {
        val skill = File(repoRoot, ".claude/skills/shared-ui-components/SKILL.md").readText()
        val reason = "Dokumentdrift: hoppar över – shared-ui-components saknar tabellerna \"Utseende\" och \"Beteende\""
        val hasTables = Regex("""^## (Utseende|Beteende)""", RegexOption.MULTILINE).containsMatchIn(skill)
        if (!hasTables) println(reason)
        assumeTrue(reason, hasTables)
        val tables = Regex("""## (Utseende|Beteende)[^\n]*\n(.*?)(?=\n## )""", RegexOption.DOT_MATCHES_ALL)
            .findAll(skill)
            .joinToString("\n") { it.groupValues[2] }
        return tables.lines()
            .filter { it.startsWith("|") }
            .mapNotNull { row -> row.split("|").getOrNull(2)?.let { Regex("`(\\w+)`").find(it)?.groupValues?.get(1) } }
            .toSet()
    }
}
