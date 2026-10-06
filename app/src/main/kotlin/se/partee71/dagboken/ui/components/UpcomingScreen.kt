package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R

/**
 * En skärm vars innehåll inte byggts än: rubriken och ett tomt tillstånd mitt på skärmen (ovanför
 * verktygsraden) med "Snart här" och [message]. Med [onBack] är det en underskärm med liten topprad och
 * tillbakapil (t.ex. Export och import i inställningsarket), annars en flik med stor rubrik och [actions].
 */
@Composable
fun UpcomingScreen(
    title: String,
    @DrawableRes icon: Int,
    message: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(title, size = if (onBack == null) TopBarSize.Large else TopBarSize.Small, onBack = onBack, actions = actions)
        Box(Modifier.fillMaxSize().padding(bottom = LocalBottomClearance.current), contentAlignment = Alignment.Center) {
            EmptyState(icon, stringResource(R.string.tab_upcoming_title), message)
        }
    }
}
