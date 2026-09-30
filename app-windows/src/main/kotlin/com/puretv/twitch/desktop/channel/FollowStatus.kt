package com.puretv.twitch.desktop.channel

import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.desktop.platform.openInBrowser
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether you follow a channel on Twitch itself. Twitch lets apps read follows
 * but not change them, so the Follow button shows this and sends you to
 * twitch.tv to follow or unfollow.
 */
class TwitchFollowStatus(
    private val api: TwitchApiClient,
    private val userId: () -> String?,
) {
    private val known = ConcurrentHashMap<String, Boolean>()

    /** Null when signed out or Twitch couldn't be reached. [fresh] skips the cache. */
    suspend fun isFollowing(broadcasterId: String, fresh: Boolean = false): Boolean? {
        val me = userId()?.takeIf { it.isNotBlank() } ?: return null
        if (broadcasterId.isBlank()) return null
        if (!fresh) known[broadcasterId]?.let { return it }
        return runCatching { api.isFollowing(me, broadcasterId) }.getOrNull()
            ?.also { known[broadcasterId] = it }
    }
}

/** What the button shows: your real Twitch follow when known, else the old PureTV-only list. */
fun followButtonShowsFollowing(onTwitch: Boolean?, inLocalList: Boolean): Boolean = onTwitch ?: inLocalList

/** The channel's page on twitch.tv, where the real Follow button lives. */
fun twitchChannelUrl(login: String): String = "https://www.twitch.tv/${sanitizeLogin(login).lowercase()}"

/** Opens the channel on twitch.tv so you can follow or unfollow there. */
fun openFollowOnTwitch(login: String) {
    openInBrowser(twitchChannelUrl(login))
}
