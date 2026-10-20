package se.partee71.dagboken.ui.components

import androidx.compose.ui.unit.dp

/**
 * Minsta tryckyta för allt interaktivt i komponenterna (NFR-14, DSN-3) – ett ställe, så att
 * appens egna klickbara ytor delar samma värde (regel 4) – samma 48 dp som android-ui:s `LeadingSlot`.
 */
internal val TOUCH_TARGET = 48.dp
