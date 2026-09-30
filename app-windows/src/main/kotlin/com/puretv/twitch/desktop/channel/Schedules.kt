package com.puretv.twitch.desktop.channel

import com.puretv.twitch.core.api.HelixSchedule
import com.puretv.twitch.core.api.TwitchApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** A followed channel whose schedule we can look up. */
data class ScheduleChannel(val id: String, val login: String, val displayName: String)

/** The next stream a followed channel has on its Twitch schedule. */
data class UpcomingStream(
    val login: String,
    val displayName: String,
    val title: String,
    val category: String,
    val startEpochSec: Long,
)

private fun parseEpoch(iso: String?): Long? =
    iso?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }

/**
 * Each channel's next scheduled stream within [horizonSec], soonest first.
 * Cancelled occurrences and anything inside the channel's vacation are skipped.
 * One entry per channel, so a daily streamer doesn't fill the whole shelf.
 */
fun upcomingFrom(
    schedules: List<HelixSchedule>,
    nowSec: Long,
    horizonSec: Long = 7 * 24 * 3600L,
    limit: Int = 12,
): List<UpcomingStream> = schedules.mapNotNull { s ->
    val vacStart = parseEpoch(s.vacation?.start_time)
    val vacEnd = parseEpoch(s.vacation?.end_time)
    s.segments.orEmpty()
        .asSequence()
        .filter { it.canceled_until == null }
        .mapNotNull { seg -> parseEpoch(seg.start_time)?.let { seg to it } }
        .filter { (_, start) -> start > nowSec && start <= nowSec + horizonSec }
        .filterNot { (_, start) -> vacStart != null && vacEnd != null && start in vacStart..vacEnd }
        .minByOrNull { it.second }
        ?.let { (seg, start) ->
            UpcomingStream(
                login = s.broadcaster_login,
                displayName = s.broadcaster_name.ifBlank { s.broadcaster_login },
                title = seg.title,
                category = seg.category?.name.orEmpty(),
                startEpochSec = start,
            )
        }
}.sortedBy { it.startEpochSec }.take(limit)

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val dayFormat = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/** "In 25 min", "Today, 8:00 PM", "Tomorrow, 6:30 PM", "Fri, 7:00 PM". */
fun formatScheduleTime(startSec: Long, nowSec: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val minutes = (startSec - nowSec) / 60
    if (minutes in 0..59) return if (minutes <= 1) "Starting now" else "In $minutes min"
    val start = Instant.ofEpochSecond(startSec).atZone(zone)
    val today = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
    val day: LocalDate = start.toLocalDate()
    val time = start.format(timeFormat)
    return when (day) {
        today -> "Today, $time"
        today.plusDays(1) -> "Tomorrow, $time"
        else -> if (day.isBefore(today.plusDays(7))) "${start.format(dayFormat)}, $time" else "${start.format(dateFormat)}, $time"
    }
}

/**
 * Upcoming streams from the channels you follow, from Twitch's official
 * schedule data. Each channel's schedule is cached for half an hour, so opening
 * Home again doesn't refetch everything.
 */
class ScheduleService(
    private val api: TwitchApiClient,
    private val follows: suspend () -> List<ScheduleChannel>,
) {
    private val cache = ConcurrentHashMap<String, Pair<Long, HelixSchedule?>>()

    suspend fun upcoming(): List<UpcomingStream> = withContext(Dispatchers.IO) {
        val channels = runCatching { follows() }.getOrDefault(emptyList())
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .take(MAX_CHANNELS)
        val now = System.currentTimeMillis() / 1000
        val gate = Semaphore(8)
        val schedules = coroutineScope {
            channels.map { ch -> async { gate.withPermit { scheduleFor(ch.id, now) } } }.awaitAll()
        }.filterNotNull()
        upcomingFrom(schedules, now)
    }

    private suspend fun scheduleFor(id: String, now: Long): HelixSchedule? {
        cache[id]?.let { (at, schedule) -> if (now - at < CACHE_SECONDS) return schedule }
        val schedule = runCatching { api.getSchedule(id, first = 5) }.getOrElse { return null }
        cache[id] = now to schedule
        return schedule
    }

    private companion object {
        const val MAX_CHANNELS = 200
        const val CACHE_SECONDS = 30 * 60L
    }
}
