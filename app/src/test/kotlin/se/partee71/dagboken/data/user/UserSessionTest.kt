package se.partee71.dagboken.data.user

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.UserVersion
import se.partee71.dagboken.data.common.UserVersionSource
import se.partee71.dagboken.data.common.currentVersion
import se.partee71.dagboken.data.common.writeBlocker
import se.partee71.dagboken.testing.FakeAuthRepository
import se.partee71.dagboken.testing.UserFixture

/** Med vanlig (köad) testdispatcher, så att ordningsproblem inte döljs. */
class UserSessionTest {

    @Test
    fun `uid följer inloggningen`() = runTest {
        val fixture = UserFixture(backgroundScope)
        assertNull(fixture.session.uid.value)
        fixture.auth.authState.value = AuthUser("uid-anna")
        assertEquals("uid-anna", fixture.session.uid.first { it != null })
        fixture.auth.signOut()
        fixture.session.uid.first { it == null }
    }

    @Test
    fun `versionen följer användaren och spärrar skrivning när den är nyare`() = runTest {
        val fixture = UserFixture(backgroundScope, FakeAuthRepository(AuthUser("uid-anna")))
        fixture.storeUser("uid-anna")
        assertEquals(UserVersion("uid-anna", Schema.CURRENT_VERSION), fixture.session.schemaVersion.filterNotNull().first())
        assertNull(fixture.session.writeBlocker())
        fixture.storeUser("uid-anna", mapOf("schemaVersion" to Schema.CURRENT_VERSION + 1))
        fixture.session.schemaVersion.first { it?.version == Schema.CURRENT_VERSION + 1 }
        assertEquals(DataError.UpdateRequired, fixture.session.writeBlocker())
    }

    @Test
    fun `ett användardokument som inte finns än har det första formatet och exists = false`() = runTest {
        val fixture = UserFixture(backgroundScope, FakeAuthRepository(AuthUser("ny")))
        assertEquals(UserVersion("ny", Schema.FIRST_VERSION, exists = false), fixture.session.schemaVersion.filterNotNull().first())
        fixture.storeUser("ny")
        assertEquals(UserVersion("ny", Schema.CURRENT_VERSION), fixture.session.schemaVersion.first { it?.exists == true })
    }

    @Test
    fun `utloggad finns ingen version för användaren`() = runTest {
        val fixture = UserFixture(backgroundScope, FakeAuthRepository(AuthUser("uid-anna")))
        fixture.storeUser("uid-anna")
        fixture.session.schemaVersion.filterNotNull().first()
        fixture.auth.signOut()
        fixture.session.uid.first { it == null }
        assertNull(fixture.session.currentVersion(), "en tidigare användares version gäller aldrig")
    }

    @Test
    fun `ett dokument i appens version stämplas inte`() = runTest {
        val fixture = UserFixture(backgroundScope, FakeAuthRepository(AuthUser("uid-anna")))
        fixture.storeUser("uid-anna")
        fixture.session.schemaVersion.filterNotNull().first()
        assertEquals(emptyList(), fixture.stamps)
    }

    @Test
    fun `ett äldre dokument vars väg hit inte ändrar data stämplas en gång`() = runTest {
        // Version 0 når aldrig sessionen från Firestore (Schema.versionOf), men mekanismen ska hålla
        // när en version 2 kommer: här matas en version under den nuvarande in direkt.
        val stamps = mutableListOf<Pair<String, Int>>()
        val source = object : UserVersionSource {
            override fun schemaVersion(uid: String) = flowOf<Int?>(0)

            override fun stamp(uid: String, version: Int) {
                stamps += uid to version
            }
        }
        val session = UserSession(FakeAuthRepository(AuthUser("uid-anna")), source, backgroundScope)
        session.schemaVersion.first { it?.version == 0 }
        assertEquals(listOf("uid-anna" to Schema.CURRENT_VERSION), stamps)
    }

    @Test
    fun `ett användardokument som inte går att läsa spärrar skrivning`() = runTest {
        val fixture = UserFixture(backgroundScope)
        fixture.denied.value = setOf("uid-anna")
        fixture.auth.authState.value = AuthUser("uid-anna")

        fixture.session.unreadable.first { it == "uid-anna" }
        assertEquals(DataError.PermissionDenied, fixture.session.writeBlocker())
    }

    @Test
    fun `ett dokument som går att läsa igen räknas inte längre som oläsbart`() = runTest {
        val fixture = UserFixture(backgroundScope)
        fixture.storeUser("uid-anna")
        fixture.denied.value = setOf("uid-anna")
        fixture.auth.authState.value = AuthUser("uid-anna")
        fixture.session.unreadable.first { it == "uid-anna" }

        fixture.denied.value = emptySet()
        fixture.auth.signOut()
        fixture.session.uid.first { it == null }
        fixture.auth.authState.value = AuthUser("uid-anna")
        fixture.session.schemaVersion.first { it?.uid == "uid-anna" }
        assertNull(fixture.session.unreadable.value)
    }

    @Test
    fun `utan känd version vägras skrivning efter en stund hellre än att skriva över nyare data`() = runTest {
        val fixture = UserFixture(backgroundScope, FakeAuthRepository(AuthUser("uid-anna")))
        fixture.versionsKnown.value = false
        fixture.session.uid.first { it != null }

        assertEquals(DataError.Offline, fixture.session.writeBlocker())
        assertEquals(5_000L, testScheduler.currentTime, "väntar fem sekunder på versionen")
    }
}
