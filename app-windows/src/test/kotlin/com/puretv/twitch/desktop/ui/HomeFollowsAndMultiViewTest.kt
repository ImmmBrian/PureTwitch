package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.follows.FollowRow
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.desktop.ui.screens.MULTI_VIEW_MAX
import com.puretv.twitch.desktop.ui.screens.MultiViewController
import com.puretv.twitch.desktop.ui.screens.mergeFollowShelf
import com.puretv.twitch.desktop.ui.screens.multiViewQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeFollowsAndMultiViewTest {
    private fun local(login: String, live: Boolean, viewers: Int = 0) =
        FollowCardState(login = login, displayName = login, avatarUrl = "", isLive = live, viewerCount = viewers)

    private fun row(login: String, viewers: Int) =
        FollowRow(login = login, displayName = login, avatarUrl = null, isLive = true, viewerCount = viewers, gameName = "")

    @Test fun shelf_merges_twitch_and_local_follows() {
        val merged = mergeFollowShelf(
            local = listOf(local("pinned", live = false), local("both", live = true, viewers = 5), local("sleepy", live = false)),
            liveFollows = listOf(row("twitchonly", 900), row("both", 50)),
        )
        // Live first by viewers, duplicates collapsed, then offline local pins.
        assertEquals(listOf("twitchonly", "both", "pinned", "sleepy"), merged.map { it.login })
        assertTrue(merged.take(2).all { it.isLive })
    }

    @Test fun multi_view_lineup_rules() {
        val mv = MultiViewController()
        assertTrue(mv.add(" Shroud "))
        assertFalse(mv.add("shroud"), "no duplicates")
        assertEquals("shroud", mv.audioLogin, "first tile gets the sound")
        repeat(MULTI_VIEW_MAX) { mv.add("c$it") }
        assertEquals(MULTI_VIEW_MAX, mv.logins.size)
        mv.remove("shroud")
        assertEquals("c0", mv.audioLogin, "sound moves to the next tile")
    }

    @Test fun tiles_drop_quality_as_they_shrink() {
        assertEquals(StreamQuality.AUTO, multiViewQuality(1))
        assertEquals(StreamQuality.P720P60, multiViewQuality(2))
        assertEquals(StreamQuality.P480P, multiViewQuality(4))
    }
}
