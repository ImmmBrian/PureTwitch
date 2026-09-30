package com.puretv.twitch.desktop.data

import androidx.compose.ui.graphics.Color
import com.puretv.twitch.desktop.ui.theme.ThemeVariant
import com.puretv.twitch.desktop.ui.theme.themeColors
import com.puretv.twitch.desktop.ui.theme.toHsl
import com.puretv.twitch.desktop.ui.theme.withAccent
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonalizationTest {
    @Test fun hex_parsing() {
        assertEquals(0xFFB39DFFL, parseHexColor("#B39DFF"))
        assertEquals(0xFF00FF00L, parseHexColor("00ff00"))
        assertNull(parseHexColor("#12345"))
        assertNull(parseHexColor("#GGGGGG"))
        assertNull(parseHexColor(null))
    }

    @Test fun unknown_names_fall_back() {
        val p = Personalization(textScale = "HUGE", density = "???", chatNameColors = "RAINBOW")
        assertEquals(TextScale.DEFAULT, p.textScaleEnum)
        assertEquals(LayoutDensity.COMFORTABLE, p.densityEnum)
        assertEquals(ChatNameColors.TWITCH, p.chatNameColorsEnum)
    }

    @Test fun hide_toggle() {
        val hidden = emptyList<String>().toggled("BROWSE", present = true)
        assertEquals(listOf("BROWSE"), hidden)
        assertEquals(emptyList(), hidden.toggled("BROWSE", present = false))
        assertTrue(Personalization(hiddenHomeSections = listOf("HERO")).let { !it.showsHomeSection(HomeSection.HERO) && it.showsHomeSection(HomeSection.LIVE) })
    }

    @Test fun look_survives_restart() {
        val dir = Files.createTempDirectory("look").toFile()
        ViewPrefsStore(dir).apply {
            setLook { it.copy(accent = "#FF7A85", density = LayoutDensity.COMPACT.name, hiddenTabs = listOf("SEARCH")) }
            flush()
        }
        val look = ViewPrefsStore(dir).prefs.value.look
        assertEquals("#FF7A85", look.accent)
        assertTrue(look.compact)
        assertEquals(listOf("SEARCH"), look.hiddenTabs)
    }

    @Test fun accent_keeps_hue_and_readable_tones() {
        val base = themeColors[ThemeVariant.VIOLET_DUSK]!!
        val teal = Color(0xFF5ED3F3)
        val tinted = base.withAccent(teal)
        assertEquals(teal, tinted.primary, "a light pick is used as-is")
        assertTrue(abs(tinted.primaryContainer.toHsl().first - teal.toHsl().first) < 2f, "container keeps the hue")
        assertTrue(tinted.onPrimary.toHsl().third < 0.25f, "text on the accent stays dark")
        assertEquals(base.surface, tinted.surface, "surfaces are untouched")
        // A dark pick gets lifted so it still reads on dark surfaces.
        assertTrue(base.withAccent(Color(0xFF102040)).primary.toHsl().third > 0.7f)
    }
}
