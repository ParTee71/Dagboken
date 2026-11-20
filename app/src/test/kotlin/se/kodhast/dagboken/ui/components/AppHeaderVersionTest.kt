package se.kodhast.dagboken.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.kodhast.dagboken.ui.theme.DagbokenTheme
import se.kodhast.ui.components.APP_HEADER_VERSION_TAG
import se.kodhast.ui.components.AppHeader

/** Versionen i sidhuvudet (NAV-12): temats `appVersion` står efter appnamnet; utan den ritas ingen version. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
class AppHeaderVersionTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(appVersion: String?) = rule.setContent {
        DagbokenTheme(appVersion = appVersion) { AppHeader("Idag", TopAppBarDefaults.exitUntilCollapsedScrollBehavior()) }
    }

    private fun version() = rule.onNodeWithTag(APP_HEADER_VERSION_TAG, useUnmergedTree = true)

    @Test
    fun `temats appVersion står efter appnamnet`() {
        show("4.20.0")
        version().assertIsDisplayed()
        version().assertTextEquals("4.20.0")
    }

    @Test
    fun `utan appVersion ritas ingen version`() {
        show(null)
        version().assertDoesNotExist()
    }
}
