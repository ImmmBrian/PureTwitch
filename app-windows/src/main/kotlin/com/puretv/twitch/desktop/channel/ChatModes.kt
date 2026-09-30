package com.puretv.twitch.desktop.channel

import com.puretv.twitch.core.model.ChatEvent

/** The chat room's current restrictions, built up from ROOMSTATE updates. */
data class ChatModes(
    val slowSeconds: Int = 0,
    val emoteOnly: Boolean = false,
    /** -1 when off, 0 for any follower, otherwise minutes followed. */
    val followersOnlyMinutes: Int = -1,
    val subsOnly: Boolean = false,
    val uniqueChat: Boolean = false,
) {
    /** Applies an update; tags missing from it keep their current value. */
    fun merge(update: ChatEvent.RoomState): ChatModes = copy(
        slowSeconds = update.slowModeSeconds ?: slowSeconds,
        emoteOnly = update.emoteOnly ?: emoteOnly,
        followersOnlyMinutes = update.followersOnlyMinutes ?: followersOnlyMinutes,
        subsOnly = update.subsOnly ?: subsOnly,
        uniqueChat = update.uniqueChat ?: uniqueChat,
    )

    /** Short labels for the chat header, most restrictive first. Empty when chat is open. */
    fun labels(): List<ChatModeLabel> = buildList {
        if (subsOnly) add(ChatModeLabel("Sub only", "Only subscribers can chat."))
        if (emoteOnly) add(ChatModeLabel("Emote only", "Messages can only contain emotes."))
        if (followersOnlyMinutes == 0) {
            add(ChatModeLabel("Followers only", "You need to follow to chat."))
        } else if (followersOnlyMinutes > 0) {
            val d = formatFollowAge(followersOnlyMinutes)
            add(ChatModeLabel("Followers $d", "You need to have followed for $d to chat."))
        }
        if (slowSeconds > 0) {
            add(ChatModeLabel("Slow ${formatSlow(slowSeconds)}", "One message every ${formatSlow(slowSeconds)}."))
        }
        if (uniqueChat) add(ChatModeLabel("Unique chat", "Messages must be different from recent ones."))
    }
}

data class ChatModeLabel(val text: String, val detail: String)

internal fun formatSlow(seconds: Int): String = when {
    seconds % 60 == 0 && seconds >= 60 -> "${seconds / 60}m"
    else -> "${seconds}s"
}

internal fun formatFollowAge(minutes: Int): String = when {
    minutes % (60 * 24 * 30) == 0 -> "${minutes / (60 * 24 * 30)}mo"
    minutes % (60 * 24 * 7) == 0 -> "${minutes / (60 * 24 * 7)}w"
    minutes % (60 * 24) == 0 -> "${minutes / (60 * 24)}d"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes}m"
}
