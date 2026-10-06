package se.partee71.dagboken.data.common

import kotlin.time.Instant
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.model.Sortable
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.core.schema.SchemaMigrator

// Regler för läsning och skrivning som gäller alla samlingar – används av både
// FirestoreCollection och FakeCollection, så att de bevisligen beter sig lika.

/**
 * Före skrivning: `updatedAt` sätts till [now] när modellen har fältet, och ett `createdAt`
 * utan värde skrivs inte – annars skulle en merge radera det lagrade värdet.
 */
fun prepareForWrite(encoded: Doc, now: Instant): Doc = buildMap {
    for ((key, value) in encoded) {
        when {
            key == "updatedAt" -> put(key, now)
            !isWrittenValue(key, value) -> Unit
            else -> put(key, value)
        }
    }
}

/** Om fältet [key] med [value] skrivs alls – inte ett `createdAt` utan värde ([prepareForWrite]). */
fun isWrittenValue(key: String, value: Any?): Boolean = !(key == "createdAt" && value == null)

/**
 * Fälten som en `update` skriver: [fields] och alltid `updatedAt`, som [prepareForWrite] satt –
 * så att en ändring av några fält syns som ändrad precis som en `upsert`. Samma regel som
 * [fieldsForMerge], med toppfält.
 */
fun fieldsForUpdate(prepared: Doc, fields: Set<String>): Doc = fieldsForMerge(prepared, fields.mapTo(mutableSetOf()) { listOf(it) })

/** En väg till ett fält, segment för segment: `listOf("theme", "mode")` = `theme` → `mode`. */
typealias FieldPath = List<String>

/**
 * Det `EntityCollection.merge` skriver: [fields] ur [prepared] som nästlade maps – `[theme, mode]`
 * blir `{theme: {mode: …}}` – och alltid `updatedAt` om det finns. En väg som inte finns i
 * [prepared] (eller går genom något som inte är en map) är ett fel, inte en tyst no-op.
 */
fun fieldsForMerge(prepared: Doc, fields: Set<FieldPath>): Doc {
    var result: Doc = prepared.filterKeys { it == "updatedAt" }
    for (path in fields) {
        require(path.isNotEmpty()) { "Tom fältväg" }
        var value: Any? = prepared
        for (key in path) {
            val map = value as? Map<*, *>
            require(map != null && map.containsKey(key)) { "Okänt fält: $path" }
            value = map[key]
        }
        result = mergeInto(result, path, value)
    }
    return result
}

private fun mergeInto(doc: Doc, path: FieldPath, value: Any?): Doc {
    val key = path.first()
    if (path.size == 1) return doc + (key to value)
    @Suppress("UNCHECKED_CAST")
    val inner = doc[key] as? Doc ?: emptyMap()
    return doc + (key to mergeInto(inner, path.drop(1), value))
}

/**
 * Fälten som skiljer [after] från [before], för `EntityCollection.merge`: toppfält, och i en
 * nästlad map dess nycklar (`[theme, mode]`) – så att bara det som faktiskt ändrats skrivs. Ett
 * värde som inte är en map på båda sidor (t.ex. en lista) jämförs och skrivs i sin helhet.
 */
fun changedFields(before: Doc, after: Doc): Set<FieldPath> = buildSet {
    for ((key, value) in after) {
        val previous = before[key]
        when {
            value is Map<*, *> && previous is Map<*, *> ->
                value.keys.filter { value[it] != previous[it] }.forEach { add(listOf(key, it.toString())) }
            value != previous || key !in before -> add(listOf(key))
        }
    }
}

/**
 * En lista av rader (maps) på [path] som matchas på radfältet [key] – t.ex. påminnelseraderna på
 * `slot`. Listan skrivs som en helhet (Firestore slår inte ihop listor), så en ändring byggs rad för
 * rad på det lagrade värdet med [withChangedRows]. Förutsätter en **fast uppsättning** rader: en
 * rad per nyckel, alltid samma nycklar – codecen ska garantera det (rader läggs inte till eller
 * tas bort här).
 */
data class KeyedList(val path: FieldPath, val key: String)

/**
 * [after] där varje nycklad lista i [lists] är [stored]s lista med just de rader utbytta som
 * [after] ändrat jämfört med [before] (matchade på nyckeln). Övriga rader tas från [stored], så att
 * en annan enhets ändring av en annan rad inte skrivs tillbaka med en gammal kopia. En ändrad rad
 * vars nyckel saknas i [stored] läggs inte till (fast uppsättning, se [KeyedList]).
 */
fun withChangedRows(before: Doc, after: Doc, stored: Doc, lists: Set<KeyedList>): Doc {
    var result: Doc = after
    for (list in lists) {
        val unchanged: List<Doc> = before.rowsAt(list.path)
        val changed: Map<Any?, Doc> = after.rowsAt(list.path).filter { row -> row !in unchanged }.associateBy { row -> row[list.key] }
        val rows: List<Doc> = stored.rowsAt(list.path).map { row -> changed[row[list.key]] ?: row }
        result = mergeInto(result, list.path, rows)
    }
    return result
}

/** Raderna (maps) i listan på [path], eller ingen. */
private fun Doc.rowsAt(path: FieldPath): List<Doc> {
    var value: Any? = this
    for (key in path) value = (value as? Map<*, *>)?.get(key)
    @Suppress("UNCHECKED_CAST")
    return (value as? List<*>).orEmpty().mapNotNull { row -> row as? Map<String, Any?> }
}

/** Ett migreringssteg-anrop; i appen alltid [SchemaMigrator.migrate], i tester utbytbart. */
typealias Migrate = (from: Int, collection: String, doc: Doc) -> Doc

/**
 * Läsning: dokument i ett äldre format lyfts av [migrate] innan de avkodas; nyare format
 * avkodas som de är (appen är då skrivskyddad, se [writeBlocker]). Okänd version läses som
 * nuvarande.
 */
fun <T> readDocument(
    codec: DocCodec<T>,
    collection: String,
    version: Int?,
    id: String,
    raw: Doc,
    migrate: Migrate = SchemaMigrator::migrate,
): T {
    val from = version ?: Schema.CURRENT_VERSION
    val doc = if (Schema.isNewerThanApp(from) || from == Schema.CURRENT_VERSION) raw else migrate(from, collection, raw)
    return codec.decode(id, doc)
}

/**
 * Ordningen i en lista: på dokument-ID (som Firestore utan `orderBy`), sedan på `sortOrder`
 * för `Sortable` – deterministiskt och likadant i riktig och fejkad samling.
 */
fun <T : Identified> sortForList(items: List<T>): List<T> {
    val byId = items.sortedBy { it.id }
    @Suppress("UNCHECKED_CAST")
    return if (byId.firstOrNull() is Sortable) byId.sortedWith(Sortable.ORDER as Comparator<T>) else byId
}
