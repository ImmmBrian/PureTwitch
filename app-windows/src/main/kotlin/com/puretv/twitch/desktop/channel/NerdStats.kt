package com.puretv.twitch.desktop.channel

import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.MessagePart
import com.puretv.twitch.desktop.data.ViewerSample
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/** TwitchTracker's rolling 30-day summary for a channel. */
data class TrackerSummary(
    val rank: Long?,
    val minutesStreamed: Long,
    val avgViewers: Long,
    val maxViewers: Long,
    val hoursWatched: Long,
    val followersGained: Long,
    val followersTotal: Long?,
) {
    val hoursStreamed: Double get() = minutesStreamed / 60.0
    val followersPerDay: Double get() = followersGained / 30.0
    /** Followers gained per hour live: how well streaming converts to follows. */
    val followersPerStreamHour: Double? get() = if (minutesStreamed > 0) followersGained / hoursStreamed else null
}

private val trackerJson = Json { ignoreUnknownKeys = true }

/** Parses the /api/channels/summary response. Null when TwitchTracker has nothing (small or new channels). */
internal fun parseTrackerSummary(text: String): TrackerSummary? {
    val o = runCatching { trackerJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
    fun n(key: String) = (o[key] as? JsonPrimitive)?.longOrNull
    val minutes = n("minutes_streamed") ?: return null
    return TrackerSummary(
        rank = n("rank")?.takeIf { it > 0 },
        minutesStreamed = minutes,
        avgViewers = n("avg_viewers") ?: 0,
        maxViewers = n("max_viewers") ?: 0,
        hoursWatched = n("hours_watched") ?: 0,
        followersGained = n("followers") ?: 0,
        followersTotal = n("followers_total"),
    )
}

/** Reads TwitchTracker's public per-channel summary. Best-effort: null on any failure. */
class TwitchTrackerClient(private val httpClient: HttpClient) {
    suspend fun summary(login: String): TrackerSummary? = runCatching {
        val safe = sanitizeLogin(login).lowercase()
        if (safe.isEmpty()) return null
        val text: String = httpClient.get("https://twitchtracker.com/api/channels/summary/$safe") {
            header("Accept", "application/json")
            header("User-Agent", "PureTV (desktop Twitch client)")
        }.body()
        parseTrackerSummary(text)
    }.getOrNull()
}

// ── This stream ──────────────────────────────────────────────────────────────

data class SessionViewers(val current: Int, val peak: Int, val low: Int, val average: Int, val samples: List<Int>)

/** Viewer samples PureTV recorded since this stream started. Null until there are two. */
fun sessionViewers(samples: List<ViewerSample>, startedAtIso: String?): SessionViewers? {
    val start = startedAtIso?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() } ?: return null
    val points = samples.filter { it.epochSec >= start }.map { it.viewers }
    if (points.size < 2) return null
    return SessionViewers(
        current = points.last(),
        peak = points.max(),
        low = points.min(),
        average = (points.sumOf { it.toLong() } / points.size).toInt(),
        samples = points,
    )
}

// ── Chat ─────────────────────────────────────────────────────────────────────

data class ChatStats(
    val messagesPerMinute: Double,
    val uniqueChatters: Int,
    /** Share of chatters showing a sub badge, 0..1. */
    val subscriberShare: Double,
    val moderatorsActive: Int,
    /** Most-used emote names with counts, highest first. */
    val topEmotes: List<Pair<String, Int>>,
    /** How far back the sample reaches, in seconds (the buffer holds the last ~200 messages). */
    val windowSeconds: Long,
)

/**
 * Live chat analytics over the messages currently in the buffer, limited to the
 * last [windowMs]. System lines (subs, raids) are left out.
 */
fun chatStats(messages: List<ChatMessage>, nowMs: Long = System.currentTimeMillis(), windowMs: Long = 5 * 60_000L): ChatStats? {
    val recent = messages.filter { !it.isSystem && it.timestamp >= nowMs - windowMs }
    if (recent.size < 3) return null
    val first = recent.minOf { it.timestamp }
    val spanMs = (nowMs - first).coerceAtLeast(1_000L)
    val byUser = recent.groupBy { it.username.lowercase() }
    val subs = byUser.values.count { msgs -> msgs.any { it.isSubscriber } }
    val mods = byUser.values.count { msgs -> msgs.any { it.isModerator } }
    val emotes = recent.flatMap { m ->
        m.parsedParts.mapNotNull { p ->
            when (p) {
                is MessagePart.TwitchEmote -> p.name
                is MessagePart.ThirdPartyEmote -> p.name
                is MessagePart.Text -> null
            }
        }
    }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
    return ChatStats(
        messagesPerMinute = recent.size * 60_000.0 / spanMs,
        uniqueChatters = byUser.size,
        subscriberShare = subs.toDouble() / byUser.size,
        moderatorsActive = mods,
        topEmotes = emotes,
        windowSeconds = spanMs / 1000,
    )
}

// ── Formatting ───────────────────────────────────────────────────────────────

/** 1234 -> "1,234"; big numbers get K/M/B with one decimal. */
fun formatStat(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1fB".format(n / 1e9)
    n >= 1_000_000 -> "%.1fM".format(n / 1e6)
    n >= 100_000 -> "%.0fK".format(n / 1e3)
    else -> "%,d".format(n)
}

fun formatDecimal(d: Double): String = if (d >= 100) "%,.0f".format(d) else "%.1f".format(d)
