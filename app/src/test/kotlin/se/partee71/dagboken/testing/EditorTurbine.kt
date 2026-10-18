package se.partee71.dagboken.testing

import app.cash.turbine.Event
import app.cash.turbine.ReceiveTurbine
import kotlin.test.fail
import se.partee71.dagboken.ui.common.EditorEvents
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.Failure

/**
 * Väntar i formulärets tillstånd (`editor.state.test { … }`) tills formuläret är klart
 * ([EditorUiState.done]) och kvitterar som ramen gör ([EditorEvents.onDoneHandled]), så att nästa
 * klar väntas in likadant. Ett sparfel under tiden fäller testet.
 */
suspend fun <T> ReceiveTurbine<EditorUiState<T>>.awaitDone(events: EditorEvents): EditorUiState<T> {
    while (true) {
        val state = awaitItem()
        state.failure?.let { fail("sparfel i stället för klart: ${it.error}") }
        if (state.done) {
            events.onDoneHandled()
            return state
        }
    }
}

/**
 * Väntar tills formuläret har ett sparfel ([EditorUiState.failure]) och kvitterar det som ramen gör
 * ([EditorEvents.onFailureShown]). Klart under tiden fäller testet.
 */
suspend fun <T> ReceiveTurbine<EditorUiState<T>>.awaitFailure(events: EditorEvents): Failure {
    while (true) {
        val state = awaitItem()
        if (state.done) fail("klart i stället för sparfel")
        state.failure?.let { failure ->
            events.onFailureShown()
            return failure
        }
    }
}

/** Varken klart eller sparfel i tillstånden som kommit; avslutar `test`-blocket. */
suspend fun <T> ReceiveTurbine<EditorUiState<T>>.expectNoEditorEvents() {
    val states = cancelAndConsumeRemainingEvents().filterIsInstance<Event.Item<EditorUiState<T>>>().map { it.value }
    states.firstOrNull { it.done || it.failure != null }?.let { fail("oväntad händelse: done = ${it.done}, failure = ${it.failure?.error}") }
}
