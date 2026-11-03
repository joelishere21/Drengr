package com.drengr.launcher.feature.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drengr.launcher.core.designsystem.DrengrCard
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.designsystem.SectionHeader
import com.drengr.launcher.core.domain.AppInfo
import com.drengr.launcher.core.domain.AppShortcut
import com.drengr.launcher.feature.productivity.DocumentTimes
import com.drengr.launcher.integrations.media.PlayedItem
import com.drengr.launcher.launcher.LocalAppInteractions
import com.drengr.launcher.launcher.components.AppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** What played lately, newest first. Tapping opens the app it played in. */
@Composable
fun RecentlyPlayedSection(items: List<PlayedItem>, onOpen: (PlayedItem) -> Unit, onClear: () -> Unit) {
    if (items.isEmpty()) return
    val colors = DrengrTheme.colors
    val now = remember(items) { System.currentTimeMillis() }
    SectionHeader("Recently played", trailing = {
        Text(
            "Clear",
            style = DrengrTheme.typography.caption,
            color = colors.textSecondary,
            modifier = Modifier
                .clip(DrengrTheme.shapes.small)
                .clickable(role = Role.Button, onClickLabel = "Clear listening history", onClick = onClear)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    })
    Spacer(Modifier.padding(top = 4.dp))
    DrengrCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
        items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(DrengrTheme.shapes.small)
                    .clickable(onClickLabel = "Open ${item.appLabel}") { onOpen(item) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.History, null, tint = colors.textSecondary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = DrengrTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(item.artist, item.appLabel, DocumentTimes.label(item.playedAt, now, ZoneId.systemDefault()))
                            .joinToString(" · "),
                        style = DrengrTheme.typography.caption,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Installed podcast apps, each with its own shortcuts (e.g. "Up next", "Downloads") when it offers them. */
@Composable
fun PodcastsSection(apps: List<AppInfo>) {
    if (apps.isEmpty()) return
    SectionHeader("Podcasts")
    Spacer(Modifier.padding(top = 4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        apps.forEach { PodcastAppRow(it) }
    }
}

@Composable
private fun PodcastAppRow(app: AppInfo) {
    val interactions = LocalAppInteractions.current
    val colors = DrengrTheme.colors
    var shortcuts by remember(app.key) { mutableStateOf(emptyList<AppShortcut>()) }
    LaunchedEffect(app.key) {
        shortcuts = withContext(Dispatchers.IO) { runCatching { interactions.shortcuts(app) }.getOrDefault(emptyList()) }
    }
    DrengrCard(
        Modifier.fillMaxWidth(),
        onClick = { interactions.launch(app) },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(app.label, style = DrengrTheme.typography.body, modifier = Modifier.weight(1f), maxLines = 1)
        }
        if (shortcuts.isNotEmpty()) {
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shortcuts.take(4).forEach { shortcut ->
                    Text(
                        shortcut.label,
                        style = DrengrTheme.typography.caption,
                        color = colors.accent,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(DrengrTheme.shapes.small)
                            .clickable(role = Role.Button) { interactions.launchShortcut(shortcut) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
