package com.puretv.twitch.desktop.discover

import com.puretv.twitch.core.model.StreamInfo
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/** A broadcast language: Helix's ISO 639-1 code plus the GQL `Language` enum name. */
data class DiscoverLanguage(val code: String, val label: String, val gqlEnum: String)

val DISCOVER_LANGUAGES: List<DiscoverLanguage> = listOf(
    DiscoverLanguage("en", "English", "EN"),
    DiscoverLanguage("es", "Spanish", "ES"),
    DiscoverLanguage("pt", "Portuguese", "PT"),
    DiscoverLanguage("de", "German", "DE"),
    DiscoverLanguage("fr", "French", "FR"),
    DiscoverLanguage("it", "Italian", "IT"),
    DiscoverLanguage("ja", "Japanese", "JA"),
    DiscoverLanguage("ko", "Korean", "KO"),
    DiscoverLanguage("zh", "Chinese", "ZH"),
    DiscoverLanguage("ru", "Russian", "RU"),
    DiscoverLanguage("pl", "Polish", "PL"),
    DiscoverLanguage("tr", "Turkish", "TR"),
    DiscoverLanguage("ar", "Arabic", "AR"),
    DiscoverLanguage("nl", "Dutch", "NL"),
    DiscoverLanguage("sv", "Swedish", "SV"),
)

fun languageFor(code: String?): DiscoverLanguage? =
    code?.let { c -> DISCOVER_LANGUAGES.firstOrNull { it.code.equals(c, ignoreCase = true) } }

/** How long the stream has been live. Bounds are in minutes; null means open-ended. */
enum class UptimeFilter(val label: String, val minMinutes: Long?, val maxMinutes: Long?) {
    ANY("Any", null, null),
    JUST_STARTED("Under 1 hr", null, 60),
    MID("1 to 4 hrs", 60, 240),
    LONG("Over 4 hrs", 240, null),
}

enum class DiscoverSort(val label: String) {
    MOST_VIEWERS("Most viewers"),
    FEWEST_VIEWERS("Fewest viewers"),
    NEWEST("Just went live"),
}

/** Quick viewer-range presets shown as chips; they fill the min/max fields. */
enum class ViewerPreset(val label: String, val min: Int?, val max: Int?) {
    ANY("Any", null, null),
    TINY("Under 10", null, 9),
    SMALL("10 to 50", 10, 50),
    MEDIUM("50 to 250", 50, 250),
    LARGE("250 to 1K", 250, 1000),
    HUGE("1K+", 1000, null),
    ;

    companion object {
        fun matching(min: Int?, max: Int?): ViewerPreset? = entries.firstOrNull { it.min == min && it.max == max }
    }
}

/**
 * Everything the Discover tab filters on. Persisted (see ViewPrefsStore), so it
 * must stay serializable and every field needs a default.
 *
 * Category, language and tags go to Twitch as server-side filters where
 * possible. Viewer range, uptime and title keyword are applied client-side to
 * the pages Twitch returns (Twitch offers no server filter for those).
 */
@Serializable
data class DiscoverFilters(
    val gameId: String? = null,
    val gameName: String? = null,
    val language: String? = null,
    val minViewers: Int? = null,
    val maxViewers: Int? = null,
    val tags: List<String> = emptyList(),
    val titleKeyword: String = "",
    val uptime: UptimeFilter = UptimeFilter.ANY,
    val sort: DiscoverSort = DiscoverSort.MOST_VIEWERS,
) {
    /** Tags cleaned to what Twitch accepts: letters and digits only, de-duplicated, max 5. */
    val cleanTags: List<String>
        get() = tags.map { t -> t.filter { it.isLetterOrDigit() } }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(5)

    val hasViewerRange: Boolean get() = minViewers != null || maxViewers != null
}

/**
 * Whether to walk the directory lowest-viewers-first. Twitch has ~100k live
 * channels and most sit near zero viewers, so the scan direction decides whether
 * the matches are near the start or buried thousands of streams deep:
 *  - "Fewest viewers" sort, or a max with no real minimum → start from the bottom.
 *  - Anything with a real minimum → start from the top and stop once below it.
 */
fun scanAscending(f: DiscoverFilters): Boolean {
    val min = f.minViewers ?: 0
    if (f.sort == DiscoverSort.FEWEST_VIEWERS && min <= 2) return true
    return f.maxViewers != null && min <= 2
}

/** Client-side half of the filter. [checkTags] is false when Twitch already filtered by tag. */
fun DiscoverFilters.matches(s: StreamInfo, now: Instant = Instant.now(), checkTags: Boolean = true): Boolean {
    minViewers?.let { if (s.viewerCount < it) return false }
    maxViewers?.let { if (s.viewerCount > it) return false }
    if (titleKeyword.isNotBlank() && !s.title.contains(titleKeyword.trim(), ignoreCase = true)) return false
    if (checkTags) {
        val want = cleanTags
        if (want.isNotEmpty()) {
            val have = s.tags.map { it.lowercase() }.toSet()
            if (!want.all { it.lowercase() in have }) return false
        }
    }
    if (uptime != UptimeFilter.ANY) {
        val minutes = uptimeMinutes(s.startedAt, now) ?: return false
        uptime.minMinutes?.let { if (minutes < it) return false }
        uptime.maxMinutes?.let { if (minutes >= it) return false }
    }
    return true
}

/** Minutes since [startedAt] (ISO-8601), or null when it can't be parsed. */
fun uptimeMinutes(startedAt: String, now: Instant = Instant.now()): Long? =
    runCatching { Duration.between(Instant.parse(startedAt), now).toMinutes() }.getOrNull()

/** "2h 14m" / "37m", or null when the start time is unknown. */
fun formatUptime(startedAt: String, now: Instant = Instant.now()): String? {
    val m = uptimeMinutes(startedAt, now) ?: return null
    if (m < 0) return null
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

/** Orders collected matches the way the user asked. */
fun List<StreamInfo>.sortedFor(sort: DiscoverSort): List<StreamInfo> = when (sort) {
    DiscoverSort.MOST_VIEWERS -> sortedByDescending { it.viewerCount }
    DiscoverSort.FEWEST_VIEWERS -> sortedBy { it.viewerCount }
    // ISO-8601 UTC timestamps sort correctly as strings; blanks sink to the end.
    DiscoverSort.NEWEST -> sortedWith(compareByDescending<StreamInfo> { it.startedAt.isNotBlank() }.thenByDescending { it.startedAt })
}
