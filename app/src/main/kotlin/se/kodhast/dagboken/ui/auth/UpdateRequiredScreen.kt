package se.kodhast.dagboken.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import se.kodhast.dagboken.R
import se.kodhast.ui.components.AppButton
import se.kodhast.ui.components.EmptyState

/** Användarens data är sparad med en nyare app: inget visas eller ändras förrän appen uppdaterats. */
@Composable
fun UpdateRequiredScreen(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val releasesUrl = stringResource(R.string.releases_url)
    EmptyState(
        icon = R.drawable.ic_download,
        title = stringResource(R.string.update_required_title),
        message = stringResource(R.string.update_required_message),
        modifier = modifier,
        note = stringResource(R.string.update_required_note),
        fullScreen = true,
        action = {
            AppButton(
                text = stringResource(R.string.update_required_action),
                onClick = { uriHandler.openUri(releasesUrl) },
                modifier = Modifier.fillMaxWidth(),
                icon = R.drawable.ic_download
            )
        }
    )
}
