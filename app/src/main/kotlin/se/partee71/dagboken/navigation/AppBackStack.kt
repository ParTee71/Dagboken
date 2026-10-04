package se.partee71.dagboken.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Back stacken med en egen stack per flik (NAV-8, NAV-11):
 * - appen startar på Idag; varje flik minns sin undersida när man byter flik;
 * - tillbaka i roten av en annan flik går till Idag, tillbaka i Idags rot lämnar appen;
 * - att välja fliken man står på går till flikens rot.
 *
 * [entries] är det som `NavDisplay` visar: Idags stack och, om en annan flik är vald, dess stack.
 */
@Stable
class AppBackStack internal constructor(current: TopLevelKey, stacks: Map<TopLevelKey, List<AppKey>>) {
    private val start = TOP_LEVEL.first()
    private val stacks: Map<TopLevelKey, SnapshotStateList<AppKey>> =
        TOP_LEVEL.associateWith { tab -> mutableStateListOf<AppKey>().apply { addAll(stacks[tab] ?: listOf(tab)) } }

    var currentTab: TopLevelKey by mutableStateOf(current)
        private set

    constructor() : this(TOP_LEVEL.first(), emptyMap())

    val entries: List<AppKey>
        get() = if (currentTab == start) stack(start).toList() else stack(start) + stack(currentTab)

    /** Bottenraden syns bara i en fliks rot; undersidor döljer den (NAV-3). */
    val atTopLevel: Boolean
        get() = entries.last() is TopLevelKey

    fun select(tab: TopLevelKey) {
        if (tab == currentTab) stack(tab).removeRange(1, stack(tab).size) else currentTab = tab
    }

    /** Öppnar [key]; ligger den redan överst (ett dubbeltryck under animationen) händer inget. */
    fun push(key: AppKey) {
        require(key !is TopLevelKey) { "Flikar väljs med select, inte push" }
        val stack = stack(currentTab)
        if (stack.last() != key) stack.add(key)
    }

    /**
     * Stänger [key] om den är den översta skärmen – det en skärm anropar när den stänger sig
     * själv. Ett andra anrop (dubbeltryck, eller "klar" efter "släng ändringar") stänger då
     * inte skärmen under.
     */
    fun popIfTop(key: AppKey) {
        if (entries.last() == key) pop()
    }

    /** Går tillbaka ett steg; `false` om det inte finns något kvar (appen ska stängas). */
    fun pop(): Boolean {
        val stack = stack(currentTab)
        when {
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            currentTab != start -> currentTab = start
            else -> return false
        }
        return true
    }

    private fun stack(tab: TopLevelKey): SnapshotStateList<AppKey> = stacks.getValue(tab)

    internal fun save(): String = json.encodeToString(Saved.serializer(), Saved(currentTab, TOP_LEVEL.map { stack(it).toList() }))

    @Serializable
    internal class Saved(val current: TopLevelKey, val stacks: List<List<AppKey>>)

    companion object {
        private val json = Json

        internal fun restore(saved: String): AppBackStack {
            val state = json.decodeFromString(Saved.serializer(), saved)
            return AppBackStack(state.current, TOP_LEVEL.zip(state.stacks).toMap())
        }

        /** Sparas som JSON, så att flikarnas stackar överlever rotation och processdöd. */
        val Saver: Saver<AppBackStack, String> = Saver(save = { it.save() }, restore = { restore(it) })
    }
}

@Composable
fun rememberAppBackStack(): AppBackStack = rememberSaveable(saver = AppBackStack.Saver) { AppBackStack() }
