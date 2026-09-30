package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.api.ChannelSearchResult
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.desktop.data.SettingsPanel
import com.puretv.twitch.desktop.data.searchSettings
import com.puretv.twitch.desktop.ui.components.readableNameArgb
import com.puretv.twitch.desktop.ui.components.relativeLuminance
import com.puretv.twitch.desktop.ui.screens.SEARCH_BAR_LIMITS
import com.puretv.twitch.desktop.ui.screens.SEARCH_TAB_LIMITS
import com.puretv.twitch.desktop.ui.screens.SearchHit
import com.puretv.twitch.desktop.ui.screens.searchHits
import com.puretv.twitch.desktop.ui.screens.sizedBoxArt
import java.awt.Component
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UniversalSearchTest {
    @Test fun settings_match_titles_and_keywords() {
        assertEquals("Accent color", searchSettings("accent").first().title)
        assertEquals(SettingsPanel.PLAYBACK, searchSettings("1080p").first().panel)
        assertEquals("Ignored users", searchSettings("mute").first().title)
        assertTrue(searchSettings("chat text").any { it.title == "Chat text size" }, "every word must match")
        assertTrue(searchSettings("zzzz").isEmpty())
        assertTrue(searchSettings("a").isEmpty(), "one letter is too vague")
    }

    @Test fun hits_are_grouped_and_limited() {
        val state = SearchUiState(
            query = "brow",
            results = List(10) { ChannelSearchResult(id = "$it", broadcaster_login = "c$it", display_name = "C$it") },
            categories = List(5) { GameInfo("$it", "Game $it") },
            settings = searchSettings("browsing"),
        )
        val bar = searchHits(state, hiddenTabs = emptyList(), limits = SEARCH_BAR_LIMITS)
        assertTrue(bar.first() is SearchHit.Page, "typing a tab name offers the tab first")
        assertEquals(SEARCH_BAR_LIMITS.channels, bar.count { it is SearchHit.Channel })
        assertEquals(SEARCH_BAR_LIMITS.categories, bar.count { it is SearchHit.Category })
        val hidden = searchHits(state, hiddenTabs = listOf("BROWSE"), limits = SEARCH_TAB_LIMITS)
        assertTrue(hidden.none { it is SearchHit.Page }, "hidden tabs aren't offered")
        assertEquals(bar.map { it.key }.distinct().size, bar.size, "keys are unique")
    }

    @Test fun box_art_is_resized() {
        assertEquals(
            "https://static-cdn.jtvnw.net/ttv-boxart/33214-72x96.jpg",
            sizedBoxArt("https://static-cdn.jtvnw.net/ttv-boxart/33214-52x72.jpg", 72, 96),
        )
        assertEquals("x-72x96.jpg", sizedBoxArt("x-{width}x{height}.jpg", 72, 96))
    }

    @Test fun dark_name_colors_are_lifted() {
        val blue = readableNameArgb(0x0000FF)
        assertTrue(relativeLuminance(blue) >= 0.24)
        assertTrue((blue and 0xFF) > ((blue shr 16) and 0xFF), "stays blue")
        val coral = 0xFF7F50 or (0xFF shl 24)
        assertEquals(coral, readableNameArgb(0xFF7F50), "readable colors are untouched")
        assertTrue(relativeLuminance(readableNameArgb(0x000000)) >= 0.24)
    }

    @Test fun hotkey_is_ctrl_shift_space() {
        val src = object : Component() {}
        fun key(mods: Int, code: Int = KeyEvent.VK_SPACE) = KeyEvent(src, KeyEvent.KEY_PRESSED, 0L, mods, code, ' ')
        assertTrue(isSearchHotkey(key(KeyEvent.CTRL_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK)))
        assertFalse(isSearchHotkey(key(KeyEvent.CTRL_DOWN_MASK)))
        assertFalse(isSearchHotkey(key(0)))
        assertFalse(isSearchHotkey(key(KeyEvent.CTRL_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_A)))
    }
}
