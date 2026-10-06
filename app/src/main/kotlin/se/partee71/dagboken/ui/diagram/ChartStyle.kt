package se.partee71.dagboken.ui.diagram

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Tone

/**
 * Diagrammens utseende på ett ställe (skill `diagram`, Papper och teal): alla färger kommer ur temat
 * (`MaterialTheme.colorScheme`, `AppColors.extended`), alla mått står här – inget diagram har egna.
 *
 * - [line]: kurvan (teal) med svag gradientfyllning ([fillTop] → genomskinlig) och punkter (TRD-6).
 * - [trend]: den streckade trendlinjen (solgul, TRD-13) när diagrammet har en serie.
 * - [previous]: föregående period, grå och nedtonad (TRD-18) när diagrammet har en serie.
 * - [today]/[todayRing]: dagens punkt i sparklinen – solgul med mörk ring, som i datumremsan.
 * - [grid], [axis], [label]: värdelinjer, axellinje och axeletiketter.
 * - [band]: ett tonat fält över några dagar (en sjukdomsepisod i Händelser och sjukdom, TRD-21) – varningstonens
 *   yta, så svag att staplar och kurva syns igenom.
 */
@Immutable
internal data class ChartColors(
    val line: Color,
    val fillTop: Color,
    val trend: Color,
    val previous: Color,
    val today: Color,
    val todayRing: Color,
    val grid: Color,
    val axis: Color,
    val label: Color,
    val band: Color,
)

internal val chartColors: ChartColors
    @Composable @ReadOnlyComposable
    get() {
        val scheme = MaterialTheme.colorScheme
        return ChartColors(
            line = scheme.primary,
            fillTop = scheme.primary.copy(alpha = FILL_ALPHA),
            trend = scheme.secondary,
            previous = scheme.outline.copy(alpha = PREVIOUS_ALPHA),
            today = scheme.secondary,
            todayRing = AppColors.extended.sunTone.content,
            grid = scheme.outlineVariant,
            axis = scheme.outlineVariant,
            label = scheme.onSurfaceVariant,
            band = AppColors.tone(Tone.Warning).content.copy(alpha = BAND_ALPHA),
        )
    }

/** Axeletiketternas text: bildtextstilen i dämpad färg (följer dynamisk textstorlek). */
internal val chartLabelStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = AppTypography.caption.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

/** En annan series färg på kurvan i ett diagram med flera serier: nedtonad för föregående period. */
internal fun Color.previousPeriod(): Color = copy(alpha = alpha * PREVIOUS_ALPHA)

/** Fältets ruta i teckenförklaringen: samma ton som fältet men tät nog att synas mot kortet. */
internal fun Color.bandSwatch(): Color = copy(alpha = BAND_SWATCH_ALPHA)

/** Spannets stapel i intervalldiagrammet (min–max) – 35 % av dagsvärdets färg. */
internal fun Color.span(): Color = copy(alpha = SPAN_ALPHA)

private const val FILL_ALPHA = 0.28f
private const val PREVIOUS_ALPHA = 0.55f
private const val SPAN_ALPHA = 0.35f
private const val BAND_ALPHA = 0.14f
private const val BAND_SWATCH_ALPHA = 0.4f

/** Ritytans höjd för linje- och stapeldiagram, och för sparklinen på Idag. */
internal val CHART_HEIGHT: Dp = 200.dp
internal val SPARKLINE_HEIGHT: Dp = 96.dp

internal val LINE_WIDTH: Dp = 2.5.dp
internal val TREND_WIDTH: Dp = 2.dp
internal val TREND_DASH: Dp = 6.dp
internal val TREND_GAP: Dp = 4.dp
internal val POINT_SIZE: Dp = 7.dp
internal val TODAY_POINT_SIZE: Dp = 11.dp
internal val TODAY_RING_WIDTH: Dp = 1.5.dp
internal val GRID_WIDTH: Dp = 1.dp
internal val AXIS_LABEL_GAP: Dp = 4.dp
internal val EDGE_PADDING: Dp = 8.dp
internal val LEGEND_SWATCH: Dp = 10.dp
internal val LEGEND_DASH_WIDTH: Dp = 16.dp

/** Stapeldiagrammen: smalaste stapel, bredaste spann (`IntervalBarChart`) och stapel (`StackedBarChart`). */
internal val MIN_BAR_WIDTH: Dp = 2.dp
internal val MAX_SPAN_WIDTH: Dp = 16.dp
internal val MAX_STACK_WIDTH: Dp = 28.dp

/** Dagsvärdets punkt i intervalldiagrammet. */
internal val INTERVAL_DOT_RADIUS: Dp = 5.dp
