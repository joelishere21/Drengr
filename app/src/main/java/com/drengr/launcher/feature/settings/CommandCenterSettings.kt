package com.drengr.launcher.feature.settings

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drengr.launcher.core.designsystem.DrengrCard
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.domain.UserSettings
import com.drengr.launcher.feature.common.rememberPermissionRequest

/** Settings → Command Center: what search covers, and clearing its history. */
@Composable
fun CommandCenterSettingsCard(
    settings: UserSettings,
    contactsGranted: Boolean,
    onSearchNotes: (Boolean) -> Unit,
    onSearchContacts: (Boolean) -> Unit,
    onContactsResult: (Boolean) -> Unit,
    onClearRecents: () -> Unit,
) {
    // Contacts access is asked for only here, when the user turns contact search on.
    val requestContacts = rememberPermissionRequest(
        permission = Manifest.permission.READ_CONTACTS,
        blockedMessage = "Contacts access is off. Turn the switch on again to open App info → Permissions.",
        onResult = onContactsResult,
    )
    DrengrCard(Modifier.fillMaxWidth()) {
        SwitchRow(
            title = "Search notes and tasks",
            subtitle = "Your notes and open tasks show up in results.",
            checked = settings.searchNotesAndTasks,
            onChange = onSearchNotes,
        )
        Spacer(Modifier.height(12.dp))
        SwitchRow(
            title = "Search contacts",
            subtitle = if (settings.searchContacts && !contactsGranted) {
                "Needs contacts access. Turn on to allow it."
            } else {
                "Find people by name. Read on the phone; nothing is copied."
            },
            checked = settings.searchContacts && contactsGranted,
            onChange = { on ->
                if (on && !contactsGranted) requestContacts() else onSearchContacts(on)
            },
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DrengrTheme.colors.divider)
        Column(Modifier.padding(top = 4.dp)) {
            ClickRow(
                title = "Clear recent",
                subtitle = "Forget the apps and actions listed under Recent",
                onClick = onClearRecents,
            )
        }
    }
}
