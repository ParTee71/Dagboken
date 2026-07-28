package se.partee71.dagboken.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ber alla instanser av [DagbokenWidget] rita om sig. Anropas efter varje skrivning som kan
 * ändra dagens checklista — både widgetens egen avbockning och appens (`HomeViewModel`,
 * `MedActionReceiver`) — så widgeten aldrig visar inaktuellt läge (WID-4).
 */
object WidgetUpdater {
    /**
     * Best-effort — misslyckas den (t.ex. ingen widget tillagd, eller en unit-testmiljö utan
     * riktiga Android-tjänster) ska det aldrig störa den faktiska skrivningen som utlöste den.
     */
    fun requestUpdate(context: Context) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            runCatching { DagbokenWidget().updateAll(context.applicationContext) }
        }
    }
}
