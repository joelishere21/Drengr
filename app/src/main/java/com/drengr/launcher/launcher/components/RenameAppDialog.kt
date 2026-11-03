package com.drengr.launcher.launcher.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.domain.AppInfo
import com.drengr.launcher.core.domain.MAX_APP_LABEL_LENGTH
import com.drengr.launcher.core.domain.normalizeAppLabel

/** Asks for a new name. [onRename] gets null when the user restores the app's own name. */
@Composable
fun RenameAppDialog(app: AppInfo, onRename: (String?) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable(app.key, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(app.label, selection = TextRange(0, app.label.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val name = normalizeAppLabel(value.text)
    val save = {
        if (name.isNotEmpty()) {
            onRename(name)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename app") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { if (it.text.length <= MAX_APP_LABEL_LENGTH) value = it },
                singleLine = true,
                placeholder = { Text(app.originalLabel) },
                supportingText = {
                    Text(
                        "Original name: ${app.originalLabel}",
                        color = DrengrTheme.colors.textSecondary,
                    )
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = save, enabled = name.isNotEmpty()) { Text("Save") }
        },
        dismissButton = {
            if (app.isRenamed) {
                TextButton(onClick = {
                    onRename(null)
                    onDismiss()
                }) { Text("Reset") }
            }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
