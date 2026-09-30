package com.puretv.twitch.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.data.ViewPrefsStore
import com.puretv.twitch.desktop.player.DesktopPlayer
import com.puretv.twitch.desktop.player.VlcPlayerView
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

/** The live stream currently open, full-size or docked in the mini player. */
class ActiveStream(val login: String, val viewModel: StreamViewModel)

/**
 * Owns the live stream session and the ONE video surface for it.
 *
 * WHY THIS EXISTS: the video is a heavyweight AWT Canvas (see VlcPlayerView). If
 * it leaves composition, its native window is destroyed, and libVLC does not
 * re-target a running stream onto a new window (mpv must tear its context down).
 * So moving the stream from the page into a corner can't be done by composing a
 * second player view. Instead the surface is composed exactly once, at the app
 * root ([VideoOverlay]), and placed over whichever [VideoSlot] is registered:
 * the stream page's video area, or the mini player's. Moving between them is a
 * setBounds on the same native window, so playback never restarts.
 *
 * It also owns the [StreamViewModel], which used to live and die with the stream
 * screen. That's what keeps playback and chat running while you browse.
 */
class PlaybackHost(private val koin: Koin) {
    /** Mini player size and the "lower quality while docked" switch live here. */
    internal val prefsStore: ViewPrefsStore = koin.get()

    var active by mutableStateOf<ActiveStream?>(null)
        private set

    /** Open [login], replacing any other stream. Reopening the current one is a no-op. */
    fun open(login: String) {
        if (active?.login.equals(login, ignoreCase = true)) return
        // Dispose the old session synchronously BEFORE the new one exists: onCleared
        // stops the player and closes chat, and doing that after the new session
        // started would stop the new stream.
        active?.viewModel?.dispose()
        active = ActiveStream(login, koin.get<StreamViewModel> { parametersOf(login) })
    }

    /**
     * Where the user dragged the mini player, in px from its default bottom-right
     * spot (so both values are <= 0). Shared by every page that shows the mini
     * player, so it stays where you put it as you move around the app.
     */
    var miniOffset by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)

    /** Stop playback and close the session (mini player's X, or before a VOD takes the player). */
    fun close() {
        active?.viewModel?.dispose()
        active = null
    }

    // ── Slots ────────────────────────────────────────────────────────────────

    internal class Slot(
        val bounds: Rect,
        val onActivity: () -> Unit,
        val onClick: () -> Unit,
        val onWheel: (Float) -> Unit,
    )

    /** The stream page's video area. Wins over [miniSlot] when both are registered. */
    internal var fullSlot by mutableStateOf<Slot?>(null)
    internal var miniSlot by mutableStateOf<Slot?>(null)

    internal val target: Slot? get() = fullSlot ?: miniSlot
}

/** Which slot a [VideoSlot] registers as. */
enum class SlotKind { FULL, MINI }

/**
 * Marks where the video should appear. Renders only a black backdrop; the real
 * surface is positioned over it by [VideoOverlay]. Unregisters on dispose so the
 * video moves to the other slot (or hides) as soon as this one leaves the screen.
 */
@Composable
fun VideoSlot(
    host: PlaybackHost,
    kind: SlotKind,
    modifier: Modifier = Modifier,
    onActivity: () -> Unit = {},
    onClick: () -> Unit = {},
    onWheel: (Float) -> Unit = {},
) {
    val activity by rememberUpdatedState(onActivity)
    val click by rememberUpdatedState(onClick)
    val wheel by rememberUpdatedState(onWheel)

    fun register(bounds: Rect) {
        val slot = PlaybackHost.Slot(bounds, { activity() }, { click() }, { w -> wheel(w) })
        val current = if (kind == SlotKind.FULL) host.fullSlot else host.miniSlot
        // Only publish when the rectangle moved; onGloballyPositioned fires on every layout pass.
        if (current?.bounds == bounds) return
        if (kind == SlotKind.FULL) host.fullSlot = slot else host.miniSlot = slot
    }

    DisposableEffect(host, kind) {
        onDispose {
            if (kind == SlotKind.FULL) host.fullSlot = null else host.miniSlot = null
        }
    }

    Box(
        modifier
            .background(Color.Black)
            .onGloballyPositioned { coords ->
                val b = coords.boundsInWindow()
                if (b.width > 0f && b.height > 0f) register(b)
            },
    )
}

/**
 * The single, app-lifetime video surface. Place it as the LAST child of a Box
 * that fills the window, so window coordinates and this Box's coordinates match.
 *
 * With no slot registered (a stream still loading, or a moment between pages) it
 * shrinks to nothing instead of leaving composition, which keeps the native
 * window, and the playing stream, alive.
 */
@Composable
fun VideoOverlay(host: PlaybackHost, player: DesktopPlayer) {
    if (host.active == null) return
    val slot = host.target
    val density = LocalDensity.current
    val bounds = slot?.bounds
    val sizeModifier = if (bounds != null) {
        with(density) {
            Modifier
                .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                .size(bounds.width.toDp(), bounds.height.toDp())
        }
    } else {
        Modifier.size(0.dp)
    }
    // Read the slot at event time, not composition time, so a click always goes
    // to whichever slot is showing the video right now.
    val latest by rememberUpdatedState(slot)
    val callbacks = remember {
        Triple(
            { latest?.onActivity?.invoke(); Unit },
            { latest?.onClick?.invoke(); Unit },
            { w: Float -> latest?.onWheel?.invoke(w); Unit },
        )
    }
    Box(sizeModifier) {
        VlcPlayerView(
            vlcPlayer = player,
            modifier = Modifier.fillMaxSize(),
            onUserActivity = callbacks.first,
            onClick = callbacks.second,
            onWheel = callbacks.third,
        )
    }
}
