package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppTypography

/**
 * Avataren uppe till höger på varje flik (NAV-9): öppnar inställningsarket med [onClick]. Visar [photo]
 * (en slot – bildladdningen ägs av anroparen), annars initialerna ur [name] ("Anna Berg" → "AB"), och utan
 * namn (utloggad eller okänt) en person-ikon. Tryckytan är 48 dp och TalkBack läser "Konto och
 * inställningar" som en knapp. Läggs i `AppTopBar(actions = …)`.
 */
@Composable
fun AccountAvatar(
    name: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    photo: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.account_open)
    val initials = name?.let(::initials)
    Box(
        modifier
            .size(TOUCH_TARGET)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(AVATAR).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
            when {
                photo != null -> photo()
                initials != null -> Text(initials, style = AppTypography.button, color = colors.onPrimaryContainer, maxLines = 1)
                else -> Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = colors.onPrimaryContainer)
            }
        }
    }
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
