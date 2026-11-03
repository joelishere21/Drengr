package com.quadro.launcher.feature.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quadro.launcher.core.designsystem.IconBadge
import com.quadro.launcher.core.designsystem.Pill
import com.quadro.launcher.core.designsystem.QuadroCard
import com.quadro.launcher.core.designsystem.QuadroTheme
import com.quadro.launcher.plugins.ActionKind
import com.quadro.launcher.plugins.LauncherAction

/** A registry action as a tappable row, tagged with how it takes effect. */
@Composable
fun ActionRow(action: LauncherAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    QuadroCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(action.icon, size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(action.label, style = QuadroTheme.typography.body, maxLines = 1)
                Text(
                    action.description,
                    style = QuadroTheme.typography.caption,
                    color = QuadroTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Pill(action.kind.label, highlighted = action.kind == ActionKind.Direct)
        }
    }
}
