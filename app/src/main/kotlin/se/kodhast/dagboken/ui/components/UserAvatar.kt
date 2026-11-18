package se.kodhast.dagboken.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import se.kodhast.dagboken.data.auth.AuthUser
import se.kodhast.ui.components.AccountAvatar

/**
 * Den inloggades avatar: android-ui:s `AccountAvatar` med [account]s namn (eller e-post) och profilfoto, som biblioteket
 * laddar med Coil och sparar på disk så att det syns utan nät (AUTH-3; utloggningen tömmer det med `ProfilePhotoCache`).
 * Med [onClick] sitter den i sidhuvudet och öppnar inställningsarket (NAV-9, NAV-12), med [online] som anslutningsplupp;
 * utan är den en ren bild (kontokortet i arket).
 */
@Composable
fun UserAvatar(account: AuthUser?, onClick: (() -> Unit)?, modifier: Modifier = Modifier, online: Boolean? = null) {
    AccountAvatar(account?.name ?: account?.email, onClick, modifier, online = online, photoUrl = account?.photoUrl)
}
