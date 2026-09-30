package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.puretv.twitch.desktop.data.BackupManager
import com.puretv.twitch.desktop.data.MiniSize
import com.puretv.twitch.desktop.data.ViewPrefsStore
import com.puretv.twitch.desktop.ui.components.ExpressiveIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.platform.openInBrowser
import com.puretv.twitch.desktop.ui.SettingsViewModel
import com.puretv.twitch.desktop.ui.components.ExpressiveButton
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveDivider
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.ExpressivePanel
import com.puretv.twitch.desktop.ui.components.ExpressiveSwitch
import com.puretv.twitch.desktop.ui.components.PageTitle
import com.puretv.twitch.desktop.ui.components.SegmentedToggle
import com.puretv.twitch.desktop.ui.components.expressiveClickable
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import com.puretv.twitch.desktop.ui.theme.ShapeIntensity
import com.puretv.twitch.desktop.ui.theme.ThemeVariant
import com.puretv.twitch.desktop.ui.theme.themeColors
import com.puretv.twitch.desktop.update.UpdateInfo
import com.puretv.twitch.desktop.update.UpdateManager
import com.puretv.twitch.desktop.update.UpdateState
import com.puretv.twitch.desktop.update.resolveReleaseUrl
import org.koin.core.Koin

@Composable
fun SettingsContent(koin: Koin, onExit: () -> Unit) {
    val viewModel = rememberDesktopViewModel { koin.get<SettingsViewModel>() }
    val state by viewModel.state.collectAsState()
    val updateManager = remember { koin.get<UpdateManager>() }
    val updateState by updateManager.state.collectAsState()
    // The colour and shape pickers write straight to the store: they are pure
    // presentation settings with no ViewModel-side validation, same class of
    // write as SettingsViewModel's own setters underneath.
    val settingsStore = remember { koin.get<DesktopSettingsStore>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 36.dp, start = 32.dp, end = 32.dp, bottom = 40.dp),
    ) {
        PageTitle("Settings")
        Spacer(Modifier.height(28.dp))

        ColourPanel(
            currentVariant = ThemeVariant.fromKey(state.settings.theme),
            onSelect = { variant -> settingsStore.updateSettings { it.copy(theme = variant.key) } },
        )
        Spacer(Modifier.height(16.dp))

        ShapeAndMotionPanel(
            currentIntensity = ShapeIntensity.fromKey(state.settings.shapeIntensity),
            onSelect = { intensity -> settingsStore.updateSettings { it.copy(shapeIntensity = intensity.key) } },
        )
        Spacer(Modifier.height(16.dp))

        val lookStore = remember { koin.get<ViewPrefsStore>() }
        val lookPrefs by lookStore.prefs.collectAsState()
        PersonalizePanel(store = lookStore, look = lookPrefs.look)
        Spacer(Modifier.height(16.dp))

        PlaybackPanel(
            selectedQuality = StreamQuality.entries.firstOrNull {
                state.settings.preferredQuality.equals(it.name, ignoreCase = true)
            } ?: StreamQuality.AUTO,
            onSelectQuality = viewModel::setPreferredQuality,
            animateEmotes = state.settings.animateEmotes,
            onAnimateEmotesChange = viewModel::setAnimateEmotes,
        )
        Spacer(Modifier.height(16.dp))

        val prefsStore = remember { koin.get<ViewPrefsStore>() }
        val prefs by prefsStore.prefs.collectAsState()

        BrowsingPanel(
            oneClickWatch = prefs.oneClickWatch,
            onOneClickWatchChange = prefsStore::setOneClickWatch,
            lowQualityWhenDocked = prefs.lowQualityWhenDocked,
            onLowQualityChange = prefsStore::setLowQualityWhenDocked,
            miniSize = prefs.miniSizeEnum,
            onMiniSizeChange = prefsStore::setMiniSize,
        )
        Spacer(Modifier.height(16.dp))

        ChatFiltersPanel(
            ignored = prefs.ignoredUsers,
            onUnignore = { prefsStore.setIgnored(it, false) },
            onIgnore = { prefsStore.setIgnored(it, true) },
            highlightWords = prefs.highlightWords,
            onHighlightWordsChange = prefsStore::setHighlightWords,
        )
        Spacer(Modifier.height(16.dp))

        BackupPanel(onExit = onExit)
        Spacer(Modifier.height(16.dp))

        AdBlockPanel()
        Spacer(Modifier.height(16.dp))

        AccountPanel(
            isLoggedIn = state.isLoggedIn,
            username = state.loginUsername,
            onLogOut = viewModel::logOut,
        )
        Spacer(Modifier.height(16.dp))

        AboutPanel(
            version = updateManager.currentVersion,
            updateState = updateState,
            onCheckForUpdates = { updateManager.checkForUpdates(force = true) },
            onDownloadAndInstall = { info -> updateManager.downloadAndInstall(info, onExit) },
            onOpenDownloadPage = { url -> openInBrowser(url) },
        )
    }
}

