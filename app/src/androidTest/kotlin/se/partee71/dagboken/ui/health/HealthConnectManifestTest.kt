package se.partee71.dagboken.ui.health

import android.content.Context
import android.content.Intent
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
