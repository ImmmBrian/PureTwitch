package com.puretv.twitch.desktop.discover

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwitchDirectoryGqlTest {
    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test fun game_count_query_aliases_and_sanitizes_ids() {
        val q = buildGameViewerCountsQuery(listOf("509658", "21779\"){evil}"))
        assertEquals("query{g0:game(id:\"509658\"){id viewersCount} g1:game(id:\"21779\"){id viewersCount}}", q)
    }

    @Test fun streams_query_root_ascending_with_filters() {
        val q = buildStreamsQuery(
            gameId = null,
            languageEnum = "EN",
            tags = listOf("chill", "bad\"tag}"),
            ascending = true,
            after = "abc=\"}",
            first = 30,
        )
        assertTrue(q.startsWith("query{streams(first:30,after:\"abc=\",options:{sort:VIEWER_COUNT_ASC,broadcasterLanguages:[EN],freeformTags:[\"chill\",\"badtag\"]}){"))
        assertFalse(q.contains("game(id"))
    }

    @Test fun streams_query_game_scoped_descending() {
        val q = buildStreamsQuery("509658", null, emptyList(), ascending = false, after = null, first = 30)
        assertTrue(q.startsWith("query{game(id:\"509658\"){streams(first:30,options:{sort:VIEWER_COUNT}){"))
    }

    @Test fun parses_game_counts_skipping_nulls() {
        val counts = parseGameViewerCounts(
            obj("""{"data":{"g0":{"id":"1","viewersCount":1234},"g1":null,"g2":{"id":"3","viewersCount":5}}}"""),
        )
        assertEquals(mapOf("1" to 1234, "3" to 5), counts)
    }

    @Test fun graphql_errors_throw() {
        assertFailsWith<DirectoryGqlException> {
            parseGameViewerCounts(obj("""{"errors":[{"message":"Cannot query field"}],"data":null}"""))
        }
    }

    private val streamsBody = """
        {"data":{"streams":{"edges":[
          {"cursor":"c1","node":{"id":"s1","title":"hello","viewersCount":7,"createdAt":"2026-09-30T10:00:00Z",
            "broadcaster":{"id":"b1","login":"alpha","displayName":"Alpha"},"game":{"id":"g","name":"Chess"},
            "previewImageURL":"https://img/1.jpg"}},
          {"cursor":"c2","node":null},
          {"cursor":"c3","node":{"id":"s3","title":"","viewersCount":9,"createdAt":"",
            "broadcaster":{"id":"b3","login":"gamma","displayName":null},"game":null,"previewImageURL":null}}
        ],"pageInfo":{"hasNextPage":true}}}}
    """.trimIndent()

    @Test fun parses_root_streams_page() {
        val page = parseStreamsPage(obj(streamsBody), gameScoped = false)
        assertEquals(listOf("alpha", "gamma"), page.streams.map { it.userLogin })
        assertEquals("Alpha", page.streams[0].userName)
        assertEquals("gamma", page.streams[1].userName) // falls back to login
        assertEquals("Chess", page.streams[0].gameName)
        assertEquals(7, page.streams[0].viewerCount)
        assertEquals("c3", page.cursor)
    }

    @Test fun last_page_has_no_cursor() {
        val page = parseStreamsPage(obj(streamsBody.replace("\"hasNextPage\":true", "\"hasNextPage\":false")), gameScoped = false)
        assertNull(page.cursor)
    }

    @Test fun game_scoped_page_and_unknown_game() {
        val scoped = """{"data":{"game":${streamsBody.removePrefix("{\"data\":").removeSuffix("}")}}}"""
        assertEquals(2, parseStreamsPage(obj(scoped), gameScoped = true).streams.size)
        val missing = parseStreamsPage(obj("""{"data":{"game":null}}"""), gameScoped = true)
        assertTrue(missing.streams.isEmpty())
        assertNull(missing.cursor)
    }

    @Test fun viewer_count_queries_stay_under_twitchs_alias_limit() {
        // Twitch answers "root field aliases ... exceeds ... (15)" above 15, which
        // blanked every Browse count when the lookup asked for 50 at a time.
        assertEquals(15, TwitchDirectoryGql.MAX_ALIASES)
        val q = buildGameViewerCountsQuery((1..TwitchDirectoryGql.MAX_ALIASES).map { "$it" })
        assertEquals(TwitchDirectoryGql.MAX_ALIASES, Regex("g\\d+:game").findAll(q).count())
    }
}
