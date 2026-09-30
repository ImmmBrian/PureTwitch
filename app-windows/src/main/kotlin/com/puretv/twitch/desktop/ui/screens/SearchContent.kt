package com.puretv.twitch.desktop.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puretv.twitch.core.api.ChannelSearchResult
import com.puretv.twitch.desktop.ui.SearchViewModel
import com.puretv.twitch.desktop.ui.components.Avatar
import com.puretv.twitch.desktop.ui.components.EditorialEmptyState
import com.puretv.twitch.desktop.ui.components.ExpressiveIconButton
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.LivePill
import com.puretv.twitch.desktop.ui.components.handCursor
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import org.koin.core.Koin

@Composable
fun SearchContent(koin: Koin, actions: SearchActions, hiddenTabs: List<String>) {
    val viewModel = rememberDesktopViewModel { koin.get<SearchViewModel>() }
    val state by viewModel.state.collectAsState()
    val hits = remember(state, hiddenTabs) { searchHits(state, hiddenTabs, SEARCH_TAB_LIMITS) }
    val c = PureTvTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 36.dp, start = 32.dp, end = 32.dp, bottom = 40.dp),
    ) {
        SearchField(query = state.query, onQueryChange = viewModel::onQueryChange)

        when {
            // Settings are local, so they can show even when Twitch can't be reached.
            state.error != null && hits.isEmpty() -> {
                EditorialEmptyState(
                    kicker = "Search",
                    title = "Search failed",
                    message = state.error!!,
                    actionLabel = "Retry",
                    onAction = { viewModel.retry() },
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            hits.isNotEmpty() -> {
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(PureTvTheme.shapes.card))
                        .background(c.surfaceLow),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        hits.forEachIndexed { i, hit ->
                            if (i == 0 || sectionTitle(hits[i - 1]) != sectionTitle(hit)) {
                                item(key = "header:" + sectionTitle(hit)) { SectionHeader(sectionTitle(hit)) }
                            }
                            item(key = hit.key) {
                                SearchHitRow(hit, selected = false, compact = false, onClick = { actions.run(hit) })
                            }
                        }
                        if (state.isSearching) {
                            item(key = "searching") { SearchingRow(Modifier.padding(16.dp)) }
                        }
                    }
                }
            }

            state.isSearching -> SearchingRow(Modifier.padding(top = 32.dp))

            state.query.trim().length >= 2 -> {
                EditorialEmptyState(
                    kicker = "Search",
                    title = "Nothing found",
                    message = "No channels, categories or settings match \"${state.query.trim()}\".",
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            else -> {
                EditorialEmptyState(
                    kicker = "Search",
                    title = "Search everything",
                    message = "Channels, categories and settings. Press Ctrl+Shift+Space anywhere to search without leaving what you're watching.",
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchingRow(modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Text("Searching...", style = PureTvType.data, color = c.onSurfaceVariant)
    }
}

/**
 * The morphing search bar: a real editable [BasicTextField] wearing the pill's
 * clothes. It shares one interaction source between the outer hover and the
 * field's own focus, so the corner squares off on either.
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val radius by animateDpAsState(
        targetValue = if (hovered || focused) 20.dp else 32.dp,
        animationSpec = PureTvMotion.MorphSpring,
        label = "searchFieldRadius",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .hoverable(interaction)
            .clip(RoundedCornerShape(radius))
            .background(c.surfaceHigh)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = ExpressiveIcons.Search,
            contentDescription = null,
            tint = c.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Search channels, categories and settings",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                interactionSource = interaction,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, color = c.onSurface),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (query.isNotEmpty()) {
            ExpressiveIconButton(
                icon = ExpressiveIcons.Close,
                contentDescription = "Clear search",
                onClick = { onQueryChange("") },
                boxSize = 44.dp,
                iconSize = 22.dp,
            )
        }
    }
}
