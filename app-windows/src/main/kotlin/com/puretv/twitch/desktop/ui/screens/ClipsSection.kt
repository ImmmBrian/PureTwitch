package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.channel.ClipInfo
import com.puretv.twitch.desktop.channel.ClipPeriod
import com.puretv.twitch.desktop.channel.formatClipDuration
import com.puretv.twitch.desktop.discover.TwitchDirectoryGql
import com.puretv.twitch.desktop.channel.formatAge
import com.puretv.twitch.desktop.ui.components.CoverImage
import com.puretv.twitch.desktop.ui.components.ExpressiveButton
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveCard
import com.puretv.twitch.desktop.ui.components.ExpressiveChipRow
import com.puretv.twitch.desktop.ui.components.formatViewerCount
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import kotlinx.coroutines.launch
import org.koin.core.Koin

private val CLIP_CARD_WIDTH = 260.dp

/**
 * A channel's most-watched clips for a chosen period, most views first, with
 * "Load more". Clicking one plays it, and the player can step through the rest
 * of this list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipsSection(
    koin: Koin,
    channelLogin: String,
    onPlayClip: (clips: List<ClipInfo>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gql = remember { koin.get<TwitchDirectoryGql>() }
    val c = PureTvTheme.colors
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf(ClipPeriod.WEEK) }
    var clips by remember(channelLogin, period) { mutableStateOf<List<ClipInfo>>(emptyList()) }
    var cursor by remember(channelLogin, period) { mutableStateOf<String?>(null) }
    var loading by remember(channelLogin, period) { mutableStateOf(true) }
    var failed by remember(channelLogin, period) { mutableStateOf(false) }

    LaunchedEffect(channelLogin, period) {
        loading = true
        failed = false
        runCatching { gql.channelClips(channelLogin, period) }
            .onSuccess { page -> clips = page.clips; cursor = page.cursor }
            .onFailure { failed = true }
        loading = false
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Clips", style = MaterialTheme.typography.headlineMedium, color = c.onSurface, modifier = Modifier.weight(1f))
            ExpressiveChipRow(
                options = ClipPeriod.entries,
                selected = period,
                label = { it.label },
                onSelect = { period = it },
            )
        }
        Spacer(Modifier.height(16.dp))
        when {
            loading && clips.isEmpty() -> CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            failed && clips.isEmpty() -> Text("Couldn't load clips.", style = MaterialTheme.typography.bodyMedium, color = c.outline)
            clips.isEmpty() -> Text("No clips from this period.", style = MaterialTheme.typography.bodyMedium, color = c.outline)
            else -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    clips.forEachIndexed { i, clip ->
                        ClipCard(clip, onClick = { onPlayClip(clips, i) })
                    }
                }
                val next = cursor
                if (next != null) {
                    Spacer(Modifier.height(12.dp))
                    ExpressiveButton(
                        text = if (loading) "Loading…" else "Load more clips",
                        onClick = {
                            if (!loading) scope.launch {
                                loading = true
                                runCatching { gql.channelClips(channelLogin, period, next) }
                                    .onSuccess { page -> clips = (clips + page.clips).distinctBy { it.slug }; cursor = page.cursor }
                                loading = false
                            }
                        },
                        style = ExpressiveButtonStyle.Tonal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipCard(clip: ClipInfo, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    ExpressiveCard(onClick = onClick, modifier = Modifier.width(CLIP_CARD_WIDTH)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(PureTvTheme.shapes.thumbShape)) {
                CoverImage(clip.thumbnailUrl.ifBlank { null }, clip.slug, clip.title, Modifier.fillMaxSize())
                ClipBadge(formatClipDuration(clip.durationSeconds), Modifier.align(Alignment.TopStart).padding(8.dp))
                ClipBadge("${formatViewerCount(clip.viewCount)} views", Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(clip.title.ifBlank { "Untitled clip" }, style = MaterialTheme.typography.titleSmall, color = c.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                clip.curatorName.takeIf { it.isNotBlank() }?.let { "by $it" },
                formatAge(clip.createdAt)?.let { "$it ago" },
            ).joinToString("  ·  ")
            if (meta.isNotEmpty()) Text(meta, style = PureTvType.dataSmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun ClipBadge(text: String, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    Text(
        text,
        style = PureTvType.dataSmall,
        color = c.onSurface,
        modifier = modifier
            .clip(PureTvTheme.shapes.smShape)
            .background(c.surfaceLowest.copy(alpha = 0.75f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
