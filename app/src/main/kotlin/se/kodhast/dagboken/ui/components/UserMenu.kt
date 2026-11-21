package se.kodhast.dagboken.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.kodhast.dagboken.R
import se.kodhast.dagboken.data.auth.AuthUser
import se.kodhast.ui.components.AccountInfo
import se.kodhast.ui.components.AccountMenuAvatar

/**
 * Den inloggades avatar i sidhuvudet med avatarmenyn under (NAV-9, NAV-12): android-ui:s `AccountMenuAvatar` med [account]s namn
 * (eller e-post) och profilfoto, som biblioteket laddar med Coil och sparar på disk så att det syns utan nät (AUTH-3; utloggningen
 * tömmer det med `ProfilePhotoCache`), och [online] som anslutningsplupp. Menyns ordning är bibliotekets och samma i alla appar:
 * kontot, Inställningar ([onOpenSettings]), i debug-bygget Komponentgalleri ([onOpenGallery], `null` i release), Om Dagboken
 * ([onOpenAbout]) och Logga ut, som frågar innan [onSignOut] anropas (AUTH-2). Dagboken visas bara inloggad, så ingen Logga in-rad.
 */
@Composable
fun UserMenu(
    account: AuthUser?,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    online: Boolean? = null,
    onOpenGallery: (() -> Unit)? = null
) {
    AccountMenuAvatar(
        account = account?.let { AccountInfo(it.name, it.email, it.photoUrl) },
        appName = stringResource(R.string.app_name),
        onOpenSettings = onOpenSettings,
        onOpenAbout = onOpenAbout,
        onSignIn = null,
        onSignOut = onSignOut,
        modifier = modifier,
        online = online,
        onOpenGallery = onOpenGallery
    )
}
