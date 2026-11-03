package com.drengr.launcher.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLayoutTest {
    private val phone = LauncherLayout(412, 915)
    private val phoneLandscape = LauncherLayout(915, 412)
    private val tabletPortrait = LauncherLayout(800, 1280)
    private val tabletLandscape = LauncherLayout(1280, 800)

    @Test fun breakpoints() {
        assertEquals(WidthClass.Compact, LauncherLayout(599, 900).widthClass)
        assertEquals(WidthClass.Medium, LauncherLayout(600, 900).widthClass)
        assertEquals(WidthClass.Expanded, LauncherLayout(840, 900).widthClass)
    }

    @Test fun phoneKeepsOriginalLayout() {
        assertFalse(phone.twoPaneHome)
        assertEquals(4, phone.dockSlots)
        assertEquals(5, phone.appGridColumns(5, 380))
        assertEquals(1, phone.appListColumns(380))
        assertEquals(4, phone.favoriteColumns(380))
    }

    @Test fun rotatingAPhoneDoesNotMakeItATablet() {
        assertFalse(phoneLandscape.isLargeScreen)
        assertTrue(phoneLandscape.isShort)
        assertTrue(phoneLandscape.twoPaneHome)
        assertEquals(4, phoneLandscape.dockSlots)
    }

    @Test fun tabletsGetMoreRoom() {
        assertTrue(tabletPortrait.isLargeScreen)
        assertFalse(tabletPortrait.twoPaneHome) // tall: one wide column
        assertTrue(tabletLandscape.twoPaneHome)
        assertEquals(5, tabletPortrait.homeAgendaLimit)
        assertEquals(6, tabletLandscape.homeAgendaLimit)
        assertEquals(3, phoneLandscape.homeAgendaLimit)
        assertEquals(3, phone.homeAgendaLimit)
        assertEquals(6, tabletLandscape.dockSlots)
        // 5 phone columns → ~95 dp cells on a tablet.
        assertEquals(8, tabletPortrait.appGridColumns(5, 760))
        assertEquals(12, tabletLandscape.appGridColumns(5, 1240))
        assertEquals(3, tabletLandscape.appListColumns(1240))
    }

    @Test fun gridNeverHasFewerColumnsThanTheSetting() =
        assertEquals(6, LauncherLayout(600, 900).appGridColumns(6, 300))

    @Test fun favoritesScaleWithPaneWidth() {
        assertEquals(4, tabletPortrait.favoriteColumns(360))
        assertEquals(7, tabletPortrait.favoriteColumns(720))
        assertEquals(6, tabletLandscape.favoriteColumns(600))
        assertEquals(8, tabletLandscape.favoriteColumns(2000))
    }

    @Test fun cardColumns() {
        assertEquals(1, phone.cardColumns(380))
        assertEquals(2, tabletPortrait.cardColumns(760))
        assertEquals(3, tabletLandscape.cardColumns(1240))
        assertEquals(1, tabletPortrait.cardColumns(300))
    }

    @Test fun overlaysAndTextCapOnlyOnWideWindows() {
        assertEquals(androidx.compose.ui.unit.Dp.Unspecified, phone.overlayMaxWidth)
        assertEquals(androidx.compose.ui.unit.Dp.Unspecified, phone.readableMaxWidth)
        assertTrue(tabletPortrait.isWide)
        assertEquals(720f, tabletLandscape.overlayMaxWidth.value)
    }
}
