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

/** Browsing preferences that should survive a restart: list sort and the last Discover filters. */
@Serializable
data class ViewPrefs(
    val sort: String = ListSort.VIEWERS.name,
    val discover: DiscoverFilters = DiscoverFilters(),
) {
    val listSort: ListSort get() = runCatching { ListSort.valueOf(sort) }.getOrDefault(ListSort.VIEWERS)
}

/**
 * Persists [ViewPrefs] to `%APPDATA%/PureTwitch/view_prefs.json`. Same shape as
 * [FollowStore]: an in-memory StateFlow updated synchronously, with the file
 * write handed to [AtomicJsonWriter] so a click never blocks the UI thread.
 */
class ViewPrefsStore(
    appDataDir: File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "PureTwitch"),
) {
    private val file = File(appDataDir, "view_prefs.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val writer = AtomicJsonWriter(file)
    private val lock = Any()

    private val _prefs = MutableStateFlow(loadFromDisk())
    val prefs: StateFlow<ViewPrefs> = _prefs.asStateFlow()

    init {
        runCatching { file.parentFile?.mkdirs() }
    }

    fun setSort(sort: ListSort) = update { it.copy(sort = sort.name) }

    fun setDiscoverFilters(filters: DiscoverFilters) = update { it.copy(discover = filters) }

    private fun update(transform: (ViewPrefs) -> ViewPrefs) = synchronized(lock) {
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
}
