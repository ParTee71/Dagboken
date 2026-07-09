package se.partee71.dagboken.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.ui.graphics.vector.ImageVector
import se.partee71.dagboken.R

sealed class Screen(
    val route: String,
    @StringRes val labelRes: Int,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
) {
    object Hem : Screen(
        route          = "hem",
        labelRes       = R.string.nav_tab_hem,
        iconSelected   = Icons.Filled.Home,
        iconUnselected = Icons.Outlined.Home,
    )
    object Aktiviteter : Screen(
        route          = "aktiviteter",
        labelRes       = R.string.nav_tab_aktivitet,
        iconSelected   = Icons.Filled.Bolt,
        iconUnselected = Icons.Outlined.Bolt,
    )
    object Mediciner : Screen(
        route          = "mediciner",
        labelRes       = R.string.nav_tab_mediciner,
        iconSelected   = Icons.Filled.Medication,
        iconUnselected = Icons.Outlined.Medication,
    )
    object Handelser : Screen(
        route          = "handelser",
        labelRes       = R.string.nav_tab_handelser,
        iconSelected   = Icons.Filled.MonitorHeart,
        iconUnselected = Icons.Outlined.MonitorHeart,
    )
    object Sjukdomar : Screen(
        route          = "sjukdomar",
        labelRes       = R.string.nav_tab_sjukdomar,
        iconSelected   = Icons.Filled.LocalHospital,
        iconUnselected = Icons.Outlined.LocalHospital,
    )

    companion object {
        val bottomNavItems by lazy { listOf(Hem, Aktiviteter, Mediciner, Handelser, Sjukdomar) }
    }
}

// Sub-routes (not in bottom nav)
object Routes {
    const val ADD_AKTIVITET                 = "add_aktivitet"
    const val EDIT_AKTIVITET                = "edit_aktivitet/{id}"
    const val EDIT_SCREENING                = "edit_screening/{id}"
    const val ADD_MEDICIN                   = "add_medicin"
    const val EDIT_MEDICIN                  = "edit_medicin/{id}"
    const val ADD_RECEPT                    = "add_recept"
    const val EDIT_RECEPT                   = "edit_recept/{id}"
    const val ADD_FAVORIT                   = "add_favorit"
    const val EDIT_FAVORIT                  = "edit_favorit/{id}"
    const val ADD_HANDELSE                  = "add_handelse"
    const val EDIT_HANDELSE                 = "edit_handelse/{id}"
    const val SETTINGS                      = "settings"
    const val MIGRATION                     = "migration"
    const val DIAGRAM                       = "diagram/{source}"
    const val SYMPTOM_DIAGRAM               = "symptom_diagram"
    const val ADD_SJUKDOM                   = "add_sjukdom"
    const val EDIT_SJUKDOM                  = "edit_sjukdom/{id}"
    const val SJUKDOM_EPISOD_DETAIL         = "sjukdom_episod/{episodId}"
    const val ADD_SJUKDOMS_INCHECKNING      = "add_sjukdoms_incheckning/{episodId}"
    const val HISTORIK                      = "historik"
    const val TRENDER                       = "trender"

    fun editAktivitet(id: String)                = "edit_aktivitet/$id"
    fun editScreening(id: String)                = "edit_screening/$id"
    fun editMedicin(id: String)                  = "edit_medicin/$id"
    fun editRecept(id: String)                   = "edit_recept/$id"
    fun editFavorit(id: String)                  = "edit_favorit/$id"
    fun editHandelse(id: String)                 = "edit_handelse/$id"
    fun diagram(source: String)                  = "diagram/$source"
    fun editSjukdom(id: String)                  = "edit_sjukdom/$id"
    fun sjukdomEpisodDetail(id: String)          = "sjukdom_episod/$id"
    fun addSjukdomsIncheckning(episodId: String) = "add_sjukdoms_incheckning/$episodId"
}
