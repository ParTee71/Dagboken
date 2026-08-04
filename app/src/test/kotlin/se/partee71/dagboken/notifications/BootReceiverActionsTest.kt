package se.partee71.dagboken.notifications

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NOT-14: systemet rensar appens larm både vid omstart och vid appuppdatering, men
 * skickar olika broadcasts. Tidigare lyssnade mottagaren bara på BOOT_COMPLETED, så
 * påminnelserna var borta efter varje uppdatering tills appen startades manuellt.
 */
class BootReceiverActionsTest {

    @Test fun `boot completed triggers a reschedule`() {
        assertTrue(Intent.ACTION_BOOT_COMPLETED in BootReceiver.RESCHEDULE_ACTIONS)
    }

    @Test fun `package replaced triggers a reschedule`() {
        assertTrue(Intent.ACTION_MY_PACKAGE_REPLACED in BootReceiver.RESCHEDULE_ACTIONS)
    }

    @Test fun `unrelated broadcasts do not trigger a reschedule`() {
        listOf(
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_PACKAGE_REMOVED,
            "se.partee71.dagboken.action.MARK_MED_TAKEN",
        ).forEach { action ->
            assertFalse("$action ska inte schemalägga om larmen", action in BootReceiver.RESCHEDULE_ACTIONS)
        }
    }
}
