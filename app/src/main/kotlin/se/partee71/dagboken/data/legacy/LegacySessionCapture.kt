package se.partee71.dagboken.data.legacy

import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.auth.AuthRepository
import se.partee71.dagboken.di.ApplicationScope

/**
 * Fångar 3.x-sessionen vid **första starten** av 4.0, före inloggningsgrinden: Firebase Auth bevarar 3.x:s inloggning
 * över uppdateringen, så `currentUser` då är kontot 3.x använde – eller inget, om 3.x kördes utan konto. Sparas en
 * gång (bara när Room-filen finns) och avgör i bekräftelsesteget om kontot är detsamma, ett annat eller okänt; okänt
 * kräver att e-posten bekräftas uttryckligen – inga tysta antaganden. Inloggningen och enhetsläget hämtas först när
 * Room-filen finns ([Lazy]), så att en enhet utan 3.x – och varje JVM-test – aldrig rör Firebase vid appstart.
 */
@Singleton
class LegacySessionCapture @Inject constructor(
    private val room: LegacyRoomSource,
    private val state: Lazy<MigrationDeviceState>,
    private val auth: Lazy<AuthRepository>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Anropas från `Application.onCreate`; gör ingenting utan Room-fil eller när sessionen redan sparats. */
    fun capture() {
        if (!room.exists()) return
        val uid = auth.get().currentUid
        scope.launch { state.get().rememberSession(LegacySession(uid)) }
    }
}
