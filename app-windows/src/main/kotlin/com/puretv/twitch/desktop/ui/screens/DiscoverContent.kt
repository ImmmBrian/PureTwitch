package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.desktop.discover.DISCOVER_LANGUAGES
import com.puretv.twitch.desktop.discover.DiscoverSort
import com.puretv.twitch.desktop.discover.DiscoverSource
import com.puretv.twitch.desktop.discover.UptimeFilter
import com.puretv.twitch.desktop.discover.ViewerPreset
import com.puretv.twitch.desktop.discover.formatUptime
import com.puretv.twitch.desktop.ui.DiscoverUiState
import com.puretv.twitch.desktop.ui.DiscoverViewModel
import com.puretv.twitch.desktop.ui.components.CoverImage
import com.puretv.twitch.desktop.ui.components.EditorialEmptyState
import com.puretv.twitch.desktop.ui.components.ExpressiveButton
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonSize
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveFilterChip
import com.puretv.twitch.desktop.ui.components.ExpressiveIconButton
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.LivePill
import com.puretv.twitch.desktop.ui.components.PageTitle
import com.puretv.twitch.desktop.ui.components.expressiveClickable
import com.puretv.twitch.desktop.ui.components.formatViewerCount
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import org.koin.core.Koin

private val FILTER_PANEL_WIDTH = 360.dp

/**
 * Discover: pick filters on the left, get a list of live channels on the right.
 *
 * Nothing is fetched while you edit. "Find streams" runs a scan with the draft;
 * "Load more" continues the same scan deeper into the directory. The draft is
 * saved when applied, so the tab reopens with your last search.
 */
@Composable
fun DiscoverContent(koin: Koin, onOpenChannel: (String) -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<DiscoverViewModel>() }
    val state by viewModel.state.collectAsState()

    Row(
        modifier = Modifier.fillMaxSize().padding(start = 32.dp, end = 24.dp, top = 36.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        FilterPanel(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.width(FILTER_PANEL_WIDTH).fillMaxHeight(),
        )
        ResultsPanel(
            state = state,
            onOpenChannel = onOpenChannel,
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::apply,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

// ── Filters ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(state: DiscoverUiState, viewModel: DiscoverViewModel, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val d = state.draft

    Column(modifier) {
        PageTitle("Discover")
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(PureTvTheme.shapes.cardShape)
                .background(c.surfaceLow)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            FilterSection("Category") {
                if (d.gameId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExpressiveFilterChip(label = d.gameName ?: "Selected", selected = true, onClick = { viewModel.pickCategory(null) })
                        Spacer(Modifier.width(8.dp))
                        Text("Click to clear", style = MaterialTheme.typography.bodySmall, color = c.outline)
                    }
                } else {
                    FilterTextField(
                        value = state.categoryQuery,
                        onValueChange = viewModel::onCategoryQuery,
                        placeholder = "Any category. Type to search",
                    )
                    if (state.categorySuggestions.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(PureTvTheme.shapes.smShape)
                                .background(c.surfaceHigh),
                        ) {
                            state.categorySuggestions.forEach { game ->
                                SuggestionRow(game.name) { viewModel.pickCategory(game) }
                            }
                        }
                    }
                }
            }

            FilterSection("Language") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveFilterChip("Any", d.language == null, { viewModel.updateDraft { it.copy(language = null) } })
                    DISCOVER_LANGUAGES.forEach { lang ->
                        ExpressiveFilterChip(lang.label, d.language == lang.code, {
                            viewModel.updateDraft { it.copy(language = lang.code) }
                        })
                    }
                }
            }

            FilterSection("Viewers") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val current = ViewerPreset.matching(d.minViewers, d.maxViewers)
                    ViewerPreset.entries.forEach { p ->
                        ExpressiveFilterChip(p.label, current == p, {
                            viewModel.updateDraft { it.copy(minViewers = p.min, maxViewers = p.max) }
                        })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterTextField(
                        value = d.minViewers?.toString().orEmpty(),
                        onValueChange = { v -> viewModel.updateDraft { it.copy(minViewers = parseCount(v)) } },
                        placeholder = "Min",
                        modifier = Modifier.weight(1f),
                    )
                    Text("to", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    FilterTextField(
                        value = d.maxViewers?.toString().orEmpty(),
                        onValueChange = { v -> viewModel.updateDraft { it.copy(maxViewers = parseCount(v)) } },
                        placeholder = "Max",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            FilterSection("Live for") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UptimeFilter.entries.forEach { u ->
                        ExpressiveFilterChip(u.label, d.uptime == u, { viewModel.updateDraft { it.copy(uptime = u) } })
                    }
                }
            }

            FilterSection("Tags", hint = "Comma separated, e.g. chill, speedrun") {
                // Keep the raw text locally so typing a comma doesn't get normalized away mid-word.
                var tagText by remember(d.tags.isEmpty()) { mutableStateOf(d.tags.joinToString(", ")) }
                FilterTextField(
                    value = tagText,
                    onValueChange = { v ->
                        tagText = v
                        viewModel.updateDraft { it.copy(tags = v.split(',').map(String::trim).filter(String::isNotEmpty)) }
                    },
                    placeholder = "Any tags",
                )
            }

            FilterSection("Title contains") {
                FilterTextField(
                    value = d.titleKeyword,
                    onValueChange = { v -> viewModel.updateDraft { it.copy(titleKeyword = v) } },
                    placeholder = "Any title",
                )
            }

            FilterSection("Sort by") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiscoverSort.entries.forEach { s ->
                        ExpressiveFilterChip(s.label, d.sort == s, { viewModel.updateDraft { it.copy(sort = s) } })
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ExpressiveButton(
                text = if (state.hasUnappliedChanges) "Find streams" else "Refresh",
                onClick = viewModel::apply,
                icon = ExpressiveIcons.Search,
                size = ExpressiveButtonSize.Large,
                modifier = Modifier.weight(1f),
            )
            ExpressiveButton(
                text = "Reset",
                onClick = viewModel::reset,
                style = ExpressiveButtonStyle.Outlined,
                size = ExpressiveButtonSize.Large,
            )
        }
    }
}

