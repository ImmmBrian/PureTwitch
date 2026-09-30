package com.puretv.twitch.desktop.ui.chat

import com.puretv.twitch.core.model.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatFiltersTest {
    private fun msg(id: String, user: String, text: String, ts: Long = id.hashCode().toLong(), system: Boolean = false, mention: Boolean = false) = ChatMessage(
        id = id, channel = "c", username = user, displayName = user, color = "", message = text,
        parsedParts = emptyList(), badges = emptyList(), timestamp = ts,
        isSubscriber = false, isModerator = false, isBroadcaster = false, isSystem = system, mentionsSelf = mention,
    )

    @Test fun ignored_users_are_hidden_but_system_lines_stay() {
        val out = applyChatFilters(
            listOf(msg("1", "Troll", "hi"), msg("2", "friend", "yo"), msg("3", "", "raid!", system = true)),
            ignored = listOf("troll"),
            highlightWords = emptyList(),
        )
        assertEquals(listOf("2", "3"), out.map { it.id })
    }

    @Test fun highlight_is_whole_word_and_case_insensitive() {
        val out = applyChatFilters(
            listOf(msg("1", "a", "GIVEAWAY now"), msg("2", "b", "giveaways are over"), msg("3", "c", "nothing")),
            ignored = emptyList(),
            highlightWords = listOf("giveaway"),
        )
        assertTrue(out[0].mentionsSelf)
        assertFalse(out[1].mentionsSelf)
        assertFalse(out[2].mentionsSelf)
    }

    @Test fun regex_characters_in_words_are_literal() {
        val m = highlightMatcher(listOf("c++", "a.b"))!!
        assertTrue(m.containsMatchIn("I love c++"))
        assertFalse(m.containsMatchIn("axb"))
    }

    @Test fun mentions_merge_without_duplicates_in_time_order() {
        val real = msg("m1", "x", "@me hi", ts = 10, mention = true)
        val visible = listOf(msg("v1", "y", "giveaway", ts = 5, mention = true), real, msg("v2", "z", "plain", ts = 20))
        assertEquals(listOf("v1", "m1"), mergeMentions(listOf(real), visible, emptyList()).map { it.id })
        assertEquals(listOf("v1"), mergeMentions(listOf(real), visible.filterNot { it.id == "m1" }, listOf("x")).map { it.id })
    }
}
