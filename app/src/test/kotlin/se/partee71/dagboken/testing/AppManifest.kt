package se.partee71.dagboken.testing

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Appens källmanifest för tester som kontrollerar det som `ApplicationInfo`/`PackageManager` inte exponerar – läst
 * och tolkat på ett ställe. Attributen läses i Android-namnrymden: `element.android("name")`.
 */
object AppManifest {

    private const val ANDROID = "http://schemas.android.com/apk/res/android"

    private val file = File("src/main/AndroidManifest.xml")

    /** Manifestets text, för en kontroll av en hel rad. */
    val text: String by lazy { file.readText() }

    private val root: Element by lazy {
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file).documentElement
    }

    /** Elementen med [tag] under [parent] (standard hela manifestet). */
    fun elements(tag: String, parent: Element = root): List<Element> {
        val nodes = parent.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    /** Det första elementet med [tag] – och med `android:name` lika med [name], när det anges. */
    fun element(tag: String, name: String? = null): Element =
        checkNotNull(elements(tag).firstOrNull { name == null || it.android("name") == name }) {
            "<$tag${name?.let { " android:name=\"$it\"" }.orEmpty()}> saknas i manifestet"
        }

    /** Attributet `android:[name]`; tom sträng när det saknas. */
    fun Element.android(name: String): String = getAttributeNS(ANDROID, name)
}
