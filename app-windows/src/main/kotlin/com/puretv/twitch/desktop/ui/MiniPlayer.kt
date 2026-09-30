package com.puretv.twitch.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.desktop.ui.components.ExpressiveSlider
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.awt.Cursor
import kotlin.math.roundToInt
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
    onDrag: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
) {
    val session = host.active ?: return
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val state by session.viewModel.state.collectAsState()
    // Only the fields this bar shows, so position ticks don't recompose it.
    val status by remember(player) {
        player.status.map { Triple(it.isPlaying, it.volume, it.isMuted) }.distinctUntilChanged()
    }.collectAsState(initial = Triple(player.status.value.isPlaying, player.status.value.volume, player.status.value.isMuted))
    val (isPlaying, volume, isMuted) = status
    val prefs by host.prefsStore.prefs.collectAsState()
    val miniSize = prefs.miniSizeEnum
    val c = PureTvTheme.colors
    val shape = PureTvTheme.shapes.cardShape

    Column(
        modifier
            .width(miniSize.widthDp.dp)
            .shadow(16.dp, shape)
            .clip(shape)
            .background(c.surfaceHigh),
    ) {
        // The title bar is the drag handle. It's Compose, so it gets the drag events;
        // the video below is a native window and would swallow them.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .then(
                    if (onDrag != null) {
                        Modifier
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.MOVE_CURSOR)))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragEnd = { dragEnd?.invoke() },
                                    onDragCancel = { dragEnd?.invoke() },
                                ) { change, amount ->
                                    change.consume()
                                    drag?.invoke(amount)
                                }
                            }
                    } else {
                        Modifier
                    },
                )
                .padding(start = if (onDrag != null) 6.dp else 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (onDrag != null) {
                Icon(
                    ExpressiveIcons.DragHandle,
                    contentDescription = "Drag to move",
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
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
                icon = ExpressiveIcons.Resize,
                contentDescription = "Size: ${miniSize.label}. Click for ${miniSize.next().label.lowercase()}",
                onClick = { host.prefsStore.setMiniSize(miniSize.next()) },
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
            // Scrolling over the picture changes the volume.
            onWheel = { w -> session.viewModel.setVolume((volume - (w * 5).roundToInt()).coerceIn(0, 100)) },
        )
        // Sound controls, so you never have to expand the player just to turn it down.
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveIconButton(
                icon = if (isMuted || volume == 0) ExpressiveIcons.VolumeOff else ExpressiveIcons.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                onClick = { session.viewModel.toggleMute() },
                boxSize = 36.dp,
                iconSize = 18.dp,
            )
            ExpressiveSlider(
                value = if (isMuted) 0f else volume / 100f,
                onValueChange = { session.viewModel.setVolume((it * 100f).roundToInt()) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            if (state.currentQuality == StreamQuality.AUDIO_ONLY) {
                Text("AUDIO ONLY", style = PureTvType.dataSmall, color = c.primary, modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
}

/**
 * Hosts a [MiniPlayer] inside whatever area it's placed in (fill that area with
 * this), starting in the bottom-right corner. Drag the title bar to move it; it
 * can't be dragged out of the area, and it keeps its spot (see
 * [PlaybackHost.miniOffset]) as you move between pages.
 */
@Composable
fun MiniPlayerDock(
    host: PlaybackHost,
    player: DesktopPlayer,
    onExpand: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    expandIsScrollTop: Boolean = false,
    edgePadding: Dp = 20.dp,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val padPx = with(LocalDensity.current) { edgePadding.toPx() }
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        var size by remember { mutableStateOf(IntSize.Zero) }

        // The offset is measured from the bottom-right corner, so it only goes negative.
        fun clamp(o: Offset): Offset {
            val minX = -(maxW - 2 * padPx - size.width).coerceAtLeast(0f)
            val minY = -(maxH - 2 * padPx - size.height).coerceAtLeast(0f)
            return Offset(o.x.coerceIn(minX, 0f), o.y.coerceIn(minY, 0f))
        }
        // On release it glides to the nearest corner, so it never sits awkwardly mid-page.
        fun nearestCorner(o: Offset): Offset {
            val c = clamp(o)
            val minX = -(maxW - 2 * padPx - size.width).coerceAtLeast(0f)
            val minY = -(maxH - 2 * padPx - size.height).coerceAtLeast(0f)
            return Offset(if (c.x < minX / 2) minX else 0f, if (c.y < minY / 2) minY else 0f)
        }
        var dragging by remember { mutableStateOf(false) }
        val target = clamp(host.miniOffset)
        val shown by animateOffsetAsState(
            targetValue = target,
            animationSpec = if (dragging) snap<Offset>() else spring<Offset>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "miniSnap",
        )

        MiniPlayer(
            host = host,
            player = player,
            onExpand = onExpand,
            onClose = onClose,
            expandIsScrollTop = expandIsScrollTop,
            onDrag = { delta ->
                dragging = true
                host.miniOffset = clamp(clamp(host.miniOffset) + delta)
            },
            onDragEnd = {
                dragging = false
                host.miniOffset = nearestCorner(host.miniOffset)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(edgePadding)
                .offset { IntOffset(shown.x.roundToInt(), shown.y.roundToInt()) }
                .onSizeChanged { size = it },
        )
    }
}
