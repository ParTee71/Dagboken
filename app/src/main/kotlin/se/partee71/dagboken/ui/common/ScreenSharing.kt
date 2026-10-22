package se.partee71.dagboken.ui.common

import kotlinx.coroutines.flow.SharingStarted
import se.partee71.ui.common.STOP_TIMEOUT_MILLIS

/**
 * Hur en skärms delade flöden (`stateIn`/`shareIn` i ViewModels) startas och stoppas – en gång för alla: de följer
 * skärmen och stoppas [STOP_TIMEOUT_MILLIS] efter sista lyssnaren. Med [forgetWhenStopped] (standard) glömmer de sitt
 * senaste värde när de stoppats (en ny dag kan ha börjat); utan behåller de det till nästa gång.
 */
fun screenSharing(forgetWhenStopped: Boolean = true): SharingStarted = if (forgetWhenStopped) {
    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0)
} else {
    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS)
}
