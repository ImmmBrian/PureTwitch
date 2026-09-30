package com.puretv.twitch.desktop.ui.components

import androidx.compose.runtime.mutableStateMapOf
import kotlin.math.abs
import kotlin.math.pow

/**
 * Lifts a chat name color until it reads clearly on PureTV's dark surfaces.
 * Twitch lets people pick #0000FF or #1E1E1E, which vanish on a dark panel, so
 * dark colors keep their hue but get lighter until their luminance clears
 * [minLuminance] (about 5:1 contrast on the chat background). Colors that
 * already read well come back unchanged.
 */
internal fun readableNameArgb(argb: Int, minLuminance: Double = 0.24): Int {
    val opaque = argb or (0xFF shl 24)
    if (relativeLuminance(opaque) >= minLuminance) return opaque
    val r = ((opaque shr 16) and 0xFF) / 255f
    val g = ((opaque shr 8) and 0xFF) / 255f
    val b = (opaque and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    var l = (max + min) / 2f
    val d = max - min
    val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f))
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d).mod(6f))
        max == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    var out = opaque
    while (l < 0.95f) {
        l += 0.02f
        out = hslToArgb(h, s, l)
        if (relativeLuminance(out) >= minLuminance) break
    }
    return out
}

internal fun relativeLuminance(argb: Int): Double {
    fun ch(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * ch((argb shr 16) and 0xFF) + 0.7152 * ch((argb shr 8) and 0xFF) + 0.0722 * ch(argb and 0xFF)
}

private fun hslToArgb(h: Float, s: Float, l: Float): Int {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f).mod(2f) - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun to255(v: Float) = ((v + m) * 255f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (to255(r) shl 16) or (to255(g) shl 8) or to255(b)
}

/**
 * Width-to-height ratio of each emote image seen so far, so chat can lay wide
 * 7TV and BTTV emotes out at their real shape. Unknown emotes start square and
 * reflow once when their image loads; after that the ratio is known app-wide.
 */
internal object EmoteAspects {
    private val ratios = mutableStateMapOf<String, Float>()

    fun of(url: String): Float = ratios[url] ?: 1f

    fun report(url: String, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val ratio = (width.toFloat() / height).coerceIn(0.25f, 4f)
        val old = ratios[url]
        if (old == null || abs(old - ratio) > 0.02f) ratios[url] = ratio
    }
}
