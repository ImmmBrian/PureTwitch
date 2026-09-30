package com.puretv.twitch.desktop.channel

import com.puretv.twitch.core.api.HelixSchedule
import com.puretv.twitch.core.api.HelixScheduleCategory
import com.puretv.twitch.core.api.HelixScheduleSegment
import com.puretv.twitch.core.api.HelixVacation
import com.puretv.twitch.core.model.ChatEvent
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatModesAndSchedulesTest {
    @Test fun partial_updates_merge() {
        val joined = ChatModes().merge(ChatEvent.RoomState(slowModeSeconds = 0, emoteOnly = false, followersOnlyMinutes = -1, subsOnly = false, uniqueChat = false))
        assertTrue(joined.labels().isEmpty(), "an open chat shows nothing")
        val slow = joined.merge(ChatEvent.RoomState(slowModeSeconds = 120, emoteOnly = null))
        val both = slow.merge(ChatEvent.RoomState(slowModeSeconds = null, emoteOnly = true))
        assertEquals(listOf("Emote only", "Slow 2m"), both.labels().map { it.text })
        val off = both.merge(ChatEvent.RoomState(slowModeSeconds = 0, emoteOnly = null))
        assertEquals(listOf("Emote only"), off.labels().map { it.text })
    }

    @Test fun followers_and_sub_labels() {
        assertEquals("Followers only", ChatModes(followersOnlyMinutes = 0).labels().single().text)
        assertEquals("Followers 10m", ChatModes(followersOnlyMinutes = 10).labels().single().text)
        assertEquals("Followers 1w", ChatModes(followersOnlyMinutes = 7 * 24 * 60).labels().single().text)
        assertEquals("Sub only", ChatModes(subsOnly = true, slowSeconds = 3).labels().first().text)
        assertEquals("Slow 3s", ChatModes(slowSeconds = 3).labels().single().text)
    }

    private fun iso(sec: Long) = Instant.ofEpochSecond(sec).toString()

    @Test fun next_stream_per_channel_soonest_first() {
        val now = 1_800_000_000L
        val a = HelixSchedule(
            broadcaster_login = "alpha", broadcaster_name = "Alpha",
            segments = listOf(
                HelixScheduleSegment(start_time = iso(now - 600), title = "already started"),
                HelixScheduleSegment(start_time = iso(now + 7200), title = "cancelled", canceled_until = iso(now + 9000)),
                HelixScheduleSegment(start_time = iso(now + 10_800), title = "Ranked", category = HelixScheduleCategory("1", "Valorant")),
                HelixScheduleSegment(start_time = iso(now + 90_000), title = "later"),
            ),
        )
        val b = HelixSchedule(broadcaster_login = "bravo", segments = listOf(HelixScheduleSegment(start_time = iso(now + 3600), title = "Chill")))
        val away = HelixSchedule(
            broadcaster_login = "charlie",
            segments = listOf(HelixScheduleSegment(start_time = iso(now + 3600))),
            vacation = HelixVacation(iso(now), iso(now + 86_400)),
        )
        val far = HelixSchedule(broadcaster_login = "delta", segments = listOf(HelixScheduleSegment(start_time = iso(now + 30L * 86_400))))
        val out = upcomingFrom(listOf(a, b, away, far, HelixSchedule(broadcaster_login = "none")), now)
        assertEquals(listOf("bravo", "alpha"), out.map { it.login })
        assertEquals("Ranked", out[1].title)
        assertEquals("Valorant", out[1].category)
        assertEquals("bravo", out[0].displayName, "falls back to the login")
    }

    @Test fun schedule_times_read_naturally() {
        val utc = ZoneOffset.UTC
        val now = Instant.parse("2026-10-01T12:00:00Z").epochSecond
        assertEquals("In 25 min", formatScheduleTime(now + 25 * 60, now, utc))
        assertEquals("Today, 8:00 PM", formatScheduleTime(Instant.parse("2026-10-01T20:00:00Z").epochSecond, now, utc))
        assertEquals("Tomorrow, 6:30 PM", formatScheduleTime(Instant.parse("2026-10-02T18:30:00Z").epochSecond, now, utc))
        assertEquals("Mon, 7:00 PM", formatScheduleTime(Instant.parse("2026-10-05T19:00:00Z").epochSecond, now, utc))
        assertEquals("Oct 12, 7:00 PM", formatScheduleTime(Instant.parse("2026-10-12T19:00:00Z").epochSecond, now, utc))
    }
}
