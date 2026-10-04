package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Gemensam yta för [DateField] och [TimeField]: ett skrivskyddat fält som visar [shown] och
 * öppnar en väljare ([onPick]) vid tryck. TalkBack läser det som en knapp med etikett, värde och fel.
 */
@Composable
internal fun PickerField(
    label: String,
    shown: String,
    pickLabel: String,
    @DrawableRes icon: Int,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    helper: String? = null,
) {
    val description = listOf(label, shown.ifEmpty { pickLabel }, error.orEmpty()).filter { it.isNotEmpty() }.joinToString(", ")
    Box(modifier) {
        // Fältet visar bara värdet; ytan ovanpå är det enda som tar emot tryck och fokus.
        AppTextField(
            value = shown,
            onValueChange = {},
            label = label,
            modifier = Modifier.focusProperties { canFocus = false }.clearAndSetSemantics {},
            error = error,
            helper = helper,
            readOnly = true,
            trailingIcon = icon,
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(role = Role.Button, onClickLabel = pickLabel, onClick = onPick)
                .semantics { contentDescription = description },
        )
    }
}
