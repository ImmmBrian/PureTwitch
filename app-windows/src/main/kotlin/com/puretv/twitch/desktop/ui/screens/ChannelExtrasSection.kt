package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.desktop.channel.ChannelExtras
import com.puretv.twitch.desktop.channel.ChannelPanel
import com.puretv.twitch.desktop.channel.displayHost
import com.puretv.twitch.desktop.discover.TwitchDirectoryGql
import com.puretv.twitch.desktop.platform.openInBrowser
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.ExpressivePanel
import com.puretv.twitch.desktop.ui.components.MarkdownText
import com.puretv.twitch.desktop.ui.components.expressiveClickable
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import org.koin.core.Koin

/** Twitch's own panel width; streamers design their panel art for it. */
private val PANEL_WIDTH = 320.dp

/**
 * Everything twitch.tv shows under a channel besides the bio: chat rules, then the
 * streamer's info panels (images, text, links) in a wrapping grid. Loads on its
 * own and renders nothing until there's something to show.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelExtrasSection(koin: Koin, channelLogin: String, panelColor: Color? = null, modifier: Modifier = Modifier) {
    val gql = remember { koin.get<TwitchDirectoryGql>() }
    val extras by produceState<ChannelExtras?>(initialValue = null, channelLogin) {
        value = runCatching { gql.channelExtras(channelLogin) }.getOrNull() ?: ChannelExtras()
    }
    val data = extras ?: return
    if (data.isEmpty) return
    val c = PureTvTheme.colors

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.rules.isNotEmpty()) {
            ExpressivePanel(modifier = Modifier.fillMaxWidth(), color = panelColor, padding = 24.dp) {
                Column {
                    Text("Chat rules", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
                    MarkdownText(
                        data.rules.joinToString("\n"),
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (data.panels.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                data.panels.forEach { PanelCard(it, PANEL_WIDTH, panelColor) }
            }
        }
    }
}

@Composable
private fun PanelCard(panel: ChannelPanel, width: Dp, color: Color?) {
    val c = PureTvTheme.colors
    ExpressivePanel(modifier = Modifier.width(width), color = color, padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            panel.title?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = c.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            panel.imageUrl?.let { url ->
                val link = panel.linkUrl
                AsyncImage(
                    model = url,
                    contentDescription = panel.title ?: panel.linkUrl?.let(::displayHost),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PureTvTheme.shapes.smShape)
                        .then(
                            if (link != null) {
                                Modifier
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { openInBrowser(link) }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
            panel.description?.let { MarkdownText(it, style = MaterialTheme.typography.bodyMedium) }
            // A link with no image to click gets its own button, so it's never unreachable.
            val link = panel.linkUrl
            if (link != null && panel.imageUrl == null) LinkChip(link)
        }
    }
}

@Composable
private fun LinkChip(url: String) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .expressiveClickable(
                interaction = interaction,
                onClick = { openInBrowser(url) },
                restRadius = PureTvTheme.shapes.pill,
                hoverRadius = PureTvTheme.shapes.pillMorph,
                color = c.surfaceHigh,
                hoverColor = c.surfaceHighest,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(ExpressiveIcons.OpenInNew, contentDescription = null, tint = c.primary, modifier = Modifier.size(16.dp))
        Text(displayHost(url), style = PureTvType.data, color = c.onSurface, maxLines = 1)
    }
}
