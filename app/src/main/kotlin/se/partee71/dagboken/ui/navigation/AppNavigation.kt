package se.partee71.dagboken.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import se.partee71.dagboken.ui.aktiviteter.AktiviteterScreen
import se.partee71.dagboken.ui.aktiviteter.add.AddEditAktivitetScreen
import se.partee71.dagboken.ui.diagram.DiagramScreen
import se.partee71.dagboken.ui.handelser.AddEditHandelseScreen
import se.partee71.dagboken.ui.handelser.HandelserScreen
import se.partee71.dagboken.ui.home.HomeScreen
import se.partee71.dagboken.ui.mediciner.MedicinerScreen
import se.partee71.dagboken.ui.mediciner.add.AddEditFavoritScreen
import se.partee71.dagboken.ui.mediciner.add.AddEditMedicinScreen
import se.partee71.dagboken.ui.mediciner.add.AddEditReceptScreen
import se.partee71.dagboken.ui.migration.MigrationScreen
import se.partee71.dagboken.ui.settings.SettingsScreen

@Composable
fun AppNavigation(
    startDestination: String = Screen.Hem.route,
    navController: NavHostController = rememberNavController(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = Screen.bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                val isStartDest = screen.route == Screen.Hem.route
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = !isStartDest
                                    }
                                    launchSingleTop = true
                                    restoreState = !isStartDest
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector        = if (selected) screen.iconSelected else screen.iconUnselected,
                                    contentDescription = screen.label,
                                )
                            },
                            label = { Text(screen.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = startDestination,
            modifier         = Modifier.padding(innerPadding),
            enterTransition  = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(350)) +
                    fadeIn(tween(350))
            },
            exitTransition   = { fadeOut(tween(200)) },
            popEnterTransition  = { fadeIn(tween(350)) },
            popExitTransition   = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(350)) +
                    fadeOut(tween(200))
            },
        ) {
            composable(Routes.MIGRATION) {
                MigrationScreen(
                    onMigrationComplete = {
                        navController.navigate(Screen.Hem.route) {
                            popUpTo(Routes.MIGRATION) { inclusive = true }
                        }
                    },
                )
            }
            composable(Screen.Hem.route) {
                HomeScreen(
                    onNavigateToAktiviteter = { navController.navigate(Screen.Aktiviteter.route) },
                    onNavigateToMediciner   = { navController.navigate(Screen.Mediciner.route) },
                    onNavigateToSettings    = { navController.navigate(Routes.SETTINGS) },
                    onNavigateToDiagram     = { navController.navigate(Routes.diagram("hem")) },
                    snackbarHostState       = snackbarHostState,
                )
            }
            composable(Screen.Aktiviteter.route) {
                AktiviteterScreen(
                    onAddNew             = { navController.navigate(Routes.ADD_AKTIVITET) },
                    onEdit               = { id -> navController.navigate(Routes.editAktivitet(id)) },
                    onNavigateToDiagram  = { navController.navigate(Routes.diagram("aktiviteter")) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    snackbarHostState    = snackbarHostState,
                )
            }
            composable(Routes.ADD_AKTIVITET) {
                AddEditAktivitetScreen(
                    editId = null,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route     = Routes.EDIT_AKTIVITET,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { backStackEntry ->
                AddEditAktivitetScreen(
                    editId = backStackEntry.arguments?.getString("id"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.Mediciner.route) {
                MedicinerScreen(
                    onAddMedicin         = { navController.navigate(Routes.ADD_MEDICIN) },
                    onEditMedicin        = { id -> navController.navigate(Routes.editMedicin(id)) },
                    onAddRecept          = { navController.navigate(Routes.ADD_RECEPT) },
                    onEditRecept         = { id -> navController.navigate(Routes.editRecept(id)) },
                    onAddFavorit         = { navController.navigate(Routes.ADD_FAVORIT) },
                    onEditFavorit        = { id -> navController.navigate(Routes.editFavorit(id)) },
                    onNavigateToDiagram  = { navController.navigate(Routes.diagram("mediciner")) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    snackbarHostState    = snackbarHostState,
                )
            }
            composable(Routes.ADD_MEDICIN) {
                AddEditMedicinScreen(editId = null, onBack = { navController.popBackStack() })
            }
            composable(
                route     = Routes.EDIT_MEDICIN,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) {
                AddEditMedicinScreen(
                    editId = it.arguments?.getString("id"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ADD_RECEPT) {
                AddEditReceptScreen(editId = null, onBack = { navController.popBackStack() })
            }
            composable(
                route     = Routes.EDIT_RECEPT,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) {
                AddEditReceptScreen(
                    editId = it.arguments?.getString("id"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ADD_FAVORIT) {
                AddEditFavoritScreen(editId = null, onBack = { navController.popBackStack() })
            }
            composable(
                route     = Routes.EDIT_FAVORIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) {
                AddEditFavoritScreen(
                    editId = it.arguments?.getString("id"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.Handelser.route) {
                HandelserScreen(
                    onAddNew             = { navController.navigate(Routes.ADD_HANDELSE) },
                    onEdit               = { id -> navController.navigate(Routes.editHandelse(id)) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    snackbarHostState    = snackbarHostState,
                )
            }
            composable(Routes.ADD_HANDELSE) {
                AddEditHandelseScreen(
                    editId = null,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route     = Routes.EDIT_HANDELSE,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { backStackEntry ->
                AddEditHandelseScreen(
                    editId = backStackEntry.arguments?.getString("id"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route     = Routes.DIAGRAM,
                arguments = listOf(navArgument("source") { type = NavType.StringType }),
            ) { backStackEntry ->
                val source = backStackEntry.arguments?.getString("source") ?: "hem"
                DiagramScreen(
                    source = source,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack   = { navController.popBackStack() },
                    onImport = { navController.navigate(Routes.MIGRATION) },
                )
            }
        }
    }
}
