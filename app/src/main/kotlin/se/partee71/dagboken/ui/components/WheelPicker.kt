package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.flow.filter
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography

/**
 * Hjulväljare: en kolumn som snäpper till ett av [items]; det valda står mitt i, markerat, och
 * grannarna tonar ut. TalkBack läser [label] och det valda värdet och kan stega med åtgärderna
 * "Öka"/"Minska" (svep går inte att göra med skärmläsare). [visibleItems] ska vara udda.
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visibleItems: Int = DEFAULT_VISIBLE,
    width: Dp = DEFAULT_WIDTH,
) {
    require(visibleItems % 2 == 1) { "visibleItems måste vara udda så att det valda står mitt i" }
    val selected = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val side = visibleItems / 2
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val snap = rememberSnapFlingBehavior(lazyListState = listState)
    val halfItem = with(LocalDensity.current) { ITEM_HEIGHT.toPx() / 2 }
    val current by rememberUpdatedState(selected)
    val change by rememberUpdatedState(onIndexChange)

    LaunchedEffect(selected) {
        if (listState.firstVisibleItemIndex != selected || listState.firstVisibleItemScrollOffset != 0) listState.animateScrollToItem(selected)
    }
    // Valet rapporteras när rullningen stannat – inte under den, så att en animerad rullning till
    // ett nytt värde aldrig rapporterar värdena den passerar.
    LaunchedEffect(listState, items.size) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                val centred = listState.firstVisibleItemIndex + if (listState.firstVisibleItemScrollOffset > halfItem) 1 else 0
                if (centred != current && centred in items.indices) {
                    change(centred)
                    // Anroparen kan avböja eller begränsa värdet – då rullar hjulet tillbaka till det
                    // som faktiskt är valt, så att det synliga och det som läses upp stämmer.
                    withFrameNanos { }
                    if (current != centred) listState.animateScrollToItem(current)
                }
            }
    }

    val decrease = stringResource(R.string.decrease_format, label)
    val increase = stringResource(R.string.increase_format, label)
    Box(
        modifier
            .width(width)
            .height(ITEM_HEIGHT * visibleItems)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = items.getOrElse(selected) { "" }
                customActions = listOf(
                    CustomAccessibilityAction(decrease) { (selected > 0).also { if (it) change(selected - 1) } },
                    CustomAccessibilityAction(increase) { (selected < items.lastIndex).also { if (it) change(selected + 1) } },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Markeringen ligger under texten, mitt i.
        Box(Modifier.fillMaxWidth().height(ITEM_HEIGHT).background(AppColors.extended.rowTint, AppShapes.smallTile))
        LazyColumn(state = listState, flingBehavior = snap, horizontalAlignment = Alignment.CenterHorizontally) {
            items(side) { Box(Modifier.height(ITEM_HEIGHT)) }
            itemsIndexed(items) { index, item ->
                val distance = abs(index - selected)
                Box(Modifier.height(ITEM_HEIGHT).alpha(if (distance == 0) 1f else if (distance == 1) NEAR_ALPHA else FAR_ALPHA), contentAlignment = Alignment.Center) {
                    Text(
                        item,
                        style = if (distance == 0) AppTypography.quantity else AppTypography.body,
                        color = if (distance == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(side) { Box(Modifier.height(ITEM_HEIGHT)) }
        }
    }
}

private val ITEM_HEIGHT = 44.dp
private val DEFAULT_WIDTH = 72.dp
private const val DEFAULT_VISIBLE = 3
private const val NEAR_ALPHA = 0.45f
private const val FAR_ALPHA = 0.2f
