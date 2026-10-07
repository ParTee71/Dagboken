package se.partee71.dagboken.data.health

import app.cash.turbine.test
import com.lemonappdev.konsist.api.Konsist
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test
import se.partee71.dagboken.core.model.DailyHealth

/**
 * Klockdatans kontrakt (HLS-5, HLS-12): standardbindningen ger luckor och läget "saknas", fejken fyller
 * perioden dygn för dygn – och **ingen** hälsodata persisteras: paketet `data/health` rör aldrig datalagret,
 * gränssnittet har inga skrivningar, och modellerna har ingen codec, samling eller plats i exporten.
 */
class HealthRepositoryTest {

    private val from = LocalDate(2026, 10, 1)
    private val to = LocalDate(2026, 10, 3)

    @Test
    fun `utan Health Connect är klockan otillgänglig och varje dygn en lucka`() = runTest {
        val repository = UnavailableHealthRepository()
        repository.status.test {
            assertEquals(HealthStatus.UNAVAILABLE, awaitItem())
            awaitComplete()
        }
        val history = repository.history(from, to).getOrThrow()
        assertEquals(listOf(from, LocalDate(2026, 10, 2), to), history.dates)
        assertFalse(history.hasAnyData)
        assertTrue(repository.day(to).getOrThrow().isEmpty)
    }

    @Test
    fun `fejken ger ett dygn per datum, mätningarna på sina dagar och räknar läsningarna`() = runTest {
        val fake = FakeHealthRepository(measured = mutableMapOf(to to DailyHealth(to, steps = 6_500, restingHeartRate = 58)))
        val history = fake.history(from, to).getOrThrow()
        assertEquals(listOf(null, null, 6_500f), history.series { it.steps })
        assertEquals(58L, fake.day(to).getOrThrow().restingHeartRate)
        assertEquals<List<ClosedRange<LocalDate>>>(listOf(from..to, to..to), fake.reads)

        fake.failure = IllegalStateException("klockan svarar inte")
        assertTrue(fake.history(from, to).isFailure)
        fake.status.value = HealthStatus.PERMISSIONS_MISSING
        fake.status.test { assertEquals(HealthStatus.PERMISSIONS_MISSING, awaitItem()) }
    }

    // ---- HLS-5: aldrig persisterad ----

    private val repoRoot = File("").absoluteFile.let { if (it.name == "app") it.parentFile else it }
    private val production = Konsist.scopeFromProduction(moduleName = "app", sourceSetName = "main")

    @Test
    fun `gränssnittet har bara läsningar och paketet rör inte datalagret`() {
        val health = production.files.filter { it.packagee?.name?.contains("data.health") == true }
        assertTrue(health.isNotEmpty(), "data/health saknas")
        val forbidden = listOf("data.common.EntityCollection", "data.common.CollectionFactory", "data.firestore", "FirebaseFirestore", "data.repository", "core.schema")
        health.forEach { file ->
            file.imports.forEach { import -> assertTrue(forbidden.none { it in import.name }, "${file.name} importerar ${import.name}") }
        }
        val repository = production.interfaces().first { it.name == "HealthRepository" }
        val writes = Regex("^(save|upsert|write|set|update|delete|merge|store|persist|put|add|remove)", RegexOption.IGNORE_CASE)
        repository.functions().forEach { assertFalse(writes.containsMatchIn(it.name), "HealthRepository.${it.name} ser ut som en skrivning (HLS-5)") }
    }

    @Test
    fun `hälsomodellerna har ingen codec, ingen samling och ingen plats i exporten`() {
        val models = Regex("\\b(DailyHealth|SleepStages|HealthHistory)\\b")
        val persisting = listOf(
            File(repoRoot, "core/src/main/kotlin/se/partee71/dagboken/core/schema"),
            File(repoRoot, "core/src/main/kotlin/se/partee71/dagboken/core/legacy"),
            File(repoRoot, "app/src/main/kotlin/se/partee71/dagboken/data/firestore"),
            File(repoRoot, "app/src/main/kotlin/se/partee71/dagboken/data/repository"),
            File(repoRoot, "tools/db/lib"),
            File(repoRoot, "firestore.rules"),
        )
        val hits = persisting.flatMap { root -> root.walk().filter { it.isFile }.filter { models.containsMatchIn(it.readText()) }.map { it.relativeTo(repoRoot).path }.toList() }
        assertEquals(emptyList(), hits, "hälsodata får inte ha en väg till Firestore eller exporten (HLS-5)")
    }
}
