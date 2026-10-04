@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package se.partee71.dagboken

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlin.test.assertNotNull
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Bevisar att API:erna i ReseApotekets ARKITEKTUR.md → "Verifierade API:er" finns och går att rendera med
 * de versioner som står i libs.versions.toml. Faller testet efter en versionsuppdatering har
 * ett API bytt namn: rätta tabellen och de delade komponenterna i samma PR.
 */
@RunWith(RobolectricTestRunner::class)
class VerifiedApisTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Expressive-komponenterna renderas`() {
        compose.setContent { DagbokenTheme { ExpressiveProbe() } }

        compose.onNodeWithText("Lägg till").assertIsDisplayed()
        compose.onNodeWithText("Verktygsrad").assertIsDisplayed()
    }

    /**
     * Hilt assisted injection för en ViewModel med nyckel. Hilt-komponenten finns inte i
     * Robolectric-testerna, så här bevisas att API:et kompileras; att det körs bevisas av
     * appens start (instrumenttest och release).
     */
    @Test
    fun `ViewModel med nyckel via Hilt assisted injection kompileras`() {
        val probe: @Composable (String) -> KeyedProbeViewModel = { id ->
            hiltViewModel<KeyedProbeViewModel, KeyedProbeViewModel.Factory> { it.create(id) }
        }
        assertNotNull(probe)
    }

    @Test
    fun `Navigation 3 visar posten överst på back stacken med egen ViewModel`() {
        compose.setContent { DagbokenTheme { NavigationProbe() } }

        compose.onNodeWithText("Idag: ProbeViewModel").assertIsDisplayed()
    }
}

@Composable
private fun ExpressiveProbe() {
    Column {
        LargeFlexibleTopAppBar(title = { Text("Idag") })
        LinearWavyProgressIndicator(progress = { 0.6f })
        LoadingIndicator()
        SplitButtonLayout(
            leadingButton = { SplitButtonDefaults.LeadingButton(onClick = {}) { Text("Lägg till") } },
            trailingButton = { SplitButtonDefaults.TrailingButton(checked = false, onCheckedChange = {}) { Text("▾") } },
        )
        ButtonGroup(overflowIndicator = {}) {
            clickableItem(onClick = {}, label = "−")
            clickableItem(onClick = {}, label = "+")
        }
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(toolbarContainerColor = Color.Black, toolbarContentColor = Color.White),
        ) { Text("Verktygsrad") }
        val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
        Box(
            Modifier
                .size(24.dp)
                .drawBehind { scale(size.minDimension, pivot = Offset.Zero) { drawPath(morph.toPath(0.5f), Color.Black) } },
        )
    }
}

@Serializable
internal data object TodayProbeKey : NavKey

internal class ProbeViewModel : ViewModel()

/**
 * Formen för en ViewModel med nyckel (`@HiltViewModel(assistedFactory = …)` i appen). Hilt-komponenten
 * finns inte i Robolectric-testerna; här bevisas att API:et kompileras – appen har ännu ingen egen
 * sådan ViewModel (etapp 5).
 */
internal class KeyedProbeViewModel(val id: String) : ViewModel() {
    fun interface Factory {
        fun create(id: String): KeyedProbeViewModel
    }
}

@Composable
private fun NavigationProbe() {
    val backStack = rememberNavBackStack(TodayProbeKey)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<TodayProbeKey> {
                val vm: ProbeViewModel = viewModel()
                Text("Idag: ${vm::class.simpleName}")
            }
        },
    )
}
