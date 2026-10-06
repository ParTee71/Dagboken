package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Minsta tryckyta för allt interaktivt i komponenterna (NFR-14, DSN-3) – ett ställe, så att
 * ikonknappar, rader och egna klickbara ytor delar samma värde (regel 4).
 */
internal val TOUCH_TARGET = 48.dp

/**
 * Det ledande elementet i en rad (kryss, ikon) i en ruta på [TOUCH_TARGET], så att titlarna linjerar
 * mellan rader med olika ledande element – `CheckRow` och inställningsarkets rader.
 */
@Composable
internal fun LeadingSlot(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.size(TOUCH_TARGET), contentAlignment = Alignment.Center) { content() }
}
