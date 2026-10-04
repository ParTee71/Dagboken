package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

enum class ButtonVariant {
    Primary,
    Secondary,
    Text,

    /** Permanent radering – bara i en `ConfirmDialog` med `destructive = true`. */
    Destructive,
}

/**
 * Den enda knappen (skill shared-ui-components). [loading] visar en snurra i stället för
 * ikonen och gör knappen inaktiv – samma "arbetar"-läge överallt. [compact] ger en lägre knapp
 * för rubrikraden (t.ex. "Spara" i `EntityEditScreen`); tryckytan är ändå minst 48 dp.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
) {
    val sized = modifier.heightIn(min = if (compact) COMPACT_HEIGHT else MIN_HEIGHT)
    val active = enabled && !loading
    val content: @Composable RowScope.() -> Unit = { ButtonContent(text, icon, loading) }
    when (variant) {
        ButtonVariant.Primary ->
            Button(onClick, sized, active, AppShapes.pill, ButtonDefaults.buttonColors().busy(loading), content = content)
        ButtonVariant.Secondary ->
            FilledTonalButton(onClick, sized, active, AppShapes.pill, ButtonDefaults.filledTonalButtonColors().busy(loading), content = content)
        ButtonVariant.Text ->
            TextButton(onClick, sized, active, AppShapes.pill, ButtonDefaults.textButtonColors().busy(loading), content = content)
        ButtonVariant.Destructive -> {
            val colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
            Button(onClick, sized, active, AppShapes.pill, colors.busy(loading), content = content)
        }
    }
}

/** En knapp som arbetar är inaktiv men behåller sina färger – den ser upptagen ut, inte avstängd. */
private fun ButtonColors.busy(loading: Boolean) =
    if (loading) copy(disabledContainerColor = containerColor, disabledContentColor = contentColor) else this

@Composable
private fun ButtonContent(text: String, @DrawableRes icon: Int?, loading: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(ICON_SIZE),
                color = LocalContentColor.current,
                strokeWidth = 2.5.dp,
            )
            icon != null -> Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ICON_SIZE))
        }
        if (loading || icon != null) Spacer(Modifier.width(Spacing.s))
        Text(text, style = AppTypography.button)
    }
}

/** Tryckyta och höjd enligt mockupen – stor nog för tummen. */
private val MIN_HEIGHT = 56.dp
private val COMPACT_HEIGHT = 40.dp
private val ICON_SIZE = 20.dp
