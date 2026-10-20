package se.partee71.dagboken.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics

/**
 * Långtrycket som extra åtgärd på ett element (SCR-9: "Starkare" på den yttersta valda känslan) – **en** gest, en haptik
 * och en TalkBack-åtgärd, så att samma långtryck ser och beter sig likadant överallt (regel 4). Läggs utanpå elementets
 * eget tryck (en knapp, en chip eller en ren visning): långtrycket upptäcks före elementet ([PointerEventPass.Initial])
 * och tar då resten av gesten, så att det vanliga trycket inte också körs; ett kort tryck eller en rörelse går igenom
 * orört. TalkBack får [label] som anpassad åtgärd, eftersom långtryck är svårt med skärmläsare. [onLongPress] `null` =
 * ingen åtgärd, elementet är som förut. [haptic] är `LocalHapticFeedback.current` hos anroparen.
 */
fun Modifier.longPressAction(label: String, haptic: HapticFeedback, onLongPress: (() -> Unit)?): Modifier {
    if (onLongPress == null) return this
    return this then LongPressElement(haptic, onLongPress) then Modifier.semantics {
        customActions = listOf(
            CustomAccessibilityAction(label) {
                onLongPress()
                true
            }
        )
    }
}

// Ett eget element i stället för pointerInput(onLongPress): en ny lambda vid varje komposition – och långtrycket ändrar
// ofta det som ritar om – får inte starta om gesten mitt i, för då når släppet elementets eget tryck.
private data class LongPressElement(val haptic: HapticFeedback, val onLongPress: () -> Unit) : ModifierNodeElement<LongPressNode>() {
    override fun create() = LongPressNode(haptic, onLongPress)

    override fun update(node: LongPressNode) {
        node.haptic = haptic
        node.onLongPress = onLongPress
    }
}

private class LongPressNode(var haptic: HapticFeedback, var onLongPress: () -> Unit) : DelegatingNode() {
    init {
        delegate(SuspendingPointerInputModifierNode { awaitEachGesture { longPress() } })
    }

    private suspend fun AwaitPointerEventScope.longPress() {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var ended = false
            while (!ended) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                ended = change == null || !change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            }
        }
        if (released != null) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onLongPress()
        // Resten av gesten hör till långtrycket: elementets eget tryck ser ett avbrutet tryck.
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
