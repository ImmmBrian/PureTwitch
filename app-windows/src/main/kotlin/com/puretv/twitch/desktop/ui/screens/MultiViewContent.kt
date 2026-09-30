package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.core.repository.StreamRepository
import com.puretv.twitch.desktop.player.LocalStreamProxy
import com.puretv.twitch.desktop.player.VlcPlayer
import com.puretv.twitch.desktop.player.VlcPlayerView
import com.puretv.twitch.desktop.ui.FollowedRailViewModel
import com.puretv.twitch.desktop.ui.components.EditorialEmptyState
import com.puretv.twitch.desktop.ui.components.ExpressiveButton
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonSize
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveFilterChip
import com.puretv.twitch.desktop.ui.components.ExpressiveIconButton
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.formatViewerCount
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import kotlinx.coroutines.delay
import org.koin.core.Koin

/** At most this many streams at once; beyond four the tiles get too small to be worth it. */
const val MULTI_VIEW_MAX = 4

/**
 * Which channels are in multi-view and which one you're listening to. Lives at
 * the app level so the lineup survives leaving the page (the players themselves
 * stop when you leave, and start again when you come back).
 */
class MultiViewController {
    val logins = mutableStateListOf<String>()
    var audioLogin by mutableStateOf<String?>(null)

    fun add(login: String): Boolean {
        val clean = login.trim().lowercase().filter { it.isLetterOrDigit() || it == '_' }
        if (clean.isEmpty() || clean in logins || logins.size >= MULTI_VIEW_MAX) return false
        logins += clean
        if (audioLogin == null) audioLogin = clean
        return true
    }

    fun remove(login: String) {
        logins.remove(login)
        if (audioLogin == login) audioLogin = logins.firstOrNull()
    }
}

/** Lower resolution as tiles shrink: full detail is wasted on a quarter of the screen. */
internal fun multiViewQuality(tileCount: Int): StreamQuality = when {
    tileCount <= 1 -> StreamQuality.AUTO
    tileCount == 2 -> StreamQuality.P720P60
    else -> StreamQuality.P480P
}

/**
 * Up to four live streams side by side. Each tile is its own VLC player fed by
 * the same local ad-block proxy as the main player. Only one tile plays sound:
 * click a tile's video (or its speaker) to listen to it.
 */
