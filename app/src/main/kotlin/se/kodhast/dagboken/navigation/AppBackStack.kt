package se.kodhast.dagboken.navigation

import androidx.compose.runtime.Composable
import se.kodhast.ui.navigation.TabBackStack
import se.kodhast.ui.navigation.rememberTabBackStack

/**
 * Back stacken med en egen stack per flik (NAV-8, NAV-11) – android-ui:s [TabBackStack] med Dagbokens fyra flikar
 * ([TOP_LEVEL], Idag först):
 * - appen startar på Idag; varje flik minns sin undersida när man byter flik;
 * - tillbaka i roten av en annan flik går till Idag, tillbaka i Idags rot lämnar appen;
 * - att välja fliken man står på går till flikens rot.
 */
typealias AppBackStack = TabBackStack<AppKey>

/** En ny back stack på Idag. */
fun AppBackStack(): AppBackStack = TabBackStack(TOP_LEVEL, AppKey.serializer())

/** Back stacken ur [AppBackStack.save]; ett läge som inte går att läsa ger startläget. */
fun restoreAppBackStack(saved: String): AppBackStack = TabBackStack.restore(saved, TOP_LEVEL, AppKey.serializer())

/** Sparas som JSON, så att flikarnas stackar överlever rotation och processdöd. */
@Composable
fun rememberAppBackStack(): AppBackStack = rememberTabBackStack(TOP_LEVEL, AppKey.serializer())
