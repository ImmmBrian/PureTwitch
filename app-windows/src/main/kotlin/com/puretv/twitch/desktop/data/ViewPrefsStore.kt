package com.puretv.twitch.desktop.data

import com.puretv.twitch.desktop.discover.DiscoverFilters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * How list screens order their content.
 *  - VIEWERS: highest viewer count first, always.
 *  - DEFAULT: whatever order Twitch returns (Helix's own ranking).
 */
enum class ListSort(val label: String) { VIEWERS("Most viewers"), DEFAULT("Twitch order") }

/** Mini player widths. The video under the title bar is 16:9 of this. */
enum class MiniSize(val label: String, val widthDp: Int) {
    SMALL("Small", 320),
    MEDIUM("Medium", 400),
    LARGE("Large", 560),
    ;

    fun next(): MiniSize = entries[(ordinal + 1) % entries.size]
}

@Serializable
data class PinnedCategory(val id: String, val name: String, val boxArtUrl: String = "")

@Serializable
data class SavedSearch(val name: String, val filters: DiscoverFilters)

/** A channel you opened, newest first in [ViewPrefs.history]. */
@Serializable
data class HistoryEntry(
    val login: String,
    val displayName: String,
    val avatarUrl: String = "",
    val gameName: String = "",
    val watchedAt: Long,
)

/** Browsing, player and chat preferences that should survive a restart. */
@Serializable
data class ViewPrefs(
    val sort: String = ListSort.VIEWERS.name,
    val discover: DiscoverFilters = DiscoverFilters(),
    /** Clicking a live channel anywhere opens the stream, skipping the channel page. */
    val oneClickWatch: Boolean = true,
    /** Drop to 480p while the stream is in the mini player; restore on expand. */
    val lowQualityWhenDocked: Boolean = true,
    val miniSize: String = MiniSize.MEDIUM.name,
    val pinnedCategories: List<PinnedCategory> = emptyList(),
    val savedSearches: List<SavedSearch> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    /** Chat logins whose messages are hidden. Lowercase. */
    val ignoredUsers: List<String> = emptyList(),
    /** Words that highlight a chat message (and copy it to the Mentions tab). */
    val highlightWords: List<String> = emptyList(),
    /** The Personalize panel: accent, text size, density, rail, Home shelves, chat look. */
    val look: Personalization = Personalization(),
) {
    val listSort: ListSort get() = runCatching { ListSort.valueOf(sort) }.getOrDefault(ListSort.VIEWERS)
    val miniSizeEnum: MiniSize get() = runCatching { MiniSize.valueOf(miniSize) }.getOrDefault(MiniSize.MEDIUM)
}

const val HISTORY_LIMIT = 50
const val SAVED_SEARCH_LIMIT = 20

/**
 * Persists [ViewPrefs] to `%APPDATA%/PureTwitch/view_prefs.json`. Same shape as
 * [FollowStore]: an in-memory StateFlow updated synchronously, with the file
 * write handed to [AtomicJsonWriter] so a click never blocks the UI thread.
 */
class ViewPrefsStore(
    appDataDir: File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "PureTwitch"),
) {
    private val file = File(appDataDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val writer = AtomicJsonWriter(file)
    private val lock = Any()

    private val _prefs = MutableStateFlow(loadFromDisk())
    val prefs: StateFlow<ViewPrefs> = _prefs.asStateFlow()

    init {
        runCatching { file.parentFile?.mkdirs() }
    }

    fun setSort(sort: ListSort) = edit { it.copy(sort = sort.name) }

    fun setDiscoverFilters(filters: DiscoverFilters) = edit { it.copy(discover = filters) }

    fun setOneClickWatch(on: Boolean) = edit { it.copy(oneClickWatch = on) }

    fun setLowQualityWhenDocked(on: Boolean) = edit { it.copy(lowQualityWhenDocked = on) }

    fun setMiniSize(size: MiniSize) = edit { it.copy(miniSize = size.name) }

    fun togglePinned(category: PinnedCategory) = edit { p ->
        val pinned = p.pinnedCategories
        p.copy(
            pinnedCategories = if (pinned.any { it.id == category.id }) pinned.filterNot { it.id == category.id } else pinned + category,
        )
    }

    /** Saves (or overwrites, by name) a Discover search. */
    fun saveSearch(name: String, filters: DiscoverFilters) = edit { p ->
        val clean = name.trim().ifEmpty { "Saved search" }.take(60)
        val rest = p.savedSearches.filterNot { it.name.equals(clean, ignoreCase = true) }
        p.copy(savedSearches = (listOf(SavedSearch(clean, filters)) + rest).take(SAVED_SEARCH_LIMIT))
    }

    fun deleteSearch(name: String) = edit { p -> p.copy(savedSearches = p.savedSearches.filterNot { it.name == name }) }

    fun recordWatch(entry: HistoryEntry) = edit { p ->
        p.copy(history = recordHistory(p.history, entry))
    }

    fun removeFromHistory(login: String) = edit { p -> p.copy(history = p.history.filterNot { it.login.equals(login, ignoreCase = true) }) }

    fun clearHistory() = edit { it.copy(history = emptyList()) }

    fun setIgnored(login: String, ignored: Boolean) = edit { p ->
        val l = login.lowercase().trim()
        val rest = p.ignoredUsers.filterNot { it == l }
        p.copy(ignoredUsers = if (ignored && l.isNotEmpty()) rest + l else rest)
    }

    fun setHighlightWords(words: List<String>) = edit { p ->
        p.copy(highlightWords = words.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(30))
    }

    fun setLook(transform: (Personalization) -> Personalization) = edit { it.copy(look = transform(it.look)) }

    fun resetLook() = edit { it.copy(look = Personalization()) }

    fun edit(transform: (ViewPrefs) -> ViewPrefs) = synchronized(lock) {
        val next = transform(_prefs.value)
        if (next == _prefs.value) return
        _prefs.value = next
        writer.enqueue { json.encodeToString(next) }
    }

    /** Block until pending writes are durable. Tests + shutdown only. */
    fun flush() = writer.flush()

    private fun loadFromDisk(): ViewPrefs {
        if (!file.exists()) return ViewPrefs()
        val text = runCatching { file.readText() }.getOrNull() ?: return ViewPrefs()
        return runCatching { json.decodeFromString<ViewPrefs>(text) }.getOrElse {
            AtomicFile.quarantineCorrupt(file)
            ViewPrefs()
        }
    }

    companion object {
        const val FILE_NAME = "view_prefs.json"
    }
}

/** Moves (or adds) [entry] to the front, one entry per channel, capped at [HISTORY_LIMIT]. */
internal fun recordHistory(history: List<HistoryEntry>, entry: HistoryEntry): List<HistoryEntry> =
    (listOf(entry) + history.filterNot { it.login.equals(entry.login, ignoreCase = true) }).take(HISTORY_LIMIT)
