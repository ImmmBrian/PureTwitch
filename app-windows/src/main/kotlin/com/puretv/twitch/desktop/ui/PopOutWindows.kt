package com.puretv.twitch.desktop.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.puretv.twitch.desktop.createAppIcon
import com.puretv.twitch.desktop.player.DesktopPlayer
import com.puretv.twitch.desktop.player.VlcPlayerView
import com.puretv.twitch.desktop.ui.components.ExpressiveButton
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonSize
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveIconButton
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.ExpressiveSlider
import com.puretv.twitch.desktop.ui.screens.LiveChatPanel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.core.Koin
import kotlin.math.roundToInt

/**
 * The stream in its own always-on-top window, so it can sit over other apps.
 * Closing it (or "Back to app") returns the video to PureTV. The video surface
 * here is a fresh native window; the player restarts the stream on it (see
 * VlcPlayer.attachToPanel), which costs a second or two of buffering.
 */
@Composable
fun PopOutPlayerWindow(host: PlaybackHost, player: DesktopPlayer, onReturn: () -> Unit) {
    val session = host.active ?: return
    val state by session.viewModel.state.collectAsState()
    val status by remember(player) {
        player.status.map { it.copy(positionMs = 0L, durationMs = 0L) }.distinctUntilChanged()
    }.collectAsState(initial = player.status.value.copy(positionMs = 0L, durationMs = 0L))
    val icon = remember { createAppIcon() }
    val windowState = rememberWindowState(size = DpSize(640.dp, 400.dp), position = WindowPosition(Alignment.BottomEnd))
    val name = state.channel?.displayName ?: session.login

    Window(
        onCloseRequest = onReturn,
        state = windowState,
        title = "$name · PureTV",
        icon = icon,
        alwaysOnTop = true,
    ) {
        val c = PureTvTheme.colors
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                VlcPlayerView(vlcPlayer = player, modifier = Modifier.fillMaxSize(), onClick = { session.viewModel.togglePlayPause() })
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).background(c.surfaceContainer).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ExpressiveIconButton(
                    icon = if (status.isPlaying) ExpressiveIcons.Pause else ExpressiveIcons.Play,
                    contentDescription = if (status.isPlaying) "Pause" else "Play",
                    onClick = { session.viewModel.togglePlayPause() },
                    boxSize = 40.dp,
                    iconSize = 20.dp,
                )
                ExpressiveIconButton(
                    icon = if (status.isMuted || status.volume == 0) ExpressiveIcons.VolumeOff else ExpressiveIcons.VolumeUp,
                    contentDescription = if (status.isMuted) "Unmute" else "Mute",
                    onClick = { session.viewModel.toggleMute() },
                    boxSize = 40.dp,
                    iconSize = 20.dp,
                )
                ExpressiveSlider(
                    value = status.volume / 100f,
                    onValueChange = { session.viewModel.setVolume((it * 100f).roundToInt()) },
                    modifier = Modifier.width(110.dp),
                )
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                ExpressiveButton(
                    text = "Back to app",
                    onClick = onReturn,
                    style = ExpressiveButtonStyle.Tonal,
                    size = ExpressiveButtonSize.Small,
                    icon = ExpressiveIcons.PopIn,
                )
            }
        }
    }
}

/**
 * Live chat in its own window, handy on a second monitor. It's the same panel as
 * the stream page's chat, driven by the same session, so nothing is lost moving
 * between the two.
 */
@Composable
fun PopOutChatWindow(
    koin: Koin,
    host: PlaybackHost,
    onReturn: () -> Unit,
    onRequestSignIn: () -> Unit,
    onOpenChannel: (String) -> Unit,
) {
    val session = host.active ?: return
    val state by session.viewModel.state.collectAsState()
    val icon = remember { createAppIcon() }
    val windowState = rememberWindowState(size = DpSize(420.dp, 760.dp))
    val name = state.channel?.displayName ?: session.login

    Window(
        onCloseRequest = onReturn,
        state = windowState,
        title = "Chat · $name",
        icon = icon,
    ) {
        Box(Modifier.fillMaxSize().background(PureTvTheme.colors.surfaceLowest).padding(8.dp)) {
            LiveChatPanel(
                koin = koin,
                viewModel = session.viewModel,
                onClose = onReturn,
                onPopOut = onReturn,
                poppedOut = true,
                onFocusChanged = { host.chatInputFocused = it },
                onRequestSignIn = onRequestSignIn,
                onOpenChannel = onOpenChannel,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
