package com.drengr.launcher.feature.common

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
import com.drengr.launcher.core.designsystem.IconBadge
import com.drengr.launcher.core.designsystem.Pill
import com.drengr.launcher.core.designsystem.DrengrCard
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.plugins.ActionKind
import com.drengr.launcher.plugins.LauncherAction

/** A registry action as a tappable row, tagged with how it takes effect. */
@Composable
fun ActionRow(action: LauncherAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DrengrCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(action.icon, size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(action.label, style = DrengrTheme.typography.body, maxLines = 1)
                Text(
                    action.description,
                    style = DrengrTheme.typography.caption,
                    color = DrengrTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Pill(action.kind.label, highlighted = action.kind == ActionKind.Direct)
        }
    }
}
