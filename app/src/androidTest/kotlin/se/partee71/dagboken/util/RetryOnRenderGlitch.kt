package se.partee71.dagboken.util

import android.util.Log

/**
 * Runs [attempt] up to [attempts] times, retrying on any thrown [Throwable].
 *
 * Replacement for [RetryTestRule] (see issue #112): that rule wraps
 * `composeRule` as a JUnit `TestRule` and retries by re-invoking the whole
 * `@Before`/`@Test`/`@After` statement, which re-enters `composeRule`'s
 * internal coroutine `TestScope` — a single-use resource
 * (https://issuetracker.google.com/issues/235383900). Attempt 2 always
 * throws `IllegalStateException: Only a single call to runTest can be
 * performed during one test`, so that rule can only mask attempt 1's real
 * failure, never actually recover from a transient render glitch.
 *
 * This function is called from *inside* the `@Test` method body instead, so
 * JUnit invokes the test method — and therefore enters `composeRule`'s
 * `TestScope` — exactly once no matter how many attempts run internally.
 * Each attempt is expected to set up and tear down its own fixtures (DB,
 * ViewModel, `ActivityScenario`) so a glitched attempt gets a genuinely
 * fresh Activity + Compose hierarchy to retry against, e.g.:
 *
 * ```
 * @Test fun some_test() = retryOnRenderGlitch {
 *     setUp()
 *     try {
 *         setContent()
 *         composeRule.onNodeWithText("...").assertIsDisplayed()
 *     } finally {
 *         tearDown()
 *     }
 * }
 * ```
 */
fun retryOnRenderGlitch(
    attempts: Int = 3,
    baseRetryDelayMillis: Long = 2000,
    attempt: () -> Unit,
) {
    var firstError: Throwable? = null
    for (i in 1..attempts) {
        try {
            attempt()
            return
        } catch (t: Throwable) {
            Log.w("RetryOnRenderGlitch", "attempt $i/$attempts failed", t)
            if (firstError == null) {
                firstError = t
            } else {
                firstError.addSuppressed(t)
            }
            if (i < attempts) Thread.sleep(baseRetryDelayMillis * i)
        }
    }
    throw firstError!!
}
