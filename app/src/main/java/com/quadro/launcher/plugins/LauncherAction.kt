package com.quadro.launcher.plugins

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.quadro.launcher.core.domain.PageId

enum class ActionCategory(val label: String) {
    System("System"),
    Productivity("Productivity"),
    Media("Media"),
    Launcher("Quadro"),
}

/** How an action takes effect, so the UI can be honest about what the launcher can do directly. */
enum class ActionKind(val label: String) {
    Direct("Direct"),
    OpensSettings("Settings"),
    OpensApp("App"),
    Navigation("Quadro"),
}

sealed interface ActionResult {
    data object Done : ActionResult
    data class Navigate(val page: PageId) : ActionResult
    data class Message(val text: String) : ActionResult
}

/**
 * A named, permission-aware command. Features register these with [ActionRegistry];
 * the Command Center and System page discover and run them.
 */
@Immutable
class LauncherAction(
    val id: String,
    val label: String,
    val description: String,
    val category: ActionCategory,
    val kind: ActionKind,
    val icon: ImageVector,
    val keywords: List<String> = emptyList(),
    /** Hardware/feature availability. Permission gaps should be handled in [execute], not hidden. */
    val isAvailable: (Context) -> Boolean = { true },
    private val execute: (Context) -> ActionResult,
) {
    /** Runs the action with an Activity context. Never throws. */
    fun run(context: Context): ActionResult = try {
        execute(context)
    } catch (e: ActivityNotFoundException) {
        ActionResult.Message("No app on this device can handle “$label”.")
    } catch (e: SecurityException) {
        ActionResult.Message("Android doesn't allow Quadro to do that.")
    } catch (e: Exception) {
        ActionResult.Message("Couldn't run “$label”.")
    }
}

/** Starts [intent], adding NEW_TASK when called with a non-Activity context. */
internal fun Context.launch(intent: Intent): ActionResult {
    if (this !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    return ActionResult.Done
}
