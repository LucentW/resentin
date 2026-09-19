package pm.antani.resentin.ui.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/**
 * F1 — altezza stimata delle righe timeline non ancora caricate.
 *
 * Un placeholder fisso (es. 48dp) sbaglia sistematicamente sempre nella stessa
 * direzione: ogni swap placeholder -> riga vera sposta tutte le righe sotto e
 * il lettore vede flicker. Qui si campiona la mediana delle righe reali
 * visibili e la si usa per i placeholder, così il residuo è ~zero-mean.
 *
 * Regole: solo righe messaggio reali (key Long = MessageEntity.id, mai
 * placeholder Paging né footer), niente righe a misura zero, mediana non
 * media (una preview link vale dieci righe normali), isteresi a step per non
 * far ballare i placeholder a ogni frame, campionamento solo a riposo.
 */
internal val MIN_TIMELINE_ROW_HEIGHT: Dp = 20.dp
internal val MAX_TIMELINE_ROW_HEIGHT: Dp = 160.dp
internal val DEFAULT_TIMELINE_ROW_HEIGHT: Dp = 48.dp
internal val TIMELINE_ROW_HEIGHT_STEP: Dp = 8.dp
internal const val UNSAMPLED_ROW_HEIGHT_PX: Int = -1

internal data class TimelineRowHeightBounds(
    val minPx: Int,
    val maxPx: Int,
    val stepPx: Int,
)

internal fun isTimelineRowKey(key: Any?): Boolean = key is Long

internal fun medianTimelineRowHeightPx(heightsPx: List<Int>): Int? {
    if (heightsPx.isEmpty()) return null
    val sorted = heightsPx.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[middle]
    } else {
        (sorted[middle - 1] + sorted[middle]) / 2
    }
}

internal fun nextTimelineRowHeightPx(
    currentPx: Int,
    sampledPx: Int?,
    bounds: TimelineRowHeightBounds,
): Int {
    val sampled = sampledPx ?: return currentPx
    val clamped = sampled.coerceIn(bounds.minPx, bounds.maxPx)
    val step = bounds.stepPx.coerceAtLeast(1)
    if (currentPx != UNSAMPLED_ROW_HEIGHT_PX && abs(clamped - currentPx) < step) return currentPx
    return (((clamped + step / 2) / step) * step).coerceIn(bounds.minPx, bounds.maxPx)
}

internal fun timelineRowHeightSamplesPx(keysAndSizes: List<Pair<Any?, Int>>): List<Int> =
    keysAndSizes.mapNotNull { (key, size) -> size.takeIf { isTimelineRowKey(key) && it > 0 } }

internal fun timelineRowHeightBounds(density: Density): TimelineRowHeightBounds =
    with(density) {
        TimelineRowHeightBounds(
            minPx = MIN_TIMELINE_ROW_HEIGHT.roundToPx(),
            maxPx = MAX_TIMELINE_ROW_HEIGHT.roundToPx(),
            stepPx = TIMELINE_ROW_HEIGHT_STEP.roundToPx(),
        )
    }

/**
 * Stima corrente dell'altezza riga. Ritorna una lambda apposta: la lettura
 * avviene dentro la composition del placeholder, così un aggiornamento
 * invalida solo i placeholder composti, non l'intera timeline.
 * Reset per canale: stanze diverse hanno righe tipiche diverse.
 */
@Composable
internal fun rememberTimelineRowHeight(
    listState: LazyListState,
    channelKey: String?,
): () -> Dp {
    val density = LocalDensity.current
    val bounds = remember(density) { timelineRowHeightBounds(density) }
    val estimatePx = remember(channelKey, bounds) { mutableIntStateOf(UNSAMPLED_ROW_HEIGHT_PX) }
    LaunchedEffect(listState, bounds, estimatePx) {
        snapshotFlow {
            if (listState.isScrollInProgress) return@snapshotFlow null
            val visible = listState.layoutInfo.visibleItemsInfo
            val heights = ArrayList<Int>(visible.size)
            for (info in visible) {
                if (isTimelineRowKey(info.key) && info.size > 0) heights.add(info.size)
            }
            nextTimelineRowHeightPx(
                currentPx = estimatePx.intValue,
                sampledPx = medianTimelineRowHeightPx(heights),
                bounds = bounds,
            )
        }.distinctUntilChanged()
            .collect { next -> if (next != null) estimatePx.intValue = next }
    }
    return remember(estimatePx, density) {
        {
            val px = estimatePx.intValue
            if (px == UNSAMPLED_ROW_HEIGHT_PX) DEFAULT_TIMELINE_ROW_HEIGHT
            else with(density) { px.toDp() }
        }
    }
}
