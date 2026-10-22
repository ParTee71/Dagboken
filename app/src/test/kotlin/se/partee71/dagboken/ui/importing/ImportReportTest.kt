package se.partee71.dagboken.ui.importing

import kotlin.test.assertEquals
import org.junit.Test
import se.partee71.dagboken.core.importing.Problem
import se.partee71.dagboken.core.schema.CollectionNames

/** Importens tabellrader och stopprapport (BCK-6): bara antal och fält, aldrig innehåll. Syntetisk data. */
class ImportReportTest {

    @Test
    fun `varje samling som importen skriver har en rad, i mockupens ordning`() {
        assertEquals(
            CollectionNames.ALL - CollectionNames.USERS,
            (CollectionNames.ALL - CollectionNames.USERS).filter { ImportEntity.of(it) != null }
        )
        assertEquals(25, ImportEntity.SETTINGS.count(ImportSamples.before), "inställningarna och listorna på en rad")
        assertEquals("6\u00a0353", countText(6353), "svensk tusentalsavgränsare")
    }

    @Test
    fun `rapportens sökväg ger samlingen utan id`() {
        assertEquals("prescriptions", reportCollection("prescriptions/6f1c2a9e"))
        assertEquals("checkins", reportCollection("illnessEpisodes/flu/checkins/c1"))
        assertEquals("options", reportCollection("options/activity#3"))
        assertEquals("notes", reportCollection("notes"))
    }

    @Test
    fun `stopprapporten - en rad per typ, listindex slås ihop och antalet summeras`() {
        val problems = listOf(
            Problem("screenings/s1", "symptom[0]", "saknar namn"),
            Problem("screenings/s2", "symptom[1]", "saknar namn"),
            Problem("screenings/s2", "symptom[12]", "saknar namn"),
            Problem("prescriptions/r1", "dagar", "ingen veckodag: 9 (0–6)"),
            Problem("settings/app", "medNotificationConfigs[2].time", "ogiltigt klockslag")
        )
        assertEquals(
            listOf(
                ReportLine("screenings", "symptom[…]", "saknar namn", 3),
                ReportLine("prescriptions", "dagar", "ingen veckodag: 9 (0–6)", 1),
                ReportLine("settings", "medNotificationConfigs[…].time", "ogiltigt klockslag", 1)
            ),
            reportOf(problems)
        )
    }
}
