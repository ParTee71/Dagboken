package se.partee71.dagboken.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
) {
    object Hem : Screen(
        route          = "hem",
        label          = "Hem",
        iconSelected   = Icons.Filled.Home,
        iconUnselected = Icons.Outlined.Home,
    )
    object Aktiviteter : Screen(
        route          = "aktiviteter",
        label          = "Aktivitet",
        iconSelected   = Icons.Filled.Bolt,
        iconUnselected = Icons.Outlined.Bolt,
    )
    object Mediciner : Screen(
        route          = "mediciner",
        label          = "Mediciner",
        iconSelected   = Icons.Filled.Medication,
        iconUnselected = Icons.Outlined.Medication,
    )
    object Diagram : Screen(
        route          = "diagram",
        label          = "Diagram",
        iconSelected   = Icons.Filled.BarChart,
        iconUnselected = Icons.Outlined.BarChart,
    )

    companion object {
        val bottomNavItems by lazy { listOf(Hem, Aktiviteter, Mediciner, Diagram) }
    }
}

// Sub-routes for add/edit screens (not in bottom nav)
object Routes {
    const val ADD_AKTIVITET  = "add_aktivitet"
    const val EDIT_AKTIVITET = "edit_aktivitet/{id}"
    const val ADD_MEDICIN    = "add_medicin"
    const val EDIT_MEDICIN   = "edit_medicin/{id}"
    const val ADD_RECEPT     = "add_recept"
    const val EDIT_RECEPT    = "edit_recept/{id}"
    const val ADD_FAVORIT    = "add_favorit"
    const val EDIT_FAVORIT   = "edit_favorit/{id}"
    const val SETTINGS       = "settings"
    const val MIGRATION      = "migration"

    fun editAktivitet(id: String) = "edit_aktivitet/$id"
    fun editMedicin(id: String)   = "edit_medicin/$id"
    fun editRecept(id: String)    = "edit_recept/$id"
    fun editFavorit(id: String)   = "edit_favorit/$id"
}
