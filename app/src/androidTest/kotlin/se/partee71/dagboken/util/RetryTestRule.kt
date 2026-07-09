package se.partee71.dagboken.util

import android.util.Log
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Retries a flaky instrumented test up to [attempts] times before failing.
 *
 * The CI emulator renders with swiftshader on 2 cores and intermittently drops
 * frames ("Failed to find ColorBuffer: N"). When a glitch lands on the exact
 * frame a Compose UI assertion inspects, the test fails with a
 * ComposeTimeoutException / not-displayed error that no amount of waitUntil
 * headroom can rule out — it is pure infrastructure lag, not a product bug.
 *
 * Wrapping the Compose rule as `RuleChain.outerRule(RetryTestRule()).around(
 * composeRule)` makes this the outermost rule, so each attempt re-runs the full
 * @Before/@After lifecycle and gets a fresh database, ViewModel and Compose
 * tree. A test that fails only on a bad frame passes on the retry; a test that
 * is genuinely broken fails every attempt and still fails the build.
 */
class RetryTestRule(private val attempts: Int = 3) : TestRule {

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                var lastError: Throwable? = null
                for (attempt in 1..attempts) {
                    try {
                        base.evaluate()
                        return
                    } catch (t: Throwable) {
                        lastError = t
                        Log.w(
                            "RetryTestRule",
                            "${description.displayName} failed on attempt $attempt/$attempts",
                            t,
                        )
                    }
                }
                throw lastError!!
            }
        }
}
