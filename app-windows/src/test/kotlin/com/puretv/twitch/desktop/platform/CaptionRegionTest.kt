package com.puretv.twitch.desktop.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptionRegionTest {
    @Test fun only_the_drag_area_counts_as_title_bar() {
        val bar = intArrayOf(0, 0, 1700, 72) // left, top, right, bottom (window buttons sit past 1700)
        assertTrue(WindowsNative.insideRegion(bar, 10, 10))
        assertTrue(WindowsNative.insideRegion(bar, 1699, 71))
        assertFalse(WindowsNative.insideRegion(bar, 1700, 10), "window buttons stay clickable")
        assertFalse(WindowsNative.insideRegion(bar, 10, 72), "content below the bar stays clickable")
        assertFalse(WindowsNative.insideRegion(null, 10, 10))
    }
}
