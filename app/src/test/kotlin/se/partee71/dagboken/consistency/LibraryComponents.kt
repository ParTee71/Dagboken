package se.partee71.dagboken.consistency

import java.io.File
import java.lang.reflect.Modifier
import java.util.jar.JarFile
import se.partee71.ui.components.AppButton

/**
 * De publika funktionerna i android-ui:s paket `se.partee71.ui.components`, lästa ur bibliotekets klasser på
 * testets klassväg – så att en rad märkt *(android-ui)* i shared-ui-components bevisligen finns i biblioteket.
 * En funktion med en value class-parameter (t.ex. `Tone`) får ett suffix efter `-` i bytekoden; det tas bort.
 */
object LibraryComponents {
    private const val PACKAGE_PATH = "se/partee71/ui/components/"

    val names: Set<String> by lazy {
        val anchor = Class.forName("se.partee71.ui.components.AppButtonKt")
        val location = File(anchor.protectionDomain.codeSource.location.toURI())
        val classes = if (location.isDirectory) {
            File(location, PACKAGE_PATH).walk().filter { it.isFile }.map { it.relativeTo(location).invariantSeparatorsPath }.toList()
        } else {
            JarFile(location).use { jar -> jar.entries().asSequence().map { it.name }.toList() }
        }
        val loader = anchor.classLoader
        val found = classes
            .filter { it.startsWith(PACKAGE_PATH) && it.endsWith(".class") && '/' !in it.removePrefix(PACKAGE_PATH) }
            .map { loader.loadClass(it.removeSuffix(".class").replace('/', '.')) }
            .flatMap { type -> type.declaredMethods.filter { Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) }.map { it.name.substringBefore('-') } }
            .toSet()
        check("AppButton" in found) { "android-ui hittades inte på klassvägen (${::AppButton.name})" }
        found
    }
}
