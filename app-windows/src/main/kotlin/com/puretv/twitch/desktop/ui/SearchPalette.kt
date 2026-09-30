package com.puretv.twitch.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.window.DialogWindow
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.screens.SEARCH_BAR_LIMITS
import com.puretv.twitch.desktop.ui.screens.SearchActions
import com.puretv.twitch.desktop.ui.screens.SearchHit
import com.puretv.twitch.desktop.ui.screens.SearchHitColumn
import com.puretv.twitch.desktop.ui.screens.searchHits
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import kotlinx.coroutines.delay
import org.koin.core.Koin
import java.awt.Dialog
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.awt.Window as AwtWindow
import kotlin.math.roundToInt

private const val PALETTE_WIDTH = 640
private const val PALETTE_MAX_HEIGHT = 640

/** True for Ctrl+Shift+Space, the key that opens the floating search bar. */
internal fun isSearchHotkey(e: KeyEvent): Boolean =
    e.id == KeyEvent.KEY_PRESSED && e.keyCode == KeyEvent.VK_SPACE && e.isControlDown && e.isShiftDown && !e.isAltDown

/**
 * Registers Ctrl+Shift+Space for the whole app (main window and pop-outs).
 * [onToggle] opens the search bar, or closes it if it's already open.
 */
@Composable
internal fun SearchHotkey(onToggle: () -> Unit) {
    val latest by rememberUpdatedState(onToggle)
    DisposableEffect(Unit) {
        val dispatcher = KeyEventDispatcher { e ->
            if (isSearchHotkey(e)) {
                latest()
                true
            } else {
                false
            }
        }
        val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        kfm.addKeyEventDispatcher(dispatcher)
        onDispose { kfm.removeKeyEventDispatcher(dispatcher) }
    }
}

/**
 * The floating search bar. A small borderless window near the top of [anchor]:
 * a real OS window so it shows above the video, which Compose can't draw over.
 * Arrow keys pick a result, Enter opens it, Esc or clicking away closes it.
 */
@Composable
internal fun SearchPalette(
    koin: Koin,
    anchor: AwtWindow,
    hiddenTabs: List<String>,
    actions: SearchActions,
    onDismiss: () -> Unit,
) {
    val viewModel = rememberDesktopViewModel { koin.get<SearchViewModel>() }
    val state by viewModel.state.collectAsState()
    val hits = remember(state, hiddenTabs) { searchHits(state, hiddenTabs, SEARCH_BAR_LIMITS) }
    var selected by remember { mutableStateOf(0) }
    LaunchedEffect(hits) { selected = 0 }
    val dismiss by rememberUpdatedState(onDismiss)

    fun pick(hit: SearchHit) {
        dismiss()
        actions.run(hit)
    }

    DialogWindow(
        create = {
            ComposeDialog(anchor, Dialog.ModalityType.MODELESS).apply {
                isUndecorated = true
                isTransparent = true
                isResizable = false
                title = "Search"
                setSize(PALETTE_WIDTH, 120)
                setLocation(anchor.x + (anchor.width - PALETTE_WIDTH) / 2, anchor.y + 88)
            }
        },
        dispose = { it.dispose() },
        onPreviewKeyEvent = { e ->
            if (e.type != KeyEventType.KeyDown) return@DialogWindow false
            when (e.key) {
                Key.Escape -> { dismiss(); true }
                Key.DirectionDown -> { if (hits.isNotEmpty()) selected = (selected + 1) % hits.size; true }
                Key.DirectionUp -> { if (hits.isNotEmpty()) selected = (selected - 1 + hits.size) % hits.size; true }
                Key.Enter, Key.NumPadEnter -> { hits.getOrNull(selected)?.let { pick(it) }; true }
                else -> false
            }
        },
    ) {
        val dialog = window
        // Clicking anywhere else closes it, like the Windows search box.
        DisposableEffect(dialog) {
            val listener = object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent?) {}
                override fun windowLostFocus(e: WindowEvent?) { dismiss() }
            }
            dialog.addWindowFocusListener(listener)
            onDispose { dialog.removeWindowFocusListener(listener) }
        }

        val c = PureTvTheme.colors
        val density = LocalDensity.current
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            dialog.toFront()
            // The window needs a moment to take focus before the field can.
            repeat(3) {
                delay(40)
                runCatching { focus.requestFocus() }
            }
        }

        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .onSizeChanged { px ->
                        // Fit the window to the card so there's no invisible area to click.
                        val h = (px.height / density.density).roundToInt().coerceIn(64, PALETTE_MAX_HEIGHT)
                        if (dialog.height != h) dialog.setSize(PALETTE_WIDTH, h)
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.surfaceContainer)
                    .border(1.dp, c.outlineVariant, RoundedCornerShape(24.dp))
                    .padding(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.surfaceHigh)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(ExpressiveIcons.Search, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    Box(Modifier.weight(1f)) {
                        if (state.query.isEmpty()) {
                            Text(
                                "Search channels, categories and settings",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                                color = c.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            value = state.query,
                            onValueChange = viewModel::onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, color = c.onSurface),
                            cursorBrush = SolidColor(c.primary),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (state.isSearching) {
                        CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    }
                }
                when {
                    hits.isNotEmpty() -> SearchHitColumn(hits, selectedIndex = selected, compact = true, onPick = ::pick)
                    state.query.trim().length >= 2 && !state.isSearching -> Text(
                        if (state.error != null) "Search failed. Check your connection." else "Nothing found.",
                        style = PureTvType.data,
                        color = c.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}
