package se.partee71.dagboken.data.health

import com.lemonappdev.konsist.api.Konsist
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import se.partee71.dagboken.core.engine.health.HealthRecords
import se.partee71.dagboken.core.engine.health.HeartRateSample
import se.partee71.dagboken.core.engine.health.StepSample
import se.partee71.dagboken.data.FixedClock
import kotlin.time.Duration.Companion.hours

/**
 * Inget klockvärde loggas eller sparas (HLS-5, NFR-13): Health Connect-porten läser, räknar och lämnar ut – men
 * ingenting i `data/health` loggar, och en läsning med fel lämnar inget värde i loggen. Att paketet inte rör
 * datalagret (Firestore, exporten) bevakar `HealthRepositoryTest`.
 */
@RunWith(RobolectricTestRunner::class)
class HealthConnectPrivacyTest {

    @Test
    fun `porten loggar ingenting`() {
        val logging = Regex("""\b(Log\.[vdiwe]|println|printStackTrace|Timber)\b""")
        val health = Konsist.scopeFromProduction(moduleName = "app", sourceSetName = "main").files
            .filter { it.packagee?.name?.endsWith("data.health") == true }
        assertTrue(health.any { it.name == "HealthConnectSource" }, "källan saknas i data/health")
        health.forEach { file -> assertTrue(!logging.containsMatchIn(file.text), "${file.name} loggar (HLS-5, NFR-13)") }
    }

    @Test
    fun `läsningar och fel lämnar inga värden i loggen`() = runTest {
        ShadowLog.reset()
        val zone = TimeZone.of("Europe/Stockholm")
        val date = LocalDate(2026, 10, 7)
        val start = date.atStartOfDayIn(zone)
        val source = FakeHealthConnectSource().apply {
            granted = HealthPermissionSet.ALL
            records = HealthRecords(
                steps = listOf(StepSample("klocka", start + 8.hours, start + 9.hours, 73_919)),
                heartRate = listOf(HeartRateSample("klocka", start + 10.hours, 47)),
            )
        }
        val repository = HealthConnectRepository(source, HealthConnectAccess(source, backgroundScope), FixedClock(start + 12.hours)) { zone }
        assertEquals(73_919L, repository.day(date).getOrThrow().steps)
        repository.history(date, date).getOrThrow()
        source.failure = IllegalStateException("steg 73919")
        assertTrue(repository.day(date).isFailure)

        val logged = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg} ${it.throwable}" }
        listOf("73919", "73 919").forEach { value -> assertTrue(value !in logged, "klockvärdet $value finns i loggen") }
    }
}
