package se.partee71.dagboken.ui.aktiviteter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.AccountBottomSheet
import se.partee71.dagboken.ui.components.AccountBubble
import se.partee71.dagboken.ui.home.AccountViewModel
import se.partee71.dagboken.ui.home.formattedDate

private data class TabItem(
    @StringRes val labelRes: Int,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
)

private val TABS = listOf(
    TabItem(R.string.tab_logga,     Icons.Filled.Edit,         Icons.Outlined.Edit),
    TabItem(R.string.filter_screening, Icons.Filled.MonitorHeart,  Icons.Outlined.MonitorHeart),
    TabItem(R.string.tab_historik,  Icons.Filled.History,       Icons.Outlined.History),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AktiviteterScreen(
    onAddNew: () -> Unit,
    onEdit: (id: String, type: String) -> Unit,
    onNavigateToDiagram: () -> Unit,
    onNavigateToSymptomDiagram: () -> Unit,
    onNavigateToSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
    vm: AktiviteterViewModel = hiltViewModel(),
    accountVm: AccountViewModel = hiltViewModel(),
) {
    val snackMsg by vm.snackbar.collectAsState()
    LaunchedEffect(snackMsg) {
        snackMsg?.let { snackbarHostState.showSnackbar(it); vm.clearSnackbar() }
    }

    val accountState by accountVm.uiState.collectAsState()
    var showAccountSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val pagerState = rememberPagerState(initialPage = 0) { TABS.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    AccountBubble(
                        email       = accountState.googleEmail,
                        photoUrl    = accountState.googlePhotoUrl,
                        displayName = accountState.googleDisplayName,
                        onClick     = { showAccountSheet = true },
                    )
                },
                title = {},
                actions = {
                    Text(
                        formattedDate(),
                        style    = MaterialTheme.typography.labelMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    IconButton(onClick = onNavigateToDiagram) {
                        Icon(Icons.Outlined.BarChart, contentDescription = stringResource(R.string.diagram_title))
                    }
                    IconButton(onClick = onNavigateToSymptomDiagram) {
                        Icon(Icons.Outlined.Healing, contentDescription = stringResource(R.string.symptom_diagram_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            if (pagerState.currentPage == 2) {
                FloatingActionButton(
                    onClick        = onAddNew,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.fab_logga_aktivitet))
                }
            }
        },
        bottomBar = {
            if (pagerState.currentPage < 2) {
                BottomAppBar(
                    actions = {
                        when (pagerState.currentPage) {
                            0 -> Button(
                                onClick  = { vm.save {} },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            ) { Text(stringResource(R.string.save_aktivitet)) }
                            1 -> Button(
                                onClick  = { vm.updateForm { copy(type = "screening") }; vm.save {} },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            ) { Text(stringResource(R.string.save_screening)) }
                        }
                    },
                )
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
                contentColor     = MaterialTheme.colorScheme.primary,
            ) {
                TABS.forEachIndexed { index, tab ->
                    val selected = pagerState.currentPage == index
                    Tab(
                        selected = selected,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon     = {
                            Icon(
                                imageVector        = if (selected) tab.iconSelected else tab.iconUnselected,
                                contentDescription = null,
                            )
                        },
                        text = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }

            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> LoggaTab(vm = vm)
                    1 -> ScreeningTab(vm = vm)
                    2 -> HistorikTab(vm = vm, onEdit = onEdit)
                }
            }
        }
    }

    if (showAccountSheet) {
        AccountBottomSheet(
            email                = accountState.googleEmail,
            photoUrl             = accountState.googlePhotoUrl,
            displayName          = accountState.googleDisplayName,
            isSigningIn          = accountState.isSigningIn,
            onDismiss            = { showAccountSheet = false },
            onSignIn             = { accountVm.signIn(context) },
            onSignOut            = { accountVm.signOut() },
            onNavigateToSettings = { showAccountSheet = false; onNavigateToSettings() },
        )
    }
}
