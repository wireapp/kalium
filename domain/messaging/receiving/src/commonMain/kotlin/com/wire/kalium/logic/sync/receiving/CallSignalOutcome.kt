/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class CallSignalOutcome(val source: CallSignalRecord, val missed: Boolean, val complete: Boolean = true)

/**
 * Only used after the receiving prefix is complete. An expired SETUP is no longer answerable;
 * a conference can still be joined, so its age alone never proves that it ended.
 * Own-device participation wins over a timeout or cancellation, including a response using
 * another session ID (AVS's one-to-one arbitration). Bound it by the next distinct call.
 */
internal fun reconcileCallSignals(
    signals: List<CallSignalRecord>,
    pendingMessageIds: Set<String>,
    self: QualifiedIDEntity,
    nowMillis: Long,
): List<CallSignalOutcome> {
    val parsed = signals.filter { it.ready }.mapNotNull { signal -> signal.parseCallSignal()?.let { signal to it } }
    val starts = parsed.filter { (signal, payload) -> payload.isStart && !signal.from(self) }
        .sortedBy { it.first.timeMillis }
    return starts.distinctBy { (signal, payload) ->
        listOf(signal.conversationValue, signal.conversationDomain, signal.senderValue, signal.senderDomain, payload.session)
    }.mapNotNull { (source, start) ->
        if (source.messageId !in pendingMessageIds) return@mapNotNull null
        val nextStart = starts.nextStartTime(source, start.session)
        if (nextStart == source.timeMillis) return@mapNotNull null // Tied calls have ambiguous response ownership.
        val related = parsed.inCallWindow(source, nextStart)
        val handled = related.any { (other, payload) ->
            other.from(self) && payload.handles(start)
        }
        val ended = related.any { (other, payload) -> payload.ends(source, start, other) }
        val expired = !source.isGroup && start.type == "SETUP" && nowMillis - source.timeMillis > SETUP_TIMEOUT_MILLIS
        if (handled || ended || expired) {
            CallSignalOutcome(source, missed = !handled, complete = !source.isGroup || ended)
        } else null
    }
}

private fun List<Pair<CallSignalRecord, CallSignalPayload>>.nextStartTime(source: CallSignalRecord, session: String) =
    firstOrNull { (other, payload) ->
        other.sameConversation(source) && other.timeMillis >= source.timeMillis && payload.session != session
    }?.first?.timeMillis

private fun List<Pair<CallSignalRecord, CallSignalPayload>>.inCallWindow(source: CallSignalRecord, nextStart: Long?) =
    filter { (other, _) ->
        other.sameConversation(source) && other.timeMillis >= source.timeMillis &&
            (nextStart == null || other.timeMillis < nextStart)
    }

private fun CallSignalPayload.handles(start: CallSignalPayload): Boolean = when {
    type == "REJECT" -> true
    type in START_TYPES && response -> true
    else -> isStart && type != "SETUP" && session == start.session
}

private fun CallSignalPayload.ends(source: CallSignalRecord, start: CallSignalPayload, other: CallSignalRecord): Boolean {
    if (session != start.session || response) return false
    return if (start.type != "SETUP") {
        type == "CONFEND"
    } else {
        type == "CANCEL" && other.senderValue == source.senderValue &&
            other.senderDomain == source.senderDomain && other.senderClientId == source.senderClientId
    }
}

private data class CallSignalPayload(val type: String, val session: String, val response: Boolean) {
    val isStart: Boolean get() = type in START_TYPES && !response
}

private fun CallSignalRecord.parseCallSignal(): CallSignalPayload? = runCatching {
    val json = Json.parseToJsonElement(payload).jsonObject
    if (json["version"]?.jsonPrimitive?.content != "3.0") return@runCatching null
    val type = json["type"]?.jsonPrimitive?.takeIf { it.isString }?.content ?: return@runCatching null
    val session = json["sessid"]?.jsonPrimitive?.takeIf { it.isString }?.content ?: return@runCatching null
    val response = json["resp"]?.jsonPrimitive?.booleanOrNull ?: return@runCatching null
    val sdp = json["sdp"]?.jsonPrimitive?.takeIf { it.isString }?.content
    if (type == "SETUP" && sdp.isNullOrBlank()) return@runCatching null
    // REJECT can intentionally carry an empty session and is correlated by the call's time window.
    if (session.isBlank() && type != "REJECT") return@runCatching null
    CallSignalPayload(type, session, response)
}.getOrNull()

private fun CallSignalRecord.from(user: QualifiedIDEntity) = senderValue == user.value && senderDomain == user.domain
private fun CallSignalRecord.sameConversation(other: CallSignalRecord) =
    conversationValue == other.conversationValue && conversationDomain == other.conversationDomain

private val START_TYPES = setOf("SETUP", "GROUPSTART", "CONFSTART")
private const val SETUP_TIMEOUT_MILLIS = 60_000L
