package se.partee71.dagboken.data.health

import androidx.activity.ComponentActivity
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.dagboken.core.engine.health.OptionalHealthMetric
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/**
 * Behörighetsporten mot en riktig aktivitet (HLS-3, HLS-14): samtyckesdialogen registreras i `attach` och startas med
 * behörighetsuppsättningen, ingenting händer utan aktivitet eller efter `onDestroy`, och en ny aktivitet (rotation) tar
 * över utan att den gamlas `onDestroy` slår ut den. Läget läses om när aktiviteten återupptas.
 */
@RunWith(RobolectricTestRunner::class)
class HealthPermissionsImplTest {
    private val application = RuntimeEnvironment.getApplication()
    private val source = FakeHealthConnectSource().apply { granted = HealthPermissionSet.CORE }

    private fun TestScope.port(): Pair<HealthPermissionsImpl, HealthConnectAccess> {
        val access = HealthConnectAccess(source, backgroundScope)
        return HealthPermissionsImpl(application, access) to access
    }

    /** En aktivitet som kopplar in porten i `onCreate`-läget, som `MainActivity`. */
    private fun attached(port: HealthPermissionsImpl): ActivityController<ComponentActivity> =
        Robolectric.buildActivity(ComponentActivity::class.java).create().also { port.attach(it.get()) }.start().resume()

    @Test
    fun `attach registrerar dialogen och Ge åtkomst startar den med behörighetsuppsättningen`() = runTest {
        val (port, _) = port()
        val activity = attached(port).get()
        port.requestAccess()
        val started = assertNotNull(shadowOf(activity).nextStartedActivityForResult, "samtyckesdialogen startades inte")
        val requested = started.intent.extras?.keySet()?.flatMap { key ->
            @Suppress("DEPRECATION")
            (started.intent.extras?.get(key) as? Array<*>)?.map { it.toString() }.orEmpty()
        }.orEmpty().toSet()
        assertEquals(HealthPermissionSet.ALL, requested, "innan läget lästs begärs hela uppsättningen")
    }

    @Test
    fun `utan aktivitet gör Ge åtkomst ingenting`() = runTest {
        val (port, _) = port()
        port.requestAccess()
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun `efter onDestroy är launchern borta och Ge åtkomst gör ingenting`() = runTest {
        val (port, _) = port()
        val controller = attached(port)
        controller.pause().stop().destroy()
        port.requestAccess()
        assertNull(shadowOf(controller.get()).nextStartedActivityForResult)
    }

    @Test
    fun `en ny aktivitet tar över – den gamlas onDestroy nollar inte den nyas launcher`() = runTest {
        val (port, _) = port()
        val old = attached(port)
        val recreated = attached(port)
        old.pause().stop().destroy()
        port.requestAccess()
        assertNotNull(shadowOf(recreated.get()).nextStartedActivityForResult, "den nya aktivitetens dialog")
        assertNull(shadowOf(old.get()).nextStartedActivityForResult)
    }

    @Test
    fun `läget läses om när aktiviteten återupptas och begäran följer enhetens uppsättning`() = runTest {
        val (port, access) = port()
        source.historyFeature = false
        access.now()
        val calls = source.sdkCalls
        val controller = attached(port)
        runCurrent()
        assertEquals(calls + 1, source.sdkCalls, "onResume läser om läget – också vid första starten")
        controller.pause().resume()
        runCurrent()
        assertEquals(calls + 2, source.sdkCalls)

        port.requestAccess()
        val started = assertNotNull(shadowOf(controller.get()).nextStartedActivityForResult)
        @Suppress("DEPRECATION")
        val requested = started.intent.extras?.keySet()?.flatMap { key -> (started.intent.extras?.get(key) as? Array<*>)?.map { it.toString() }.orEmpty() }.orEmpty().toSet()
        assertEquals(HealthPermissionSet.ALL - HealthPermissionSet.OPTIONAL.getValue(OptionalHealthMetric.HISTORY), requested)
    }
}
