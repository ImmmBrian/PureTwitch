package com.puretv.twitch.desktop.channel

import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.MessagePart
import com.puretv.twitch.desktop.data.ViewerSample
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NerdStatsTest {
    // Real response from TwitchTracker's summary endpoint.
    @Test fun parses_tracker_summary() {
        val s = parseTrackerSummary("""{"rank":58,"minutes_streamed":11734,"avg_viewers":11286,"max_viewers":41874,"hours_watched":2207258,"followers":5787,"followers_total":11293053}""")!!
        assertEquals(58L, s.rank)
        assertEquals(11286L, s.avgViewers)
        assertTrue(kotlin.math.abs(s.hoursStreamed - 195.57) < 0.01)
        assertTrue(kotlin.math.abs(s.followersPerDay - 192.9) < 0.1)
        assertNull(parseTrackerSummary("[]"))
        assertNull(parseTrackerSummary("<html>blocked</html>"))
    }

    @Test fun session_viewers_only_count_this_stream() {
        val start = Instant.parse("2026-09-30T10:00:00Z").epochSecond
        val samples = listOf(
            ViewerSample(start - 3600, 9999), // yesterday's stream: ignored
            ViewerSample(start + 60, 100),
            ViewerSample(start + 120, 300),
            ViewerSample(start + 180, 200),
        )
        val s = sessionViewers(samples, "2026-09-30T10:00:00Z")!!
        assertEquals(300, s.peak)
        assertEquals(100, s.low)
        assertEquals(200, s.average)
        assertEquals(200, s.current)
        assertNull(sessionViewers(samples.take(2), "2026-09-30T10:00:00Z"), "needs two samples")
    }

    private fun msg(user: String, at: Long, sub: Boolean = false, mod: Boolean = false, emotes: List<String> = emptyList(), system: Boolean = false) =
        ChatMessage(
            id = "$user$at", channel = "c", username = user, displayName = user, color = "", message = "hi",
            parsedParts = emotes.map { MessagePart.TwitchEmote(id = it, name = it) },
            badges = emptyList(), timestamp = at, isSubscriber = sub, isModerator = mod, isBroadcaster = false, isSystem = system,
        )

    @Test fun chat_stats() {
        val now = 1_000_000_000L
        val chat = listOf(
            msg("a", now - 60_000, sub = true, emotes = listOf("Kappa", "LUL")),
            msg("b", now - 30_000, mod = true, emotes = listOf("Kappa")),
            msg("a", now - 10_000),
            msg("sys", now - 5_000, system = true),
            msg("old", now - 10 * 60_000), // outside the 5-minute window
        )
        val s = chatStats(chat, now)!!
        assertEquals(2, s.uniqueChatters)
        assertEquals(0.5, s.subscriberShare)
        assertEquals(1, s.moderatorsActive)
        assertEquals(3.0, s.messagesPerMinute)
        assertEquals("Kappa" to 2, s.topEmotes.first())
    }

    @Test fun number_formats() {
        assertEquals("1,234", formatStat(1234))
        assertEquals("221K", formatStat(220_725))
        assertEquals("11.3M", formatStat(11_293_053))
    }
}
