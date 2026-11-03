package com.drengr.launcher.feature.home

import com.drengr.launcher.core.domain.HomeGesture

/** Decides which Home gesture a finished vertical drag was, kept free of Compose for tests. */
object HomeGestures {
    /** [totalDy] is the drag's vertical distance in px (positive = down). */
    fun swipe(totalDy: Float, threshold: Float): HomeGesture? = when {
        totalDy > threshold -> HomeGesture.SwipeDown
        totalDy < -threshold -> HomeGesture.SwipeUp
        else -> null
    }

    /** Whether a touch at [x] starts in the left or right [edge] strip of a [width]-wide area. */
    fun startsAtEdge(x: Float, width: Float, edge: Float): Boolean = x < edge || x > width - edge
}
