package com.puretv.twitch.desktop.discover

import com.puretv.twitch.core.api.StreamPage
import com.puretv.twitch.core.model.StreamInfo
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DiscoverRepositoryTest {
    private val now = Instant.parse("2026-09-30T12:00:00Z")

    private fun s(viewers: Int, id: String) = StreamInfo(
        id = id, userId = id, userLogin = id, userName = id, viewerCount = viewers, startedAt = "2026-09-30T11:00:00Z",
    )

    /** A fake directory: pages of [pageSize], highest-first, keyed by a numeric cursor. */
    private class FakeDirectory(private val all: List<StreamInfo>, private val pageSize: Int = 3) : DirectoryPager {
        val calls = mutableListOf<Triple<DiscoverSource, Boolean, String?>>()
        var failGql = false
        override suspend fun page(source: DiscoverSource, filters: DiscoverFilters, ascending: Boolean, after: String?): StreamPage {
            calls += Triple(source, ascending, after)
            if (source == DiscoverSource.GQL && failGql) throw IllegalStateException("gql down")
            val ordered = if (ascending) all.sortedBy { it.viewerCount } else all.sortedByDescending { it.viewerCount }
            val start = after?.toInt() ?: 0
            val page = ordered.drop(start).take(pageSize)
            val next = (start + pageSize).takeIf { it < ordered.size }?.toString()
            return StreamPage(page, next)
        }
    }

    private val directory = (1..12).map { s(it * 10, "c$it") } // 10..120 viewers

    @Test fun collects_matches_and_stops_below_minimum() = runBlocking {
        val fake = FakeDirectory(directory)
        val repo = DiscoverRepository(fake, targetMatches = 100, maxPages = 50, now = { now })
        val batch = repo.scan(DiscoverFilters(minViewers = 60, maxViewers = 90))
        assertEquals(listOf(90, 80, 70, 60), batch.matches.map { it.viewerCount })
        // Descending pages: [120,110,100] [90,80,70] [60,50,40] [30,20,10]. The fourth
        // is entirely below 60, so the scan stops there instead of reading further.
        assertEquals(4, fake.calls.size)
        assertNull(batch.next)
    }

    @Test fun small_channel_search_walks_up_from_the_bottom() = runBlocking {
        val fake = FakeDirectory(directory)
        val repo = DiscoverRepository(fake, targetMatches = 100, maxPages = 50, now = { now })
        val batch = repo.scan(DiscoverFilters(maxViewers = 30))
        assertEquals(listOf(10, 20, 30), batch.matches.map { it.viewerCount })
        assertEquals(true, fake.calls.first().second)
        // [10,20,30] then [40,50,60] is entirely above the max, so stop.
        assertEquals(2, fake.calls.size)
        assertNull(batch.next)
    }

    @Test fun page_budget_returns_a_resumable_cursor() = runBlocking {
        val fake = FakeDirectory(directory)
        val repo = DiscoverRepository(fake, targetMatches = 100, maxPages = 2, now = { now })
        val first = repo.scan(DiscoverFilters())
        assertEquals(6, first.matches.size)
        val next = assertNotNull(first.next)
        val second = repo.scan(DiscoverFilters(), next)
        assertEquals(6, second.matches.size)
        assertNull(second.next)
    }

    @Test fun falls_back_to_helix_when_graphql_fails_first() = runBlocking {
        val fake = FakeDirectory(directory).apply { failGql = true }
        val repo = DiscoverRepository(fake, targetMatches = 100, maxPages = 50, now = { now })
        val batch = repo.scan(DiscoverFilters(minViewers = 100))
        assertEquals(DiscoverSource.HELIX, batch.source)
        assertEquals(listOf(120, 110, 100), batch.matches.map { it.viewerCount })
        // Helix can't sort ascending.
        assertEquals(false, fake.calls.last().second)
    }

    @Test fun both_backends_failing_surfaces_the_error() {
        val pager = object : DirectoryPager {
            override suspend fun page(source: DiscoverSource, filters: DiscoverFilters, ascending: Boolean, after: String?): StreamPage =
                throw IllegalStateException("offline")
        }
        val repo = DiscoverRepository(pager, now = { now })
        assertFailsWith<IllegalStateException> { runBlocking { repo.scan(DiscoverFilters()) } }
    }
}