@Composable
fun MultiViewContent(
    koin: Koin,
    controller: MultiViewController,
    onWatchFull: (String) -> Unit,
) {
    val c = PureTvTheme.colors
    val proxy = remember { koin.get<LocalStreamProxy>() }
    val streams = remember { koin.get<StreamRepository>() }
    val proxyReady by produceState(initialValue = false) {
        value = runCatching { proxy.start() }.isSuccess
    }
    // Viewer counts and titles for the header of each tile, refreshed every minute.
    val infoByLogin by produceState(initialValue = emptyMap<String, StreamInfo>(), controller.logins.toList()) {
        while (true) {
            val logins = controller.logins.toList()
            if (logins.isNotEmpty()) {
                value = runCatching { streams.streamsForChannels(logins) }.getOrNull()
                    ?.associateBy { it.userLogin.lowercase() } ?: value
            }
            delay(60_000)
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AddBar(koin = koin, controller = controller)

        val logins = controller.logins.toList()
        when {
            logins.isEmpty() -> EditorialEmptyState(
                kicker = "Multi-view",
                title = "Watch up to four streams at once",
                message = "Add channels above. Click a stream to hear it; the others stay muted.",
            )
            !proxyReady -> Text("Starting the video proxy…", style = PureTvType.data, color = c.onSurfaceVariant)
            else -> {
                val quality = multiViewQuality(logins.size)
                val rows = if (logins.size <= 2) listOf(logins) else logins.chunked(2)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { login ->
                                key(login) {
                                    MultiTile(
                                        login = login,
                                        info = infoByLogin[login],
                                        quality = quality,
                                        hasAudio = controller.audioLogin == login,
                                        onListen = { controller.audioLogin = login },
                                        onRemove = { controller.remove(login) },
                                        onWatchFull = { onWatchFull(login) },
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddBar(koin: Koin, controller: MultiViewController) {
    val c = PureTvTheme.colors
    val rail = rememberDesktopViewModel { koin.get<FollowedRailViewModel>() }
    val railState by rail.state.collectAsState()
    LaunchedEffect(Unit) { rail.refresh() }
    var text by remember { mutableStateOf("") }
    val full = controller.logins.size >= MULTI_VIEW_MAX

    fun submit() {
        if (controller.add(text)) text = ""
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .width(260.dp)
                .height(44.dp)
                .clip(PureTvTheme.shapes.pillShape)
                .background(c.surfaceHigh)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) {
                Text(if (full) "Four is the limit" else "Add a channel by name", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                enabled = !full,
                singleLine = true,
                textStyle = TextStyle(color = c.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Enter || ev.key == Key.NumPadEnter)) {
                        submit(); true
                    } else {
                        false
                    }
                },
            )
        }
        ExpressiveButton(text = "Add", onClick = { submit() }, enabled = !full && text.isNotBlank(), size = ExpressiveButtonSize.Medium)
        // Quick picks: followed channels that are live right now.
        val picks = railState.live.filter { it.login.lowercase() !in controller.logins }
        if (picks.isNotEmpty() && !full) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                picks.forEach { f ->
                    ExpressiveFilterChip(label = "+ ${f.displayName}", selected = false, onClick = { controller.add(f.login) })
                }
            }
        }
    }
}

@Composable
private fun MultiTile(
    login: String,
    info: StreamInfo?,
    quality: StreamQuality,
    hasAudio: Boolean,
    onListen: () -> Unit,
    onRemove: () -> Unit,
    onWatchFull: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    // One player per tile, released when the tile goes away.
    val player = remember(login) { VlcPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, quality) { player.play(LocalStreamProxy.streamUrl(login, quality)) }
    val status by player.status.collectAsState()
    // Only the focused tile has sound. VLC's mute is a toggle, so steer it to the wanted state.
    LaunchedEffect(hasAudio, status.isMuted) {
        if (hasAudio == status.isMuted) player.toggleMute()
    }

    Column(
        modifier
            .clip(PureTvTheme.shapes.cardShape)
            .background(c.surfaceContainer)
            .border(2.dp, if (hasAudio) c.primary else Color.Transparent, PureTvTheme.shapes.cardShape),
    ) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(info?.userName ?: login, style = MaterialTheme.typography.titleSmall, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = if (info != null) {
                    listOfNotNull(info.gameName.takeIf { it.isNotBlank() }, "${formatViewerCount(info.viewerCount)} viewers").joinToString(" · ")
                } else {
                    "Offline or loading"
                }
                Text(meta, style = PureTvType.dataSmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ExpressiveIconButton(
                icon = if (hasAudio) ExpressiveIcons.VolumeUp else ExpressiveIcons.VolumeOff,
                contentDescription = if (hasAudio) "Listening" else "Listen to this one",
                onClick = onListen,
                tint = if (hasAudio) c.primary else null,
                boxSize = 36.dp,
                iconSize = 18.dp,
            )
            ExpressiveIconButton(ExpressiveIcons.Expand, "Watch full size", onWatchFull, boxSize = 36.dp, iconSize = 18.dp)
            ExpressiveIconButton(ExpressiveIcons.Close, "Remove", onRemove, boxSize = 36.dp, iconSize = 18.dp)
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
            val err = status.error
            if (err != null && !status.isPlaying && !status.isBuffering) {
                Text(err, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            } else {
                VlcPlayerView(vlcPlayer = player, modifier = Modifier.fillMaxSize(), onClick = onListen)
            }
        }
    }
}
