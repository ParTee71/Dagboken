package se.partee71.dagboken.ui.theme

import androidx.compose.ui.unit.dp

/**
 * De enda ikonstorlekarna (skill `ui-style`) – en skala efter var ikonen sitter, så att samma sorts
 * ikon har samma storlek överallt. Ikonknappar och menyer använder Material-standarden [control].
 */
object IconSize {
    /** Markering i en dag (datumremsans bock). */
    val marker = 14.dp

    /** I en pill (`InfoPill`). */
    val pill = 16.dp

    /** I en ikonruta (`SectionHeader`) och i ett filterchip. */
    val tile = 18.dp

    /** I en knapp (`AppButton`, `AddSplitButton`) och i en vald cell. */
    val button = 20.dp

    /** I en rad eller ett mätvärde (kryss, `StatPill`, flikarna). */
    val row = 22.dp

    /** Ikonknappar, menyer och det kompakta tomma tillståndet – Materials standard. */
    val control = 24.dp

    /** Det tomma tillståndets stora ikon. */
    val hero = 56.dp
}