// ── Colour ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColourPanel(currentVariant: ThemeVariant, onSelect: (ThemeVariant) -> Unit) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Colour", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "PureTV derives its whole scheme from one source colour, the way Material dynamic " +
                    "colour does. Pick a palette and every surface, container and accent re-tones together.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ThemeVariant.entries.forEach { variant ->
                    ColourSwatch(
                        variant = variant,
                        selected = variant == currentVariant,
                        onClick = { onSelect(variant) },
                    )
                }
            }
        }
    }
}

/**
 * One palette tile. Always previews its OWN colours ([themeColors] keyed by
 * [variant]), never the currently active theme, so every swatch shows what
 * picking it would actually look like. Only the selection ring uses the active
 * theme's primary, because that ring is app chrome, not palette preview.
 */
@Composable
private fun ColourSwatch(variant: ThemeVariant, selected: Boolean, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val vc = themeColors[variant]!!
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .width(176.dp)
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = 24.dp,
                hoverRadius = 32.dp,
                color = if (selected) c.primary else Color.Transparent,
            )
            .padding(4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(vc.surfaceLow)
                .padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(2f).height(56.dp).clip(RoundedCornerShape(16.dp)).background(vc.primary),
                )
                Box(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp))
                        .background(vc.secondaryContainer),
                )
                Box(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp))
                        .background(vc.tertiaryContainer),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selected) {
                    Icon(
                        ExpressiveIcons.CheckCircle,
                        contentDescription = null,
                        tint = vc.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(variant.displayName, style = MaterialTheme.typography.titleMedium, color = vc.onSurface)
            }
            Text(variant.seed, style = PureTvType.data, color = vc.onSurfaceVariant)
        }
    }
}

// ── Shape & motion ─────────────────────────────────────────────────────────────────

@Composable
private fun ShapeAndMotionPanel(currentIntensity: ShapeIntensity, onSelect: (ShapeIntensity) -> Unit) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Shape and motion", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(20.dp))
            SettingsRow(
                label = "Expressiveness",
                description = "How far corners round, morph on press, and how springy the motion feels.",
            ) {
                SegmentedToggle(
                    options = ShapeIntensity.entries,
                    selected = currentIntensity,
                    label = { it.displayName },
                    onSelect = onSelect,
                )
            }
        }
    }
}

// ── Playback ─────────────────────────────────────────────────────────────────────

@Composable
private fun PlaybackPanel(
    selectedQuality: StreamQuality,
    onSelectQuality: (StreamQuality) -> Unit,
    animateEmotes: Boolean,
    onAnimateEmotesChange: (Boolean) -> Unit,
) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Playback", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(20.dp))
            SettingsRow(
                label = "Preferred quality",
                description = "The variant PureTV reaches for first when a stream opens.",
            ) {
                SegmentedToggle(
                    options = StreamQuality.entries,
                    selected = selectedQuality,
                    label = { it.label },
                    onSelect = onSelectQuality,
                )
            }
            ExpressiveDivider(Modifier.padding(vertical = 24.dp))
            SettingsRow(
                label = "Animate emotes",
                description = "Play animated 7TV and BTTV emotes. Off shows a still frame, which is lighter on the CPU.",
            ) {
                ExpressiveSwitch(checked = animateEmotes, onCheckedChange = onAnimateEmotesChange)
            }
            // Playback backend + GPU upscaling live in the in-player gear menu
            // (per-stream, applied live) rather than here. See PlayerSettingsMenu.
        }
    }
}

// ── Browsing & mini player ─────────────────────────────────────────────────────────

@Composable
private fun BrowsingPanel(
    oneClickWatch: Boolean,
    onOneClickWatchChange: (Boolean) -> Unit,
    lowQualityWhenDocked: Boolean,
    onLowQualityChange: (Boolean) -> Unit,
    miniSize: MiniSize,
    onMiniSizeChange: (MiniSize) -> Unit,
) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Browsing and mini player", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(20.dp))
            SettingsRow(
                label = "One click to watch",
                description = "Clicking a live channel anywhere opens the stream. Off opens the channel page first.",
            ) { ExpressiveSwitch(checked = oneClickWatch, onCheckedChange = onOneClickWatchChange) }
            ExpressiveDivider(Modifier.padding(vertical = 24.dp))
            SettingsRow(
                label = "Lower quality in the mini player",
                description = "Drops to 480p while the stream is small, then goes back when you expand it. Saves CPU and data.",
            ) { ExpressiveSwitch(checked = lowQualityWhenDocked, onCheckedChange = onLowQualityChange) }
            ExpressiveDivider(Modifier.padding(vertical = 24.dp))
            SettingsRow(
                label = "Mini player size",
                description = "You can also change it from the button on the mini player.",
            ) {
                SegmentedToggle(options = MiniSize.entries, selected = miniSize, label = { it.label }, onSelect = onMiniSizeChange)
            }
        }
    }
}

