package com.puretv.twitch.desktop.ui

import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.desktop.discover.DiscoverCursor
import com.puretv.twitch.desktop.discover.DiscoverFilters
import com.puretv.twitch.desktop.discover.DiscoverRepository
import com.puretv.twitch.desktop.discover.DiscoverSource
import com.puretv.twitch.desktop.discover.sortedFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    /** The filters being edited in the panel. Nothing is fetched until [DiscoverViewModel.apply]. */
    val draft: DiscoverFilters = DiscoverFilters(),
    /** The filters the current results were produced with. */
    val applied: DiscoverFilters = DiscoverFilters(),
    val results: List<StreamInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** Total streams examined for the current results, matched or not. */
    val scanned: Int = 0,
    val canLoadMore: Boolean = false,
    val source: DiscoverSource? = null,
    val error: String? = null,
    val categoryQuery: String = "",
    val categorySuggestions: List<GameInfo> = emptyList(),
) {
    val hasUnappliedChanges: Boolean get() = draft != applied
}

/**
 * Backs the Discover tab: an editable filter draft, a scan that runs when the user
 * applies it, and "Load more" that resumes the scan from where it stopped.
 */
class DiscoverViewModel(
    private val repository: DiscoverRepository,
    private val searchCategories: suspend (String) -> List<GameInfo>,
    initialFilters: DiscoverFilters,
    private val persistFilters: (DiscoverFilters) -> Unit,
    /** Logins you follow (on Twitch and locally), for "Hide channels I follow". */
    private val followedLogins: suspend () -> Set<String> = { emptySet() },
) : DesktopViewModel() {
    private var followedCache: Set<String>? = null

    private suspend fun followed(): Set<String> =
        followedCache ?: runCatching { followedLogins() }.getOrDefault(emptySet())
            .map { it.lowercase() }.toSet()
            .also { followedCache = it }

    private val _state = MutableStateFlow(
        DiscoverUiState(draft = initialFilters, applied = initialFilters, categoryQuery = initialFilters.gameName.orEmpty()),
    )
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private var next: DiscoverCursor? = null
    private var scanJob: Job? = null
    private var categoryJob: Job? = null

    init { apply() }

    fun updateDraft(transform: (DiscoverFilters) -> DiscoverFilters) {
        _state.update { it.copy(draft = transform(it.draft)) }
    }

    /** Run a fresh scan with the current draft. */
    fun apply() {
        val filters = _state.value.draft
        persistFilters(filters)
        scanJob?.cancel()
        next = null
        _state.update {
            it.copy(applied = filters, results = emptyList(), scanned = 0, canLoadMore = false, isLoading = true, isLoadingMore = false, error = null)
        }
        scanJob = scope.launch { runScan(filters, from = null) }
    }

    fun loadMore() {
        val s = _state.value
        val from = next ?: return
        if (s.isLoading || s.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true, error = null) }
        scanJob = scope.launch { runScan(s.applied, from) }
    }

    /** Load a saved search into the panel and run it. */
    fun load(filters: DiscoverFilters) {
        _state.update {
            it.copy(draft = filters, categoryQuery = filters.gameName.orEmpty(), categorySuggestions = emptyList())
        }
        apply()
    }

    /** Clear every filter back to "everything live, most viewers first" and rescan. */
    fun reset() {
        _state.update { it.copy(draft = DiscoverFilters(), categoryQuery = "", categorySuggestions = emptyList()) }
        apply()
    }

    private suspend fun runScan(filters: DiscoverFilters, from: DiscoverCursor?) {
        try {
            val batch = repository.scan(filters, from)
            next = batch.next
            val kept = if (filters.hideFollowed) {
                val skip = followed()
                batch.matches.filterNot { it.userLogin.lowercase() in skip }
            } else {
                batch.matches
            }
            _state.update { st ->
                val merged = (st.results + kept).distinctBy { it.id }.sortedFor(filters.sort)
                st.copy(
                    results = merged,
                    scanned = st.scanned + batch.scanned,
                    canLoadMore = batch.next != null,
                    source = batch.source,
                    isLoading = false,
                    isLoadingMore = false,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = "Couldn't reach Twitch's directory. Check your connection and try again.",
                )
            }
        }
    }

    // ── Category picker ──────────────────────────────────────────────────────

    fun onCategoryQuery(query: String) {
        _state.update { it.copy(categoryQuery = query) }
        categoryJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(categorySuggestions = emptyList()) }
            return
        }
        categoryJob = scope.launch {
            delay(300)
            val found = runCatching { searchCategories(query) }.getOrDefault(emptyList())
            _state.update { it.copy(categorySuggestions = found.take(8)) }
        }
    }

    fun pickCategory(game: GameInfo?) {
        categoryJob?.cancel()
        _state.update {
            it.copy(
                draft = it.draft.copy(gameId = game?.id, gameName = game?.name),
                categoryQuery = game?.name.orEmpty(),
                categorySuggestions = emptyList(),
            )
        }
    }
}
