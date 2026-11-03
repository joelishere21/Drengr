package com.drengr.launcher.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drengr.launcher.core.designsystem.DrengrCard
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.domain.GestureTargets
import com.drengr.launcher.core.domain.HomeGesture
import com.drengr.launcher.core.domain.UserSettings
import com.drengr.launcher.plugins.LauncherAction

/** A gesture target as the picker and the Gestures card show it. */
data class GestureChoice(val target: String, val label: String, val description: String, val group: String)

/** Settings → Gestures: what each Home gesture does, and whether the screen edges are left to Android. */
@Composable
fun GestureSettingsCard(
    settings: UserSettings,
    choices: List<GestureChoice>,
    onSetGesture: (HomeGesture, String) -> Unit,
    onSetEdgeGuard: (Boolean) -> Unit,
) {
    var editing by remember { mutableStateOf<HomeGesture?>(null) }
    DrengrCard(Modifier.fillMaxWidth()) {
        HomeGesture.entries.forEach { gesture ->
            val target = settings.gestureTarget(gesture)
            ClickRow(
                title = gesture.label,
                subtitle = choices.firstOrNull { it.target == target }?.label ?: "Not available on this phone",
                onClick = { editing = gesture },
            )
        }
        Text(
            "On empty space on the Home page. Long-press always opens Settings.",
            style = DrengrTheme.typography.caption,
            color = DrengrTheme.colors.textSecondary,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        HorizontalDivider(color = DrengrTheme.colors.divider)
        Column(Modifier.padding(top = 12.dp)) {
            SwitchRow(
                title = "Leave screen edges to Android",
                subtitle = "Swipes that start at the left or right edge are ignored, so they don't clash with the back gesture.",
                checked = settings.gestureEdgeGuard,
                onChange = onSetEdgeGuard,
            )
        }
    }

    editing?.let { gesture ->
        GesturePicker(
            gesture = gesture,
            selected = settings.gestureTarget(gesture),
            choices = choices,
            onPick = {
                onSetGesture(gesture, it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun GesturePicker(
    gesture: HomeGesture,
    selected: String,
    choices: List<GestureChoice>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(gesture.label) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                choices.groupBy { it.group }.forEach { (group, inGroup) ->
                    item(key = "group:$group") {
                        Text(
                            group.uppercase(),
                            style = DrengrTheme.typography.label,
                            color = DrengrTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(inGroup, key = { it.target }) { choice ->
                        ChoiceRow(
                            title = choice.label,
                            subtitle = choice.description,
                            selected = choice.target == selected,
                            onClick = { onPick(choice.target) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Nothing and Command Center first, then every action available on this device by category. */
fun gestureChoices(actions: List<LauncherAction>): List<GestureChoice> =
    listOf(
        GestureChoice(GestureTargets.NONE, "Nothing", "The gesture is off", "Drengr"),
        GestureChoice(GestureTargets.COMMAND_CENTER, "Command Center", "Search apps and actions", "Drengr"),
    ) + actions
        .filter { it.id != GestureTargets.NONE && it.id != GestureTargets.COMMAND_CENTER }
        .sortedBy { it.category.ordinal }
        .map { GestureChoice(it.id, it.label, it.description, it.category.label) }