// ── Chat filters ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatFiltersPanel(
    ignored: List<String>,
    onUnignore: (String) -> Unit,
    onIgnore: (String) -> Unit,
    highlightWords: List<String>,
    onHighlightWordsChange: (List<String>) -> Unit,
) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Chat", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(20.dp))

            Text("Highlight words", style = MaterialTheme.typography.titleMedium, color = c.onSurface)
            Text(
                "Messages containing these words light up like a mention and also show in the Mentions tab. Separate with commas.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            var wordsText by remember(highlightWords) { mutableStateOf(highlightWords.joinToString(", ")) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingsTextField(wordsText, { wordsText = it }, "e.g. giveaway, your name", Modifier.weight(1f))
                ExpressiveButton(
                    text = "Save",
                    onClick = { onHighlightWordsChange(wordsText.split(',')) },
                    style = ExpressiveButtonStyle.Tonal,
                    enabled = wordsText != highlightWords.joinToString(", "),
                )
            }

            ExpressiveDivider(Modifier.padding(vertical = 24.dp))

            Text("Ignored users", style = MaterialTheme.typography.titleMedium, color = c.onSurface)
            Text(
                "Their messages are hidden in every chat. You can also ignore someone by clicking their name in chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            var newIgnore by remember { mutableStateOf("") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingsTextField(newIgnore, { newIgnore = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(25) }, "Twitch username", Modifier.weight(1f))
                ExpressiveButton(
                    text = "Ignore",
                    onClick = { onIgnore(newIgnore); newIgnore = "" },
                    style = ExpressiveButtonStyle.Tonal,
                    enabled = newIgnore.isNotBlank(),
                )
            }
            if (ignored.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ignored.forEach { login ->
                        Row(
                            modifier = Modifier
                                .clip(PureTvTheme.shapes.pillShape)
                                .background(c.surfaceHigh)
                                .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(login, style = PureTvType.data, color = c.onSurface)
                            ExpressiveIconButton(
                                icon = ExpressiveIcons.Close,
                                contentDescription = "Stop ignoring $login",
                                onClick = { onUnignore(login) },
                                boxSize = 28.dp,
                                iconSize = 14.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = PureTvTheme.colors
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(PureTvTheme.shapes.pillShape)
            .background(c.surfaceHigh)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = c.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
            cursorBrush = SolidColor(c.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Backup ─────────────────────────────────────────────────────────────────────────

/**
 * Export writes one file; import stages it for the next launch (see
 * BackupManager for why it can't apply while the app is running).
 */
@Composable
private fun BackupPanel(onExit: () -> Unit) {
    val c = PureTvTheme.colors
    val backup = remember { BackupManager() }
    var status by remember { mutableStateOf<String?>(null) }
    var restartNeeded by remember { mutableStateOf(backup.hasPendingRestore) }
    ExpressivePanel {
        Column {
            Text("Backup and restore", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "Saves your settings, follows, pinned categories, saved searches, history, chat filters and " +
                    "continue-watching spots to one file. Sign-in isn't included; you sign in again after restoring.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveButton(
                    text = "Export backup",
                    icon = ExpressiveIcons.Download,
                    onClick = {
                        val file = chooseFile(save = true, suggestedName = "PureTV-backup.json")
                        if (file != null) {
                            status = runCatching { backup.export(file) }
                                .fold({ n -> "Saved $n data files to ${file.name}." }, { "Couldn't save the backup: ${it.message}" })
                        }
                    },
                )
                ExpressiveButton(
                    text = "Restore from file",
                    style = ExpressiveButtonStyle.Outlined,
                    onClick = {
                        val file = chooseFile(save = false, suggestedName = null)
                        if (file != null) {
                            backup.stageRestore(file).fold(
                                { status = "Backup loaded. Close and reopen PureTV to finish restoring."; restartNeeded = true },
                                { status = it.message ?: "Couldn't read that backup." },
                            )
                        }
                    },
                )
            }
            status?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = PureTvType.data, color = c.primary)
            }
            if (restartNeeded) {
                Spacer(Modifier.height(12.dp))
                ExpressiveButton(text = "Close PureTV now", onClick = onExit, style = ExpressiveButtonStyle.Tonal)
            }
        }
    }
}

/** Native Windows open/save dialog. Returns null if cancelled. */
private fun chooseFile(save: Boolean, suggestedName: String?): java.io.File? {
    val dialog = java.awt.FileDialog(
        null as java.awt.Frame?,
        if (save) "Export PureTV backup" else "Restore PureTV backup",
        if (save) java.awt.FileDialog.SAVE else java.awt.FileDialog.LOAD,
    )
    suggestedName?.let { dialog.file = it }
    if (!save) dialog.setFilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val f = java.io.File(dialog.directory, name)
    return if (save && !f.name.endsWith(".json", ignoreCase = true)) java.io.File(f.parentFile, f.name + ".json") else f
}

// ── Ad blocking ──────────────────────────────────────────────────────────────────

/**
 * Desktop's ad blocking is unconditional: [com.puretv.twitch.desktop.player.LocalStreamProxy]
 * always runs the stream through [com.puretv.twitch.core.adblock.AdBlockEngine], and never
 * reads `adBlockEnabled` / `adBlockStrategy` / `customProxyUrl` from [com.puretv.twitch.core.model.AppSettings].
 * Those fields exist for the Android and TV clients, which do wire them up. So
 * this panel states a fact rather than offering controls that would not do
 * anything on this platform.
 */
@Composable
private fun AdBlockPanel() {
    val c = PureTvTheme.colors
    ExpressivePanel(color = c.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(c.onTertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    ExpressiveIcons.Shield,
                    contentDescription = null,
                    tint = c.tertiaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Ad blocking is always on",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.onTertiaryContainer,
                )
                Text(
                    "Filtered on-device across live streams and past videos. Nothing to configure.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onTertiaryContainer,
                )
            }
        }
    }
}

