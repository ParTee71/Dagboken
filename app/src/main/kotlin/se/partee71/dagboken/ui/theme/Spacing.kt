package se.partee71.dagboken.ui.theme

import androidx.compose.ui.unit.dp

/** De enda tillåtna avstånden (skill `ui-style`). */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Den bredaste en dialog blir – M3:s tak för dialoger (`ConfirmDialog` med ett fält, som sätter sin bredd själv). */
    val dialogMaxWidth = 560.dp
}
