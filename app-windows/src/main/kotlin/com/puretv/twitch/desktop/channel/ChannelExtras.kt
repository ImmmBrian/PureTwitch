package com.puretv.twitch.desktop.channel

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * One of the info panels a streamer puts under their stream on twitch.tv: an
 * optional title, an optional image, optional Markdown text, and an optional link
 * (usually the image's click-through).
 */
data class ChannelPanel(
    val id: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val linkUrl: String?,
)

/** What twitch.tv shows under a channel beyond the bio: its panels and its chat rules. */
data class ChannelExtras(
    val panels: List<ChannelPanel> = emptyList(),
    val rules: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = panels.isEmpty() && rules.isEmpty()
}

internal fun buildChannelExtrasQuery(login: String): String {
    val safe = login.filter { it.isLetterOrDigit() || it == '_' }
    return "query{user(login:\"$safe\"){chatSettings{rules} " +
        "panels{id type ... on DefaultPanel{title description imageURL linkURL}}}}"
}

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Tolerant on purpose: Twitch can answer with `errors` for one field (rules can
 * sit behind an integrity check) while still returning the rest, so anything
 * present is kept and anything missing is simply empty.
 */
internal fun parseChannelExtras(root: JsonObject): ChannelExtras {
    val user = (root["data"] as? JsonObject)?.get("user") as? JsonObject ?: return ChannelExtras()

    val rules = ((user["chatSettings"] as? JsonObject)?.get("rules") as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trimEnd() }
        .orEmpty()
        .let(::tidyRules)

    val panels = (user["panels"] as? JsonArray).orEmpty().mapNotNull { el ->
        val p = el as? JsonObject ?: return@mapNotNull null
        // Extension panels are interactive web apps Twitch hosts; they can't render here.
        if (p.text("type") != "DEFAULT") return@mapNotNull null
        val panel = ChannelPanel(
            id = p.text("id") ?: return@mapNotNull null,
            title = p.text("title"),
            description = p.text("description"),
            imageUrl = p.text("imageURL")?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
            linkUrl = p.text("linkURL")?.let(::normalizeUrl),
        )
        // A panel with nothing in it is a placeholder the streamer left blank.
        if (panel.title == null && panel.description == null && panel.imageUrl == null && panel.linkUrl == null) null else panel
    }
    return ChannelExtras(panels, rules)
}

/** Drop leading/trailing blank lines and collapse runs of blanks to one. */
internal fun tidyRules(lines: List<String>): List<String> {
    val out = mutableListOf<String>()
    for (line in lines) {
        if (line.isBlank()) {
            if (out.isNotEmpty() && out.last().isNotBlank()) out += ""
        } else {
            out += line
        }
    }
    while (out.isNotEmpty() && out.last().isBlank()) out.removeAt(out.lastIndex)
    return out
}

/** Accepts "cohh.tv/store" style links by adding https://; rejects anything that isn't web. */
internal fun normalizeUrl(raw: String): String? {
    val t = raw.trim()
    if (t.isEmpty() || t.any { it.isWhitespace() }) return null
    val lower = t.lowercase()
    return when {
        lower.startsWith("https://") || lower.startsWith("http://") -> t
        lower.contains("://") || lower.startsWith("javascript:") || lower.startsWith("mailto:") -> null
        t.contains('.') -> "https://$t"
        else -> null
    }
}

/** "youtube.com" from "https://www.youtube.com/c/x", for link chips. */
fun displayHost(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore('?').removePrefix("www.")
