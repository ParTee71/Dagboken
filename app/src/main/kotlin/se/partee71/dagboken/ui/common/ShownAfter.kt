package se.partee71.dagboken.ui.common

import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/**
 * Sant först när källan varit sann i [delay] – falskt direkt. Så blinkar inte en indikator
 * för något som går fort (synkindikatorn, NFR-1).
 */
fun Flow<Boolean>.shownAfter(delay: Duration): Flow<Boolean> = transformLatest { on ->
    if (on) delay(delay)
    emit(on)
}.distinctUntilChanged()
