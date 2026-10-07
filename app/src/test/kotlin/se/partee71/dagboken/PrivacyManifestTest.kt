package se.partee71.dagboken

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.xmlpull.v1.XmlPullParser
import se.partee71.dagboken.data.health.HealthPermissionSet
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hälsodata lämnar inte appen via Androids backup eller enhetsöverföring (NFR-8, skill data-privacy-security), och Health
 * Connect nås bara med läsbehörigheter och en rationale-handler som bara systemet kan starta (HLS-3, HLS-9, HLS-14, TP-10).
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
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""),
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
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""<package android:name="com.google.android.apps.healthdata" />"""), "<queries> för Health Connect saknas")
    }
}
