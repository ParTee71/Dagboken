package se.partee71.dagboken.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import se.partee71.dagboken.R

sealed class Screen(
    val route: String,
    @StringRes val labelRes: Int,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
) {
    object Idag : Screen(
        route          = "idag",
        labelRes       = R.string.nav_tab_idag,
        iconSelected   = Icons.Filled.Home,
        iconUnselected = Icons.Outlined.Home,
    )
    object Historik : Screen(
        route          = Routes.HISTORIK,
        labelRes       = R.string.nav_tab_historik,
        iconSelected   = Icons.Filled.History,
        iconUnselected = Icons.Outlined.History,
    )
    object Trender : Screen(
        route          = Routes.TRENDER,
        labelRes       = R.string.nav_tab_trender,
        iconSelected   = Icons.AutoMirrored.Filled.TrendingUp,
        iconUnselected = Icons.AutoMirrored.Outlined.TrendingUp,
    )
    object Hantera : Screen(
        route          = "hantera",
        labelRes       = R.string.nav_tab_hantera,
        iconSelected   = Icons.Filled.Settings,
        iconUnselected = Icons.Outlined.Settings,
    )

    companion object {
        val bottomNavItems by lazy { listOf(Idag, Historik, Trender, Hantera) }
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
    const val ADD_HANDELSE                  = "add_handelse?datum={datum}"
    const val EDIT_HANDELSE                 = "edit_handelse/{id}"
    const val MIGRATION                     = "migration"
    const val SJUKDOMAR                     = "sjukdomar"
    const val ADD_SJUKDOM                   = "add_sjukdom"
    const val EDIT_SJUKDOM                  = "edit_sjukdom/{id}"
    const val SJUKDOM_EPISOD_DETAIL         = "sjukdom_episod/{episodId}"
    const val ADD_SJUKDOMS_INCHECKNING      = "add_sjukdoms_incheckning/{episodId}"
    const val SCHEMA                        = "schema"
    const val HISTORIK                      = "historik"
    const val TRENDER                       = "trender"

    fun editAktivitet(id: String)                = "edit_aktivitet/$id"
    fun editScreening(id: String)                = "edit_screening/$id"
    fun editMedicin(id: String)                  = "edit_medicin/$id"
    fun editRecept(id: String)                   = "edit_recept/$id"
    fun editFavorit(id: String)                  = "edit_favorit/$id"
    fun editHandelse(id: String)                 = "edit_handelse/$id"
    fun editSjukdom(id: String)                  = "edit_sjukdom/$id"
    fun sjukdomEpisodDetail(id: String)          = "sjukdom_episod/$id"
    fun addSjukdomsIncheckning(episodId: String) = "add_sjukdoms_incheckning/$episodId"
    fun addHandelse(datum: String)               = "add_handelse?datum=$datum"
}
