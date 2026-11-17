package se.kodhast.dagboken.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import se.kodhast.dagboken.data.auth.AuthUser
import se.kodhast.ui.components.AccountAvatar

/**
 * Den inloggades avatar: android-ui:s `AccountAvatar` med [account]s namn (eller e-post) och profilfoto. Med [onClick]
 * sitter den i sidhuvudet och öppnar inställningsarket (NAV-9, NAV-12), med [online] som anslutningsplupp; utan är den
 * en ren bild (kontokortet i arket).
 */
@Composable
fun UserAvatar(account: AuthUser?, onClick: (() -> Unit)?, modifier: Modifier = Modifier, online: Boolean? = null) {
    val photo = account?.photoUrl
    AccountAvatar(account?.name ?: account?.email, onClick, modifier, online = online, photo = photo?.let { url -> { AccountPhoto(url) } })
}

/**
 * Profilfotot (Google), laddat med Coil och sparat i appens privata cache, så att det syns inloggat utan nät (AUTH-3);
 * utloggningen tar bort det (`ProfilePhotoCache`). Ett utbytt foto har en ny adress och hämtas på nytt. Går det inte
 * att hämta syns avatarens initialer under.
 */
@Composable
private fun AccountPhoto(url: String) {
    AsyncImage(url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}
