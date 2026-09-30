package com.puretv.twitch.desktop.channel

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URLEncoder

/** One clip, as listed on a channel's Clips tab. */
data class ClipInfo(
    val slug: String,
    val title: String,
    val viewCount: Int,
    val durationSeconds: Int,
    val createdAt: String,
    val thumbnailUrl: String,
    val curatorName: String,
    val gameName: String,
)

data class ClipPage(val clips: List<ClipInfo>, val cursor: String?)

/** The time windows Twitch's clip directory supports. */
enum class ClipPeriod(val label: String, val gql: String) {
    WEEK("7 days", "LAST_WEEK"),
    MONTH("30 days", "LAST_MONTH"),
    ALL("All time", "ALL_TIME"),
}

private fun JsonObject.s(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.i(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

internal fun sanitizeLogin(login: String) = login.filter { it.isLetterOrDigit() || it == '_' }
internal fun sanitizeSlug(slug: String) = slug.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
private fun sanitizeCursor(c: String) = c.filter { it.isLetterOrDigit() || it in "=+/_-" }

internal fun buildClipsQuery(login: String, period: ClipPeriod, after: String?, first: Int = 20): String {
    val cursor = after?.let(::sanitizeCursor)?.takeIf { it.isNotEmpty() }?.let { ",after:\"$it\"" }.orEmpty()
    return "query{user(login:\"${sanitizeLogin(login)}\"){clips(first:$first$cursor,criteria:{period:${period.gql},sort:VIEWS_DESC})" +
        "{edges{cursor node{slug title viewCount durationSeconds createdAt thumbnailURL curator{displayName} game{name}}} pageInfo{hasNextPage}}}}"
}

internal fun parseClipPage(root: JsonObject): ClipPage {
    val user = (root["data"] as? JsonObject)?.get("user") as? JsonObject ?: return ClipPage(emptyList(), null)
    val conn = user["clips"] as? JsonObject ?: return ClipPage(emptyList(), null)
    val edges = (conn["edges"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val clips = edges.mapNotNull { e ->
        val n = e["node"] as? JsonObject ?: return@mapNotNull null
        ClipInfo(
            slug = n.s("slug") ?: return@mapNotNull null,
            title = n.s("title").orEmpty(),
            viewCount = n.i("viewCount") ?: 0,
            durationSeconds = n.i("durationSeconds") ?: 0,
            createdAt = n.s("createdAt").orEmpty(),
            thumbnailUrl = n.s("thumbnailURL").orEmpty(),
            curatorName = (n["curator"] as? JsonObject)?.s("displayName").orEmpty(),
            gameName = (n["game"] as? JsonObject)?.s("name").orEmpty(),
        )
    }
    val hasNext = ((conn["pageInfo"] as? JsonObject)?.get("hasNextPage") as? JsonPrimitive)?.booleanOrNull ?: false
    // Twitch only fills the cursor on the last edge of a page.
    val cursor = edges.lastOrNull()?.s("cursor")?.takeIf { it.isNotBlank() }
    return ClipPage(clips, if (hasNext) cursor else null)
}

internal fun buildClipPlaybackQuery(slug: String): String =
    "query{clip(slug:\"${sanitizeSlug(slug)}\"){playbackAccessToken(params:{platform:\"web\",playerBackend:\"mediaplayer\",playerType:\"site\"})" +
        "{signature value} videoQualities{frameRate quality sourceURL}}}"

/**
 * The clip's MP4 URL with its access token attached (the CDN refuses unsigned
 * requests). Picks the highest quality at or below [maxQuality] (e.g. 1080).
 */
internal fun parseClipPlaybackUrl(root: JsonObject, maxQuality: Int = 1080): String? {
    val clip = (root["data"] as? JsonObject)?.get("clip") as? JsonObject ?: return null
    val token = clip["playbackAccessToken"] as? JsonObject ?: return null
    val sig = token.s("signature") ?: return null
    val value = token.s("value") ?: return null
    val qualities = (clip["videoQualities"] as? JsonArray).orEmpty().mapNotNull { q ->
        val o = q as? JsonObject ?: return@mapNotNull null
        val url = o.s("sourceURL")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
        (o.s("quality")?.toIntOrNull() ?: 0) to url
    }.sortedByDescending { it.first }
    val pick = qualities.firstOrNull { it.first <= maxQuality } ?: qualities.lastOrNull() ?: return null
    return pick.second + "?sig=" + sig + "&token=" + URLEncoder.encode(value, "UTF-8")
}

/** "1:05" / "0:30". */
fun formatClipDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** "5m", "3h", "4d", "2mo", "1y" since an ISO-8601 time, or null if unparseable. */
fun formatAge(iso: String, now: java.time.Instant = java.time.Instant.now()): String? {
    val then = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return null
    val minutes = java.time.Duration.between(then, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)}d"
        minutes < 60 * 24 * 365 -> "${minutes / (60 * 24 * 30)}mo"
        else -> "${minutes / (60 * 24 * 365)}y"
    }
}
