@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package se.partee71.dagboken.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.IconSize
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Kryssrad: en [ItemRow] med ett formmorfande kryss – en ring som fjädrar till en fylld
 * "kaka" med bock – och dämpad "klar"-stil när den är ikryssad (skill `ui-style`).
 * Hela raden är tryckytan; TalkBack läser den som en kryssruta. Med [onClick] är krysset en egen
 * knapp (läses med titel och undertext, t.ex. "Levaxin 50 µg, 9 tabletter") och resten av raden
 * öppnar detaljer – som en dos detaljer ([onClickLabel] läses upp). [accent] är en statusfärg i vänsterkanten (NFR-16).
 * [note] med text ger anteckningsikonen efter [trailing] (MED-12). [menu] är radens övriga åtgärder
 * (t.ex. "Hoppa över" på en dos, MED-3): de nås med långtryck på raden och med `⋮` sist (NFR-17).
 * [below] står under titel och undertext (t.ex. dosens "Försenat"), så att titeln behåller bredden.
 * [enabled] = false visar tillståndet utan att det går att växla (t.ex. en loggad vid behov-dos, som tas
 * bort i stället för att bockas av); TalkBack läser den som en inaktiv kryssruta, och en [menu] nås ändå med
 * långtryck och `⋮`.
 */
@Composable
fun CheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    accent: Color? = null,
    note: String = "",
    menu: List<AppMenuItem> = emptyList(),
    below: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val toggle = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
    val openMenu = if (menu.isEmpty()) null else ({ menuOpen = true })
    val menuLabel = stringResource(R.string.more_options)
    val interaction = when {
        onClick != null -> Modifier.combinedClickable(onClickLabel = onClickLabel, onLongClickLabel = menuLabel.takeIf { openMenu != null }, onLongClick = openMenu, onClick = onClick)
        openMenu == null -> toggle
        // Inaktiv med meny (en loggad vid behov-dos): krysset växlar inte, men långtrycket öppnar menyn (NFR-17) –
        // samma roll, tillstånd och "inaktiv" för TalkBack som utan meny.
        !enabled -> Modifier
            .combinedClickable(role = Role.Checkbox, onLongClickLabel = menuLabel, onLongClick = openMenu) {}
            .semantics {
                toggleableState = ToggleableState(checked)
                disabled()
            }
        // Långtrycket kräver en klickyta i stället för toggleable – samma roll och tillstånd för TalkBack.
        else -> Modifier
            .combinedClickable(role = Role.Checkbox, onLongClickLabel = menuLabel, onLongClick = openMenu) { onCheckedChange(!checked) }
            .semantics { toggleableState = ToggleableState(checked) }
    }
    val end: (@Composable () -> Unit)? = if (trailing == null && note.isBlank() && menu.isEmpty()) {
        null
    } else {
        {
            trailing?.invoke()
            NoteIndicator(note, title)
            if (menu.isNotEmpty()) {
                Box {
                    AppIconButton(R.drawable.ic_more_vert, menuLabel, onClick = { menuOpen = true })
                    AppMenuPopup(menu, menuOpen, onDismiss = { menuOpen = false })
                }
            }
        }
    }
    ItemRowLayout(
        title = title,
        modifier = modifier,
        interaction = interaction,
        subtitle = subtitle,
        leading = {
            // Samma storlek i båda varianterna, så att titlarna linjerar; med onClick är rutan knappen.
            val label = listOfNotNull(title, subtitle).joinToString(", ")
            val button = if (onClick == null) Modifier else Modifier.clip(CircleShape).then(toggle).semantics { contentDescription = label }
            LeadingSlot(button) { MorphingCheck(checked) }
        },
        trailing = end,
        tinted = checked,
        done = checked,
        accent = accent,
        // Under texten, i linje med titeln (efter krysset).
        below = below?.let { { Box(Modifier.padding(start = TOUCH_TARGET + Spacing.m)) { it() } } },
    )
}

@Composable
private fun MorphingCheck(checked: Boolean) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "kryss",
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.size(SIZE), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(SIZE)) {
            val path = morph.toPath(progress)
            val stroke = STROKE.toPx()
            val inner = size.minDimension - stroke
            val offset = Offset(stroke / 2, stroke / 2)
            translate(offset.x, offset.y) {
                scale(inner, pivot = Offset.Zero) {
                    if (progress < 1f) drawPath(path, color, alpha = 1f - progress, style = Stroke(width = stroke / inner))
                    if (progress > 0f) drawPath(path, color, alpha = progress)
                }
            }
        }
        Icon(
            painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(IconSize.row).alpha(progress.coerceIn(0f, 1f)),
        )
    }
}

private val SIZE = 40.dp
private val STROKE = 2.5.dp
