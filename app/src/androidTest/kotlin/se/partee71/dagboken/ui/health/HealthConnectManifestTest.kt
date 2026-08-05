package se.partee71.dagboken.ui.health

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression för buggen där "Ge åtkomst" inte gjorde något: Health Connect visar
 * inte samtyckesdialogen om appen inte deklarerar en behörighets-rationale-handler
 * i manifestet (HLS-3). Verifierar att handlern finns.
 */
@RunWith(AndroidJUnit4::class)
class HealthConnectManifestTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun app_declares_health_permissions_rationale_handler() {
        val intent = Intent("androidx.health.connect.action.SHOW_PERMISSIONS_RATIONALE")
            .setPackage(ctx.packageName)
        val handlers = ctx.packageManager.queryIntentActivities(intent, 0)
        assertTrue(
            "Health Connect-rationale-handlern måste vara deklarerad i AndroidManifest, " +
                "annars visas ingen samtyckesdialog när användaren trycker \"Ge åtkomst\".",
            handlers.isNotEmpty(),
        )
    }

    @Test fun app_declares_every_health_permission_the_repository_requests() {
        // HLS-8/HLS-9: en behörighet som repot begär men manifestet inte deklarerar
        // går aldrig att bevilja — Health Connect visar den helt enkelt inte.
        val declared = ctx.packageManager
            .getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toSet()

        val expected = listOf(
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_HEART_RATE",
            "android.permission.health.READ_RESTING_HEART_RATE",
            "android.permission.health.READ_SLEEP",
            "android.permission.health.READ_EXERCISE",
            "android.permission.health.READ_ACTIVE_CALORIES_BURNED",
            "android.permission.health.READ_DISTANCE",
            "android.permission.health.READ_OXYGEN_SATURATION",
            "android.permission.health.READ_BLOOD_PRESSURE",
            "android.permission.health.READ_HEALTH_DATA_HISTORY",
        )

        val missing = expected.filterNot { it in declared }
        assertTrue("Saknade hälsobehörigheter i AndroidManifest: $missing", missing.isEmpty())
    }

    @Test fun app_declares_view_permission_usage_alias_for_android_14() {
        val intent = Intent(Intent.ACTION_VIEW_PERMISSION_USAGE)
            .addCategory("android.intent.category.HEALTH_PERMISSIONS")
            .setPackage(ctx.packageName)
        val handlers = ctx.packageManager.queryIntentActivities(intent, 0)
        assertTrue(
            "VIEW_PERMISSION_USAGE-aliaset (Android 14+ hälsobehörigheter) måste vara deklarerat.",
            handlers.isNotEmpty(),
        )
    }
}
