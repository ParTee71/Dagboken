package se.partee71.dagboken.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ber alla instanser av appens hemskärmswidgets rita om sig (#161/#162). Anropas efter
 * varje skrivning som kan ändra dagens vyer — både widgetarnas egna åtgärder och appens
 * (`HomeViewModel`, `MedActionReceiver`) — så widgetarna aldrig visar inaktuellt läge
 * (WID-4).
 */
object WidgetUpdater {
    /**
     * Best-effort per widget — misslyckas en (t.ex. den widgeten inte är tillagd, eller en
     * unit-testmiljö utan riktiga Android-tjänster) ska det aldrig störa den faktiska
     * skrivningen som utlöste den, eller de andra widgetarnas uppdatering.
     */
    fun requestUpdate(context: Context) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val appContext = context.applicationContext
            runCatching { DagbokenWidget().updateAll(appContext) }
            runCatching { ScreeningWidget().updateAll(appContext) }
            runCatching { VidBehovWidget().updateAll(appContext) }
        }
    }
}
