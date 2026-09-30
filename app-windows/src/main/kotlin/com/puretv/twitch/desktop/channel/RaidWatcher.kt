package com.puretv.twitch.desktop.channel

import com.puretv.twitch.core.api.TwitchConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** The channel you're watching just raided [toLogin]. */
data class RaidEvent(val toLogin: String, val toName: String, val viewers: Int)

/** The EventSub WebSocket messages this client cares about. */
internal sealed interface EventSubMessage {
    data class Welcome(val sessionId: String) : EventSubMessage
    data class Reconnect(val url: String) : EventSubMessage
    data class Raid(val event: RaidEvent) : EventSubMessage
    data object Other : EventSubMessage
}

private val json = Json { ignoreUnknownKeys = true }

private fun JsonObject.obj(key: String) = this[key] as? JsonObject
private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun parseEventSubMessage(text: String): EventSubMessage {
    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return EventSubMessage.Other
    val type = root.obj("metadata")?.str("message_type")
    val payload = root.obj("payload") ?: return EventSubMessage.Other
    return when (type) {
        "session_welcome" -> payload.obj("session")?.str("id")?.let { EventSubMessage.Welcome(it) } ?: EventSubMessage.Other
        "session_reconnect" -> payload.obj("session")?.str("reconnect_url")?.let { EventSubMessage.Reconnect(it) } ?: EventSubMessage.Other
        "notification" -> {
            if (payload.obj("subscription")?.str("type") != "channel.raid") return EventSubMessage.Other
            val e = payload.obj("event") ?: return EventSubMessage.Other
            val login = e.str("to_broadcaster_user_login")?.takeIf { it.isNotBlank() } ?: return EventSubMessage.Other
            EventSubMessage.Raid(
                RaidEvent(
                    toLogin = login,
                    toName = e.str("to_broadcaster_user_name")?.takeIf { it.isNotBlank() } ?: login,
                    viewers = (e["viewers"] as? JsonPrimitive)?.intOrNull ?: 0,
                ),
            )
        }
        else -> EventSubMessage.Other
    }
}

internal fun buildRaidSubscriptionBody(fromBroadcasterId: String, sessionId: String): String = buildJsonObject {
    put("type", "channel.raid")
    put("version", "1")
    putJsonObject("condition") { put("from_broadcaster_user_id", fromBroadcasterId) }
    putJsonObject("transport") {
        put("method", "websocket")
        put("session_id", sessionId)
    }
}.toString()

/**
 * Tells you when the channel you're watching raids someone, using Twitch's
 * official EventSub WebSocket (the `channel.raid` event needs no special
 * permission, only a signed-in user token).
 *
 * The flow stays connected for as long as it's collected: it follows Twitch's
 * reconnect hand-offs, and after a dropped connection it waits and starts a fresh
 * session. It ends quietly if nobody is signed in or Twitch refuses the
 * subscription, since raids are a nice-to-have, never an error to show.
 */
class RaidWatcher(
    private val httpClient: HttpClient,
    private val token: () -> String?,
) {
    fun watch(broadcasterId: String): Flow<RaidEvent> = channelFlow {
        if (broadcasterId.isBlank()) return@channelFlow
        // Named so the socket session's own send(Frame) can't be picked by mistake.
        val events = this
        var url = EVENTSUB_URL
        while (isActive) {
            val tok = token() ?: return@channelFlow
            var next: String? = null
            var refused = false
            try {
                httpClient.webSocket(url) {
                    while (true) {
                        // Twitch sends a keepalive every ~10s; silence well past that means a dead socket.
                        val frame = withTimeoutOrNull(KEEPALIVE_GRACE_MS) { incoming.receive() } ?: break
                        val text = (frame as? Frame.Text)?.readText() ?: continue
                        when (val msg = parseEventSubMessage(text)) {
                            // A reconnect URL session inherits the old subscriptions; only a fresh one needs subscribing.
                            is EventSubMessage.Welcome -> if (url == EVENTSUB_URL && !subscribe(tok, broadcasterId, msg.sessionId)) {
                                refused = true
                                break
                            }
                            is EventSubMessage.Raid -> events.send(msg.event)
                            is EventSubMessage.Reconnect -> {
                                next = msg.url
                                break
                            }
                            EventSubMessage.Other -> {}
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Network hiccup: fall through to a delayed fresh reconnect.
            }
            if (refused) return@channelFlow
            val reconnect = next
            if (reconnect != null && reconnect.startsWith("wss://")) {
                url = reconnect
            } else {
                url = EVENTSUB_URL
                delay(RETRY_DELAY_MS)
            }
        }
    }

    private suspend fun subscribe(token: String, broadcasterId: String, sessionId: String): Boolean = runCatching {
        httpClient.post("${TwitchConfig.API_BASE}/eventsub/subscriptions") {
            header("Client-Id", TwitchConfig.CLIENT_ID)
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(buildRaidSubscriptionBody(broadcasterId, sessionId))
        }
    }.isSuccess

    companion object {
        const val EVENTSUB_URL = "wss://eventsub.wss.twitch.tv/ws"
        private const val KEEPALIVE_GRACE_MS = 30_000L
        private const val RETRY_DELAY_MS = 15_000L
    }
}
