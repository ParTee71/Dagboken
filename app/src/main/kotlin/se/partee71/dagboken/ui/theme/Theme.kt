@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package se.partee71.dagboken.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Appens tema: Material 3 Expressive med fjädrande rörelse, [AppColors] och [AppTypography]
 * (skill `ui-style`, TP-2, DSN-1–5). Ingen dynamic color. [darkTheme] kommer från temavalet i
 * inställningsarket – ljust, mörkt eller auto på klockslag (SET-1, `AppThemeViewModel` i `MainActivity`);
 * utan val (utloggad) följer appen systemet.
 */
@Composable
fun DagbokenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalExtendedColors provides if (darkTheme) AppColors.darkExtended else AppColors.lightExtended) {
        MaterialExpressiveTheme(
            colorScheme = if (darkTheme) AppColors.dark else AppColors.light,
            motionScheme = MotionScheme.expressive(),
            typography = AppTypeScale,
            content = content,
        )
    }
}