private fun parseCount(raw: String): Int? = raw.filter { it.isDigit() }.take(7).toIntOrNull()

@Composable
private fun FilterSection(title: String, hint: String? = null, content: @Composable () -> Unit) {
    val c = PureTvTheme.colors
    Column {
        Text(title.uppercase(), style = PureTvType.kicker, color = c.onSurfaceVariant)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = c.outline, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun FilterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(PureTvTheme.shapes.pillShape)
            .background(c.surfaceHigh)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = c.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
            cursorBrush = SolidColor(c.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SuggestionRow(text: String, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 0.dp,
                hoverRadius = 0.dp,
                color = Color.Transparent,
                hoverColor = c.surfaceHighest,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, color = c.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Results ──────────────────────────────────────────────────────────────────

@Composable
private fun ResultsPanel(
    state: DiscoverUiState,
    onOpenChannel: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    Column(modifier) {
        // Summary line: what the results are and how deep the scan went.
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when {
                    state.isLoading -> "Scanning live channels…"
                    else -> "${state.results.size} ${if (state.results.size == 1) "match" else "matches"}"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = c.onSurface,
            )
            if (!state.isLoading && state.scanned > 0) {
                Text(
                    "from ${formatViewerCount(state.scanned)} live channels checked",
                    style = PureTvType.data,
                    color = c.onSurfaceVariant,
                )
            }
            if (state.hasUnappliedChanges && !state.isLoading) {
                Spacer(Modifier.weight(1f))
                Text("Filters changed. Press Find streams.", style = MaterialTheme.typography.bodySmall, color = c.primary)
            }
        }
        if (state.source == DiscoverSource.HELIX && state.draft.maxViewers != null && state.applied.gameId == null) {
            Text(
                "Small-channel search works best with a category picked.",
                style = MaterialTheme.typography.bodySmall,
                color = c.outline,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(PureTvTheme.shapes.cardShape)
                .background(c.surfaceLow),
        ) {
            when {
                state.isLoading -> Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Text("Checking the live directory", style = PureTvType.data, color = c.onSurfaceVariant)
                }

                state.error != null && state.results.isEmpty() -> EditorialEmptyState(
                    kicker = "Discover",
                    title = "Search failed",
                    message = state.error,
                    actionLabel = "Try again",
                    onAction = onRetry,
                )

                state.results.isEmpty() -> EditorialEmptyState(
                    kicker = "Discover",
                    title = "No matches yet",
                    message = if (state.canLoadMore) {
                        "Nothing matched in the channels checked so far. Keep scanning, or loosen a filter."
                    } else {
                        "Nobody live matches these filters right now. Try loosening one."
                    },
                    actionLabel = if (state.canLoadMore) "Keep scanning" else null,
                    onAction = if (state.canLoadMore) onLoadMore else null,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.results, key = { it.id }) { stream ->
                        DiscoverRow(stream = stream, onClick = { onOpenChannel(stream.userLogin) })
                    }
                    if (state.canLoadMore || state.isLoadingMore || state.error != null) {
                        item(key = "load_more") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                when {
                                    state.isLoadingMore -> CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                    state.error != null -> ExpressiveButton(text = "Retry", onClick = onLoadMore, style = ExpressiveButtonStyle.Tonal)
                                    else -> ExpressiveButton(text = "Load more", onClick = onLoadMore, style = ExpressiveButtonStyle.Tonal)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One result: 16:9 thumb with LIVE + viewers, then name, title and a meta line. */
@Composable
private fun DiscoverRow(stream: StreamInfo, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = PureTvTheme.shapes.sm,
                hoverRadius = PureTvTheme.shapes.md,
                color = Color.Transparent,
                hoverColor = c.surfaceHigh,
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .width(192.dp)
                .height(108.dp)
                .clip(PureTvTheme.shapes.thumbShape),
        ) {
            val thumb = stream.thumbnailUrl.replace("{width}", "440").replace("{height}", "248")
            CoverImage(imageUrl = thumb.ifBlank { null }, seed = stream.userName, contentDescription = stream.title, modifier = Modifier.fillMaxSize())
            LivePill(modifier = Modifier.align(Alignment.TopStart).padding(8.dp), height = 22.dp)
            Text(
                formatViewerCount(stream.viewerCount),
                style = PureTvType.data,
                color = c.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(PureTvTheme.shapes.smShape)
                    .background(c.surfaceLowest.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(stream.userName, style = MaterialTheme.typography.titleMedium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (stream.title.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(stream.title, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            val meta = listOfNotNull(
                stream.gameName.takeIf { it.isNotBlank() },
                "${formatViewerCount(stream.viewerCount)} viewers",
                formatUptime(stream.startedAt)?.let { "live $it" },
                stream.language.takeIf { it.isNotBlank() }?.uppercase(),
            ).joinToString("  ·  ")
            Text(meta, style = PureTvType.dataSmall, color = c.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (stream.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stream.tags.take(6).joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ExpressiveIconButton(
            icon = ExpressiveIcons.Play,
            contentDescription = "Open ${stream.userName}",
            onClick = onClick,
            style = ExpressiveButtonStyle.Tonal,
        )
    }
}
