package se.partee71.dagboken.core.legacy

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.ExportFormat

/** Batchformen för migreringen på enheten (OMB-2): högst 500 skrivningar, högst 20 episoder, episoden med sina incheckningar. */
class MigrationBatchesTest {
    private val uid = "u"

    private fun doc(path: String) = ExportFormat.Document(path, mapOf("x" to 1))

    private fun plain(collection: String, n: Int) = List(n) { doc(CollectionNames.document(uid, collection, "d%04d".format(it))) }

    private fun episode(id: String, checkins: Int) =
        listOf(doc(CollectionNames.document(uid, CollectionNames.ILLNESS_EPISODES, id))) + List(checkins) { doc(CollectionNames.checkin(uid, id, "c%04d".format(it))) }

    /** Episoder som hör till batchen: episoddokumentet eller incheckningarnas förälder. */
    private fun episodesIn(batch: List<ExportFormat.Document>): Set<String> =
        batch.mapNotNull { d ->
            when (CollectionNames.collectionOf(d.path)) {
                CollectionNames.ILLNESS_EPISODES -> d.path
                CollectionNames.CHECKINS -> d.path.split('/').dropLast(2).joinToString("/")
                else -> null
            }
        }.toSet()

    private fun assertWellFormed(documents: List<ExportFormat.Document>, batches: List<List<ExportFormat.Document>>) {
        assertEquals(documents, batches.flatten(), "varje dokument exakt en gång, i samma ordning")
        for (batch in batches) {
            assertTrue(batch.size <= MigrationBatches.MAX_WRITES, "högst 500 skrivningar: ${batch.size}")
            assertTrue(episodesIn(batch).size <= MigrationBatches.MAX_EPISODES, "högst 20 episoder: ${episodesIn(batch).size}")
        }
    }

    @Test
    fun `episoden ligger i samma batch som sina incheckningar och högst 20 episoder per batch`() {
        val documents = (0 until 25).flatMap { episode("ep%02d".format(it), 4) }.sortedBy { it.path }
        val batches = MigrationBatches.plan(documents)
        assertWellFormed(documents, batches)
        assertEquals(listOf(100, 25), batches.map { it.size })
        assertEquals(listOf(20, 5), batches.map { episodesIn(it).size })
        for (batch in batches) {
            for (episodePath in episodesIn(batch)) assertTrue(batch.any { it.path == episodePath }, "episoden $episodePath ska ligga i batchen")
        }
    }

    @Test
    fun `vanliga dokument fylls upp till 500 per batch`() {
        val documents = plain(CollectionNames.DOSES, 1200)
        val batches = MigrationBatches.plan(documents)
        assertWellFormed(documents, batches)
        assertEquals(listOf(500, 500, 200), batches.map { it.size })
    }

    @Test
    fun `en episod med fler incheckningar än som ryms delas upp med episoden först`() {
        val documents = plain(CollectionNames.ACTIVITIES, 3) + episode("stor", 600) + plain(CollectionNames.SCREENINGS, 2)
        val batches = MigrationBatches.plan(documents)
        assertWellFormed(documents, batches)
        assertEquals(listOf(3, 500, 101, 2), batches.map { it.size })
        assertEquals(CollectionNames.ILLNESS_EPISODES, CollectionNames.collectionOf(batches[1].first().path))
    }

    @Test
    fun `episoder och vanliga dokument blandas upp till gränserna`() {
        val documents = plain(CollectionNames.DOSES, 490) + (0 until 30).flatMap { episode("e%02d".format(it), 1) }
        val batches = MigrationBatches.plan(documents, maxWrites = 500, maxEpisodes = 20)
        assertWellFormed(documents, batches)
        assertEquals(listOf(500, 40, 10), batches.map { it.size }, "490 doser + 5 episoder à 2, sedan 20 episoder, sedan 5")
    }

    @Test
    fun `tomt ger inga batchar och små gränser respekteras`() {
        assertEquals(emptyList(), MigrationBatches.plan(emptyList()))
        val documents = episode("a", 1) + episode("b", 1)
        assertEquals(listOf(2, 2), MigrationBatches.plan(documents, maxWrites = 3, maxEpisodes = 1).map { it.size })
    }
}
