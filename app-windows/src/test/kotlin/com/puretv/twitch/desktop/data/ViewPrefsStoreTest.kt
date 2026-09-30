package com.puretv.twitch.desktop.data

import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.desktop.discover.DiscoverFilters
import com.puretv.twitch.desktop.discover.DiscoverSort
import com.puretv.twitch.desktop.discover.UptimeFilter
import com.puretv.twitch.desktop.ui.BrowseUiState
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ViewPrefsStoreTest {
    @Test fun defaults_to_most_viewers() {
        val dir = Files.createTempDirectory("prefs").toFile()
        assertEquals(ListSort.VIEWERS, ViewPrefsStore(dir).prefs.value.listSort)
    }

    @Test fun sort_and_discover_filters_survive_a_restart() {
        val dir = Files.createTempDirectory("prefs").toFile()
        val filters = DiscoverFilters(
            gameId = "509658", gameName = "Just Chatting", language = "en",
            minViewers = 10, maxViewers = 50, tags = listOf("chill"),
            uptime = UptimeFilter.MID, sort = DiscoverSort.FEWEST_VIEWERS,
        )
        ViewPrefsStore(dir).apply {
            setSort(ListSort.DEFAULT)
            setDiscoverFilters(filters)
            flush()
        }
        val reloaded = ViewPrefsStore(dir).prefs.value
        assertEquals(ListSort.DEFAULT, reloaded.listSort)
        assertEquals(filters, reloaded.discover)
    }

    @Test fun unknown_sort_value_falls_back() {
        assertEquals(ListSort.VIEWERS, ViewPrefs(sort = "SIDEWAYS").listSort)
    }

    @Test fun browse_orders_categories_by_viewers_unknown_last() {
        val games = listOf(GameInfo("a", "A"), GameInfo("b", "B"), GameInfo("c", "C"), GameInfo("d", "D"))
        val state = BrowseUiState(games = games, viewers = mapOf("a" to 5, "c" to 900, "b" to 40))
        assertEquals(listOf("c", "b", "a", "d"), state.gamesByViewers().map { it.id })
        assertEquals(games, BrowseUiState(games = games).gamesByViewers())
    }
}
