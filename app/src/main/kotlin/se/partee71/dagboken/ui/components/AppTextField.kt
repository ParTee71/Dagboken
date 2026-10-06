package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import se.partee71.dagboken.core.schema.TextLimits
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography

/**
 * Det enda textfältet: etikett, och under fältet antingen [error] (röd, läses av TalkBack som
 * fel) eller [helper]. Fält i formulär får sina fel från `EditorState`. Tangentbordet börjar
 * med stor bokstav (NFR-18); ett fält som anger egna [keyboardOptions], t.ex. siffror, styr själv.
 * Texten växer inte förbi [maxLength] – samma tak som rules har (NFR-16); en inklistring som
 * skulle gå över ignoreras hellre än kapas mitt i. En lagrad längre text (från verktygen) kortas
 * aldrig av, men måste kortas under taket för att gå att spara. [suffix] står efter värdet inne i
 * fältet, nedtonat – t.ex. enheten på en doshöjning ("25 mg"). [valueIsPlaceholder] tonar ned värdet som
 * en platshållare – för `PickerField`, som visar en text i stället för ett tomt värde (`DateField(emptyLabel)`).
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    helper: String? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    @DrawableRes trailingIcon: Int? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    maxLength: Int = if (singleLine) TextLimits.SHORT else TextLimits.LONG,
    suffix: String? = null,
    valueIsPlaceholder: Boolean = false,
) {
    val card = AppColors.extended.card
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength || it.length < value.length) onValueChange(it) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = if (valueIsPlaceholder) AppTypography.body.copy(color = MaterialTheme.colorScheme.onSurfaceVariant) else AppTypography.body,
        label = { Text(label) },
        trailingIcon = trailingIcon?.let { { Icon(painterResource(it), contentDescription = null) } },
        suffix = suffix?.let { { Text(it, style = AppTypography.body, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        supportingText = (error ?: helper)?.let { { Text(it, style = AppTypography.itemSubtitle) } },
        isError = error != null,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        shape = AppShapes.field,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = card,
            unfocusedContainerColor = card,
            disabledContainerColor = card,
            errorContainerColor = card,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

