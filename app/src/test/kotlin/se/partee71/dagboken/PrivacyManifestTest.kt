package se.partee71.dagboken

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.WindowManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.xmlpull.v1.XmlPullParser
import se.partee71.dagboken.data.health.HealthPermissionSet
import se.partee71.dagboken.testing.AppManifest
import se.partee71.dagboken.testing.AppManifest.android
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Manifestet: hälsodata lämnar inte appen via Androids backup eller enhetsöverföring (NFR-23, skill data-privacy-security),
 * Health Connect nås bara med läsbehörigheter och en rationale-handler som bara systemet kan starta (HLS-3, HLS-9, HLS-14,
 * TP-10), och appen stöder RTL, systemets predictive back (NFR-4) och tangentbordets inset (NFR-11).
 */
@RunWith(RobolectricTestRunner::class)
class PrivacyManifestTest {

    private val context = RuntimeEnvironment.getApplication()
    private val appInfo = context.applicationInfo

    @Test
    fun `Androids backup är avstängd`() {
        assertEquals(0, appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun `molnbackup och enhetsöverföring undantar alla domäner`() {
        // ApplicationInfo.dataExtractionRulesRes är dold API – kontrollera manifestet direkt.
        assertTrue(
            AppManifest.text.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""),
            "android:dataExtractionRules saknas i manifestet",
        )
        val excluded = mutableMapOf<String, MutableSet<String>>()
        val parser = context.resources.getXml(R.xml.data_extraction_rules)
        var section: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "cloud-backup", "device-transfer" -> section = parser.name
                "exclude" -> if (parser.getAttributeValue(null, "path") == ".") {
                    excluded.getOrPut(checkNotNull(section)) { mutableSetOf() } += parser.getAttributeValue(null, "domain")
                }
            }
        }
        val allDomains = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
        assertEquals(mapOf("cloud-backup" to allDomains, "device-transfer" to allDomains), excluded)
    }

    @Test
    fun `appen stöder RTL och systemets predictive back (NFR-4)`() {
        assertTrue((appInfo.flags and ApplicationInfo.FLAG_SUPPORTS_RTL) != 0, "android:supportsRtl saknas på <application>")
        // Flaggan för predictive back är dold API i ApplicationInfo – attributet läses på <application>.
        assertEquals(
            "true",
            AppManifest.element("application").android("enableOnBackInvokedCallback"),
            "android:enableOnBackInvokedCallback saknas på <application> – utan den får appen ingen predictive back-animation",
        )
    }

    @Test
    fun `huvudaktiviteten får tangentbordets inset (NFR-11)`() {
        // Med edge-to-edge räknar Compose själv med tangentbordet (imePadding i ramarna); adjustResize gör att insetet
        // levereras också före Android 11.
        val activity = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            activity.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST,
            "android:windowSoftInputMode=\"adjustResize\" saknas på MainActivity",
        )
    }

    // ---- Health Connect (HLS-3, HLS-9, HLS-14, TP-10): bara läsning, och samtyckesdialogen kan visas ----

    private val requested: Set<String> = context.packageManager
        .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions.orEmpty().toSet()

    @Test
    fun `manifestet begär exakt behörighetsuppsättningen – bara läsning, inget blodtryck`() {
        val health = requested.filter { it.startsWith("android.permission.health.") }.toSet()
        assertEquals(HealthPermissionSet.ALL, health, "en begärd behörighet som manifestet saknar visas aldrig i dialogen")
        assertTrue(health.all { it.startsWith("android.permission.health.READ_") }, "skrivbehörighet i manifestet: $health")
        assertTrue(requested.none { "BLOOD_PRESSURE" in it }, "blodtrycket är borttaget i 4.0")
        assertTrue(requested.none { it.startsWith("android.permission.health.WRITE_") })
    }

    @Test
    fun `rationale-handlern finns för Android 13 och äldre och för 14 och senare`() {
        val legacy = Intent("androidx.health.connect.action.SHOW_PERMISSIONS_RATIONALE").setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(legacy, 0).isNotEmpty(), "SHOW_PERMISSIONS_RATIONALE saknas – ingen samtyckesdialog")
        val usage = Intent(Intent.ACTION_VIEW_PERMISSION_USAGE).addCategory("android.intent.category.HEALTH_PERMISSIONS").setPackage(context.packageName)
        val aliases = context.packageManager.queryIntentActivities(usage, 0)
        assertTrue(aliases.isNotEmpty(), "VIEW_PERMISSION_USAGE-aliaset saknas (Android 14+)")
        // Aliaset är exporterat men nås bara av systemet (skill android-intent-security).
        assertTrue(aliases.all { it.activityInfo.permission == "android.permission.START_VIEW_PERMISSION_USAGE" })
    }

    @Test
    fun `Health Connect syns för appen`() {
        assertTrue(AppManifest.text.contains("""<package android:name="com.google.android.apps.healthdata" />"""), "<queries> för Health Connect saknas")
    }
}
