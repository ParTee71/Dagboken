package se.partee71.dagboken.testing

import java.util.concurrent.TimeUnit
import org.junit.rules.Timeout

/**
 * Skyddsnätet mot hängande tester (regel 2): ett test som fastnar fälls med trådarnas stackar i
 * felet, i stället för att äta upp CI-jobbets tidsgräns – och nästa test körs ändå. Regeln
 * omsluter testet, `@Before`/`@After` och de andra reglerna när den deklareras som yttersta:
 *
 * ```
 * @get:Rule(order = StuckTestTimeout.OUTERMOST)
 * val timeout = StuckTestTimeout.rule()
 * ```
 *
 * Testets egna tidsgränser (`withTimeout` runt varje steg) ger det tydliga felet; den här fångar
 * det som blockerar utanför dem – en `runBlocking` i en uppställning, ett `Tasks.await` eller ett
 * lås.
 */
object StuckTestTimeout {
    /** Lägst ordning = yttersta regeln (JUnit 4.13: högre `order` ligger innanför). */
    const val OUTERMOST = Int.MIN_VALUE

    /** Långt över vad ett test mot emulatorn tar (sekunder), kort mot CI-jobbets gräns. */
    const val DEFAULT_SECONDS = 60L

    fun rule(seconds: Long = DEFAULT_SECONDS): Timeout =
        Timeout.builder()
            .withTimeout(seconds, TimeUnit.SECONDS)
            .withLookingForStuckThread(true)
            .build()
}
