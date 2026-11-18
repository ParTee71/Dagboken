package se.kodhast.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.kodhast.dagboken.R
import se.kodhast.dagboken.data.auth.AuthUser
import se.kodhast.ui.components.AccountRow
import se.kodhast.ui.components.AppBottomSheet
import se.kodhast.ui.components.ItemRow
import se.kodhast.ui.components.SignOutRow

/** De fem loggvalen bakom plusknappen, i ordning (NAV-10). */
enum class LogChoice(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Mood(R.string.log_mood, R.drawable.ic_mood),
    Activity(R.string.log_activity, R.drawable.ic_activity),
    Dose(R.string.dose_label, R.drawable.ic_pill),
    Event(R.string.log_event, R.drawable.ic_event),
    Illness(R.string.log_illness, R.drawable.ic_thermometer)
}

/** Inställningsarkets rader i ordning (NAV-9) – var och en öppnar en underskärm med tillbakapil. */
enum class SettingsPage(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Profile(R.string.settings_profile, R.drawable.ic_person),
    Reminders(R.string.settings_reminders, R.drawable.ic_bell),
    Theme(R.string.settings_theme, R.drawable.ic_moon),
    Lists(R.string.settings_lists, R.drawable.ic_list),
    ExportImport(R.string.settings_export_import, R.drawable.ic_download),
    About(R.string.settings_about, R.drawable.ic_info)
}

/**
 * Inställningsarket bakom avataren (NAV-9) – appens fasta ark, byggt
 * på [AppBottomSheet] med en [ItemRow] per val: överst kontot ([account] i android-ui:s [AccountRow]:
 * foto eller initialer, namn och e-post, AUTH-3), sedan en rad per [SettingsPage] som [onOpen] öppnar, i debug-bygget
 * komponentgalleriet ([onOpenGallery] = `null` i release) och sist "Logga ut" ([SignOutRow]), som frågar
 * innan [onSignOut] anropas (AUTH-2). Kontoraden och Logga ut är android-ui:s, likadana i alla appar.
 */
@Composable
fun AccountSheet(
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onOpenGallery: (() -> Unit)?,
    modifier: Modifier = Modifier,
    account: AuthUser? = null,
    onOpen: ((SettingsPage) -> Unit)? = null
) {
    AppBottomSheet(stringResource(R.string.account_title), onDismiss, modifier) {
        account?.let { AccountRow(it.name, it.email, it.photoUrl) }
        onOpen?.let { open -> SettingsPage.entries.forEach { page -> SheetRow(page.label, page.icon, { open(page) }, navigates = true) } }
        onOpenGallery?.let { SheetRow(R.string.component_gallery, R.drawable.ic_widgets, it, navigates = true) }
        SignOutRow(stringResource(R.string.app_name), onSignOut)
    }
}

@Composable
private fun SheetRow(@StringRes label: Int, @DrawableRes icon: Int, onClick: () -> Unit, navigates: Boolean = false) {
    ItemRow(
        stringResource(label),
        // Samma ledande bredd som kontots avatar (och kryssraden), så att titlarna linjerar.
        icon = icon,
        onClick = onClick,
        navigates = navigates
    )
}