// ── Account ──────────────────────────────────────────────────────────────────────

@Composable
private fun AccountPanel(isLoggedIn: Boolean, username: String?, onLogOut: () -> Unit) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Account", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(20.dp))
            if (isLoggedIn) {
                SettingsRow(label = "Signed in", description = username ?: "(unknown)") {
                    ExpressiveButton(text = "Log out", onClick = onLogOut, style = ExpressiveButtonStyle.Outlined)
                }
            } else {
                Text(
                    "Not signed in. Open the Account tab to sign in with Twitch.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                )
            }
        }
    }
}

// ── About ────────────────────────────────────────────────────────────────────────

@Composable
private fun AboutPanel(
    version: String,
    updateState: UpdateState,
    onCheckForUpdates: () -> Unit,
    onDownloadAndInstall: (UpdateInfo) -> Unit,
    onOpenDownloadPage: (String) -> Unit,
) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("PureTV for Twitch", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
                    Text("v$version", style = PureTvType.data, color = c.onSurfaceVariant)
                }
                if (updateState is UpdateState.Idle) {
                    ExpressiveButton(
                        text = "Check for updates",
                        onClick = onCheckForUpdates,
                        style = ExpressiveButtonStyle.Outlined,
                    )
                }
            }
            when (updateState) {
                is UpdateState.Idle -> Unit
                is UpdateState.Available -> {
                    Spacer(Modifier.height(20.dp))
                    ExpressiveDivider()
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Update available: ${updateState.info.version}",
                        style = PureTvType.data,
                        color = c.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    ExpressiveButton(
                        text = "Download and install ${updateState.info.version}",
                        onClick = { onDownloadAndInstall(updateState.info) },
                    )
                }
                is UpdateState.Downloading -> {
                    Spacer(Modifier.height(20.dp))
                    ExpressiveDivider()
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Downloading update, ${(updateState.progress * 100).toInt()}%",
                        style = PureTvType.data,
                        color = c.onSurfaceVariant,
                    )
                }
                is UpdateState.Error -> {
                    Spacer(Modifier.height(20.dp))
                    ExpressiveDivider()
                    Spacer(Modifier.height(20.dp))
                    Text("Update failed: ${updateState.message}", style = PureTvType.data, color = c.error)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExpressiveButton(
                            text = "Open download page",
                            onClick = { onOpenDownloadPage(updateState.releaseUrl ?: resolveReleaseUrl("")) },
                        )
                        ExpressiveButton(
                            text = "Retry",
                            onClick = onCheckForUpdates,
                            style = ExpressiveButtonStyle.Outlined,
                        )
                    }
                }
            }
        }
    }
}

// ── Row scaffolding ────────────────────────────────────────────────────────────────

/** Label + description on the left (weight 1f), a control on the right. */
@Composable
private fun SettingsRow(label: String, description: String, trailing: @Composable () -> Unit) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = c.onSurface)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
        trailing()
    }
}
