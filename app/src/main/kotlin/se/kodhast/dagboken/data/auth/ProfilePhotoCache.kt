package se.kodhast.dagboken.data.auth

import android.content.Context
import coil3.SingletonImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import se.kodhast.dagboken.data.common.suspendRunCatching
import se.kodhast.dagboken.di.IoDispatcher

/**
 * Profilfotots cache på enheten (AUTH-3): Coil sparar fotot i appens privata cache (`cacheDir`, som Android inte
 * säkerhetskopierar), så att det syns inloggat utan nät. Ett utbytt foto har en ny adress och blir en ny post.
 */
interface ProfilePhotoCache {
    /** Tar bort de sparade bilderna – vid utloggning (AUTH-2). Ett fel här stoppar inte utloggningen. */
    suspend fun clear(): Result<Unit>
}

/**
 * [ProfilePhotoCache] i Coils gemensamma bildladdare. Profilfotot är appens enda bild från nätet (skill
 * `data-privacy-security`), så hela Coils cache töms – minne och disk.
 */
class CoilProfilePhotoCache @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : ProfilePhotoCache {
    override suspend fun clear(): Result<Unit> = suspendRunCatching {
        withContext(io) {
            val loader = SingletonImageLoader.get(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
    }
}
