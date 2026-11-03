package com.drengr.launcher.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Base surface for every card in the launcher: rounded, subtle border, optional click/long-click. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrengrCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    elevated: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DrengrTheme.colors
    val shape = DrengrTheme.shapes.large
    val click = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (elevated) colors.surfaceElevated else colors.surface)
            .border(1.dp, colors.divider, shape)
            .then(click)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title.uppercase(),
            style = DrengrTheme.typography.label,
            color = DrengrTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = DrengrTheme.typography.headline)
            if (subtitle != null) {
                Text(subtitle, style = DrengrTheme.typography.mono, color = DrengrTheme.colors.textSecondary)
            }
        }
        actions?.invoke(this)
    }
}

/** Outline icon on a soft-accent tile. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color = DrengrTheme.colors.accent,
    background: Color = DrengrTheme.colors.accentSoft,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(DrengrTheme.shapes.small)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** Small pill label, used to mark actions as "Direct" vs "Settings". */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val colors = DrengrTheme.colors
    Text(
        text = text,
        style = DrengrTheme.typography.caption,
        color = if (highlighted) colors.accent else colors.textSecondary,
        maxLines = 1,
        modifier = modifier
            .clip(DrengrTheme.shapes.pill)
            .background(if (highlighted) colors.accentSoft else colors.surfaceElevated)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun DrengrSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit = {},
) {
    val colors = DrengrTheme.colors
    val shape = DrengrTheme.shapes.medium
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = DrengrTheme.typography.body.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.divider, shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = DrengrTheme.typography.body,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Clear",
                        tint = colors.textSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onValueChange("") },
                    )
                }
            }
        },
    )
}
