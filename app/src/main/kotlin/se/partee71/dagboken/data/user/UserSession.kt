package se.partee71.dagboken.data.user

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.core.schema.SchemaMigrator
import se.partee71.dagboken.data.auth.AuthRepository
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.UserScope
import se.partee71.dagboken.data.common.UserVersion
import se.partee71.dagboken.data.common.UserVersionSource
import se.partee71.dagboken.di.ApplicationScope

/**
 * Den inloggade användaren: uid från Firebase Auth ([AuthRepository]), versionen ur
 * `users/{uid}`. Utloggad finns ingen användare och samlingarna visar ingenting.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class UserSession @Inject constructor(
    auth: AuthRepository,
    private val versions: UserVersionSource,
    @ApplicationScope scope: CoroutineScope,
) : UserScope {

    override val uid: StateFlow<String?> =
        auth.authState.map { it?.uid }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)

    private val _unreadable = MutableStateFlow<String?>(null)
    override val unreadable: StateFlow<String?> = _unreadable.asStateFlow()

    override val schemaVersion: StateFlow<UserVersion?> =
        uid.flatMapLatest { id ->
            if (id == null) {
                emptyFlow()
            } else {
                versions.schemaVersion(id)
                    .onEach { version -> if (version != null) stampIfDataFree(id, version) }
                    .map { UserVersion(id, it ?: Schema.FIRST_VERSION, exists = it != null) }
                    .onEach { _unreadable.compareAndSet(id, null) }
                    .catch { error ->
                        // Källan försöker själv igen vid andra fel; bara en nekad läsning når hit.
                        // Skrivningar nekas då (writeBlocker) och nästa start försöker igen.
                        if (error == DataError.PermissionDenied) _unreadable.value = id
                    }
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Användare som stämplats den här körningen – en nekad stämpling upprepas inte i en slinga. */
    private val stamped = mutableSetOf<String>()

    /**
     * Ett äldre användardokument vars väg till appens version inte ändrar några dokument stämplas
     * direkt, så att en äldre app på en annan enhet ber om uppdatering i stället för att läsa ett
     * nytt värde fel och skriva tillbaka det. Kräver dokumentändringar körs `migrate.mjs`.
     */
    private fun stampIfDataFree(uid: String, version: Int) {
        if (SchemaMigrator.canStamp(version) && synchronized(stamped) { stamped.add(uid) }) {
            versions.stamp(uid, Schema.CURRENT_VERSION)
        }
    }
}
