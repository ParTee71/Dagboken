package se.partee71.dagboken.ui.theme

import androidx.compose.ui.unit.dp

/**
 * De enda ikonstorlekarna (skill `ui-style`) – en skala efter var ikonen sitter, så att samma sorts
 * ikon har samma storlek överallt.
 */
object IconSize {
    /** Markering i en dag (datumremsans bock). */
    val marker = 14.dp

    /** I en pill (`InfoPill`). */
    val pill = 16.dp

    /** I en rad eller ett mätvärde (kryss, `StatPill`, flikarna). */
    val row = 22.dp
}
