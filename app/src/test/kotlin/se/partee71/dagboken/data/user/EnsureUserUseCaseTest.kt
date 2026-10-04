package se.partee71.dagboken.data.user

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.dataError
import se.partee71.dagboken.testing.UserFixture

class EnsureUserUseCaseTest {

    @Test
    fun `första inloggningen skapar users-uid med schemaVersion 1 och createdAt`() = runTest(UnconfinedTestDispatcher()) {
        val fixture = UserFixture(backgroundScope)

        val status = fixture.ensureUser("uid-anna").getOrThrow()

        assertEquals(UserStatus.Ready("uid-anna"), status)
        assertEquals(1, Schema.CURRENT_VERSION)
        assertEquals(mapOf("schemaVersion" to 1L, "createdAt" to fixture.clock.instant), fixture.user("uid-anna"))
    }

    @Test
    fun `ett befintligt dokument skrivs aldrig över – inte ens okända fält eller createdAt`() = runTest(UnconfinedTestDispatcher()) {
        val fixture = UserFixture(backgroundScope)
        val existing = mapOf("schemaVersion" to 1L, "createdAt" to "från en annan enhet", "framtidaFält" to true)
        fixture.storeUser("uid-anna", existing)

        assertEquals(UserStatus.Ready("uid-anna"), fixture.ensureUser("uid-anna").getOrThrow())
        assertEquals(existing, fixture.user("uid-anna"))
    }

    @Test
    fun `ett dokument med nyare schemaversion kräver uppdatering och lämnas orört`() = runTest(UnconfinedTestDispatcher()) {
        val fixture = UserFixture(backgroundScope)
        fixture.storeUser("uid-anna", mapOf("schemaVersion" to (Schema.CURRENT_VERSION + 1).toLong()))

        assertIs<UserStatus.RequiresUpdate>(fixture.ensureUser("uid-anna").getOrThrow())
        assertEquals((Schema.CURRENT_VERSION + 1).toLong(), fixture.user("uid-anna")?.get("schemaVersion"))
    }

    @Test
    fun `utan nät skapas inget och felet kommer tillbaka`() = runTest(UnconfinedTestDispatcher()) {
        val fixture = UserFixture(backgroundScope)
        fixture.directory.failure = DataError.Offline

        assertEquals(DataError.Offline, fixture.ensureUser("uid-anna").dataError())
        assertNull(fixture.user("uid-anna"))
    }

    @Test
    fun `det nya dokumentet har bara version och tidpunkt`() {
        val doc = EnsureUserUseCase.initialDocument(kotlin.time.Instant.fromEpochSeconds(10))
        assertEquals(setOf(EnsureUserUseCase.SCHEMA_VERSION, EnsureUserUseCase.CREATED_AT), doc.keys)
    }
}
