package com.glasscast.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.EpisodeSort

/**
 * Episode order, as two drawn icons: three bars beside an arrow. Newest first
 * is a descending list — bars shortening, arrow down; oldest first is the
 * reverse. Stroked with round ends to sit with the rest of the icon set.
 */
private fun sortIcon(name: String, newestFirst: Boolean): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        val bars = if (newestFirst) listOf(10f, 7f, 4f) else listOf(4f, 7f, 10f)
        bars.forEachIndexed { i, length ->
            val y = 7f + 5f * i
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(3.5f, y)
                lineTo(3.5f + length, y)
            }
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            if (newestFirst) {
                moveTo(18.5f, 5f); lineTo(18.5f, 19f)
                moveTo(15.5f, 16f); lineTo(18.5f, 19f); lineTo(21.5f, 16f)
            } else {
                moveTo(18.5f, 19f); lineTo(18.5f, 5f)
                moveTo(15.5f, 8f); lineTo(18.5f, 5f); lineTo(21.5f, 8f)
            }
        }
    }.build()

val NewestFirstIcon: ImageVector by lazy { sortIcon("NewestFirst", newestFirst = true) }
val OldestFirstIcon: ImageVector by lazy { sortIcon("OldestFirst", newestFirst = false) }

/** The order toggle for a show's episodes, sized and styled as a [Pill]. */
@Composable
fun SortPill(sort: EpisodeSort, onToggle: () -> Unit) {
    val newest = sort == EpisodeSort.NEWEST_FIRST
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = if (newest) NewestFirstIcon else OldestFirstIcon,
            contentDescription = if (newest) tr("Newest first") else tr("Oldest first"),
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp)
        )
    }
}
