package com.puretv.twitch.desktop.data

import kotlinx.serialization.Serializable

/** App-wide text size. Only text scales; icons, thumbnails and spacing stay put. */
enum class TextScale(val label: String, val factor: Float) {
    SMALL("Small", 0.92f),
    DEFAULT("Default", 1f),
    LARGE("Large", 1.12f),
}

enum class LayoutDensity(val label: String) { COMFORTABLE("Comfortable"), COMPACT("Compact") }

/** The shelves on Home that can be switched off. */
enum class HomeSection(val label: String) {
    HERO("Featured stream"),
    CONTINUE("Continue watching"),
    RECENT("Recently watched"),
    FOLLOWS("Channels you follow"),
    LIVE("Live now"),
}

/** How chat usernames are colored. */
enum class ChatNameColors(val label: String) {
    TWITCH("Their colors"),
    ACCENT("Accent"),
    PLAIN("Plain"),
}

/** The accent swatches offered before "custom". Chosen to read well on every dark theme. */
val ACCENT_SWATCHES: List<String> = listOf(
    "#B39DFF", "#7FB2FF", "#5ED3F3", "#4FD1A5", "#A4D65E",
    "#FFD166", "#FFA26B", "#FF7A85", "#F48FB1", "#E0E0E0",
)

/**
 * Everything on the Personalize panel. Stored as names (not enums) so a future
 * option or a hand-edited file can't break loading; the typed getters fall back
 * to defaults for anything unknown.
 */
@Serializable
data class Personalization(
    /** "#RRGGBB", or null for the theme's own accent. */
    val accent: String? = null,
    val textScale: String = TextScale.DEFAULT.name,
    val density: String = LayoutDensity.COMFORTABLE.name,
    /** Left-rail tabs the user hid (Destination names). Settings can never be hidden. */
    val hiddenTabs: List<String> = emptyList(),
    /** Destination name the app opens on. */
    val launchTab: String = "HOME",
    val hiddenHomeSections: List<String> = emptyList(),
    val chatTextScale: String = TextScale.DEFAULT.name,
    val chatTimestamps: Boolean = true,
    val chatNameColors: String = ChatNameColors.TWITCH.name,
) {
    val textScaleEnum: TextScale get() = enumOr(textScale, TextScale.DEFAULT)
    val densityEnum: LayoutDensity get() = enumOr(density, LayoutDensity.COMFORTABLE)
    val compact: Boolean get() = densityEnum == LayoutDensity.COMPACT
    val chatTextScaleEnum: TextScale get() = enumOr(chatTextScale, TextScale.DEFAULT)
    val chatNameColorsEnum: ChatNameColors get() = enumOr(chatNameColors, ChatNameColors.TWITCH)
    fun showsHomeSection(section: HomeSection): Boolean = section.name !in hiddenHomeSections

    /** The accent as ARGB, or null when unset or unparseable. */
    val accentArgb: Long? get() = parseHexColor(accent)
}

private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
    runCatching { enumValueOf<E>(name) }.getOrDefault(default)

/** "#RRGGBB" or "RRGGBB" to opaque ARGB; null for anything else. */
fun parseHexColor(raw: String?): Long? {
    val hex = raw?.trim()?.removePrefix("#") ?: return null
    if (hex.length != 6 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return 0xFF000000L or hex.toLong(16)
}

/** Flip a name in or out of a list (for the hide switches). */
internal fun List<String>.toggled(name: String, present: Boolean): List<String> =
    if (present) (this - name) + name else this - name
