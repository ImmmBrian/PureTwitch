package com.puretv.twitch.desktop.channel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChannelExtrasTest {
    private fun parse(text: String) = parseChannelExtras(Json.parseToJsonElement(text).jsonObject)

    @Test fun query_sanitizes_login() {
        assertEquals(
            "query{user(login:\"bad\"){chatSettings{rules} panels{id type ... on DefaultPanel{title description imageURL linkURL}}}}",
            buildChannelExtrasQuery("bad\"){}"),
        )
    }

    // Shape copied from a real Twitch response.
    @Test fun keeps_real_panels_and_skips_extensions_and_blanks() {
        val extras = parse(
            """
            {"data":{"user":{"chatSettings":{"rules":["- be respectful!","","","- no spam / links",""]},
             "panels":[
              {"id":"1","type":"DEFAULT","title":null,"description":null,"imageURL":"https://panels.twitch.tv/a.png","linkURL":"https://load.gg/x"},
              {"id":"2","type":"DEFAULT","title":null,"description":null,"imageURL":null,"linkURL":null},
              {"id":"3","type":"EXTENSION"},
              {"id":"4","type":"DEFAULT","title":"Store","description":"Now **open**!","imageURL":null,"linkURL":"cohh.tv/store"}
             ]}}}
            """.trimIndent(),
        )
        assertEquals(listOf("1", "4"), extras.panels.map { it.id })
        assertEquals("https://cohh.tv/store", extras.panels[1].linkUrl)
        assertEquals(listOf("- be respectful!", "", "- no spam / links"), extras.rules)
    }

    @Test fun partial_errors_keep_whatever_came_back() {
        val extras = parse(
            """{"errors":[{"message":"failed integrity check"}],"data":{"user":{"chatSettings":null,"panels":[{"id":"9","type":"DEFAULT","title":"Hi","description":null,"imageURL":null,"linkURL":null}]}}}""",
        )
        assertEquals(1, extras.panels.size)
        assertTrue(extras.rules.isEmpty())
    }

    @Test fun missing_user_is_empty() {
        assertTrue(parse("""{"data":{"user":null}}""").isEmpty)
    }
}
