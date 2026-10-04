package se.partee71.dagboken

import android.content.pm.PackageManager
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Bara startskärmen av appens egna komponenter når andra appar (skill android-intent-security,
 * #88). Bibliotekens komponenter skyddas av egna behörigheter och räknas inte här.
 */
@RunWith(RobolectricTestRunner::class)
class ComponentExportTest {

    @Test
    fun `bara MainActivity är exporterad`() {
        val context = RuntimeEnvironment.getApplication()
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        val own = MainActivity::class.java.name.substringBeforeLast('.')
        val exported = listOfNotNull(info.activities, info.receivers, info.services, info.providers)
            .flatMap { components -> components.filter { it.exported && it.name.startsWith(own) }.map { it.name } }
        assertEquals(listOf(MainActivity::class.java.name), exported)
    }
}
