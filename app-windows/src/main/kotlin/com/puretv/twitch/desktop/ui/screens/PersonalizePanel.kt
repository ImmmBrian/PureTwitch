package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.data.ACCENT_SWATCHES
import com.puretv.twitch.desktop.data.ChatNameColors
import com.puretv.twitch.desktop.data.HomeSection
import com.puretv.twitch.desktop.data.LayoutDensity
import com.puretv.twitch.desktop.data.Personalization
import com.puretv.twitch.desktop.data.TextScale
import com.puretv.twitch.desktop.data.ViewPrefsStore
import com.puretv.twitch.desktop.data.parseHexColor
import com.puretv.twitch.desktop.data.toggled
import com.puretv.twitch.desktop.ui.Destination
import com.puretv.twitch.desktop.ui.components.ExpressiveButton
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonSize
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveFilterChip
import com.puretv.twitch.desktop.ui.components.ExpressivePanel
import com.puretv.twitch.desktop.ui.components.ExpressiveSwitch
import com.puretv.twitch.desktop.ui.components.SegmentedToggle
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType

/**
 * Settings > Personalize: the handful of choices that make the app feel like
 * yours without adding anything to the main screens. Every change applies live.
 */
@Composable
internal fun PersonalizePanel(store: ViewPrefsStore, look: Personalization) {
    val c = PureTvTheme.colors
    ExpressivePanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Personalize", style = MaterialTheme.typography.headlineSmall, color = c.onSurface)
                    Text("Changes apply right away.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                }
                if (look != Personalization()) {
                    ExpressiveButton(
                        text = "Reset all",
                        onClick = store::resetLook,
                        style = ExpressiveButtonStyle.Text,
                        size = ExpressiveButtonSize.Small,
                    )
                }
            }

            Group("Accent color", "Tints buttons, highlights and selections on top of your theme.") {
                AccentPicker(current = look.accent, onPick = { hex -> store.setLook { it.copy(accent = hex) } })
            }

            Group("Text size", "Makes all text in the app smaller or larger.") {
                SegmentedToggle(
                    options = TextScale.entries,
                    selected = look.textScaleEnum,
                    label = { it.label },
                    onSelect = { v -> store.setLook { it.copy(textScale = v.name) } },
                    height = 44.dp,
                )
            }

            Group("Density", "Compact fits more streams on each row.") {
                SegmentedToggle(
                    options = LayoutDensity.entries,
                    selected = look.densityEnum,
                    label = { it.label },
                    onSelect = { v -> store.setLook { it.copy(density = v.name) } },
                    height = 44.dp,
                )
            }

            Group("Left menu", "Hide tabs you don't use. Settings always stays.") {
                ChipFlow {
                    Destination.entries.filter { it != Destination.SETTINGS }.forEach { d ->
                        val shown = d.name !in look.hiddenTabs
                        ExpressiveFilterChip(d.label, shown, {
                            store.setLook { it.copy(hiddenTabs = it.hiddenTabs.toggled(d.name, present = shown)) }
                        })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Opens on", style = PureTvType.kicker, color = c.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    Destination.entries.filter { it.name !in look.hiddenTabs }.forEach { d ->
                        ExpressiveFilterChip(d.label, look.launchTab == d.name, {
                            store.setLook { it.copy(launchTab = d.name) }
                        })
                    }
                }
            }

            Group("Home", "Choose which shelves Home shows.") {
                ChipFlow {
                    HomeSection.entries.forEach { s ->
                        val shown = look.showsHomeSection(s)
                        ExpressiveFilterChip(s.label, shown, {
                            store.setLook { it.copy(hiddenHomeSections = it.hiddenHomeSections.toggled(s.name, present = shown)) }
                        })
                    }
                }
            }

            Group("Channel stats", "The audience panel and Stats for nerds under streams and on channel pages.") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Show stats", style = MaterialTheme.typography.bodyLarge, color = c.onSurface, modifier = Modifier.width(120.dp))
                    ExpressiveSwitch(checked = look.showStats, onCheckedChange = { on -> store.setLook { it.copy(showStats = on) } })
                }
            }

            Group("Chat", null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Text size", style = MaterialTheme.typography.bodyLarge, color = c.onSurface, modifier = Modifier.width(120.dp))
                    SegmentedToggle(
                        options = TextScale.entries,
                        selected = look.chatTextScaleEnum,
                        label = { it.label },
                        onSelect = { v -> store.setLook { it.copy(chatTextScale = v.name) } },
                        height = 40.dp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Name colors", style = MaterialTheme.typography.bodyLarge, color = c.onSurface, modifier = Modifier.width(120.dp))
                    SegmentedToggle(
                        options = ChatNameColors.entries,
                        selected = look.chatNameColorsEnum,
                        label = { it.label },
                        onSelect = { v -> store.setLook { it.copy(chatNameColors = v.name) } },
                        height = 40.dp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Timestamps", style = MaterialTheme.typography.bodyLarge, color = c.onSurface, modifier = Modifier.width(120.dp))
                    ExpressiveSwitch(checked = look.chatTimestamps, onCheckedChange = { on -> store.setLook { it.copy(chatTimestamps = on) } })
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, description: String?, content: @Composable () -> Unit) {
    val c = PureTvTheme.colors
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.onSurface)
        if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(current: String?, onPick: (String?) -> Unit) {
    val c = PureTvTheme.colors
    var custom by remember(current) { mutableStateOf(current?.takeIf { it !in ACCENT_SWATCHES }.orEmpty()) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ExpressiveFilterChip("Theme default", current == null, { onPick(null) })
        ACCENT_SWATCHES.forEach { hex ->
            val color = parseHexColor(hex)?.let { Color(it) } ?: return@forEach
            val selected = current.equals(hex, ignoreCase = true)
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(if (selected) 3.dp else 0.dp, if (selected) c.onSurface else Color.Transparent, CircleShape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onPick(hex) },
            )
        }
        // Custom hex: applies as soon as it's a valid #RRGGBB.
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(PureTvTheme.shapes.pillShape)
                .background(c.surfaceHigh)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val preview = parseHexColor(custom)?.let { Color(it) }
            Box(Modifier.size(16.dp).clip(CircleShape).background(preview ?: c.outlineVariant))
            Box(Modifier.width(90.dp), contentAlignment = Alignment.CenterStart) {
                if (custom.isEmpty()) Text("#custom", style = PureTvType.data, color = c.onSurfaceVariant)
                BasicTextField(
                    value = custom,
                    onValueChange = { v ->
                        val clean = v.filter { it == '#' || it.isLetterOrDigit() }.take(7)
                        custom = clean
                        val normalized = if (clean.startsWith("#")) clean else "#$clean"
                        if (parseHexColor(normalized) != null) onPick(normalized.uppercase())
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = c.onSurface, fontFamily = PureTvType.data.fontFamily, fontSize = PureTvType.data.fontSize),
                    cursorBrush = SolidColor(c.primary),
                )
            }
        }
    }
}
