package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppTypography

/**
 * Avataren uppe till höger på varje flik (NAV-9): öppnar inställningsarket med [onClick]. Visar
 * profilfotot – [photo] (en slot) eller [photoUrl], som laddas med Coil bara till minnet, aldrig till
 * disk (AUTH-3) – ovanpå initialerna ur [name] ("Anna Berg" → "AB"); utan namn (utloggad eller okänt)
 * en person-ikon. Går fotot inte att hämta (inget nät) syns initialerna. Tryckytan är 48 dp och TalkBack
 * läser "Konto och inställningar" som en knapp. Läggs i `AppTopBar(actions = …)`. Utan [onClick] är
 * avataren bara en bild (kontokortet i inställningsarket, där namnet står bredvid) och läses inte upp.
 */
@Composable
fun AccountAvatar(
    name: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    photoUrl: String? = null,
    photo: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.account_open)
    val initials = name?.let(::initials)
    val interaction = if (onClick == null) {
        Modifier.clearAndSetSemantics { }
    } else {
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
    }
    Box(
        modifier
            .size(TOUCH_TARGET)
            .clip(CircleShape)
            .then(interaction),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(AVATAR).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
            // Initialerna ligger alltid under fotot: syns tills fotot laddats, och om det aldrig gör det.
            if (initials != null) {
                Text(initials, style = AppTypography.button, color = colors.onPrimaryContainer, maxLines = 1)
            } else {
                Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = colors.onPrimaryContainer)
            }
            when {
                photo != null -> photo()
                photoUrl != null -> RemotePhoto(photoUrl)
            }
        }
    }
}

/** Fotot från kontot (Google) – bara i minnescachen: aldrig sparat på enheten (AUTH-3, skill data-privacy-security). */
@Composable
private fun RemotePhoto(url: String) {
    val context = LocalPlatformContext.current
    val request = remember(url, context) {
        ImageRequest.Builder(context).data(url).diskCachePolicy(CachePolicy.DISABLED).build()
    }
    AsyncImage(request, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}

/** Första bokstaven i det första och det sista ordet, versalt: "Anna Berg" → "AB", "anna" → "A"; inget namn → `null`. */
internal fun initials(name: String): String? {
    val words = name.trim().split(Regex("\\s+")).filter { word -> word.any { it.isLetter() } }
    if (words.isEmpty()) return null
    return listOf(words.first(), words.last()).take(if (words.size > 1) 2 else 1)
        .map { word -> word.first { it.isLetter() }.uppercaseChar() }
        .joinToString("")
}

/** Den synliga cirkeln; tryckytan runt den är [TOUCH_TARGET]. */
private val AVATAR = 36.dp
