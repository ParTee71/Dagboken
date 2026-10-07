package se.partee71.dagboken.data.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.net.toUri
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.partee71.dagboken.core.engine.health.OptionalHealthMetric

/**
 * Behörighetsflödet mot Health Connect (HLS-3, HLS-4, HLS-14). Aktiviteten kopplar in sig med [attach] – samtyckesdialogen
 * kräver en launcher registrerad i dess `onCreate` – och porten läser om läget när dialogen stängts och när appen
 * kommer tillbaka i förgrunden ([HealthConnectAccess.changed]). Utan inkopplad aktivitet gör "Ge åtkomst" ingenting.
 */
@Singleton
class HealthPermissionsImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val access: HealthConnectAccess,
) : HealthPermissions {
    private var launcher: ActivityResultLauncher<Set<String>>? = null

    /** Ur det delade läget; `null` när det inte gick att läsa (HLS-14: hellre ingen rad än en gissning). */
    override val missingOptional: Flow<Set<OptionalHealthMetric>?> = access.current.map { it.getOrNull()?.missingOptional }

    /**
     * Registrerar samtyckesdialogen i [activity] (före `STARTED`, alltså i `onCreate`). Kontraktet tar bara
     * behörighetsuppsättningen; svaret är inte intressant i sig – läget läses om ur Health Connect. En ny aktivitet
     * (t.ex. efter rotation) tar över; den gamlas `onDestroy` nollar bara sin egen launcher.
     */
    fun attach(activity: ComponentActivity) {
        val registered = activity.registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { access.changed() }
        launcher = registered
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                // Också vid första starten: läget läses då en gång till, vilket är billigt och håller koden enkel.
                override fun onResume(owner: LifecycleOwner) = access.changed()

                override fun onDestroy(owner: LifecycleOwner) {
                    if (launcher === registered) launcher = null
                }
            },
        )
    }

    /** Begär det som går att ge på enheten ([HealthAccess.requestable]); innan läget lästs hela uppsättningen. */
    override fun requestAccess() {
        launcher?.launch(access.latest?.requestable ?: HealthPermissionSet.ALL)
    }

    /**
     * Health Connect i Play Butik (HLS-4) – samma väg för att installera och uppdatera. Saknas Play Butik öppnas sidan i
     * webbläsaren. Båda är explicita mål utan data från appen (skill android-intent-security).
     */
    override fun openHealthConnect() {
        val store = Intent(Intent.ACTION_VIEW, "market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding".toUri())
            .setPackage(PLAY_STORE_PACKAGE)
            .putExtra("overlay", true)
            .putExtra("callerId", context.packageName)
        val web = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE".toUri())
        for (candidate in listOf(store, web)) {
            try {
                context.startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // Nästa kandidat.
            }
        }
    }

    internal companion object {
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
        const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}
