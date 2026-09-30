package com.puretv.twitch.desktop.channel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipsAndRaidsTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun clips_query_is_sanitized() {
        val q = buildClipsQuery("sh\"roud", ClipPeriod.WEEK, "Mw==\"}")
        assertTrue(q.startsWith("query{user(login:\"shroud\"){clips(first:20,after:\"Mw==\",criteria:{period:LAST_WEEK,sort:VIEWS_DESC})"))
    }

    // Shape copied from a real Twitch response: cursors are null except on the last edge.
    @Test fun parses_a_clip_page() {
        val page = parseClipPage(
            obj(
                """{"data":{"user":{"clips":{"edges":[
                  {"cursor":null,"node":{"slug":"A-1","title":"first","viewCount":1092,"durationSeconds":30,"createdAt":"2026-09-23T19:23:41Z","thumbnailURL":"https://x/1.jpg","curator":{"displayName":"Cur"},"game":{"name":"WARDOGS"}}},
                  {"cursor":"Mw==","node":{"slug":"B-2","title":"second","viewCount":551,"durationSeconds":60,"createdAt":"2026-09-24T20:55:43Z","thumbnailURL":"https://x/2.jpg","curator":null,"game":null}}
                ],"pageInfo":{"hasNextPage":true}}}}}""",
            ),
        )
        assertEquals(listOf("A-1", "B-2"), page.clips.map { it.slug })
        assertEquals("Cur", page.clips[0].curatorName)
        assertEquals("", page.clips[1].gameName)
        assertEquals("Mw==", page.cursor)
    }

    @Test fun last_clip_page_has_no_cursor() {
        val page = parseClipPage(obj("""{"data":{"user":{"clips":{"edges":[{"cursor":"x","node":{"slug":"A"}}],"pageInfo":{"hasNextPage":false}}}}}"""))
        assertNull(page.cursor)
    }

    @Test fun clip_url_is_signed_and_picks_quality() {
        val body = """{"data":{"clip":{"playbackAccessToken":{"signature":"sig1","value":"{\"a\":1}"},
            "videoQualities":[{"quality":"1080","sourceURL":"https://cdn/1080.mp4"},{"quality":"720","sourceURL":"https://cdn/720.mp4"}]}}}"""
        assertEquals("https://cdn/1080.mp4?sig=sig1&token=%7B%22a%22%3A1%7D", parseClipPlaybackUrl(obj(body)))
        assertEquals("https://cdn/720.mp4?sig=sig1&token=%7B%22a%22%3A1%7D", parseClipPlaybackUrl(obj(body), maxQuality = 720))
        assertNull(parseClipPlaybackUrl(obj("""{"data":{"clip":null}}""")))
    }

    @Test fun age_and_duration_formats() {
        val now = Instant.parse("2026-09-30T12:00:00Z")
        assertEquals("45m", formatAge("2026-09-30T11:15:00Z", now))
        assertEquals("3d", formatAge("2026-09-27T11:00:00Z", now))
        assertEquals("1:05", formatClipDuration(65))
    }

    @Test fun eventsub_welcome_reconnect_and_raid() {
        val welcome = parseEventSubMessage("""{"metadata":{"message_type":"session_welcome"},"payload":{"session":{"id":"S1","keepalive_timeout_seconds":10}}}""")
        assertEquals(EventSubMessage.Welcome("S1"), welcome)
        val reconnect = parseEventSubMessage("""{"metadata":{"message_type":"session_reconnect"},"payload":{"session":{"id":"S1","reconnect_url":"wss://x/ws?id=1"}}}""")
        assertEquals(EventSubMessage.Reconnect("wss://x/ws?id=1"), reconnect)
        val raid = parseEventSubMessage(
            """{"metadata":{"message_type":"notification"},"payload":{"subscription":{"type":"channel.raid"},
               "event":{"from_broadcaster_user_login":"a","to_broadcaster_user_id":"9","to_broadcaster_user_login":"bee","to_broadcaster_user_name":"Bee","viewers":1234}}}""",
        )
        assertIs<EventSubMessage.Raid>(raid)
        assertEquals(RaidEvent("bee", "Bee", 1234), raid.event)
        assertEquals(EventSubMessage.Other, parseEventSubMessage("""{"metadata":{"message_type":"session_keepalive"},"payload":{}}"""))
        assertEquals(EventSubMessage.Other, parseEventSubMessage("not json"))
    }

    @Test fun raid_subscription_body() {
        val body = obj(buildRaidSubscriptionBody("123", "S1"))
        assertEquals("channel.raid", body["type"].toString().trim('"'))
        assertTrue(body.toString().contains("\"from_broadcaster_user_id\":\"123\""))
        assertTrue(body.toString().contains("\"session_id\":\"S1\""))
    }
}
