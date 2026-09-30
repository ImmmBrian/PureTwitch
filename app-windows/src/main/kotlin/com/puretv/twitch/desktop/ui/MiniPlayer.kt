package com.puretv.twitch.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.player.DesktopPlayer
import com.puretv.twitch.desktop.ui.components.ExpressiveIconButton
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.formatViewerCount
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

val MINI_PLAYER_WIDTH: Dp = 400.dp

/**
 * The docked stream: a title bar with controls above a 16:9 [VideoSlot].
 *
 * The controls sit in their own bar, never over the picture, because the video is
 * a native window that paints above anything Compose draws (see [PlaybackHost]).
 * Clicking the picture itself also expands.
 *
 * @param onClose null hides the close button (on the stream page, where docking
 *   is just "scrolled down" and closing would be surprising).
 */
@Composable
fun MiniPlayer(
    host: PlaybackHost,
    player: DesktopPlayer,
    onExpand: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    expandIsScrollTop: Boolean = false,
) {
    val session = host.active ?: return
    val state by session.viewModel.state.collectAsState()
    val isPlaying by remember(player) { player.status.map { it.isPlaying }.distinctUntilChanged() }
        .collectAsState(initial = player.status.value.isPlaying)
    val c = PureTvTheme.colors
    val shape = PureTvTheme.shapes.cardShape

    Column(
        modifier
            .width(MINI_PLAYER_WIDTH)
            .shadow(16.dp, shape)
            .clip(shape)
            .background(c.surfaceHigh),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.channel?.displayName ?: session.login,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.streamInfo?.let { info ->
                    Text(
                        listOfNotNull(info.gameName.takeIf { it.isNotBlank() }, "${formatViewerCount(info.viewerCount)} viewers")
                            .joinToString(" · "),
                        style = PureTvType.dataSmall,
                        color = c.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ExpressiveIconButton(
                icon = if (isPlaying) ExpressiveIcons.Pause else ExpressiveIcons.Play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClick = { session.viewModel.togglePlayPause() },
                boxSize = 40.dp,
                iconSize = 20.dp,
            )
            ExpressiveIconButton(
                icon = if (expandIsScrollTop) ExpressiveIcons.ScrollTop else ExpressiveIcons.Expand,
                contentDescription = if (expandIsScrollTop) "Back to the player" else "Open full player",
                onClick = onExpand,
                boxSize = 40.dp,
                iconSize = 20.dp,
            )
            if (onClose != null) {
                ExpressiveIconButton(
                    icon = ExpressiveIcons.Close,
                    contentDescription = "Close player",
                    onClick = onClose,
                    boxSize = 40.dp,
                    iconSize = 20.dp,
                )
            }
        }
        VideoSlot(
            host = host,
            kind = SlotKind.MINI,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            onClick = onExpand,
        )
    }
}
