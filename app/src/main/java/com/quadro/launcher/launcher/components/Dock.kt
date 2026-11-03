package com.quadro.launcher.launcher.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.quadro.launcher.core.designsystem.QuadroTheme
import com.quadro.launcher.core.domain.AppInfo

/** Persistent dock shown on every primary page: up to four apps, Applications, Command Center. */
@Composable
fun Dock(
    apps: List<AppInfo>,
    onOpenApplications: () -> Unit,
    onOpenCommandCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QuadroTheme.colors
    val shape = QuadroTheme.shapes.large
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.divider, shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        apps.forEach { app ->
            AppItem(app, Modifier.weight(1f), style = AppItemStyle.IconOnly, iconSize = 46.dp)
        }
        DockButton(Icons.Outlined.Apps, "Applications", onOpenApplications, Modifier.weight(1f))
        DockButton(Icons.Outlined.Terminal, "Command Center", onOpenCommandCenter, Modifier.weight(1f))
    }
}

@Composable
private fun DockButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.padding(6.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(46.dp)
                .clip(QuadroTheme.shapes.medium)
                .background(QuadroTheme.colors.accentSoft)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = QuadroTheme.colors.accent, modifier = Modifier.size(24.dp))
        }
    }
}
