package se.partee71.dagboken.ui.mediciner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

private data class TabItem(
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
)

private val TABS = listOf(
    TabItem("Idag",     Icons.Filled.CheckCircle,   Icons.Outlined.CheckCircle),
    TabItem("Schema",   Icons.Filled.CalendarMonth,  Icons.Outlined.CalendarMonth),
    TabItem("Vid behov", Icons.Filled.LocalPharmacy, Icons.Outlined.LocalPharmacy),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicinerScreen(
    onAddMedicin:  () -> Unit,
    onEditMedicin: (String) -> Unit,
    onAddRecept:   () -> Unit,
    onEditRecept:  (String) -> Unit,
    onAddFavorit:  () -> Unit,
    onEditFavorit: (String) -> Unit,
    onNavigateToDiagram: () -> Unit,
    snackbarHostState: SnackbarHostState,
    vm: MedicinerViewModel = hiltViewModel(),
) {
    val snackMsg by vm.snackbar.collectAsState()
    LaunchedEffect(snackMsg) {
        snackMsg?.let { snackbarHostState.showSnackbar(it); vm.clearSnackbar() }
    }

    val pagerState = rememberPagerState(initialPage = 0) { TABS.size }
    val scope = rememberCoroutineScope()

    val fabAction: () -> Unit = when (pagerState.currentPage) {
        1    -> onAddRecept
        2    -> onAddFavorit
        else -> onAddMedicin
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mediciner") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = onNavigateToDiagram) {
                        Icon(Icons.Outlined.BarChart, contentDescription = "Diagram")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = fabAction,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor   = MaterialTheme.colorScheme.onSecondary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ny")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor   = MaterialTheme.colorScheme.surface,
                contentColor     = MaterialTheme.colorScheme.secondary,
            ) {
                TABS.forEachIndexed { index, tab ->
                    val selected = pagerState.currentPage == index
                    Tab(
                        selected = selected,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.iconSelected else tab.iconUnselected,
                                contentDescription = null,
                            )
                        },
                        text = { Text(tab.label) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> IdagTab(vm = vm, onEdit = onEditMedicin)
                    1 -> SchemaTab(vm = vm, onEdit = onEditRecept)
                    2 -> VidBehovTab(vm = vm, onEdit = onEditFavorit, snackbarHostState = snackbarHostState)
                }
            }
        }
    }
}
