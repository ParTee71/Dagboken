package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

enum class IconButtonVariant { Tonal, Plain }

/** Den enda ikonknappen: minst 48 dp och alltid en [contentDescription] för TalkBack. */
@Composable
fun AppIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: IconButtonVariant = IconButtonVariant.Plain,
    enabled: Boolean = true,
) {
    val sized = modifier.size(TOUCH_TARGET)
    val content: @Composable () -> Unit = { Icon(painterResource(icon), contentDescription) }
    when (variant) {
        IconButtonVariant.Tonal -> FilledTonalIconButton(onClick, sized, enabled, content = content)
        IconButtonVariant.Plain -> IconButton(onClick, sized, enabled, content = content)
    }
}

