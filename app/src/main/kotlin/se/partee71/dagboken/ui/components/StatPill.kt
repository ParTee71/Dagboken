package se.partee71.dagboken.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Appens nyckeltalsbricka — ikon, värde och etikett i en färgad yta.
 *
 * Anges [onClick] blir hela brickan tryckyta med `Role.Button` och minst
 * [MIN_TOUCH_TARGET] i höjd, på samma sätt som listraden (NFR-17) och den ihopfällbara
 * sektionen ([Foldout], NFR-18). Utan [onClick] är brickan ren avläsning, precis som förut
 * — de allra flesta är det. Bygg inte en klickbar variant vid sidan om (regel 4).
 *
 * [onClickLabel] beskriver **åtgärden** för skärmläsaren ("Begär åtkomst"), inte innehållet.
 */
@Composable
fun StatPill(
    icon: ImageVector,
    value: String,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Surface(
        color        = containerColor,
        contentColor = contentColor,
        shape        = MaterialTheme.shapes.large,
        modifier     = if (onClick == null) {
            modifier
        } else {
            modifier
                .heightIn(min = MIN_TOUCH_TARGET)
                .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
        },
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(22.dp),
            )
            Column {
                Text(
                    text       = value,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
        }
    }
}
