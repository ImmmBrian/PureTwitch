package com.puretv.twitch.core.chat

import com.puretv.twitch.core.model.ChatEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RoomStateParseTest {
    @Test fun fullRoomStateOnJoin() {
        val line = "@emote-only=0;followers-only=10;r9k=0;room-id=1;slow=30;subs-only=1 :tmi.twitch.tv ROOMSTATE #shroud"
        val e = TwitchIrcParser.parse(line, "shroud")
        assertIs<ChatEvent.RoomState>(e)
        assertEquals(30, e.slowModeSeconds)
        assertEquals(false, e.emoteOnly)
        assertEquals(10, e.followersOnlyMinutes)
        assertEquals(true, e.subsOnly)
        assertEquals(false, e.uniqueChat)
    }

    @Test fun partialUpdateLeavesOtherModesUnknown() {
        val e = TwitchIrcParser.parse("@emote-only=1;room-id=1 :tmi.twitch.tv ROOMSTATE #shroud", "shroud")
        assertIs<ChatEvent.RoomState>(e)
        assertEquals(true, e.emoteOnly)
        assertNull(e.slowModeSeconds)
        assertNull(e.followersOnlyMinutes)
        assertNull(e.subsOnly)
    }
}
