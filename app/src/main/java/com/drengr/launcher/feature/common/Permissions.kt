package com.drengr.launcher.feature.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat

/**
 * Returns a function that asks for [permission] when the user taps a feature that needs it.
 * Once Android stops showing the prompt (denied twice), the next tap opens App info instead,
 * where the permission can still be granted.
 */
@Composable
fun rememberPermissionRequest(
    permission: String,
    blockedMessage: String,
    onResult: (granted: Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    var blocked by rememberSaveable(permission) { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activity = context.findActivity()
        if (!granted && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            blocked = true
            Toast.makeText(context, blockedMessage, Toast.LENGTH_LONG).show()
        }
        currentOnResult(granted)
    }

    return {
        if (blocked) {
            blocked = false // the user may grant it in App info; let the next tap ask again
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        } else {
            launcher.launch(permission)
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
