package com.puretv.twitch.desktop.ui.screens

import com.puretv.twitch.desktop.ui.theme.gridColumns
import com.puretv.twitch.desktop.ui.theme.LocalCompactLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.desktop.data.PinnedCategory
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveIconButton
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.SectionHeading
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
    var query by remember { mutableStateOf("") }

    val pinned = remember(prefs.pinnedCategories) { prefs.pinnedCategories.map { GameInfo(it.id, it.name, it.boxArtUrl) } }
    val pinnedIds = remember(pinned) { pinned.map { it.id }.toSet() }
    LaunchedEffect(pinnedIds) { viewModel.ensureViewers(pinnedIds.toList()) }

    val games = remember(state.games, state.viewers, state.searchResults, sort, query) {
        val base = if (sort == ListSort.VIEWERS) state.gamesByViewers() else state.games
        val q = query.trim()
        if (q.isEmpty()) {
            base
        } else {
            // Instant local matches first, then Twitch's search for everything else.
            val local = base.filter { it.name.contains(q, ignoreCase = true) }
            val localIds = local.map { it.id }.toSet()
            val remote = state.searchResults.filter { it.id !in localIds }
            val merged = local + remote
            if (sort == ListSort.VIEWERS) merged.sortedByDescending { state.viewers[it.id] ?: -1 } else merged
        }
    }
    val pinnedSorted = remember(pinned, state.viewers, sort) {
        if (sort == ListSort.VIEWERS) pinned.sortedByDescending { state.viewers[it.id] ?: -1 } else pinned
    }

    fun togglePin(game: GameInfo) = prefsStore.togglePinned(PinnedCategory(game.id, game.name, game.boxArtUrl))

    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns(6, LocalCompactLayout.current)),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 36.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        fullSpan {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PageTitle("Browse", modifier = Modifier.weight(1f))
                    CategorySearchField(
                        value = query,
                        onValueChange = {
                            query = it
                            viewModel.search(it)
                        },
                        modifier = Modifier.width(320.dp),
                    )
                    SegmentedToggle(
                        options = ListSort.entries,
                        selected = sort,
                        label = { it.label },
                        onSelect = { prefsStore.setSort(it) },
                        height = 44.dp,
                    )
                }
                Spacer(Modifier.height(if (pinnedSorted.isNotEmpty() && query.isBlank()) 12.dp else 28.dp))
            }
        }

        // Pinned categories stay on top, whatever the sort.
        if (pinnedSorted.isNotEmpty() && query.isBlank()) {
            fullSpan { SectionHeading(title = "Pinned") }
            items(pinnedSorted, key = { "pin_${it.id}" }) { game ->
                GameTile(
                    name = game.name,
                    boxArtUrl = game.boxArtUrl,
                    viewers = state.viewers[game.id],
                    pinned = true,
                    onTogglePin = { togglePin(game) },
                    onClick = { onOpenCategory(game.id, game.name) },
                )
            }
            fullSpan { SectionHeading(title = "All categories", modifier = Modifier.padding(top = 12.dp)) }
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
            state.games.isEmpty() && query.isBlank() -> fullSpan {
                EditorialEmptyState(
                    kicker = "Categories",
                    title = if (state.isLoading) "Loading categories…" else "Nothing to browse yet",
                    message = if (state.isLoading) "Fetching the top categories." else "Top categories will appear here in a moment.",
                )
            }
            games.isEmpty() -> fullSpan {
                EditorialEmptyState(
                    kicker = "Categories",
                    title = "No categories match \"${query.trim()}\"",
                    message = "Check the spelling, or try a shorter name.",
                )
            }
            else -> items(games, key = { it.id }) { game ->
                GameTile(
                    name = game.name,
                    boxArtUrl = game.boxArtUrl,
                    viewers = state.viewers[game.id],
                    pinned = game.id in pinnedIds,
                    onTogglePin = { togglePin(game) },
                    onClick = { onOpenCategory(game.id, game.name) },
                )
            }
        }
    }
}

@Composable
private fun CategorySearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(PureTvTheme.shapes.pillShape)
            .background(c.surfaceHigh)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(ExpressiveIcons.Search, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("Search categories", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = c.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            ExpressiveIconButton(
                icon = ExpressiveIcons.Close,
                contentDescription = "Clear search",
                onClick = { onValueChange("") },
                boxSize = 28.dp,
                iconSize = 16.dp,
            )
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
private fun GameTile(
    name: String,
    boxArtUrl: String,
    viewers: Int?,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onClick: () -> Unit,
) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

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
            // Pin toggle: always visible once pinned, otherwise on hover.
            if (pinned || hovered) {
                ExpressiveIconButton(
                    icon = if (pinned) ExpressiveIcons.Pin else ExpressiveIcons.PinOutlined,
                    contentDescription = if (pinned) "Unpin $name" else "Pin $name",
                    onClick = onTogglePin,
                    style = ExpressiveButtonStyle.Tonal,
                    boxSize = 36.dp,
                    iconSize = 18.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
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
            // The exact number, so close categories can be told apart.
            if (viewers != null) "${"%,d".format(viewers)} viewers" else " ",
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
