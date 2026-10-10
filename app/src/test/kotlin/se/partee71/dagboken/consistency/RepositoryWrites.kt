package se.partee71.dagboken.consistency

import com.lemonappdev.konsist.api.Konsist

/**
 * Skrivningar mot datalagret, härledda ur koden – en gång för alla läsvyer (TRD-4 Trender, HLS-5 `ui/health`).
 *
 * - **Skrivmetoder ([writes]):** varje `suspend`-funktion i ett gränssnitt under `data` som heter `*Repository`,
 *   `EntryStore` eller `EntityCollection`, och varje top-level `suspend`-extension under `data` på ett av dem
 *   (`updateChanged`, `upsertPlaced` …) – utom läsningarna i [READS]. En ny skrivning fångas utan ändring här; en ny
 *   läsning som en läsvy behöver läggs till i [READS].
 * - **Anrop ([callsIn]):** ett skrivnamn som anrop (`.namn(`, `.namn {`) eller referens (`::namn`) på **vilken mottagare
 *   som helst** – också konkreta klasser och kedjor – efter att kommentarer strippats. Undantagna är bara kända
 *   minnesmottagare, så att `_state.update {` och `list.add(` inte ger falsklarm: namn som börjar med `_`, och namn som
 *   samma fil deklarerar med en föränderlig samling eller ett flöde i minnet ([IN_MEMORY], som typ eller som
 *   initierare). En mottagare som inte är ett namn (`foo().save(`) räknas alltid.
 */
object RepositoryWrites {

    /** Suspend-funktioner i gränssnitten och extensions som bara läser. */
    val READS = setOf(
        "get", "getAll", "getEpisode", "cached", "cachedBetween", "cachedDates", "confirmed", "confirmedFrom", "awaitWrites",
        "nextSortOrder", "checkinCount", "missingOn", "plan", "history", "day", "new", "catalog", "updated",
    )

    /** Typer och fabriker för minnesmottagare vars `.add(`/`.update {`/`.remove(` inte rör datalagret. */
    private const val IN_MEMORY =
        "MutableStateFlow|MutableSharedFlow|MutableList|MutableSet|MutableMap|MutableCollection|SnapshotStateList|" +
            "SnapshotStateMap|ArrayList|ArrayDeque|HashMap|HashSet|LinkedHashMap|LinkedHashSet|mutableListOf|" +
            "mutableSetOf|mutableMapOf|mutableStateListOf|mutableStateMapOf"

    private val production by lazy { Konsist.scopeFromProduction(moduleName = "app", sourceSetName = "main") }

    private val dataFiles by lazy { production.files.filter { it.packagee?.name?.contains(".data") == true } }

    private val interfaces by lazy {
        production.interfaces().filter {
            it.resideInPackage("..data..") && (it.name.endsWith("Repository") || it.name in setOf("EntryStore", "EntityCollection"))
        }
    }

    /** Skrivmetodernas namn: gränssnittens och extensionernas. */
    val writes: Set<String> by lazy {
        val types = interfaces.map { it.name }.toSet()
        val members = interfaces.flatMap { it.functions() }.filter { it.hasSuspendModifier }.map { it.name }
        val extension = Regex("""\bsuspend\s+fun\s+(?:<[^>]*>\s*)?(\w+)(?:<[^>]*>)?\.(\w+)\s*\(""")
        val extensions = dataFiles.flatMap { file ->
            extension.findAll(stripComments(file.text)).filter { it.groupValues[1] in types }.map { it.groupValues[2] }.toList()
        }
        (members + extensions).filter { it !in READS }.toSet()
    }

    /**
     * Skrivanropen i paketet som slutar på [packageSuffix] (t.ex. `ui.trends`) och dess underpaket, som
     * `Fil.kt:rad  anrop`. Inga filer fäller testet.
     */
    fun callsIn(packageSuffix: String): List<String> {
        val packages = production.files.mapNotNull { it.packagee?.name }.toSet()
        val bases = packages.filter { it == packageSuffix || it.endsWith(".$packageSuffix") }.toSet()
        val files = production.files.filter { file ->
            val name = file.packagee?.name ?: return@filter false
            bases.any { name == it || name.startsWith("$it.") }
        }
        check(files.isNotEmpty()) { "paketet $packageSuffix saknas" }
        val names = writes.joinToString("|")
        // Grupp 1 = mottagarens namn, om mottagaren är ett namn (inte ett anrop eller index).
        val call = Regex("""(?:\b(\w+)|[)\]])?\s*(?:\.\s*(?:$names)\s*[({]|::\s*(?:$names)\b)""")
        val inMemory = Regex("""\b(\w+)\s*(?::\s*(?:$IN_MEMORY)\b|(?::[^=\n]*)?=\s*(?:$IN_MEMORY)\b)""")
        return files.flatMap { file ->
            val text = stripComments(file.text)
            val memory = inMemory.findAll(text).map { it.groupValues[1] }.toSet()
            call.findAll(text)
                .filter { hit -> hit.groupValues[1].let { it.isEmpty() || (!it.startsWith("_") && it !in memory) } }
                .map { hit ->
                    val line = text.substring(0, hit.range.first).count { it == '\n' } + 1
                    "  ${file.name}.kt:$line  `${hit.value.trim().replace(Regex("\\s+"), "")}`"
                }
                .toList()
        }
    }
}
