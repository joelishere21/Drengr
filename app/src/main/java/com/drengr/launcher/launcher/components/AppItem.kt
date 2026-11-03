package com.drengr.launcher.launcher.components

import android.app.ActivityOptions
import android.graphics.Rect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dock
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shortcut
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.designsystem.onWallpaper
import com.drengr.launcher.core.domain.AppInfo
import com.drengr.launcher.launcher.LocalAppInteractions
import com.drengr.launcher.launcher.LocalAppMembership

enum class AppItemStyle { Grid, List, IconOnly }

/** An app tile: tap to launch (with a reveal animation), long-press for actions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItem(
    app: AppInfo,
    modifier: Modifier = Modifier,
    style: AppItemStyle = AppItemStyle.Grid,
    iconSize: Dp = 52.dp,
) {
    val interactions = LocalAppInteractions.current
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val interaction = Modifier
        .onGloballyPositioned { coordinates = it }
        .clip(DrengrTheme.shapes.medium)
        .combinedClickable(
            onClickLabel = "Open ${app.label}",
            onLongClickLabel = "App actions",
            onClick = {
                val bounds = coordinates?.takeIf { it.isAttached }?.boundsInWindow()
                if (bounds != null) {
                    val rect = Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
                    val options = ActivityOptions.makeClipRevealAnimation(
                        view, rect.left, rect.top, rect.width(), rect.height(),
                    ).toBundle()
                    interactions.launch(app, rect, options)
                } else {
                    interactions.launch(app)
                }
            },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menuOpen = true
            },
        )

    Box(modifier) {
        when (style) {
            AppItemStyle.Grid -> Column(
                modifier = interaction
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(app, iconSize)
                Spacer(Modifier.height(6.dp))
                Text(
                    app.label,
                    style = DrengrTheme.typography.caption.onWallpaper(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            AppItemStyle.List -> Row(
                modifier = interaction
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app, 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = DrengrTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (app.isWorkProfile) "${app.category.label} · Work" else app.category.label,
                        style = DrengrTheme.typography.caption,
                        color = DrengrTheme.colors.textSecondary,
                    )
                }
            }

            AppItemStyle.IconOnly -> Box(
                modifier = interaction.padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(app, iconSize)
            }
        }
        AppActionsMenu(app = app, expanded = menuOpen, onDismiss = { menuOpen = false })
    }
}

@Composable
fun AppIcon(app: AppInfo, size: Dp, modifier: Modifier = Modifier) {
    val interactions = LocalAppInteractions.current
    var icon: ImageBitmap? by remember(app.key) { mutableStateOf(interactions.cachedIcon(app)) }
    LaunchedEffect(app.key) {
        if (icon == null) icon = interactions.loadIcon(app)
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.3f))
                .background(DrengrTheme.colors.surfaceElevated),
        )
    }
}

@Composable
private fun AppActionsMenu(app: AppInfo, expanded: Boolean, onDismiss: () -> Unit) {
    val interactions = LocalAppInteractions.current
    val membership = LocalAppMembership.current
    val shortcuts = remember(expanded, app.key) {
        if (expanded) interactions.shortcuts(app) else emptyList()
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            app.label,
            style = DrengrTheme.typography.label,
            color = DrengrTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        shortcuts.forEach { shortcut ->
            DropdownMenuItem(
                text = { Text(shortcut.label) },
                leadingIcon = { Icon(Icons.Outlined.Shortcut, null) },
                onClick = {
                    onDismiss()
                    interactions.launchShortcut(shortcut)
                },
            )
        }
        if (shortcuts.isNotEmpty()) HorizontalDivider(color = DrengrTheme.colors.divider)
        val isFavorite = app.key in membership.favorites
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Remove from favorites" else "Add to favorites") },
            leadingIcon = { Icon(if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarOutline, null) },
            onClick = {
                onDismiss()
                interactions.toggleFavorite(app)
            },
        )
        val inDock = app.key in membership.dock
        DropdownMenuItem(
            text = { Text(if (inDock) "Remove from dock" else "Add to dock") },
            leadingIcon = { Icon(Icons.Outlined.Dock, null) },
            onClick = {
                onDismiss()
                interactions.toggleDock(app)
            },
        )
        DropdownMenuItem(
            text = { Text("App info") },
            leadingIcon = { Icon(Icons.Outlined.Info, null) },
            onClick = {
                onDismiss()
                interactions.openAppInfo(app)
            },
        )
        DropdownMenuItem(
            text = { Text("Uninstall") },
            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
            onClick = {
                onDismiss()
                interactions.uninstall(app)
            },
        )
    }
}
