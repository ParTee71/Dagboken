package se.partee71.dagboken.ui.migration

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import se.partee71.dagboken.core.model.LegacySource
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureLightAndDarkPaused
import se.partee71.dagboken.testing.captureScreenLightAndDark
import se.partee71.dagboken.ui.migration.MigrationSamples.before
import se.partee71.dagboken.ui.migration.MigrationSamples.halfway
import se.partee71.dagboken.ui.migration.MigrationSamples.importReview
import se.partee71.dagboken.ui.migration.MigrationSamples.importStopped

/**
 * Första starten utan Room-fil och importens lägen (OMB-5, BCK-6, BCK-14) i ljust och mörkt, ett läge per tavla i
 * mockupen (avsnitt 16; NFR-20). Samma vy visas i Export och import ([ImportStageContent]). Påhittad data.
 */
@RunWith(RobolectricTestRunner::class)
class ImportScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun content(stage: ImportStage) = MigrationScreen(MigrationUiState(MigrationStage.Fallback(stage)), {})

    private fun capture(name: String, stage: ImportStage) = rule.captureLightAndDark("Import_$name") { content(stage) }

    @Test
    fun `Välkommen - valen`() = capture("valen", ImportStage.Choose)

    @Test
    fun `Ingen backup på Drive`() = capture("ingenDrive", ImportStage.NoDriveBackup)

    @Test
    fun `Läser Drive`() = rule.captureLightAndDarkPaused("Import_laser") { content(ImportStage.Reading(LegacySource.DRIVE)) }

    @Test
    @Config(qualifiers = TALL)
    fun `Granska`() = capture("granska", importReview)

    @Test
    @Config(qualifiers = TALL)
    fun `Bekräfta`() = rule.captureScreenLightAndDark("Import_bekrafta") { content(importReview.copy(confirming = true)) }

    @Test
    fun `Stopp`() = capture("stopp", importStopped)

    @Test
    fun `Ingen backup`() = capture("ingenBackup", ImportStage.NotABackup)

    @Test
    fun `Läsfel`() = capture("lasfel", ImportStage.ReadFailed(LegacySource.DRIVE, DataError.Offline))

    @Test
    @Config(qualifiers = TALL)
    fun `Skriver`() = capture("skriver", ImportStage.Writing(before, halfway))

    @Test
    @Config(qualifiers = TALL)
    fun `Klar`() = capture("klar", ImportStage.Done(before, before))

    @Test
    fun `Avvikelse`() = capture("avvikelse", ImportStage.Mismatch(before, before + ("doses" to 3902), mapOf("doses" to 2)))

    @Test
    fun `Avbruten`() = capture("avbruten", ImportStage.WriteFailed(DataError.QuotaExceeded, 3, "RESOURCE_EXHAUSTED"))

    private companion object {
        const val TALL = "w390dp-h1700dp-xxhdpi"
    }
}
