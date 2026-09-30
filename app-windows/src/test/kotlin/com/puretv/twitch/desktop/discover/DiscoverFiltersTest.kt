package com.puretv.twitch.desktop.discover

import com.puretv.twitch.core.model.StreamInfo
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoverFiltersTest {
    private val now = Instant.parse("2026-09-30T12:00:00Z")

    private fun stream(
        viewers: Int,
        title: String = "",
        tags: List<String> = emptyList(),
        startedAt: String = "2026-09-30T11:30:00Z",
        id: String = viewers.toString(),
    ) = StreamInfo(
        id = id, userId = "u$id", userLogin = "login$id", userName = "Name$id",
        title = title, viewerCount = viewers, startedAt = startedAt, tags = tags,
    )

    @Test fun viewer_range_is_inclusive() {
        val f = DiscoverFilters(minViewers = 10, maxViewers = 50)
        assertTrue(f.matches(stream(10), now))
        assertTrue(f.matches(stream(50), now))
        assertFalse(f.matches(stream(9), now))
        assertFalse(f.matches(stream(51), now))
    }

    @Test fun title_keyword_is_case_insensitive() {
        val f = DiscoverFilters(titleKeyword = "Speedrun")
        assertTrue(f.matches(stream(5, title = "any% SPEEDRUN attempts"), now))
        assertFalse(f.matches(stream(5, title = "just chatting"), now))
    }

    @Test fun every_tag_must_be_present_when_checked() {
        val f = DiscoverFilters(tags = listOf("chill", "English"))
        assertTrue(f.matches(stream(5, tags = listOf("English", "Chill", "cozy")), now))
        assertFalse(f.matches(stream(5, tags = listOf("English")), now))
        // GraphQL results carry no tags because Twitch already filtered them.
        assertTrue(f.matches(stream(5, tags = emptyList()), now, checkTags = false))
    }

    @Test fun clean_tags_strip_symbols_and_duplicates() {
        val f = DiscoverFilters(tags = listOf(" #chill ", "Chill", "", "speed-run", "a", "b", "c", "d"))
        assertEquals(listOf("chill", "speedrun", "a", "b", "c"), f.cleanTags)
    }

    @Test fun uptime_buckets() {
        val just = stream(5, startedAt = "2026-09-30T11:30:00Z") // 30m
        val mid = stream(5, startedAt = "2026-09-30T10:00:00Z") // 2h
        val long = stream(5, startedAt = "2026-09-30T06:00:00Z") // 6h
        assertTrue(DiscoverFilters(uptime = UptimeFilter.JUST_STARTED).matches(just, now))
        assertFalse(DiscoverFilters(uptime = UptimeFilter.JUST_STARTED).matches(mid, now))
        assertTrue(DiscoverFilters(uptime = UptimeFilter.MID).matches(mid, now))
        assertTrue(DiscoverFilters(uptime = UptimeFilter.LONG).matches(long, now))
        assertFalse(DiscoverFilters(uptime = UptimeFilter.LONG).matches(stream(5, startedAt = ""), now))
    }

    @Test fun format_uptime() {
        assertEquals("30m", formatUptime("2026-09-30T11:30:00Z", now))
        assertEquals("2h 5m", formatUptime("2026-09-30T09:55:00Z", now))
        assertNull(formatUptime("garbage", now))
    }

    @Test fun scan_direction() {
        assertTrue(scanAscending(DiscoverFilters(maxViewers = 50)))
        assertTrue(scanAscending(DiscoverFilters(sort = DiscoverSort.FEWEST_VIEWERS)))
        // A real minimum means the bottom of the directory is all misses: go top-down.
        assertFalse(scanAscending(DiscoverFilters(minViewers = 10, maxViewers = 50)))
        assertFalse(scanAscending(DiscoverFilters()))
    }

    @Test fun passed_range_stops_the_scan() {
        val desc = DiscoverFilters(minViewers = 100)
        assertTrue(passedRange(desc, listOf(stream(90), stream(80)), ascending = false))
        assertFalse(passedRange(desc, listOf(stream(120), stream(80)), ascending = false))
        val asc = DiscoverFilters(maxViewers = 20)
        assertTrue(passedRange(asc, listOf(stream(21), stream(30)), ascending = true))
        assertFalse(passedRange(asc, listOf(stream(19), stream(30)), ascending = true))
    }

    @Test fun sorting() {
        val list = listOf(
            stream(5, startedAt = "2026-09-30T08:00:00Z", id = "a"),
            stream(50, startedAt = "2026-09-30T11:00:00Z", id = "b"),
            stream(20, startedAt = "", id = "c"),
        )
        assertEquals(listOf("b", "c", "a"), list.sortedFor(DiscoverSort.MOST_VIEWERS).map { it.id })
        assertEquals(listOf("a", "c", "b"), list.sortedFor(DiscoverSort.FEWEST_VIEWERS).map { it.id })
        assertEquals(listOf("b", "a", "c"), list.sortedFor(DiscoverSort.NEWEST).map { it.id })
    }

    @Test fun viewer_presets_round_trip() {
        assertEquals(ViewerPreset.SMALL, ViewerPreset.matching(10, 50))
        assertEquals(ViewerPreset.ANY, ViewerPreset.matching(null, null))
        assertNull(ViewerPreset.matching(7, 70))
    }
}
