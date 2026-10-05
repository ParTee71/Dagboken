package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.data.auth.AuthUser

/** De fem loggvalen bakom plusknappen, i ordning (NAV-10). */
enum class LogChoice(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Mood(R.string.log_mood, R.drawable.ic_mood),
    Activity(R.string.log_activity, R.drawable.ic_activity),
    Dose(R.string.log_dose, R.drawable.ic_pill),
    Event(R.string.log_event, R.drawable.ic_event),
    Illness(R.string.log_illness, R.drawable.ic_thermometer),
}

/** Inställningsarkets rader i ordning (NAV-9) – var och en öppnar en underskärm med tillbakapil. */
enum class SettingsPage(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Profile(R.string.settings_profile, R.drawable.ic_person),
    Reminders(R.string.settings_reminders, R.drawable.ic_bell),
    Theme(R.string.settings_theme, R.drawable.ic_moon),
    Lists(R.string.settings_lists, R.drawable.ic_list),
    ExportImport(R.string.settings_export_import, R.drawable.ic_download),
    About(R.string.settings_about, R.drawable.ic_info),
}

/**
 * Inställningsarket bakom avataren (NAV-9) – ett av appens två fasta ark, som [LogMenuSheet] byggt
 * på [AppBottomSheet] med en [ItemRow] per val: överst kontot ([account]: foto, namn, e-post och
 * "Inloggad med Google", AUTH-3), sedan en rad per [SettingsPage] som [onOpen] öppnar, i debug-bygget
 * komponentgalleriet ([onOpenGallery] = `null` i release) och sist "Logga ut", som frågar med
 * [ConfirmDialog] innan [onSignOut] anropas (AUTH-2).
 */
@Composable
fun AccountSheet(
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onOpenGallery: (() -> Unit)?,
    modifier: Modifier = Modifier,
    account: AuthUser? = null,
    onOpen: ((SettingsPage) -> Unit)? = null,
) {
    AppBottomSheet(stringResource(R.string.account_title), onDismiss, modifier) {
        account?.let { AccountRow(it) }
        onOpen?.let { open -> SettingsPage.entries.forEach { page -> SheetRow(page.label, page.icon, { open(page) }, navigates = true) } }
        onOpenGallery?.let { SheetRow(R.string.component_gallery, R.drawable.ic_widgets, it, navigates = true) }
        var confirmSignOut by rememberSaveable { mutableStateOf(false) }
        SheetRow(R.string.sign_out, R.drawable.ic_logout, { confirmSignOut = true })
        if (confirmSignOut) {
            ConfirmDialog(
                title = stringResource(R.string.sign_out_title),
                message = stringResource(R.string.sign_out_message),
                confirmLabel = stringResource(R.string.sign_out),
                onConfirm = {
                    confirmSignOut = false
                    onSignOut()
                },
                onDismiss = { confirmSignOut = false },
            )
        }
    }
}

/** Kontot överst i arket: foto eller initialer, namn, e-post och inloggningssättet (AUTH-3). */
@Composable
private fun AccountRow(account: AuthUser) {
    val provider = stringResource(R.string.account_signed_in_google)
    val title = account.name ?: account.email ?: provider
    val subtitle = listOfNotNull(account.email?.takeIf { it != title }, provider.takeIf { it != title })
    ItemRow(
        title,
        subtitle = subtitle.joinToString("\n").ifEmpty { null },
        leading = { AccountAvatar(account.name ?: account.email, onClick = null, photoUrl = account.photoUrl) },
        tinted = true,
    )
}

/** Plusknappens loggmeny (NAV-10): exakt fem val. [onPick] får valet. */
@Composable
fun LogMenuSheet(onDismiss: () -> Unit, onPick: (LogChoice) -> Unit, modifier: Modifier = Modifier) {
    AppBottomSheet(stringResource(R.string.log_menu_title), onDismiss, modifier) {
        LogChoice.entries.forEach { choice -> SheetRow(choice.label, choice.icon, { onPick(choice) }) }
    }
}

@Composable
private fun SheetRow(@StringRes label: Int, @DrawableRes icon: Int, onClick: () -> Unit, navigates: Boolean = false) {
    ItemRow(
        stringResource(label),
        leading = { Icon(painterResource(icon), contentDescription = null) },
        onClick = onClick,
        navigates = navigates,
    )
}
