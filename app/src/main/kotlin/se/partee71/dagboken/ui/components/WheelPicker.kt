package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/**
 * Snap-scroll wheel picker. Mirrors WheelColumn from src/components/UI.jsx.
 * Items snap to center; selected item is full opacity, others fade by distance.
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 44.dp,
    visibleItems: Int = 3,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    LaunchedEffect(selectedIndex) {
        if (listState.firstVisibleItemIndex != selectedIndex) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemScrollOffset to listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { (offset, index) ->
                val snappedIndex = if (offset > itemHeight.value / 2) index + 1 else index
                if (snappedIndex != selectedIndex && snappedIndex in items.indices) {
                    onIndexChanged(snappedIndex)
                }
            }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleItems)
            .width(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Padding items so selected item centers
            items((visibleItems / 2)) { Box(Modifier.height(itemHeight)) }

            itemsIndexed(items) { index, item ->
                val distance = abs(index - selectedIndex)
                val alpha = when (distance) {
                    0    -> 1f
                    1    -> 0.55f
                    else -> 0.25f
                }
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .alpha(alpha),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item,
                        style = if (distance == 0) MaterialTheme.typography.titleMedium
                                else MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = if (distance == 0) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items((visibleItems / 2)) { Box(Modifier.height(itemHeight)) }
        }
    }
}
