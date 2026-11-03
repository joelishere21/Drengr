package com.drengr.launcher.core.domain

/** Gestures on empty space on the Home page that the user can bind to an action. */
enum class HomeGesture(val label: String, val defaultTarget: String) {
    SwipeUp("Swipe up", GestureTargets.COMMAND_CENTER),
    SwipeDown("Swipe down", GestureTargets.NOTIFICATIONS),
    DoubleTap("Double-tap", GestureTargets.NONE),
}

/**
 * What a gesture does: [NONE], [COMMAND_CENTER], or the id of any Command Center action
 * (see ActionRegistry), such as "camera" or "flashlight".
 */
object GestureTargets {
    const val NONE = "none"
    const val COMMAND_CENTER = "command_center"
    /** The "Show notifications" action. */
    const val NOTIFICATIONS = "notifications"
}
