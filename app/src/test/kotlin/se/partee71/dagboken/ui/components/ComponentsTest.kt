package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.R
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureLightAndDarkPaused
import se.partee71.dagboken.ui.theme.DagbokenTheme
import se.partee71.dagboken.ui.theme.Spacing

/** Beteende och skärmdumpar (ljust + mörkt) för de delade komponenterna. */
@RunWith(RobolectricTestRunner::class)
class ComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `AppButton anropar onClick`() {
        var clicks = 0
        rule.setContent { DagbokenTheme { AppButton("Spara", onClick = { clicks++ }) } }
        rule.onNodeWithText("Spara").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `AppButton som laddar är inaktiv och anropar inte onClick`() {
        var clicks = 0
        rule.setContent { DagbokenTheme { AppButton("Loggar in …", onClick = { clicks++ }, loading = true) } }
        rule.onNodeWithText("Loggar in …").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `AppLoading - standard`() = rule.captureLightAndDarkPaused("AppLoading_standard") {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.xxl)) { AppLoading() }
    }

    @Test
    fun `AppButton - varianter och lägen`() = rule.captureLightAndDarkPaused("AppButton_varianter") {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            AppButton("Logga in med Google", {}, Modifier.fillMaxWidth(), icon = R.drawable.ic_login)
            AppButton("Loggar in …", {}, Modifier.fillMaxWidth(), loading = true)
            AppButton("Lägg till", {}, variant = ButtonVariant.Secondary)
            AppButton("Avbryt", {}, variant = ButtonVariant.Text)
            AppButton("Spara", {}, enabled = false)
        }
    }

    @Test
    fun `AppSnackbarHost - meddelande`() = rule.captureLightAndDark("AppSnackbarHost_meddelande") {
        val state = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { state.showSnackbar("Något gick fel. Försök igen.", duration = SnackbarDuration.Indefinite) }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.BottomCenter) {
            AppSnackbarHost(state)
        }
    }

    @Test
    fun `EmptyState - i en lista`() = captureLightAndDark("EmptyState_lista") {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            EmptyState(
                icon = R.drawable.ic_book,
                title = "Inget loggat än",
                message = "Det du loggar med plusknappen hamnar här, dag för dag.",
                action = { AppButton("Logga", {}, Modifier.fillMaxWidth()) },
            )
        }
    }

    @Test
    fun `EmptyState - helskärm med fotnot`() = captureLightAndDark("EmptyState_helskarm") {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            EmptyState(
                icon = R.drawable.ic_download,
                title = "Uppdatera appen",
                message = "Installera den senaste versionen för att fortsätta.",
                note = "Ingenting ändras förrän appen är uppdaterad.",
                fullScreen = true,
                action = { AppButton("Hämta senaste versionen", {}, Modifier.fillMaxWidth()) },
            )
        }
    }
}
