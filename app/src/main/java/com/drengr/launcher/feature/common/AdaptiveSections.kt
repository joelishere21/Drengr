package com.drengr.launcher.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drengr.launcher.core.designsystem.LocalLauncherLayout

private val COLUMN_GAP = 20.dp

/**
 * Two groups of page sections: side by side on wide windows, stacked (with [stackedGap]
 * between them) on phones. Safe inside a vertically scrolling page.
 */
@Composable
fun TwoColumns(
    modifier: Modifier = Modifier,
    stackedGap: Dp = 20.dp,
    first: @Composable ColumnScope.() -> Unit,
    second: @Composable ColumnScope.() -> Unit,
) {
    if (LocalLauncherLayout.current.isWide) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
            Column(Modifier.weight(1f), content = first)
            Column(Modifier.weight(1f), content = second)
        }
    } else {
        Column(modifier.fillMaxWidth()) {
            first()
            Spacer(Modifier.height(stackedGap))
            second()
        }
    }
}

/**
 * Similar cards in rows: one per row on phones, up to three on wide windows. Items keep their
 * own heights; each row is as tall as its tallest card.
 */
@Composable
fun <T> CardGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    itemKey: ((T) -> Any)? = null,
    content: @Composable (T) -> Unit,
) {
    val layout = LocalLauncherLayout.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = layout.cardColumns(maxWidth.value.toInt())
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { item ->
                        Column(Modifier.weight(1f)) {
                            if (itemKey != null) key(itemKey(item)) { content(item) } else content(item)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
