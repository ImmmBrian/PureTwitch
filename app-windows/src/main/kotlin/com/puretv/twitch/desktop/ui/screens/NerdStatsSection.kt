package com.puretv.twitch.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.desktop.channel.TrackerSummary
import com.puretv.twitch.desktop.channel.TwitchTrackerClient
import com.puretv.twitch.desktop.channel.chatStats
import com.puretv.twitch.desktop.channel.formatDecimal
import com.puretv.twitch.desktop.channel.formatStat
import com.puretv.twitch.desktop.channel.sessionViewers
import com.puretv.twitch.desktop.data.ViewPrefsStore
import com.puretv.twitch.desktop.data.ViewerHistoryStore
import com.puretv.twitch.desktop.player.LocalStreamProxy
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.ExpressivePanel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import kotlinx.coroutines.delay
import org.koin.core.Koin

/**
 * "Stats for nerds": a collapsible card of real numbers only (no earnings or
 * sub guesses). TwitchTracker's last 30 days, this stream's viewer curve (from
 * PureTV's own sampling), live chat analytics, and how much PureTV's ad blocker
 * has done. Each block hides itself when it has nothing real to show.
 *
 * @param chat the live chat buffer when a stream is open (stream page), or null
 *   on the channel page, where there is no chat to analyse.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NerdStatsSection(
    koin: Koin,
    channelLogin: String,
    startedAtIso: String?,
    currentViewers: Int?,
    chat: List<ChatMessage>?,
    panelColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    val prefsStore = remember { koin.get<ViewPrefsStore>() }
    val prefs by prefsStore.prefs.collectAsState()
    val look = prefs.look
    if (!look.showStats) return
    val c = PureTvTheme.colors
    val expanded = look.statsExpanded

    ExpressivePanel(modifier = modifier.fillMaxWidth(), color = panelColor, padding = 20.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PureTvTheme.shapes.smShape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { prefsStore.setLook { it.copy(statsExpanded = !it.statsExpanded) } }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Stats for nerds", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
                    Text(
                        if (expanded) "Real numbers only. Twitch keeps subs and earnings private." else "Last 30 days, this stream, chat and ad blocking",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) ExpressiveIcons.ExpandLess else ExpressiveIcons.ExpandMore,
                    contentDescription = if (expanded) "Collapse stats" else "Expand stats",
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }

            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                // Only fetch/tick while open, so a collapsed card costs nothing.
                ExpandedStats(koin, channelLogin, startedAtIso, currentViewers, chat)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedStats(
    koin: Koin,
    channelLogin: String,
    startedAtIso: String?,
    currentViewers: Int?,
    chat: List<ChatMessage>?,
) {
    val c = PureTvTheme.colors
    val tracker = remember { koin.get<TwitchTrackerClient>() }
    val historyStore = remember { koin.get<ViewerHistoryStore>() }
    val proxy = remember { koin.get<LocalStreamProxy>() }

    val summary by produceState<Result<TrackerSummary?>?>(initialValue = null, channelLogin) {
        value = Result.success(tracker.summary(channelLogin))
    }
    // Re-read the locally sampled viewer history and the chat clock every 30s.
    val tick by produceState(initialValue = 0L, channelLogin) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val session = remember(tick, startedAtIso) {
        historyStore.get(channelLogin)?.let { sessionViewers(it.samples, startedAtIso) }
    }
    val chatNow = remember(tick, chat) { chat?.let { chatStats(it) } }

    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // ── Last 30 days ──
        StatGroup("Last 30 days", "via TwitchTracker") {
            when (val r = summary) {
                null -> Text("Loading…", style = PureTvType.data, color = c.onSurfaceVariant)
                else -> {
                    val s = r.getOrNull()
                    if (s == null) {
                        Text("TwitchTracker has no data for this channel yet.", style = MaterialTheme.typography.bodyMedium, color = c.outline)
                    } else {
                        TileFlow {
                            s.rank?.let { StatTile("Rank", "#${formatStat(it)}", "on Twitch") }
                            StatTile("Avg viewers", formatStat(s.avgViewers))
                            StatTile("Peak viewers", formatStat(s.maxViewers))
                            StatTile("Hours streamed", formatDecimal(s.hoursStreamed))
                            StatTile("Hours watched", formatStat(s.hoursWatched))
                            StatTile("New followers", "+" + formatStat(s.followersGained), "${formatDecimal(s.followersPerDay)} a day")
                            s.followersPerStreamHour?.let { StatTile("Follows per live hour", formatDecimal(it)) }
                            s.followersTotal?.let { StatTile("Total followers", formatStat(it)) }
                            if (currentViewers != null && s.avgViewers > 0) {
                                val diff = (currentViewers - s.avgViewers) * 100.0 / s.avgViewers
                                StatTile("Now vs average", (if (diff >= 0) "+" else "") + "%.0f%%".format(diff), "${formatStat(currentViewers.toLong())} watching")
                            }
                        }
                    }
                }
            }
        }

        // ── This stream ──
        if (session != null) {
            StatGroup("This stream", "sampled by PureTV while you watch") {
                TileFlow {
                    StatTile("Peak", formatStat(session.peak.toLong()))
                    StatTile("Average", formatStat(session.average.toLong()))
                    StatTile("Low", formatStat(session.low.toLong()))
                    val change = session.current - session.samples.first()
                    StatTile("Since you joined", (if (change >= 0) "+" else "") + formatStat(change.toLong()))
                }
                Spacer(Modifier.height(12.dp))
                LineSparkline(session.samples, Modifier.fillMaxWidth().height(56.dp))
            }
        }

        // ── Chat ──
        if (chatNow != null) {
            StatGroup("Chat", "last ${chatNow.windowSeconds / 60}m ${chatNow.windowSeconds % 60}s of messages") {
                TileFlow {
                    StatTile("Messages / min", formatDecimal(chatNow.messagesPerMinute))
                    StatTile("Active chatters", formatStat(chatNow.uniqueChatters.toLong()))
                    StatTile("Subs chatting", "%.0f%%".format(chatNow.subscriberShare * 100))
                    StatTile("Mods active", chatNow.moderatorsActive.toString())
                }
                if (chatNow.topEmotes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Top emotes: " + chatNow.topEmotes.joinToString("  ·  ") { (name, n) -> "$name ×$n" },
                        style = PureTvType.data,
                        color = c.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ── Ad blocking ──
        val swaps = remember(tick) { proxy.adSwapsSinceLaunch }
        val stripped = remember(tick) { proxy.adSegmentsStrippedSinceLaunch }
        StatGroup("Ad blocking", "since PureTV opened, all streams") {
            TileFlow {
                // Live playlists refresh about every 2 seconds, so each swap or cut is ~2s of ad avoided.
                StatTile("Ad time skipped", "~" + formatDuration((swaps + stripped) * 2L))
                StatTile("Clean swaps", formatStat(swaps.toLong()), "ad playlist replaced")
                StatTile("Segments cut", formatStat(stripped.toLong()), "when no clean copy existed")
            }
        }
    }
}

private fun formatDuration(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
    seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds}s"
}

@Composable
private fun StatGroup(title: String, source: String, content: @Composable () -> Unit) {
    val c = PureTvTheme.colors
    Column {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title.uppercase(), style = PureTvType.kicker, color = c.onSurface)
            Text(source, style = PureTvType.dataSmall, color = c.outline)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun StatTile(label: String, value: String, note: String? = null) {
    val c = PureTvTheme.colors
    Column(
        Modifier
            .width(150.dp)
            .clip(PureTvTheme.shapes.smShape)
            .background(c.surfaceHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = PureTvType.dataSmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleLarge, color = c.onSurface, maxLines = 1)
        if (note != null) Text(note, style = PureTvType.dataSmall, color = c.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A plain line of viewers over this stream, scaled between its own low and high. */
@Composable
private fun LineSparkline(points: List<Int>, modifier: Modifier) {
    val color = PureTvTheme.colors.primary
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val lo = points.min().toFloat()
        val hi = points.max().toFloat().let { if (it == lo) lo + 1f else it }
        val stepX = size.width / (points.size - 1)
        fun y(v: Int) = size.height - (v - lo) / (hi - lo) * size.height
        for (i in 1 until points.size) {
            drawLine(
                color = color,
                start = Offset((i - 1) * stepX, y(points[i - 1])),
                end = Offset(i * stepX, y(points[i])),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
