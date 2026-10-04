package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R

/** De fem loggvalen bakom plusknappen, i ordning (NAV-10). */
enum class LogChoice(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Mood(R.string.log_mood, R.drawable.ic_mood),
    Activity(R.string.log_activity, R.drawable.ic_activity),
    Dose(R.string.log_dose, R.drawable.ic_pill),
    Event(R.string.log_event, R.drawable.ic_event),
    Illness(R.string.log_illness, R.drawable.ic_thermometer),
}

/**
 * Inställningsarket bakom avataren (NAV-9) – ett av appens två fasta ark, som [LogMenuSheet] byggt
 * på [AppBottomSheet] med en [ItemRow] per val. Etapp 1 har bara "Logga ut" och, i debug-bygget,
 * komponentgalleriet ([onOpenGallery] = `null` i release); resten av arket kommer i etapp 5.
 */
@Composable
fun AccountSheet(onDismiss: () -> Unit, onSignOut: () -> Unit, onOpenGallery: (() -> Unit)?, modifier: Modifier = Modifier) {
    AppBottomSheet(stringResource(R.string.account_title), onDismiss, modifier) {
        SheetRow(R.string.sign_out, R.drawable.ic_logout, onSignOut)
        onOpenGallery?.let { SheetRow(R.string.component_gallery, R.drawable.ic_widgets, it, navigates = true) }
    }
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
