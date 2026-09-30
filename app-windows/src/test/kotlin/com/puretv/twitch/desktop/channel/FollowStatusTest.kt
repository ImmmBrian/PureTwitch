package com.puretv.twitch.desktop.channel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FollowStatusTest {
    @Test fun twitch_follow_wins_over_the_local_list() {
        assertTrue(followButtonShowsFollowing(onTwitch = true, inLocalList = false), "followed on Twitch but never added in PureTV")
        assertFalse(followButtonShowsFollowing(onTwitch = false, inLocalList = true), "unfollowed on Twitch")
        assertTrue(followButtonShowsFollowing(onTwitch = null, inLocalList = true), "signed out falls back to the local list")
        assertFalse(followButtonShowsFollowing(onTwitch = null, inLocalList = false))
    }

    @Test fun channel_url_is_clean() {
        assertEquals("https://www.twitch.tv/shroud", twitchChannelUrl("Shroud"))
        assertEquals("https://www.twitch.tv/evilname", twitchChannelUrl("evil/../name?x=1"))
    }
}
