package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.channel.ClipInfo
import com.puretv.twitch.desktop.channel.formatAge
import com.puretv.twitch.desktop.discover.TwitchDirectoryGql
import com.puretv.twitch.desktop.player.DesktopPlayer
import com.puretv.twitch.desktop.player.VlcPlayerView
import com.puretv.twitch.desktop.player.formatTimecode
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveIconButton
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.ExpressiveSlider
import com.puretv.twitch.desktop.ui.components.ExpressiveSwitch
import com.puretv.twitch.desktop.ui.components.formatViewerCount
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import org.koin.core.Koin
import kotlin.math.roundToInt

/**
 * Plays one clip from a list, with previous/next and optional autoplay of the
 * next clip when this one ends. Clips are plain signed MP4s, so they play
 * straight from Twitch's CDN (no ads to strip).
 */
@Composable
fun ClipPlayerContent(koin: Koin, clips: List<ClipInfo>, startIndex: Int, onBack: () -> Unit) {
    val player = remember { koin.get<DesktopPlayer>() }
    val gql = remember { koin.get<TwitchDirectoryGql>() }
    val c = PureTvTheme.colors
    var index by remember { mutableStateOf(startIndex.coerceIn(0, (clips.size - 1).coerceAtLeast(0))) }
    var autoplay by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    if (clips.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val clip = clips[index]
    val status by player.status.collectAsState()

    LaunchedEffect(clip.slug) {
        error = null
        val url = runCatching { gql.clipPlaybackUrl(clip.slug) }.getOrNull()
        if (url == null) error = "Twitch wouldn't serve this clip. It may have been deleted." else player.play(url)
    }
    // Advance when the clip reaches its end. VLC doesn't report "finished" as a
    // stopped state, so watch the position instead, once per clip.
    LaunchedEffect(clip.slug, autoplay) {
        if (!autoplay) return@LaunchedEffect
        var advanced = false
        player.status.collect { st ->
            if (!advanced && st.durationMs > 3_000 && st.positionMs >= st.durationMs - 1_000 && index < clips.lastIndex) {
                advanced = true
                index++
            }
        }
    }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PureTvTheme.shapes.cardShape)
                .background(c.surfaceContainer)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ExpressiveIconButton(ExpressiveIcons.Back, "Back", onBack)
            Column(Modifier.weight(1f)) {
                Text(clip.title.ifBlank { "Untitled clip" }, style = MaterialTheme.typography.titleLarge, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    clip.curatorName.takeIf { it.isNotBlank() }?.let { "Clipped by $it" },
                    "${formatViewerCount(clip.viewCount)} views",
                    clip.gameName.takeIf { it.isNotBlank() },
                    formatAge(clip.createdAt)?.let { "$it ago" },
                ).joinToString("  ·  ")
                Text(meta, style = PureTvType.data, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${index + 1} of ${clips.size}", style = PureTvType.data, color = c.onSurfaceVariant)
            ExpressiveIconButton(ExpressiveIcons.Previous, "Previous clip", { if (index > 0) index-- }, enabled = index > 0)
            ExpressiveIconButton(ExpressiveIcons.Next, "Next clip", { if (index < clips.lastIndex) index++ }, enabled = index < clips.lastIndex)
        }

        // Video
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
            val err = error
            if (err != null) {
                Text(err, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            } else {
                VlcPlayerView(vlcPlayer = player, modifier = Modifier.fillMaxSize(), onClick = { player.togglePlayPause() })
            }
        }

        // Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(PureTvTheme.shapes.cardShape)
                .background(c.surfaceContainer)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ExpressiveIconButton(
                icon = if (status.isPlaying) ExpressiveIcons.Pause else ExpressiveIcons.Play,
                contentDescription = if (status.isPlaying) "Pause" else "Play",
                onClick = { player.togglePlayPause() },
                style = ExpressiveButtonStyle.Filled,
            )
            ExpressiveIconButton(
                icon = if (status.isMuted || status.volume == 0) ExpressiveIcons.VolumeOff else ExpressiveIcons.VolumeUp,
                contentDescription = if (status.isMuted) "Unmute" else "Mute",
                onClick = { player.toggleMute() },
                style = ExpressiveButtonStyle.Tonal,
            )
            ExpressiveSlider(
                value = status.volume / 100f,
                onValueChange = { player.setVolume((it * 100f).roundToInt()) },
                modifier = Modifier.width(110.dp),
            )
            var dragMs by remember(clip.slug) { mutableStateOf<Long?>(null) }
            val duration = status.durationMs.coerceAtLeast(1)
            val shown = dragMs ?: status.positionMs
            Text(formatTimecode(shown), style = PureTvType.data, color = c.onSurfaceVariant)
            ExpressiveSlider(
                value = (shown.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                onValueChange = { f -> dragMs = (f * duration).toLong() },
                onValueChangeFinished = { dragMs?.let { player.seekTo(it) }; dragMs = null },
                enabled = status.isSeekable,
                modifier = Modifier.weight(1f),
            )
            Text(formatTimecode(status.durationMs), style = PureTvType.data, color = c.onSurfaceVariant)
            Text("Autoplay", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
            ExpressiveSwitch(checked = autoplay, onCheckedChange = { autoplay = it })
        }
    }
}
