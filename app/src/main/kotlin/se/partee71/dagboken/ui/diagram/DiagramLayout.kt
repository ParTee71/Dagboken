package se.partee71.dagboken.ui.diagram

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.components.DagbokenScaffold

@Composable
fun DiagramLayout(
    title: String,
    onBack: (() -> Unit)? = null,
    selector: @Composable () -> Unit,
    rangeChips: @Composable () -> Unit,
    chart: @Composable (chartModifier: Modifier) -> Unit,
    legend: @Composable () -> Unit,
    periodLabel: (@Composable () -> Unit)? = null,
    portraitExtras: (@Composable () -> Unit)? = null,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        LandscapeLayout(onBack, selector, rangeChips, chart, legend)
    } else {
        PortraitLayout(title, onBack, selector, rangeChips, chart, legend, periodLabel, portraitExtras)
    }
}

@Composable
private fun PortraitLayout(
    title: String,
    onBack: (() -> Unit)?,
    selector: @Composable () -> Unit,
    rangeChips: @Composable () -> Unit,
    chart: @Composable (Modifier) -> Unit,
    legend: @Composable () -> Unit,
    periodLabel: (@Composable () -> Unit)?,
    portraitExtras: (@Composable () -> Unit)?,
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
            selector()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rangeChips()
            }
            periodLabel?.invoke()
            DagbokenCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    chart(Modifier.fillMaxWidth().height(280.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        legend()
                    }
                }
            }
            portraitExtras?.invoke()
        }
    }
}

@Composable
private fun LandscapeLayout(
    onBack: (() -> Unit)?,
    selector: @Composable () -> Unit,
    rangeChips: @Composable () -> Unit,
    chart: @Composable (Modifier) -> Unit,
    legend: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background),
    ) {
        chart(
            Modifier
                .fillMaxSize()
                .padding(top = 42.dp, bottom = 32.dp, start = 8.dp, end = 8.dp),
        )

        // Top overlay: back + selector + range chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(cs.surface.copy(alpha = 0.92f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            selector()
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rangeChips()
            }
        }

        // Bottom overlay: legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(cs.surface.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            legend()
        }
    }
}
