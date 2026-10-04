package se.partee71.dagboken.data.common

import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.dagboken.data.ContractItem
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.TestUserScope

/** `nextSortOrder`/`upsertPlaced`: något nytt hamnar sist, och sparandet väntar aldrig på nätet. */
class EntityCollectionTest {

    @Test
    fun `något nytt hamnar efter det sista i listan`() = runTest {
        val items = FakeCollectionFactory().contractItems()
        items.upsert(ContractItem("a", "Promenad", sortOrder = 4)).getOrThrow()
        items.upsertPlaced(ContractItem("b", "Yoga"), isNew = true) { copy(sortOrder = it) }.getOrThrow()
        assertEquals(5, items.get("b").getOrThrow()?.sortOrder)
        items.upsertPlaced(ContractItem("b", "Yoga", sortOrder = 1), isNew = false) { copy(sortOrder = it) }.getOrThrow()
        assertEquals(1, items.get("b").getOrThrow()?.sortOrder, "något som redan finns behåller sin plats")
    }

    @Test
    fun `utan lista att läsa blir platsen 0 i stället för att vänta`() = runTest {
        val items = FakeCollectionFactory(scope = TestUserScope(uid = null)).contractItems()
        assertEquals(0, items.nextSortOrder())
    }
}
