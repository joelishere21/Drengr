package com.drengr.launcher.launcher.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.drengr.launcher.core.domain.PageId
import com.drengr.launcher.core.designsystem.DrengrTheme

/** Dots that stretch for the current page, plus the page name. Tap a dot to jump. */
@Composable
fun PageIndicator(
    pages: List<PageId>,
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DrengrTheme.colors
    val current = pagerState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            pages.forEachIndexed { index, page ->
                val selected = index == current
                val width by animateDpAsState(if (selected) 22.dp else 6.dp, label = "dotWidth")
                val color by animateColorAsState(if (selected) colors.accent else colors.divider, label = "dotColor")
                Box(
                    Modifier
                        .clip(DrengrTheme.shapes.pill)
                        .clickable { onSelect(index) }
                        .semantics { contentDescription = "Go to ${page.title}" }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(width)
                            .clip(DrengrTheme.shapes.pill)
                            .background(color),
                    )
                }
            }
        }
        if (pages.isNotEmpty()) {
            Text(
                pages[current].title.uppercase(),
                style = DrengrTheme.typography.label,
                color = colors.textSecondary,
            )
        }
    }
}
