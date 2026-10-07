package se.partee71.dagboken

import android.content.pm.ActivityInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Bara startskärmen av appens egna komponenter når andra appar (skill android-intent-security,
 * #88). Undantaget är Health Connects rationale-alias (HLS-3), som bara systemet kan starta
 * (`START_VIEW_PERMISSION_USAGE`). Bibliotekens komponenter skyddas av egna behörigheter och räknas inte här.
 */
@RunWith(RobolectricTestRunner::class)
class ComponentExportTest {

    @Test
    fun `bara MainActivity är exporterad, och rationale-aliaset bara för systemet`() {
        val context = RuntimeEnvironment.getApplication()
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        val own = MainActivity::class.java.name.substringBeforeLast('.')
        val exported = listOfNotNull(info.activities, info.receivers, info.services, info.providers)
            .flatMap { components -> components.filter { it.exported && it.name.startsWith(own) } }
        val open = exported.filter { it.startPermission != SYSTEM_ONLY }.map { it.name }
        assertEquals(listOf(MainActivity::class.java.name), open)
        assertEquals(listOf("$own.ViewPermissionUsageActivity"), exported.filter { it.startPermission == SYSTEM_ONLY }.map { it.name })
    }

    /** Behörigheten som krävs för att starta komponenten, `null` när vem som helst får. */
    private val ComponentInfo.startPermission: String?
        get() = when (this) {
            is ActivityInfo -> permission
            is ServiceInfo -> permission
            else -> null
        }

    private companion object {
        const val SYSTEM_ONLY = "android.permission.START_VIEW_PERMISSION_USAGE"
    }
}
