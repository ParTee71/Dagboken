package se.partee71.dagboken.ui.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.usecase.ScreeningEventStatus
import se.partee71.dagboken.util.retryOnRenderGlitch
import java.time.LocalDate

/**
 * [IdagChecklistCard] konstrueras här direkt från tillstånd och lambdor — ingen ViewModel,
 * ingen databas och ingen Hilt-graf (#176). Kortet tog tidigare emot `AktiviteterViewModel`
 * och `MedicinerViewModel`, vilket gjorde att varje test av det krävde hela skärmen; med
 * [ScreeningFormBinding]/[VidBehovBinding] går det att testa isolerat.
 *
 * Beteendet mot hela skärmen täcks fortsatt av `HomeScreenTest`.
 */
@RunWith(AndroidJUnit4::class)
class IdagChecklistCardTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val today = LocalDate.now()

    private fun medicin(namn: String, tagen: Boolean = false) = Medicin(
        id        = "med-$namn",
        timestamp = "${today}T08:00:00.000Z",
        datum     = today.toString(),
        tid       = "08:00",
        namn      = namn,
        dos       = "50",
        enhet     = "mcg",
        tidpunkt  = "Morgon",
        tagen     = tagen,
    )

    private fun favorit(namn: String) = Favorit(
        id = "fav-$namn", namn = namn, dos = "400", enhet = "mg",
        tidpunkt = "Vid behov", minTidMellan = 0, isFavorite = true,
    )

    private fun screeningBinding(
        onStart: (String, LocalDate) -> Unit = { _, _ -> },
    ) = ScreeningFormBinding(
        energy                  = 5,
        stress                  = 3,
        symptomScores           = emptyMap(),
        symptomOptions          = emptyList(),
        onStart                 = onStart,
        onEnergyChange          = {},
        onStressChange          = {},
        onScoresChange          = {},
        onToggleSymptomFavorite = {},
        onSave                  = {},
    )

    private fun vidBehovBinding(
        favoriter: List<Favorit> = emptyList(),
        onTap: (Favorit) -> Unit = {},
    ) = VidBehovBinding(
        favoriter        = favoriter,
        others           = emptyList(),
        notes            = emptyMap(),
        onTap            = onTap,
        onEdit           = {},
        onDelete         = {},
        onToggleFavorite = {},
        onLogEfterhand   = {},
    )

    @Suppress("LongParameterList")
    private fun card(
        selectedDate: LocalDate = today,
        isToday: Boolean = true,
        onPreviousDay: () -> Unit = {},
        onNextDay: () -> Unit = {},
        mediciner: List<Medicin> = emptyList(),
        tagenCount: Int = 0,
        kommandeMediciner: List<Medicin> = emptyList(),
        snartMediciner: List<Medicin> = emptyList(),
        medicinerOverdue: Boolean = false,
        onToggleMedicin: (Medicin) -> Unit = {},
        screeningEvents: List<ScreeningEventStatus> = emptyList(),
        screening: ScreeningFormBinding = screeningBinding(),
        vidBehov: VidBehovBinding = vidBehovBinding(),
    ): @Composable () -> Unit = {
        IdagChecklistCard(
            selectedDate                  = selectedDate,
            isToday                       = isToday,
            onPreviousDay                 = onPreviousDay,
            onNextDay                     = onNextDay,
            mediciner                     = mediciner,
            tagenCount                    = tagenCount,
            kommandeMediciner             = kommandeMediciner,
            snartMediciner                = snartMediciner,
            medicinerOverdue              = medicinerOverdue,
            onToggleMedicin               = onToggleMedicin,
            screeningEvents               = screeningEvents,
            screening                     = screening,
            initialExpandedScreeningLabel = null,
            onScreeningLabelConsumed      = {},
            vidBehov                      = vidBehov,
        )
    }

    private fun render(content: @Composable () -> Unit, assertions: () -> Unit) =
        retryOnRenderGlitch {
            val scenario = ActivityScenario.launch(ComponentActivity::class.java)
            try {
                scenario.onActivity {
                    it.setContent {
                        MaterialTheme {
                            // Scrollbar förälder så performScrollTo() fungerar när
                            // checklistan blir högre än skärmen.
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                content()
                            }
                        }
                    }
                }
                assertions()
            } finally {
                scenario.close()
            }
        }

    // ─── Sektionerna ──────────────────────────────────────────────────────────

    @Test fun card_renders_all_three_sections_from_plain_state() = render(
        content = card(
            mediciner       = listOf(medicin("Levaxin")),
            screeningEvents = listOf(
                ScreeningEventStatus(label = "Efter frukost", time = "08:00", logged = false, overdue = false),
            ),
            vidBehov        = vidBehovBinding(favoriter = listOf(favorit("Ipren"))),
        ),
        assertions = {
            composeRule.onNodeWithText("Dagens mediciner").assertIsDisplayed()
            composeRule.onNodeWithText("Levaxin").assertIsDisplayed()
            composeRule.onNodeWithText("Daglig screening").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Efter frukost").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Vid behov").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Ipren").performScrollTo().assertIsDisplayed()
        },
    )

    // MED-13: en dos inom horisonten ligger i listan men märks "Snart"; en dos bortom
    // horisonten göms bakom "Visa kommande".

    @Test fun dose_inside_the_horizon_is_listed_and_marked_snart() {
        val near = medicin("Kvällsvitamin")
        render(
            content = card(mediciner = listOf(near), snartMediciner = listOf(near)),
            assertions = {
                composeRule.onNodeWithText("Kvällsvitamin").assertIsDisplayed()
                composeRule.onNodeWithText("Snart", substring = true).assertIsDisplayed()
                composeRule.onAllNodesWithText("Visa kommande", substring = true).assertCountEquals(0)
            },
        )
    }

    @Test fun dose_beyond_the_horizon_is_hidden_and_not_marked_snart() {
        val far = medicin("Nattvitamin")
        render(
            content = card(mediciner = listOf(far), kommandeMediciner = listOf(far)),
            assertions = {
                composeRule.onAllNodesWithText("Nattvitamin").assertCountEquals(0)
                composeRule.onAllNodesWithText("Snart", substring = true).assertCountEquals(0)
                composeRule.onNodeWithText("Visa kommande", substring = true).performScrollTo().performClick()
                composeRule.onNodeWithText("Nattvitamin").performScrollTo().assertIsDisplayed()
            },
        )
    }

    @Test fun sections_without_content_are_omitted_entirely() = render(
        content = card(mediciner = listOf(medicin("Levaxin"))),
        assertions = {
            composeRule.onNodeWithText("Dagens mediciner").assertIsDisplayed()
            composeRule.onAllNodesWithText("Daglig screening").assertCountEquals(0)
            composeRule.onAllNodesWithText("Vid behov").assertCountEquals(0)
        },
    )

    @Test fun logged_screening_event_is_shown_as_logged_and_not_expandable() = render(
        content = card(
            screeningEvents = listOf(
                ScreeningEventStatus(label = "Lunch", time = "12:00", logged = true, overdue = false),
            ),
        ),
        assertions = {
            composeRule.onNodeWithText("Loggad").assertIsDisplayed()
            composeRule.onNodeWithText("Lunch").performClick()
            // Klicket ska inte fälla ut något formulär för ett redan loggat tillfälle.
            composeRule.waitForIdle()
            composeRule.onAllNodesWithTag("screening_next").assertCountEquals(0)
            composeRule.onAllNodesWithTag("screening_save").assertCountEquals(0)
        },
    )

    @Test fun overdue_screening_event_shows_the_reminder_time() = render(
        content = card(
            screeningEvents = listOf(
                ScreeningEventStatus(label = "Efter frukost", time = "08:00", logged = false, overdue = true),
            ),
        ),
        assertions = {
            composeRule.onNodeWithText("Försenat").assertIsDisplayed()
            composeRule.onNodeWithText("Påminnelse var: 08:00").assertIsDisplayed()
        },
    )

    // ─── Lambdorna ────────────────────────────────────────────────────────────

    @Test fun toggling_a_medicin_reports_exactly_that_medicin() {
        var toggled: Medicin? = null
        val levaxin = medicin("Levaxin")
        render(
            content = card(
                mediciner       = listOf(levaxin, medicin("Metformin")),
                onToggleMedicin = { toggled = it },
            ),
            assertions = {
                toggled = null
                composeRule.onNodeWithContentDescription("Markera Levaxin som tagen")
                    .performScrollTo().performClick()
                composeRule.waitUntil(5000) { toggled != null }
            },
        )
        assertEquals(levaxin, toggled)
    }

    @Test fun tapping_a_vid_behov_favorit_reports_it() {
        var tapped: Favorit? = null
        val ipren = favorit("Ipren")
        render(
            content = card(vidBehov = vidBehovBinding(favoriter = listOf(ipren), onTap = { tapped = it })),
            assertions = {
                tapped = null
                composeRule.onNodeWithText("Ipren").performScrollTo().performClick()
                composeRule.waitUntil(5000) { tapped != null }
            },
        )
        assertEquals(ipren, tapped)
    }

    // ─── Datumnavigering (#114) ───────────────────────────────────────────────

    @Test fun next_day_is_disabled_on_today_and_previous_day_reports_back() {
        var wentBack = false
        render(
            content = card(onPreviousDay = { wentBack = true }),
            assertions = {
                wentBack = false
                composeRule.onNodeWithContentDescription("Nästa dag").assertIsNotEnabled()
                composeRule.onNodeWithContentDescription("Föregående dag").performClick()
                composeRule.waitUntil(5000) { wentBack }
            },
        )
        assertEquals(true, wentBack)
    }

    @Test fun next_day_is_enabled_when_viewing_an_earlier_day() {
        var wentForward = false
        render(
            content = card(
                selectedDate = today.minusDays(1),
                isToday      = false,
                onNextDay    = { wentForward = true },
            ),
            assertions = {
                wentForward = false
                composeRule.onNodeWithContentDescription("Nästa dag").performClick()
                composeRule.waitUntil(5000) { wentForward }
            },
        )
        assertEquals(true, wentForward)
    }

    /**
     * Formuläret laddas för den dag användaren bläddrat till, inte för dagens datum —
     * annars skulle en screening loggad bakåt i tiden hamna på fel dag (HEM-14).
     */
    @Test fun expanding_a_screening_loads_the_form_for_the_selected_day() {
        var started: Pair<String, LocalDate>? = null
        val yesterday = today.minusDays(1)
        render(
            content = card(
                selectedDate    = yesterday,
                isToday         = false,
                screeningEvents = listOf(
                    ScreeningEventStatus(label = "Efter frukost", time = "08:00", logged = false, overdue = true),
                ),
                screening       = screeningBinding(onStart = { label, date -> started = label to date }),
            ),
            assertions = {
                // Nollställs per försök — retryOnRenderGlitch kör blocket om vid en glitch.
                started = null
                composeRule.onNodeWithText("Efter frukost").performScrollTo().performClick()
                composeRule.waitUntil(5000) { started != null }
            },
        )
        assertEquals("Efter frukost" to yesterday, started)
    }
}
