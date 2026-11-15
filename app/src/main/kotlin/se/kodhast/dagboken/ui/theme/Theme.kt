package se.kodhast.dagboken.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import se.kodhast.ui.components.AppBrandStyle
import se.kodhast.ui.components.LocalAppBrandStyle
import se.kodhast.ui.components.LocalDetailSectionGap
import se.kodhast.ui.components.LocalEditScreenMargin
import se.kodhast.ui.components.LocalItemRowTextGap
import se.kodhast.ui.components.LocalQuantityStepperButtonSize
import se.kodhast.ui.components.QuantityStepperDefaults
import se.kodhast.ui.theme.AppTheme
import se.kodhast.ui.theme.Spacing

/**
 * Appens tema: android-ui:s `AppTheme` (Material 3 Expressive med fjädrande rörelse) med appens [AppColors],
 * [AppShapes] och [AppTypography] (skill `ui-style`, TP-2, DSN-1–5) – färger, former och typsnitt är appens,
 * biblioteket har inga egna. Listradernas avstånd mellan titel och undertext är `Spacing.xs` (NFR-17). Ramarnas mått
 * sätts här en gång: sidmarginalen i formulär (`LocalEditScreenMargin`) och avståndet mellan sektionerna i
 * detaljskärmar (`LocalDetailSectionGap`) är båda `Spacing.l`, och stegarens knappar är 48 dp
 * (`LocalQuantityStepperButtonSize`, NFR-14). Ingen dynamic color. [darkTheme] kommer från temavalet i inställningsarket – ljust, mörkt eller auto på klockslag
 * (SET-1, `AppThemeViewModel` i `MainActivity`); utan val (utloggad) följer appen systemet. Kodhäst-igenkänningen
 * (DSN-8) slås på här och bara här: prickar framför sektionsrubriker utan ikonruta, prickarna som laddning och
 * hästen i fullstora tomma lägen – ritade i appens färger.
 */
@Composable
fun DagbokenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) AppColors.darkExtended else AppColors.lightExtended,
        LocalItemRowTextGap provides Spacing.xs,
        LocalEditScreenMargin provides Spacing.l,
        LocalDetailSectionGap provides Spacing.l,
        LocalQuantityStepperButtonSize provides QuantityStepperDefaults.TouchTargetButtonSize,
        LocalAppBrandStyle provides BrandStyle
    ) {
        AppTheme(
            colorScheme = if (darkTheme) AppColors.dark else AppColors.light,
            extendedColors = if (darkTheme) AppColors.darkTheme else AppColors.lightTheme,
            shapes = AppShapes.theme,
            typeScale = AppTypeScale,
            motionScheme = MotionScheme.expressive()
        ) {
            // Innehållsfärgen är onSurface också utanför en skärmram, så att AppCard och ikoner utan egen färg
            // får samma färg överallt (android-ui:s AppCard ärver den omgivande).
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
        }
    }
}

/** Kodhäst-delarna i innehållet (DSN-8), alla tre påslagna. */
private val BrandStyle = AppBrandStyle(sectionDots = true, dotLoading = true, emptyStateMark = true)
