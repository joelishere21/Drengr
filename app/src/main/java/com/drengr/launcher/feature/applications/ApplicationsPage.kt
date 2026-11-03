package com.drengr.launcher.feature.applications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.drengr.launcher.core.common.FuzzyMatcher
import com.drengr.launcher.core.designsystem.PageHeader
import com.drengr.launcher.core.designsystem.DrengrSearchField
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.designsystem.LocalLauncherLayout
import com.drengr.launcher.core.designsystem.SectionHeader
import com.drengr.launcher.core.domain.AppCategory
import com.drengr.launcher.core.domain.AppInfo
import com.drengr.launcher.core.domain.AppLayout
import com.drengr.launcher.core.domain.searchKeywords
import com.drengr.launcher.launcher.LauncherUiState
import com.drengr.launcher.launcher.LocalAppInteractions
import com.drengr.launcher.launcher.components.AppItem
import com.drengr.launcher.launcher.components.AppItemStyle

@Composable
fun ApplicationsPage(
    state: LauncherUiState,
    isCurrent: Boolean,
    onLayoutChange: (AppLayout) -> Unit,
) {
    val interactions = LocalAppInteractions.current
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val category = AppCategory.entries.firstOrNull { it.name == categoryName }
    val layout = state.settings.appLayout
    val windowLayout = LocalLauncherLayout.current
    val gridState = rememberLazyGridState()

    // Leaving the page clears the search so it's fresh next time.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            query = ""
            focusManager.clearFocus()
        }
    }

    val presentCategories = remember(state.apps) {
        state.apps.map { it.category }.toSet().let { present -> AppCategory.entries.filter { it in present } }
    }
    val results: List<AppInfo> = remember(state.apps, query, category) {
        val pool = if (category == null) state.apps else state.apps.filter { it.category == category }
        if (query.isBlank()) {
            pool
        } else {
            pool.mapNotNull { app -> FuzzyMatcher.score(query, app.label, app.searchKeywords)?.let { it to app } }
                .sortedByDescending { it.first }
                .map { it.second }
        }
    }
    LaunchedEffect(query, category) { gridState.scrollToItem(0) }

    val browsing = query.isBlank() && category == null
    val itemStyle = if (layout == AppLayout.Grid) AppItemStyle.Grid else AppItemStyle.List

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = DrengrTheme.spacing.pageHorizontal),
    ) {
        PageHeader(
            title = "Applications",
            subtitle = "${state.apps.size} installed",
            modifier = Modifier.padding(top = 12.dp),
        ) {
            IconButton(
                onClick = { onLayoutChange(if (layout == AppLayout.Grid) AppLayout.List else AppLayout.Grid) },
            ) {
                Icon(
                    if (layout == AppLayout.Grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                    contentDescription = if (layout == AppLayout.Grid) "Show as list" else "Show as grid",
                    tint = DrengrTheme.colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        DrengrSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search apps",
            onSubmit = {
                results.firstOrNull()?.let {
                    focusManager.clearFocus()
                    interactions.launch(it)
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        CategoryChips(
            categories = presentCategories,
            selected = category,
            onSelect = { categoryName = it?.name },
        )
        Spacer(Modifier.height(6.dp))

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = maxWidth.value.toInt()
            val columns = if (layout == AppLayout.Grid) {
                windowLayout.appGridColumns(state.settings.gridColumns, width)
            } else {
                windowLayout.appListColumns(width)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

                if (!state.appsLoaded) {
                    item(span = fullSpan) { EmptyText("Loading apps…") }
                }
                if (browsing && state.favorites.isNotEmpty()) {
                    item(span = fullSpan, key = "h_fav") { SectionHeader("Favorites", Modifier.padding(top = 10.dp)) }
                    items(state.favorites, key = { "fav_" + it.key }) { AppItem(it, style = itemStyle) }
                }
                if (browsing && state.frequent.isNotEmpty()) {
                    item(span = fullSpan, key = "h_freq") { SectionHeader("Frequently used", Modifier.padding(top = 10.dp)) }
                    items(state.frequent.take(columns.coerceAtLeast(4)), key = { "freq_" + it.key }) {
                        AppItem(it, style = itemStyle)
                    }
                }
                if (state.appsLoaded) {
                    item(span = fullSpan, key = "h_all") {
                        SectionHeader(
                            when {
                                query.isNotBlank() -> "${results.size} results"
                                category != null -> category.label
                                else -> "All apps"
                            },
                            Modifier.padding(top = 10.dp),
                        )
                    }
                }
                items(results, key = { it.key }) { AppItem(it, style = itemStyle) }
                if (state.appsLoaded && results.isEmpty()) {
                    item(span = fullSpan) { EmptyText("No apps match “$query”.") }
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(
    categories: List<AppCategory>,
    selected: AppCategory?,
    onSelect: (AppCategory?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Chip("All", selected == null) { onSelect(null) } }
        items(categories, key = { it.name }) { c ->
            Chip(c.label, selected == c) { onSelect(if (selected == c) null else c) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = DrengrTheme.colors
    val shape = DrengrTheme.shapes.pill
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) colors.accentSoft else colors.surface)
            .border(1.dp, if (selected) colors.accent else colors.divider, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = DrengrTheme.typography.caption,
            color = if (selected) colors.accent else colors.textSecondary,
        )
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = DrengrTheme.typography.body,
        color = DrengrTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    )
}
