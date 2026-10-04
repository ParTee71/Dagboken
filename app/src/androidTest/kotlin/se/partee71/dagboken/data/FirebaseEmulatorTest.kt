package se.partee71.dagboken.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.Source
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.testing.StuckTestTimeout

/**
 * Regressionstest: CI hängde när en skrivning följde direkt på ett användarbyte under en levande
 * Firestore-instans (ungefär var 19:e byte). [FirebaseEmulator] ger varje användare en egen
 * FirebaseApp; här körs regelns hela livscykel – före, ny användare, efter – många gånger i rad.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseEmulatorTest {

    /** Ett par sekunder i CI; gränsen är för hela slingan, med marginal för en långsam emulator. */
    @get:Rule(order = StuckTestTimeout.OUTERMOST)
    val timeout = StuckTestTimeout.rule(seconds = 120)

    @Test
    fun femtio_testanvandare_i_rad_skapas_och_skriver_som_sig_sjalva() {
        val uids = List(USERS) { n ->
            asOwnTest("anvandare_$n") { emulator ->
                val user = emulator.newUser()
                // Läsningen som ägaren går bara igenom rules om Firestore skickar just den här användaren.
                val doc = user.db.document(Paths.user(user.uid)).get(Source.SERVER).await()
                assertEquals(Schema.FIRST_VERSION.toLong(), doc.getLong("schemaVersion"))
                user.uid
            }
        }
        assertEquals(USERS, uids.toSet().size, "varje användare är ny")
    }

    /** Kör [block] som JUnit kör ett test: inuti en ny [FirebaseEmulator]-regel, med före och efter. */
    private fun asOwnTest(name: String, block: suspend (FirebaseEmulator) -> String): String {
        val rule = FirebaseEmulator()
        var result = ""
        val test = object : Statement() {
            override fun evaluate() {
                result = runBlocking { block(rule) }
            }
        }
        rule.apply(test, Description.createTestDescription(javaClass, name)).evaluate()
        return result
    }

    private companion object {
        const val USERS = 50
    }
}
