package com.puretv.twitch.desktop.discover

import com.puretv.twitch.core.api.StreamPage
import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.model.StreamInfo
import java.time.Instant

/** Which backend served a scan. GraphQL can sort ascending and filter tags server-side; Helix can't. */
enum class DiscoverSource { GQL, HELIX }

/** Where to resume a scan. Opaque to the UI: hand it back to [DiscoverRepository.scan] for "Load more". */
data class DiscoverCursor(val source: DiscoverSource, val cursor: String)

data class DiscoverBatch(
    val matches: List<StreamInfo>,
    /** Streams examined in this batch, matched or not. */
    val scanned: Int,
    val source: DiscoverSource,
    /** Null when the directory is exhausted, or the scan passed the viewer range and can stop. */
    val next: DiscoverCursor?,
)

/** One page fetch. Split out so tests can drive the scan loop without the network. */
interface DirectoryPager {
    suspend fun page(source: DiscoverSource, filters: DiscoverFilters, ascending: Boolean, after: String?): StreamPage
}

/**
 * Walks Twitch's live directory page by page and keeps what passes [DiscoverFilters].
 *
 * Twitch has no server-side filter for viewer ranges, uptime or title words, so
 * those are checked here against each page. Each call scans until it has
 * [targetMatches] results or has read [maxPages] pages, then returns a cursor so
 * the UI can offer "Load more" instead of silently truncating.
 *
 * GraphQL is tried first because it can walk the directory lowest-viewers-first
 * (the only practical way to reach small channels). If the very first GraphQL
 * request of a fresh search fails, the scan falls back to Helix and stays there.
 */
class DiscoverRepository(
    private val pager: DirectoryPager,
    private val targetMatches: Int = 40,
    private val maxPages: Int = 20,
    private val now: () -> Instant = { Instant.now() },
) {
    constructor(api: TwitchApiClient, gql: TwitchDirectoryGql) : this(
        pager = object : DirectoryPager {
            override suspend fun page(source: DiscoverSource, filters: DiscoverFilters, ascending: Boolean, after: String?): StreamPage {
                val f = filters
                return when (source) {
                    DiscoverSource.GQL -> gql.streamsPage(
                        gameId = f.gameId,
                        languageEnum = languageFor(f.language)?.gqlEnum,
                        tags = f.cleanTags,
                        ascending = ascending,
                        after = after,
                    )
                    DiscoverSource.HELIX -> api.getStreamsPage(
                        gameIds = listOfNotNull(f.gameId?.takeIf { it.isNotBlank() }),
                        languages = listOfNotNull(f.language),
                        first = 100,
                        after = after,
                    )
                }
            }
        },
    )

    suspend fun scan(filters: DiscoverFilters, from: DiscoverCursor? = null): DiscoverBatch {
        var source = from?.source ?: DiscoverSource.GQL
        var cursor: String? = from?.cursor
        val fresh = from == null
        val matches = mutableListOf<StreamInfo>()
        var scanned = 0
        var pages = 0
        val instant = now()

        while (pages < maxPages) {
            // Helix only sorts highest-first, so ascending is a GraphQL-only mode.
            val ascending = source == DiscoverSource.GQL && scanAscending(filters)
            val page = try {
                pager.page(source, filters, ascending, cursor)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (fresh && pages == 0 && source == DiscoverSource.GQL) {
                    source = DiscoverSource.HELIX
                    cursor = null
                    continue
                }
                // Mid-scan failure: return what we have and let "Load more" retry this page.
                if (matches.isEmpty() && pages == 0) throw e
                return DiscoverBatch(matches, scanned, source, cursor?.let { DiscoverCursor(source, it) })
            }
            pages++
            scanned += page.streams.size
            // Tags: GraphQL already filtered server-side (and its nodes carry no tags).
            val checkTags = source == DiscoverSource.HELIX
            page.streams.filterTo(matches) { filters.matches(it, instant, checkTags) }

            val next = page.cursor
            if (next == null || page.streams.isEmpty()) return DiscoverBatch(matches, scanned, source, null)
            if (passedRange(filters, page.streams, ascending)) return DiscoverBatch(matches, scanned, source, null)
            cursor = next
            if (matches.size >= targetMatches) break
        }
        return DiscoverBatch(matches, scanned, source, cursor?.let { DiscoverCursor(source, it) })
    }
}

/**
 * True once the scan has walked past the viewer range and no later page can
 * match: highest-first and the page already dropped below the minimum, or
 * lowest-first and it already climbed above the maximum.
 */
internal fun passedRange(f: DiscoverFilters, page: List<StreamInfo>, ascending: Boolean): Boolean {
    if (page.isEmpty()) return false
    return if (ascending) {
        val max = f.maxViewers ?: return false
        page.minOf { it.viewerCount } > max
    } else {
        val min = f.minViewers ?: return false
        page.maxOf { it.viewerCount } < min
    }
}
