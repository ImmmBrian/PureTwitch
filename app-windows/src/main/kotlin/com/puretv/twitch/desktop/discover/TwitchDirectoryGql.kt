package com.puretv.twitch.desktop.discover

import com.puretv.twitch.core.api.StreamPage
import com.puretv.twitch.core.api.TwitchConfig
import com.puretv.twitch.core.model.StreamInfo
import com.puretv.twitch.desktop.channel.ChannelExtras
import com.puretv.twitch.desktop.channel.ClipPage
import com.puretv.twitch.desktop.channel.ClipPeriod
import com.puretv.twitch.desktop.channel.buildClipPlaybackQuery
import com.puretv.twitch.desktop.channel.buildClipsQuery
import com.puretv.twitch.desktop.channel.parseClipPage
import com.puretv.twitch.desktop.channel.parseClipPlaybackUrl
import com.puretv.twitch.desktop.channel.buildChannelExtrasQuery
import com.puretv.twitch.desktop.channel.parseChannelExtras
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put

/** Raised when Twitch's GraphQL answers with `errors` or an unexpected shape. */
class DirectoryGqlException(message: String) : Exception(message)

/**
 * The two directory reads Helix can't do, served by Twitch's web GraphQL API
 * (the same one the stream player already uses for playback tokens):
 *
 *  - per-category viewer totals, for the Browse grid;
 *  - live streams sorted lowest-viewers-first and filtered by language/tags, so
 *    Discover can reach small channels without paging through the whole site.
 *
 * Queries are sent inline (no persisted-query hash to go stale). Every value that
 * lands inside the query text is sanitized first, and the query itself is JSON-
 * encoded by kotlinx, never string-concatenated into the request body.
 */
