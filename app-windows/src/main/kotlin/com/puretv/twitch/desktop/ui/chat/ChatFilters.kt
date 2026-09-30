package com.puretv.twitch.desktop.ui.chat

import com.puretv.twitch.core.model.ChatMessage

/**
 * Applies your chat preferences at display time, so changing them updates the
 * chat already on screen:
 *  - messages from [ignored] logins are hidden;
 *  - messages containing any of [highlightWords] (whole word, any case) are
 *    marked like a mention, which also puts them in the Mentions tab.
 * System lines (subs, raids) are never hidden or highlighted.
 */
fun applyChatFilters(
    messages: List<ChatMessage>,
    ignored: Collection<String>,
    highlightWords: Collection<String>,
): List<ChatMessage> {
    if (ignored.isEmpty() && highlightWords.isEmpty()) return messages
    val ignoredSet = ignored.map { it.lowercase() }.toSet()
    val matcher = highlightMatcher(highlightWords)
    return messages.mapNotNull { m ->
        when {
            m.isSystem -> m
            m.username.lowercase() in ignoredSet -> null
            matcher != null && !m.mentionsSelf && !m.deleted && matcher.containsMatchIn(m.message) -> m.copy(mentionsSelf = true)
            else -> m
        }
    }
}

/** The Mentions tab: real @-mentions plus highlighted messages, oldest first, no duplicates. */
fun mergeMentions(mentions: List<ChatMessage>, visible: List<ChatMessage>, ignored: Collection<String>): List<ChatMessage> {
    val ignoredSet = ignored.map { it.lowercase() }.toSet()
    return (mentions.filterNot { it.username.lowercase() in ignoredSet } + visible.filter { it.mentionsSelf })
        .distinctBy { it.id }
        .sortedBy { it.timestamp }
}

internal fun highlightMatcher(words: Collection<String>): Regex? {
    val clean = words.map { it.trim() }.filter { it.isNotEmpty() }
    if (clean.isEmpty()) return null
    val alternation = clean.joinToString("|") { Regex.escape(it) }
    return Regex("(?<![\\p{L}\\p{N}_])(?:$alternation)(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
}
