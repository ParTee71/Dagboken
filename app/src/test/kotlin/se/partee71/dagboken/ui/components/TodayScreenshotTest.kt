package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/**
 * Skärmdumpar (ljust + mörkt) av Idags nya komponenter i mockupens tillstånd (etapp 4.2, canvasen
 * "Komponenter", avsnitt 6). Påhittad data; onsdag 7 okt 2026 är "idag".
 */
@RunWith(RobolectricTestRunner::class)
class TodayScreenshotTest {

    @Composable
    private fun Sheet(content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            content = content,
        )
    }

    private val today = LocalDate(2026, 10, 7)
    private val entries = setOf(LocalDate(2026, 10, 5), LocalDate(2026, 10, 6), today)

    @Test
    fun `DateStrip - idag vald, tidigare dag vald och dagen klar`() {
        captureLightAndDark("DateStrip_idag_vald") { Sheet { DateStrip(today, today, {}, {}, today = today, datesWithEntries = entries) } }
        captureLightAndDark("DateStrip_tidigare_dag") {
            Sheet { DateStrip(today, LocalDate(2026, 10, 5), {}, {}, today = today, datesWithEntries = entries) }
        }
        captureLightAndDark("DateStrip_dagen_klar") {
            Sheet {
                DateStrip(today, today, {}, {}, today = today, datesWithEntries = entries, todayDone = true)
                DateStrip(today, LocalDate(2026, 10, 6), {}, {}, today = today, datesWithEntries = entries, todayDone = true)
            }
        }
    }

    @Test
    fun `ProgressBar - tom, pågår och klar`() {
        captureLightAndDark("ProgressBar_lagen") {
            Sheet {
                ProgressBar(0, 9)
                ProgressBar(4, 9)
                ProgressBar(9, 9)
            }
        }
    }

    @Test
    fun `OccasionRow - fyra lägen och en tidigare dag`() {
        captureLightAndDark("OccasionRow_lagen") {
            Sheet {
                AppCard { GalleryOccasionRows() }
                AppCard { OccasionRow("Lunch", OccasionStatus.Late, {}, time = "12:00", isToday = false) }
            }
        }
    }

    @Test
    fun `DayDoneCard - med och utan underlag`() {
        captureLightAndDark("DayDoneCard_lagen") {
            Sheet {
                DayDoneCard(6.8, 0.5, 12)
                DayDoneCard(null, null, null)
            }
        }
    }

    @Test
    fun `AccountAvatar - initialer, foto och utloggad`() {
        captureLightAndDark("AccountAvatar_lagen") {
            Sheet {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    AccountAvatar("Anna Berg", {})
                    AccountAvatar("Anna Berg", {}) { Box(Modifier.fillMaxSize().background(AppColors.tone(Tone.Sun).container)) }
                    AccountAvatar(null, {})
                }
            }
        }
    }
}
