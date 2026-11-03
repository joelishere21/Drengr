package com.drengr.launcher.feature.productivity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drengr.launcher.core.designsystem.DrengrCard
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.designsystem.IconBadge
import com.drengr.launcher.integrations.documents.RecentDocument
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Files the user pinned with the system file picker, most recently used first. Tap opens the file
 * in its app; long-press removes it (and Drengr's access to it).
 */
@Composable
fun RecentDocumentsCard(
    documents: List<RecentDocument>,
    onAdd: (List<android.net.Uri>) -> Unit,
    onOpen: (RecentDocument) -> Unit,
    onRemove: (RecentDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DrengrTheme.colors
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onAdd(uris)
    }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val now = remember(documents) { System.currentTimeMillis() }
    val shown = if (showAll) documents else documents.take(COLLAPSED_COUNT)

    DrengrCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.FolderOpen)
            Spacer(Modifier.width(12.dp))
            Text("Recent documents", style = DrengrTheme.typography.title, modifier = Modifier.weight(1f))
            Row(
                Modifier
                    .clip(DrengrTheme.shapes.small)
                    .clickable(role = Role.Button, onClickLabel = "Add documents") {
                        picker.launch(arrayOf("*/*"))
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.padding(end = 4.dp))
                Text("Add", color = colors.accent, style = DrengrTheme.typography.body)
            }
        }

        if (documents.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Pin files you use often. Drengr can open only the files you pick here.",
                style = DrengrTheme.typography.caption,
                color = colors.textSecondary,
            )
        } else {
            Spacer(Modifier.height(6.dp))
            shown.forEach { doc ->
                DocumentRow(doc, now, onOpen = { onOpen(doc) }, onRemove = { onRemove(doc) })
            }
            if (documents.size > COLLAPSED_COUNT) {
                Text(
                    if (showAll) "Show fewer" else "Show all ${documents.size}",
                    color = colors.accent,
                    style = DrengrTheme.typography.caption,
                    modifier = Modifier
                        .clip(DrengrTheme.shapes.small)
                        .clickable(role = Role.Button) { showAll = !showAll }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentRow(document: RecentDocument, now: Long, onOpen: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(DrengrTheme.shapes.small)
                .combinedClickable(
                    onClickLabel = "Open",
                    onLongClickLabel = "More options",
                    onLongClick = { menuOpen = true },
                    onClick = onOpen,
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                DocumentIcons.forType(document.mimeType),
                contentDescription = null,
                tint = DrengrTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(document.name, style = DrengrTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    DocumentTimes.label(document.usedAt, now, ZoneId.systemDefault()),
                    style = DrengrTheme.typography.caption,
                    color = DrengrTheme.colors.textSecondary,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Remove from list") },
                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}

private const val COLLAPSED_COUNT = 5

internal object DocumentIcons {
    fun forType(mimeType: String?): ImageVector {
        val type = mimeType.orEmpty().lowercase()
        return when {
            type == "application/pdf" -> Icons.Outlined.PictureAsPdf
            type.startsWith("image/") -> Icons.Outlined.Image
            type.startsWith("video/") -> Icons.Outlined.Movie
            type.startsWith("audio/") -> Icons.Outlined.AudioFile
            "spreadsheet" in type || "excel" in type || type == "text/csv" -> Icons.Outlined.TableChart
            "presentation" in type || "powerpoint" in type -> Icons.Outlined.Slideshow
            type.startsWith("text/") || "document" in type || "msword" in type -> Icons.Outlined.Description
            else -> Icons.Outlined.InsertDriveFile
        }
    }
}

/** "Just now", "3 h ago", "Yesterday", "Monday", "Mar 4", "Mar 4, 2025". */
internal object DocumentTimes {
    fun label(usedAt: Long, now: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
        val minutes = (now - usedAt) / 60_000
        if (minutes < 1) return "Just now"
        if (minutes < 60) return "$minutes min ago"
        val then = Instant.ofEpochMilli(usedAt).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(then.toLocalDate(), today)
        val pattern = when {
            days <= 0 -> return "${minutes / 60} h ago"
            days == 1L -> return "Yesterday"
            days < 7 -> "EEEE"
            then.year == today.year -> "MMM d"
            else -> "MMM d, yyyy"
        }
        return then.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}
