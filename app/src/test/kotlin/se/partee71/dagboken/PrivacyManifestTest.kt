package se.partee71.dagboken

import android.content.pm.ApplicationInfo
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.xmlpull.v1.XmlPullParser
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Hälsodata lämnar inte appen via Androids backup eller enhetsöverföring (NFR-8, skill data-privacy-security). */
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
}
