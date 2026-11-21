package se.kodhast.dagboken.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import se.kodhast.dagboken.R
import se.kodhast.dagboken.ui.components.SettingsPage
import se.kodhast.ui.common.DetailUiState
import se.kodhast.ui.components.AppCard
import se.kodhast.ui.components.AppDivider
import se.kodhast.ui.components.EntityDetailScreen
import se.kodhast.ui.components.ItemRow

/**
 * Inställningar, som avatarmenyns rad Inställningar öppnar (NAV-9): en detaljskärm med liten topprad och tillbakapil, och ett kort med en
 * rad per [SettingsPage] i ordning som [onOpen] öppnar. Kontot, Om Dagboken, galleriet och Logga ut står i avatarmenyn, inte här.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpen: (SettingsPage) -> Unit) {
    EntityDetailScreen(
        state = DetailUiState.Content(Unit),
        header = null,
        onBack = onBack,
        title = stringResource(R.string.account_title)
    ) {
        AppCard {
            SettingsPage.entries.forEachIndexed { index, page ->
                if (index > 0) AppDivider()
                ItemRow(stringResource(page.label), icon = page.icon, onClick = { onOpen(page) }, navigates = true)
            }
        }
    }
}
