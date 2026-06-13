package se.partee71.dagboken.ui.aktiviteter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AktiviteterScreen(
    onAddNew: () -> Unit,
    onEdit: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    vm: AktiviteterViewModel = hiltViewModel(),
) {
    val snackMsg by vm.snackbar.collectAsState()
    LaunchedEffect(snackMsg) {
        snackMsg?.let { snackbarHostState.showSnackbar(it); vm.clearSnackbar() }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Logga", "Historik")

    Scaffold(
        topBar = { TopAppBar(title = { Text("Aktiviteter") }) },
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(onClick = onAddNew) {
                    Icon(Icons.Default.Add, contentDescription = "Logga aktivitet")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text     = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 -> LoggaTab(vm = vm)
                1 -> HistorikTab(vm = vm, onEdit = onEdit)
            }
        }
    }
}
