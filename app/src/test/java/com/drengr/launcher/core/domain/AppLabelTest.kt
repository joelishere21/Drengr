package com.drengr.launcher.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLabelTest {
    @Test fun trimsAndCollapsesWhitespace() {
        assertEquals("My Bank", normalizeAppLabel("  My \t\n Bank  "))
    }

    @Test fun blankBecomesEmpty() {
        assertEquals("", normalizeAppLabel(" \n "))
    }

    @Test fun capsLengthWithoutTrailingSpace() {
        val long = "a".repeat(MAX_APP_LABEL_LENGTH - 1) + " bcd"
        assertEquals("a".repeat(MAX_APP_LABEL_LENGTH - 1), normalizeAppLabel(long))
    }
}
