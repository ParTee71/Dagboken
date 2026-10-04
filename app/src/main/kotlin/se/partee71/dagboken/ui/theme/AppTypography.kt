package se.partee71.dagboken.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.partee71.dagboken.R

/** En vikt ur ett variabelt typsnitt – samma fil, olika `wght` (DSN-2). */
private fun variable(font: Int, weight: FontWeight) =
    Font(font, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Fraunces (serif) för rubriker. */
private val Fraunces = FontFamily(
    variable(R.font.fraunces, FontWeight.Medium),
    variable(R.font.fraunces, FontWeight.SemiBold),
)

/** Figtree för brödtext, knappar och etiketter. */
private val Figtree = FontFamily(
    variable(R.font.figtree, FontWeight.Normal),
    variable(R.font.figtree, FontWeight.Medium),
    variable(R.font.figtree, FontWeight.SemiBold),
    variable(R.font.figtree, FontWeight.Bold),
)

private fun serif(size: Int, line: Int, weight: FontWeight = FontWeight.SemiBold, spacing: Double = 0.0) =
    TextStyle(fontFamily = Fraunces, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.em)

private fun sans(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = Figtree, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

/** Material 3-typskalan med Fraunces (rubriker) och Figtree (brödtext) – används av [DagbokenTheme]. */
internal val AppTypeScale = Typography(
    displaySmall = serif(32, 38, spacing = -0.01),
    headlineMedium = serif(28, 34),
    headlineSmall = serif(22, 28),
    titleLarge = serif(19, 24, FontWeight.Medium),
    titleMedium = sans(16, 22, FontWeight.SemiBold),
    titleSmall = sans(14, 20, FontWeight.SemiBold),
    bodyLarge = sans(16, 24, FontWeight.Normal),
    bodyMedium = sans(14, 20, FontWeight.Normal),
    bodySmall = sans(13, 18, FontWeight.Medium),
    labelLarge = sans(15, 20, FontWeight.SemiBold),
    labelMedium = sans(13, 16, FontWeight.SemiBold),
    labelSmall = sans(12, 16, FontWeight.Bold).copy(letterSpacing = 0.05.em),
)

/**
 * Appens namngivna textstilar – det enda sättet feature-kod väljer typografi (skill `ui-style`, DSN-2).
 */
object AppTypography {
    /** Stor serifrubrik överst på en flik ("Idag"). */
    val screenTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.displaySmall

    /** Stor siffra, t.ex. dagens snittenergi. */
    val bigNumber: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineMedium

    /** Rubrik i dialoger och tomma tillstånd. */
    val headline: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineSmall

    /** Sektionsrubrik och rubrik på en underskärm. */
    val sectionTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleLarge

    /** Ett framträdande värde i ett kort ("7 h 45 min"). */
    val quantity: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleLarge

    /** Titeln på en listrad; avstavas i stället för att brytas mitt i ordet i smala kolumner. */
    val itemTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)

    /** Undertext och fotnoter. */
    val itemSubtitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall

    /** Löptext, t.ex. meningen i ett tomt tillstånd. */
    val body: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge

    /** Text i pills och chips. */
    val pill: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelMedium

    /** Liten rubrik över en grupp i inställningar ("UTSEENDE"). */
    val caption: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelSmall

    val button: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelLarge
}
