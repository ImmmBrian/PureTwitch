package com.puretv.twitch.desktop.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.core.api.ChannelSearchResult
import com.puretv.twitch.core.model.GameInfo
import com.puretv.twitch.desktop.data.SettingsEntry
import com.puretv.twitch.desktop.data.SettingsPanel
import com.puretv.twitch.desktop.ui.Destination
import com.puretv.twitch.desktop.ui.SearchUiState
import com.puretv.twitch.desktop.ui.components.Avatar
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.LivePill
import com.puretv.twitch.desktop.ui.components.handCursor
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType

/** One result in search: a page, a setting, a category or a channel. */
sealed class SearchHit {
    abstract val key: String

    data class Page(val destination: Destination) : SearchHit() {
        override val key: String get() = "page:${destination.name}"
    }
    data class Setting(val entry: SettingsEntry) : SearchHit() {
        override val key: String get() = "setting:${entry.title}"
    }
    data class Category(val game: GameInfo) : SearchHit() {
        override val key: String get() = "category:${game.id}"
    }
    data class Channel(val result: ChannelSearchResult) : SearchHit() {
        override val key: String get() = "channel:${result.id}"
    }
}

/** How many of each kind to show. The floating bar shows fewer than the Search tab. */
data class SearchLimits(val pages: Int, val channels: Int, val categories: Int, val settings: Int)

val SEARCH_TAB_LIMITS = SearchLimits(pages = 2, channels = 20, categories = 8, settings = 6)
val SEARCH_BAR_LIMITS = SearchLimits(pages = 1, channels = 4, categories = 2, settings = 2)

/**
 * Everything the query found, in display order: a matching page first (typing
 * "browse" should go to Browse), then channels, categories and settings.
 */
fun searchHits(state: SearchUiState, hiddenTabs: List<String>, limits: SearchLimits): List<SearchHit> {
    val q = state.query.trim().lowercase()
    if (q.length < 2) return emptyList()
    val pages = Destination.entries
        .filter { it == Destination.SETTINGS || it.name !in hiddenTabs }
        .filter { it.label.lowercase().startsWith(q) }
        .take(limits.pages)
        .map { SearchHit.Page(it) }
    return pages +
        state.results.take(limits.channels).map { SearchHit.Channel(it) } +
        state.categories.take(limits.categories).map { SearchHit.Category(it) } +
        state.settings.take(limits.settings).map { SearchHit.Setting(it) }
}

internal fun sectionTitle(hit: SearchHit): String = when (hit) {
    is SearchHit.Page -> "Go to"
    is SearchHit.Channel -> "Channels"
    is SearchHit.Category -> "Categories"
    is SearchHit.Setting -> "Settings"
}

/** Where each kind of result goes. The app wires these to its navigation. */
class SearchActions(
    val openChannel: (String) -> Unit,
    val watch: (String) -> Unit,
    val openCategory: (id: String, name: String) -> Unit,
    val openSettings: (SettingsPanel) -> Unit,
    val openPage: (Destination) -> Unit,
) {
    fun run(hit: SearchHit) = when (hit) {
        is SearchHit.Page -> openPage(hit.destination)
        is SearchHit.Setting -> openSettings(hit.entry.panel)
        is SearchHit.Category -> openCategory(hit.game.id, hit.game.name)
        is SearchHit.Channel -> if (hit.result.is_live) watch(hit.result.broadcaster_login) else openChannel(hit.result.broadcaster_login)
    }
}

/** Search category art comes back as a tiny fixed size; ask for a sharper one. */
internal fun sizedBoxArt(url: String, width: Int, height: Int): String =
    url.replace("{width}", "$width").replace("{height}", "$height")
        .replace(Regex("-\\d+x\\d+\\.(jpg|png)$"), "-${width}x$height.$1")

@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title.uppercase(),
        style = PureTvType.kicker,
        color = PureTvTheme.colors.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/**
 * One result row. [selected] is the keyboard highlight in the floating bar;
 * [compact] trims the row height there.
 */
@Composable
internal fun SearchHitRow(hit: SearchHit, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        targetValue = when {
            selected -> c.secondaryContainer
            hovered -> c.surfaceHigh
            else -> Color.Transparent
        },
        animationSpec = tween(PureTvMotion.Fast),
        label = "searchHitFill",
    )
    val lead = if (compact) 36 else 48
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PureTvTheme.shapes.mdShape)
            .hoverable(interaction)
            .handCursor()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(fill)
            .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val title: String
        val subtitle: String?
        when (hit) {
            is SearchHit.Page -> {
                IconBadge(hit.destination.icon, lead)
                title = hit.destination.label
                subtitle = "Open the ${hit.destination.label} tab"
            }
            is SearchHit.Setting -> {
                IconBadge(ExpressiveIcons.Settings, lead)
                title = hit.entry.title
                subtitle = "Settings · ${hit.entry.panel.title}"
            }
            is SearchHit.Category -> {
                Box(
                    Modifier
                        .size(width = (lead * 0.75f).dp, height = lead.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.surfaceHigh),
                ) {
                    if (hit.game.boxArtUrl.isNotBlank()) {
                        AsyncImage(
                            model = sizedBoxArt(hit.game.boxArtUrl, 72, 96),
                            contentDescription = hit.game.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = (lead * 0.75f).dp, height = lead.dp),
                        )
                    }
                }
                title = hit.game.name
                subtitle = "Category"
            }
            is SearchHit.Channel -> {
                Avatar(
                    displayName = hit.result.display_name,
                    imageUrl = hit.result.thumbnail_url.takeIf { it.isNotBlank() },
                    size = lead,
                )
                title = hit.result.display_name
                subtitle = if (hit.result.is_live) hit.result.game_name.ifBlank { hit.result.title.ifBlank { "Live" } } else "Offline"
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = if (selected) c.onSecondaryContainer else c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) c.onSecondaryContainer.copy(alpha = 0.8f) else c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hit is SearchHit.Channel && hit.result.is_live) LivePill()
    }
}

@Composable
private fun IconBadge(icon: ImageVector, size: Int) {
    val c = PureTvTheme.colors
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(c.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size((size / 2).dp))
    }
}

/** Results with section headers, as a plain column (the floating bar sizes itself to this). */
@Composable
internal fun SearchHitColumn(hits: List<SearchHit>, selectedIndex: Int, compact: Boolean, onPick: (SearchHit) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        hits.forEachIndexed { i, hit ->
            if (i == 0 || sectionTitle(hits[i - 1]) != sectionTitle(hit)) SectionHeader(sectionTitle(hit))
            SearchHitRow(hit, selected = i == selectedIndex, compact = compact, onClick = { onPick(hit) })
        }
    }
}
