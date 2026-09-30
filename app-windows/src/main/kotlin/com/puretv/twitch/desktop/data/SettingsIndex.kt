package com.puretv.twitch.desktop.data

/** The panels on the Settings page, in page order. Search results jump to one. */
enum class SettingsPanel(val title: String) {
    COLOUR("Colour"),
    SHAPE("Shape and motion"),
    PERSONALIZE("Personalize"),
    PLAYBACK("Playback"),
    BROWSING("Browsing and mini player"),
    CHAT("Chat"),
    BACKUP("Backup and restore"),
    ADBLOCK("Ad blocking"),
    ACCOUNT("Account"),
    ABOUT("About and updates"),
}

/** One searchable setting. [keywords] are other words people might type for it. */
data class SettingsEntry(
    val title: String,
    val panel: SettingsPanel,
    val keywords: List<String> = emptyList(),
)

val SETTINGS_INDEX: List<SettingsEntry> = listOf(
    SettingsEntry("Theme colour", SettingsPanel.COLOUR, listOf("theme", "color", "palette", "dark", "black", "amoled", "ember", "teal", "forest")),
    SettingsEntry("Expressiveness", SettingsPanel.SHAPE, listOf("shape", "motion", "animation", "rounded", "corners")),
    SettingsEntry("Accent color", SettingsPanel.PERSONALIZE, listOf("accent", "colour", "tint", "highlight", "hex")),
    SettingsEntry("Text size", SettingsPanel.PERSONALIZE, listOf("font", "bigger", "smaller", "zoom", "scale")),
    SettingsEntry("Density", SettingsPanel.PERSONALIZE, listOf("compact", "comfortable", "grid", "columns")),
    SettingsEntry("Left menu tabs", SettingsPanel.PERSONALIZE, listOf("hide", "rail", "sidebar", "navigation", "tabs")),
    SettingsEntry("Opens on", SettingsPanel.PERSONALIZE, listOf("launch", "start", "startup", "default tab")),
    SettingsEntry("Home shelves", SettingsPanel.PERSONALIZE, listOf("home", "sections", "coming up", "schedule", "featured", "continue watching", "recently watched")),
    SettingsEntry("Channel stats", SettingsPanel.PERSONALIZE, listOf("stats", "nerds", "twitchtracker", "audience", "viewers")),
    SettingsEntry("Chat text size", SettingsPanel.PERSONALIZE, listOf("chat", "font", "readable", "bigger")),
    SettingsEntry("Chat name colors", SettingsPanel.PERSONALIZE, listOf("chat", "username", "names", "colour")),
    SettingsEntry("Chat timestamps", SettingsPanel.PERSONALIZE, listOf("chat", "time", "clock")),
    SettingsEntry("Preferred quality", SettingsPanel.PLAYBACK, listOf("quality", "resolution", "1080p", "720p", "source", "bitrate")),
    SettingsEntry("Animate emotes", SettingsPanel.PLAYBACK, listOf("gif", "animated", "emotes", "7tv", "bttv")),
    SettingsEntry("One click to watch", SettingsPanel.BROWSING, listOf("click", "open stream", "channel page")),
    SettingsEntry("Lower quality in the mini player", SettingsPanel.BROWSING, listOf("mini", "docked", "480p", "bandwidth")),
    SettingsEntry("Mini player size", SettingsPanel.BROWSING, listOf("mini", "small", "large", "picture in picture", "pip")),
    SettingsEntry("Highlight words", SettingsPanel.CHAT, listOf("chat", "keywords", "mentions", "ping")),
    SettingsEntry("Ignored users", SettingsPanel.CHAT, listOf("chat", "block", "mute", "ignore")),
    SettingsEntry("Backup and restore", SettingsPanel.BACKUP, listOf("export", "import", "save", "settings file")),
    SettingsEntry("Ad blocking", SettingsPanel.ADBLOCK, listOf("ads", "adblock", "commercials")),
    SettingsEntry("Account", SettingsPanel.ACCOUNT, listOf("log out", "sign out", "login", "twitch account")),
    SettingsEntry("Check for updates", SettingsPanel.ABOUT, listOf("update", "version", "about", "release")),
)

/**
 * Settings matching [query], best first. Every word typed has to match the
 * title or a keyword; a title that starts with the query ranks highest.
 */
fun searchSettings(query: String, index: List<SettingsEntry> = SETTINGS_INDEX, limit: Int = 6): List<SettingsEntry> {
    val q = query.trim().lowercase()
    if (q.length < 2) return emptyList()
    val words = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return index.mapNotNull { e ->
        val title = e.title.lowercase()
        val haystack = listOf(title, e.panel.title.lowercase()) + e.keywords.map { it.lowercase() }
        if (!words.all { w -> haystack.any { it.contains(w) } }) return@mapNotNull null
        val score = when {
            title.startsWith(q) -> 0
            title.contains(q) -> 1
            e.keywords.any { it.lowercase().startsWith(q) } -> 2
            else -> 3
        }
        e to score
    }.sortedBy { it.second }.take(limit).map { it.first }
}
