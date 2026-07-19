package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.DagbokenScaffold

/**
 * Ett diagram inom Trender-ytan (#141): egen väljare, eget diagram med egen
 * y-skala, egen legend och egen [MinMaxCaption]. [selector] utelämnas för diagram
 * utan serieval (t.ex. Energi (dag), som alltid visas).
 */
data class DiagramSection(
    val title: String,
    val selector: (@Composable () -> Unit)? = null,
    val chart: @Composable (chartModifier: Modifier) -> Unit,
    val legend: (@Composable () -> Unit)? = null,
    val minMax: (@Composable () -> Unit)? = null,
)

/**
 * Scaffold för Trender: titel/tillbaka, gemensam periodväljare, och en eller flera
 * [DiagramSection] staplade i ett scrollbart innehåll. Ersätter (#141) den tidigare
 * varianten med ett enda chart/legend-par samt landskapsägnad helskärmsoverlay —
 * den specialbehandlingen gav inte mening längre då flera oberoende diagram (olika
 * skalor, en av dem ett intervalldiagram) inte kan visas som en enda fullskärmslinje.
 * Portratt- och landskapsläge visar därför samma staplade, scrollbara innehåll.
 */
@Composable
fun DiagramLayout(
    title: String,
    onBack: (() -> Unit)? = null,
    rangeChips: @Composable () -> Unit,
    sections: List<DiagramSection>,
    periodLabel: (@Composable () -> Unit)? = null,
    portraitExtras: (@Composable () -> Unit)? = null,
) {
    DagbokenScaffold(
        title  = title,
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rangeChips()
            }
            periodLabel?.invoke()
            sections.forEach { section ->
                DagbokenCard(title = section.title) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        section.selector?.invoke()
                        section.chart(Modifier.fillMaxWidth())
                        section.minMax?.invoke()
                        section.legend?.let { legend ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                legend()
                            }
                        }
                    }
                }
            }
            portraitExtras?.invoke()
        }
    }
}