class TwitchDirectoryGql(
    private val httpClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private suspend fun post(query: String): JsonObject {
        val body = buildJsonObject { put("query", query) }.toString()
        val text: String = httpClient.post(TwitchConfig.GQL_ENDPOINT) {
            header("Client-ID", TwitchConfig.GQL_CLIENT_ID)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
        return json.parseToJsonElement(text).jsonObject
    }

    /** Viewer totals for these category ids. Missing or failed ids are simply absent. */
    suspend fun gameViewerCounts(ids: List<String>): Map<String, Int> {
        val clean = ids.map { id -> id.filter { it.isDigit() } }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, Int>()
        clean.chunked(50).forEach { chunk ->
            out += parseGameViewerCounts(post(buildGameViewerCountsQuery(chunk)))
        }
        return out
    }

    /** One page of live streams. Throws [DirectoryGqlException] on any GraphQL error. */
    suspend fun streamsPage(
        gameId: String?,
        languageEnum: String?,
        tags: List<String>,
        ascending: Boolean,
        after: String?,
        first: Int = PAGE_SIZE,
    ): StreamPage = parseStreamsPage(post(buildStreamsQuery(gameId, languageEnum, tags, ascending, after, first)), gameScoped = gameId != null)

    /** A channel's info panels and chat rules. Best-effort: partial answers are kept. */
    suspend fun channelExtras(login: String): ChannelExtras =
        parseChannelExtras(post(buildChannelExtrasQuery(login)))

    /** One page of a channel's most-viewed clips in [period]. */
    suspend fun channelClips(login: String, period: ClipPeriod, after: String? = null): ClipPage =
        parseClipPage(post(buildClipsQuery(login, period, after)))

    /** A playable, signed MP4 URL for the clip, or null if Twitch won't serve it. */
    suspend fun clipPlaybackUrl(slug: String, maxQuality: Int = 1080): String? =
        parseClipPlaybackUrl(post(buildClipPlaybackQuery(slug)), maxQuality)

    companion object {
        const val PAGE_SIZE = 30
    }
}

// ── Query builders (pure, unit-tested) ───────────────────────────────────────

private const val STREAM_FIELDS =
    "edges{cursor node{id title viewersCount createdAt broadcaster{id login displayName} game{id name} previewImageURL(width:440,height:248)}} pageInfo{hasNextPage}"

internal fun buildGameViewerCountsQuery(ids: List<String>): String =
    ids.mapIndexed { i, id -> "g$i:game(id:\"${id.filter { it.isDigit() }}\"){id viewersCount}" }
        .joinToString(" ", prefix = "query{", postfix = "}")

internal fun buildStreamsQuery(
    gameId: String?,
    languageEnum: String?,
    tags: List<String>,
    ascending: Boolean,
    after: String?,
    first: Int,
): String {
    val opts = buildList {
        add("sort:" + if (ascending) "VIEWER_COUNT_ASC" else "VIEWER_COUNT")
        languageEnum?.filter { it.isLetter() || it == '_' }?.takeIf { it.isNotEmpty() }?.let { add("broadcasterLanguages:[$it]") }
        val cleanTags = tags.map { t -> t.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
        if (cleanTags.isNotEmpty()) add("freeformTags:[" + cleanTags.joinToString(",") { "\"$it\"" } + "]")
    }.joinToString(",")
    val cursor = after?.filter { it.isLetterOrDigit() || it in "=+/_-" }?.takeIf { it.isNotEmpty() }
    val args = buildString {
        append("first:").append(first.coerceIn(1, 100))
        if (cursor != null) append(",after:\"").append(cursor).append('"')
        append(",options:{").append(opts).append('}')
    }
    val streams = "streams($args){$STREAM_FIELDS}"
    val safeGame = gameId?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
    return if (safeGame != null) "query{game(id:\"$safeGame\"){$streams}}" else "query{$streams}"
}

// ── Response parsers (pure, unit-tested) ─────────────────────────────────────

private fun JsonObject.throwIfErrors() {
    val errors = this["errors"]
    if (errors is JsonArray && errors.isNotEmpty()) {
        val msg = errors.firstOrNull()?.let { (it as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull } ?: "unknown error"
        throw DirectoryGqlException("Twitch GraphQL error: $msg")
    }
}

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
private fun JsonObject.int(key: String): Int? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull

internal fun parseGameViewerCounts(root: JsonObject): Map<String, Int> {
    root.throwIfErrors()
    val data = root["data"].obj() ?: throw DirectoryGqlException("GraphQL response had no data")
    val out = mutableMapOf<String, Int>()
    data.values.forEach { el ->
        val g = el.obj() ?: return@forEach
        val id = g.str("id") ?: return@forEach
        val viewers = g.int("viewersCount") ?: return@forEach
        out[id] = viewers
    }
    return out
}

internal fun parseStreamsPage(root: JsonObject, gameScoped: Boolean): StreamPage {
    root.throwIfErrors()
    val data = root["data"].obj() ?: throw DirectoryGqlException("GraphQL response had no data")
    val conn = if (gameScoped) {
        val game = data["game"]
        // A null game means the id matched nothing: an empty result, not an error.
        if (game == null || game is JsonNull) return StreamPage(emptyList(), null)
        game.obj()?.get("streams").obj()
    } else {
        data["streams"].obj()
    } ?: throw DirectoryGqlException("GraphQL response had no streams connection")

    val edges = (conn["edges"] as? JsonArray).orEmpty()
    val streams = edges.mapNotNull { e ->
        val node = e.obj()?.get("node").obj() ?: return@mapNotNull null
        val b = node["broadcaster"].obj() ?: return@mapNotNull null
        val login = b.str("login") ?: return@mapNotNull null
        val game = node["game"].obj()
        StreamInfo(
            id = node.str("id") ?: return@mapNotNull null,
            userId = b.str("id").orEmpty(),
            userLogin = login,
            userName = b.str("displayName") ?: login,
            gameId = game?.str("id").orEmpty(),
            gameName = game?.str("name").orEmpty(),
            title = node.str("title").orEmpty(),
            viewerCount = node.int("viewersCount") ?: 0,
            startedAt = node.str("createdAt").orEmpty(),
            language = "",
            thumbnailUrl = node.str("previewImageURL").orEmpty(),
        )
    }
    val hasNext = (conn["pageInfo"].obj()?.get("hasNextPage") as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull ?: false
    val lastCursor = edges.lastOrNull()?.obj()?.str("cursor")?.takeIf { it.isNotBlank() }
    return StreamPage(streams, if (hasNext) lastCursor else null)
}
