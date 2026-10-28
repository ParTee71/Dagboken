package se.partee71.dagboken.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Låser innehållet för beröring och svep medan [block] är sant (t.ex. ett ark i klart-läget, NFR-21): händelserna
 * förbrukas innan barnen ser dem. Default `false` = ingen skillnad.
 */
fun Modifier.blockInput(block: Boolean): Modifier = if (!block) {
    this
} else {
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}
