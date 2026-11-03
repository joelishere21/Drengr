package com.drengr.launcher.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import com.drengr.launcher.core.domain.GestureTargets
import com.drengr.launcher.core.domain.HomeGesture
import com.drengr.launcher.core.domain.UserSettings
import com.drengr.launcher.feature.home.HomeGestures
import com.drengr.launcher.plugins.ActionCategory
import com.drengr.launcher.plugins.ActionKind
import com.drengr.launcher.plugins.ActionResult
import com.drengr.launcher.plugins.LauncherAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureSettingsTest {
    @Test fun defaultsMatchTheOldBehavior() {
        val s = UserSettings()
        assertEquals(GestureTargets.COMMAND_CENTER, s.gestureTarget(HomeGesture.SwipeUp))
        assertEquals(GestureTargets.NOTIFICATIONS, s.gestureTarget(HomeGesture.SwipeDown))
        assertEquals(GestureTargets.NONE, s.gestureTarget(HomeGesture.DoubleTap))
        assertEquals("camera", s.copy(gestures = mapOf(HomeGesture.DoubleTap to "camera")).gestureTarget(HomeGesture.DoubleTap))
    }

    @Test fun swipeDirection() {
        assertEquals(HomeGesture.SwipeDown, HomeGestures.swipe(150f, 100f))
        assertEquals(HomeGesture.SwipeUp, HomeGestures.swipe(-150f, 100f))
        assertNull(HomeGestures.swipe(60f, 100f))
    }

    @Test fun edges() {
        assertTrue(HomeGestures.startsAtEdge(10f, 1000f, 48f))
        assertTrue(HomeGestures.startsAtEdge(990f, 1000f, 48f))
        assertFalse(HomeGestures.startsAtEdge(500f, 1000f, 48f))
    }

    @Test fun choicesStartWithBuiltInsAndGroupByCategory() {
        fun action(id: String, category: ActionCategory) =
            LauncherAction(id, id, "", category, ActionKind.Direct, Icons.Outlined.Settings) { ActionResult.Done }
        val choices = gestureChoices(listOf(action("media", ActionCategory.Media), action("wifi", ActionCategory.System)))
        assertEquals(listOf(GestureTargets.NONE, GestureTargets.COMMAND_CENTER, "wifi", "media"), choices.map { it.target })
        assertEquals("System", choices[2].group)
    }
}
