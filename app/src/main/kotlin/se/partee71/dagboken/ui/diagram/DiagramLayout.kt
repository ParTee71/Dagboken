package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.DagbokenScaffold
import se.partee71.dagboken.ui.components.Foldout

/**
 * Ett diagram inom Trender-ytan (#141): egen periodväljare (#149), egen serieväljare, eget
 * diagram med egen y-skala, egen legend och egen [MinMaxCaption]. [selector] utelämnas för
 * diagram utan serieval (t.ex. Energi (dag), som alltid visas). [periodSelector] är valfri
 * för konsumenter som inte behöver periodstyrning per diagram.
 *
 * Kortet är ihopfällbart (TRD-14) och [expanded] styrs av anroparen — inget kort håller
 * eget utfällningstillstånd, så Trender kan öppna med samtliga stängda. Allt utom titelraden
 * (inklusive periodväljaren) renderas först i utfällt läge.
 */
data class DiagramSection(
    val title: String,
    val periodSelector: (@Composable () -> Unit)? = null,
    val selector: (@Composable () -> Unit)? = null,
    val chart: @Composable (chartModifier: Modifier) -> Unit,
    val legend: (@Composable () -> Unit)? = null,
    val minMax: (@Composable () -> Unit)? = null,
    val expanded: Boolean = false,
    val onToggleExpanded: () -> Unit = {},
)

/**
 * Scaffold för Trender: titel/tillbaka och en eller flera [DiagramSection] staplade i ett
 * scrollbart innehåll — varje sektion styr sin egen period (#149, [DiagramSection.periodSelector])
 * i stället för en gemensam periodväljare för hela ytan. Ersätter (#141) den tidigare varianten
 * med ett enda chart/legend-par samt landskapsägnad helskärmsoverlay — den specialbehandlingen
 * gav inte mening längre då flera oberoende diagram (olika skalor, en av dem ett
 * intervalldiagram) inte kan visas som en enda fullskärmslinje. Portratt- och landskapsläge
 * visar därför samma staplade, scrollbara innehåll.
 *
 * Varje sektion är ett **ihopfällbart sektionskort** (NFR-18): [DagbokenCard] med en delad
 * [Foldout] inuti, i stället för kortets egen titelrad. Ett stängt kort komponerar inte sitt
 * diagram alls, vilket är poängen när ytan rymmer ett tiotal diagram (TRD-14).
 */
@Composable
fun DiagramLayout(
    title: String,
    onBack: (() -> Unit)? = null,
    sections: List<DiagramSection>,
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
            sections.forEach { section ->
                DagbokenCard {
                    Foldout(
                        title      = section.title,
                        expanded   = section.expanded,
                        onToggle   = section.onToggleExpanded,
                        // Behåll kortets rubriktypografi i stället för avsnittsrubrikens,
                        // så ett ihopfällbart diagramkort ser ut som ett kort och inte som
                        // ett avsnitt inuti ett kort.
                        titleStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        titleColor = MaterialTheme.colorScheme.onSurface,
                        // Periodväljaren säger ingenting utan sitt diagram och visas
                        // därför bara i utfällt läge (TRD-14).
                        trailing   = if (section.expanded) section.periodSelector else null,
                    ) {
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
            }
            portraitExtras?.invoke()
        }
    }
}
