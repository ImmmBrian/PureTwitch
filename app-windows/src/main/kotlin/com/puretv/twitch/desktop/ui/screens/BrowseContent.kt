package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.data.ListSort
import com.puretv.twitch.desktop.data.ViewPrefsStore
import com.puretv.twitch.desktop.ui.BrowseViewModel
import com.puretv.twitch.desktop.ui.components.SegmentedToggle
import com.puretv.twitch.desktop.ui.components.formatViewerCount
import com.puretv.twitch.desktop.ui.theme.PureTvType
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.components.CoverImage
import com.puretv.twitch.desktop.ui.components.EditorialEmptyState
import com.puretv.twitch.desktop.ui.components.PageTitle
import com.puretv.twitch.desktop.ui.components.expressiveClickable
import com.puretv.twitch.desktop.ui.components.expressiveSurface
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import org.koin.core.Koin

/**
 * Browse = a grid of category cover art, presented as a magazine contact sheet.
 * Tapping a cover opens the Category screen ([onOpenCategory]) listing that
 * game's live streams.
 *
 * Covers come from [com.puretv.twitch.core.model.GameInfo.boxArtUrl], a
 * `{width}x{height}` template (same shape as stream thumbnails). Box art is
 * portrait, so the 3:4 tiles match Twitch's native aspect ratio. Helix returns
 * no viewer numbers for categories, so the per-tile counts come from a separate
 * GraphQL lookup ([BrowseUiState.viewers][com.puretv.twitch.desktop.ui.BrowseUiState.viewers])
 * that fills in after the grid appears. The header's sort toggle is shared with
 * Home and persisted (see [ViewPrefsStore]).
 */
@Composable
fun BrowseContent(koin: Koin, onOpenCategory: (gameId: String, gameName: String) -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<BrowseViewModel>() }
    val state by viewModel.state.collectAsState()
    val prefsStore = remember { koin.get<ViewPrefsStore>() }
    val prefs by prefsStore.prefs.collectAsState()
    val sort = prefs.listSort
    val games = remember(state.games, state.viewers, sort) {
        if (sort == ListSort.VIEWERS) state.gamesByViewers() else state.games
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 36.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        fullSpan {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PageTitle("Browse", modifier = Modifier.weight(1f))
                    SegmentedToggle(
                        options = ListSort.entries,
                        selected = sort,
                        label = { it.label },
                        onSelect = { prefsStore.setSort(it) },
                        height = 44.dp,
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        when {
            state.error != null -> fullSpan {
                EditorialEmptyState(
                    kicker = "Categories",
                    title = "Couldn't load categories",
                    message = state.error!!,
                    actionLabel = "Retry",
                    onAction = { viewModel.load() },
                )
            }
            state.games.isEmpty() -> fullSpan {
                EditorialEmptyState(
                    kicker = "Categories",
                    title = if (state.isLoading) "Loading categories…" else "Nothing to browse yet",
                    message = if (state.isLoading) "Fetching the top categories." else "Top categories will appear here in a moment.",
                )
            }
            else -> items(games, key = { it.id }) { game ->
                GameTile(
                    name = game.name,
                    boxArtUrl = game.boxArtUrl,
                    viewers = state.viewers[game.id],
                    onClick = { onOpenCategory(game.id, game.name) },
                )
            }
        }
    }
}

/**
 * A single box-art poster: 3:4 cover that rounds out and lifts on hover, name
 * below in [titleMedium][MaterialTheme.typography]. One interaction source
 * drives both the card's lift and the art's radius morph, so hovering
 * anywhere on the tile, art or title, animates the whole thing together.
 */
@Composable
private fun GameTile(name: String, boxArtUrl: String, viewers: Int?, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier.expressiveClickable(
            interaction = interaction,
            onClick = onClick,
            restRadius = 0.dp,
            hoverRadius = 0.dp,
            lift = 6.dp,
        ),
    ) {
        val art = boxArtUrl
            .replace("{width}", "285")
            .replace("{height}", "380")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .expressiveSurface(
                    interaction = interaction,
                    restRadius = shapes.card,
                    hoverRadius = shapes.cardMorph,
                ),
        ) {
            CoverImage(
                imageUrl = art.ifBlank { null },
                seed = name,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = c.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )
        // Reserve the line even before counts arrive so the grid doesn't jump.
        Text(
            if (viewers != null) "${formatViewerCount(viewers)} viewers" else " ",
            style = PureTvType.data,
            color = c.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** A full-width row inside the grid (page header, empty state). */
private fun LazyGridScope.fullSpan(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}
