package com.drengr.launcher.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ListsTest {
    private val dock = listOf("phone", "chat", "web", "camera")

    @Test fun movesLeftAndRight() {
        assertEquals(listOf("chat", "phone", "web", "camera"), dock.moving("chat", -1))
        assertEquals(listOf("phone", "web", "chat", "camera"), dock.moving("chat", 1))
    }

    @Test fun clampsAtTheEnds() {
        assertEquals(dock, dock.moving("phone", -1))
        assertEquals(listOf("chat", "web", "camera", "phone"), dock.moving("phone", 10))
    }

    @Test fun ignoresUnknownItems() {
        assertEquals(dock, dock.moving("mail", 1))
    }

    @Test fun ordersByListThenOriginal() {
        val devices = listOf("lamp", "strip", "plug", "new")
        assertEquals(listOf("plug", "lamp", "strip", "new"), devices.inOrder(listOf("plug", "gone", "lamp")) { it })
        assertEquals(devices, devices.inOrder(emptyList()) { it })
    }
}
