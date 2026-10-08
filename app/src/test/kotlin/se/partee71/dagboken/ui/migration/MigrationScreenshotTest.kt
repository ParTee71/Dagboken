package se.partee71.dagboken.ui.migration

import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.legacy.AccountCheck
import se.partee71.dagboken.data.legacy.CopyFailure
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureLightAndDarkPaused
import se.partee71.dagboken.ui.migration.MigrationSamples.before
import se.partee71.dagboken.ui.migration.MigrationSamples.halfway
import se.partee71.dagboken.ui.migration.MigrationSamples.review
import se.partee71.dagboken.ui.migration.MigrationSamples.saved

/**
 * Migreringsskärmen i ljust och mörkt, ett läge per tavla i mockupen (avsnitt 15 och 15b; NFR-20: bredvid mockupen i
 * PR:en). Granskningen, skrivningen och klar-läget är långa och tas med hela höjden. Påhittad data.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private fun capture(name: String, stage: MigrationStage) = rule.captureLightAndDark("Migration_$name") { MigrationScreen(MigrationUiState(stage), {}) }

    @Test
    fun `Läser`() = rule.captureLightAndDarkPaused("Migration_laser") { MigrationScreen(MigrationUiState(MigrationStage.Reading), {}) }

    @Test
    @Config(qualifiers = TALL)
    fun `Kopia saknas`() = capture("kopiaSaknas", review())

    @Test
    @Config(qualifiers = TALL)
    fun `Kopia kontrolleras`() = rule.captureLightAndDarkPaused("Migration_kopiaKontrollerar") {
        MigrationScreen(MigrationUiState(review(CopyStep.Checking)), {})
    }

    @Test
    @Config(qualifiers = TALL)
    fun `Kopia klar`() = capture("kopiaKlar", review(saved))

    @Test
    @Config(qualifiers = TALL)
    fun `Kopia fel`() = capture("kopiaFel", review(CopyStep.Failed(CopyFailure.COUNT_MISMATCH)))

    @Test
    @Config(qualifiers = TALL)
    fun `Kopia inaktuell och annat konto än 3x`() = capture("kopiaInaktuell", review(CopyStep.Stale(LocalDate(2026, 10, 5)), AccountCheck.DIFFERENT))

    @Test
    @Config(qualifiers = TALL)
    fun `Skriver`() = capture("skriver", MigrationStage.Writing(before, halfway))

    @Test
    @Config(qualifiers = TALL)
    fun `Klar`() = capture("klar", MigrationStage.Written(before, before))

    @Test
    @Config(qualifiers = TALL)
    fun `Kopia klar och okänt konto i 3x - kryssa i kontot`() = capture("kontoOkant", review(saved, AccountCheck.UNKNOWN))

    @Test
    @Config(qualifiers = TALL)
    fun `Skriver - läser läget på servern`() = capture("skriverLaserServern", MigrationStage.Writing(before, null))

    @Test
    @Config(qualifiers = TALL)
    fun `Kopia klar och flytten har börjat - Avbryt flytten i stället för Inte nu`() = capture("kopiaKlarPaborjad", review(saved).copy(started = true))

    @Test
    fun `Avbryter flytten`() = rule.captureLightAndDarkPaused("Migration_avbryter") { MigrationScreen(MigrationUiState(MigrationStage.Aborting), {}) }

    @Test
    @Config(qualifiers = TALL)
    fun `Klar med poster som fanns redan`() = capture(
        "klarFannsRedan",
        MigrationStage.Written(before, before + ("settings" to 0), existing = mapOf("settings" to 1)),
    )

    @Test
    fun `Avvikelse`() = capture("avvikelse", MigrationStage.Mismatch(before, before + ("doses" to 3902), mapOf("doses" to 2)))

    @Test
    fun `Avbruten`() = capture("avbruten", MigrationStage.WriteFailed(DataError.QuotaExceeded, 3, "RESOURCE_EXHAUSTED"))

    @Test
    fun `Stopp`() = capture("stopp", MigrationSamples.stopped)

    @Test
    fun `Äldre version`() = capture("version", MigrationStage.WrongVersion(10))

    @Test
    fun `Oläsbar`() = capture("olasbar", MigrationStage.ReadFailed(null))

    @Test
    fun `Offline`() = capture("offline", MigrationStage.CheckFailed(DataError.Offline))

    private companion object {
        const val TALL = "w390dp-h1700dp-xxhdpi"
    }
}
