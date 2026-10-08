package se.partee71.dagboken.data.legacy

import android.content.Context
import androidx.work.WorkManager
import androidx.work.await
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import se.partee71.dagboken.BuildConfig
import se.partee71.dagboken.core.legacy.LegacyRoomSchema

// Små enhetsberoenden runt migreringen (OMB-2), var och en bakom ett gränssnitt så att use caset testas mot fejkar.

/** Det 3.x lämnade kvar i WorkManager. */
interface LegacyWork {
    /** Avbokar 3.x:s periodiska Drive-backup (`dagboken_daily_backup`) – dess worker finns inte i 4.0 – och väntar in att det gick; ett fel kastas. */
    suspend fun cancelBackupJob()
}

class WorkManagerLegacyWork @Inject constructor(@ApplicationContext private val context: Context) : LegacyWork {
    override suspend fun cancelBackupJob() {
        WorkManager.getInstance(context).cancelUniqueWork(LegacyRoomSchema.BACKUP_WORK_NAME).await()
    }
}

/** Appens `versionName` – skrivs i migreringsmarkören. */
class AppVersion @Inject constructor() {
    val name: String get() = BuildConfig.VERSION_NAME
}
