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
 *
 * `composeRule` is a single JUnit rule instance shared by every attempt (this
 * rule only wraps it, per `RuleChain.outerRule(RetryTestRule()).around(
 * composeRule)`), and its internal coroutine `TestScope` is single-use: once
 * attempt 1's `runTest` finishes — pass *or* throw — the scope is spent, so
 * attempt 2 re-entering it throws deterministically:
 * `IllegalStateException: Only a single call to \`runTest\` can be performed
 * during one test` (a known androidx.compose.ui.test limitation — see
 * https://issuetracker.google.com/issues/235383900). That means an in-process
 * retry can never recover a failed attempt here — it can only ever mask the
 * real first-attempt failure behind this exception. The real recovery for a
 * genuine transient render glitch happens at the CI-job level (a full
 * `connectedDebugAndroidTest` re-run gets a fresh process and a fresh rule
 * instance); this rule's job is to surface what attempt 1 actually failed
 * with, not to paper over it. So: report the FIRST error (with any later
 * attempts' errors attached as suppressed, for visibility), not the last.
 */
class RetryTestRule(
    private val attempts: Int = 3,
    private val baseRetryDelayMillis: Long = 2000,
) : TestRule {

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                var firstError: Throwable? = null
                for (attempt in 1..attempts) {
                    try {
                        base.evaluate()
                        return
                    } catch (t: Throwable) {
                        Log.w(
                            "RetryTestRule",
                            "${description.displayName} failed on attempt $attempt/$attempts",
                            t,
                        )
                        if (firstError == null) {
                            firstError = t
                        } else {
                            firstError.addSuppressed(t)
                        }
                        if (attempt < attempts) Thread.sleep(baseRetryDelayMillis * attempt)
                    }
                }
                throw firstError!!
            }
        }
}
