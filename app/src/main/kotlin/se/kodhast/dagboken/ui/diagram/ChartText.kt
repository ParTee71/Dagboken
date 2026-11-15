package se.kodhast.dagboken.ui.diagram

import android.content.res.Resources
import androidx.annotation.StringRes
import se.kodhast.dagboken.R
import se.kodhast.dagboken.core.engine.formatChartValue

// Diagrammens textrad (TRD-9) på ett ställe: `MinMaxCaption` och kortens sammanfattning i Trender formaterar
// samma sak likadant ("Lägst 3 · Högst 8 · Snitt 6,4").

/** Avskiljaren mellan delarna i en diagramtext och i Trenders sammanfattningar. */
const val CHART_SEPARATOR = " · "

/** "Snitt 6,4" – ett mått ([name]) med sitt värde på diagrammens sätt (svenskt decimalkomma). */
fun chartStatText(resources: Resources, @StringRes name: Int, value: Float): String = resources.getString(R.string.chart_stat_format, resources.getString(name), formatChartValue(value))
