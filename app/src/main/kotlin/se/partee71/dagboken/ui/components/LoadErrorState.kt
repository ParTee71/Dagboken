package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.ui.common.toMessage

/** Läsfelet i ramarna (lista, redigering, detalj): samma ikon, text och "Försök igen" överallt – utom för något som inte finns längre (NFR-1, NFR-5). */
@Composable
internal fun LoadErrorState(title: String, error: DataError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = if (error == DataError.NotFound) R.drawable.ic_box else R.drawable.ic_wifi_off,
        title = title,
        message = stringResource(error.toMessage()),
        modifier = modifier,
        isError = true,
        // Något som inte finns längre blir inte bättre av ett nytt försök – då bara tillbaka.
        action = if (error == DataError.NotFound) null else {
            { AppButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary) }
        },
    )
}
