/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.sync.receiving.CallSignalRecord
import com.wire.kalium.logic.sync.receiving.CallSignalStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Apple handle replay. Each lock covers one enqueue, so a foreground request can interrupt a drain. */
internal class CallSignalReplay(
    private val journal: CallSignalStore,
    private val generation: String,
    private val submit: suspend (CallSignalRecord) -> Unit,
    private val stale: (CallSignalRecord) -> Unit,
) {
    private val mutex = Mutex()
    private val priorities = mutableMapOf<Pair<String, String>, Int>()
    private val watermarks = mutableMapOf<Pair<String, String>, Long>()
    private val accepted = mutableSetOf<String>()

    suspend fun replay() {
        while (mutex.withLock { replayNext() }) Unit
    }

    suspend fun replayIncoming(callId: String) {
        val conversation = mutex.withLock {
            val source = journal.signals().firstOrNull { it.id == callId }
            check(source != null && source.isStartRequest()) { "Incoming call signal is unavailable" }
            source.conversation.also { priorities[it] = priorities.getOrElse(it) { 0 } + 1 }
        }
        try {
            // Other drainers may help replay this conversation, but this request never drains unrelated calls.
            while (mutex.withLock { replayNext(conversation) }) Unit
            mutex.withLock { requireCurrentStart(callId) }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    val remaining = priorities.getValue(conversation) - 1
                    if (remaining == 0) priorities.remove(conversation) else priorities[conversation] = remaining
                }
            }
        }
    }

    private suspend fun replayNext(only: Pair<String, String>? = null): Boolean {
        val pending = journal.signals().filter { it.submittedGeneration != generation }
        val signal = if (only != null) {
            pending.firstOrNull { it.conversation == only }
        } else {
            pending.firstOrNull { it.conversation in priorities } ?: pending.firstOrNull()
        } ?: return false
        if (signal.id !in accepted) {
            val watermark = watermarks[signal.conversation]
            if (watermark != null && signal.timeMillis < watermark) {
                stale(signal)
            } else {
                submit(signal)
                watermarks[signal.conversation] = signal.timeMillis
            }
        }
        // Native acceptance cannot be rolled back. Even a failed durable ack must not enqueue twice
        // into this live handle; a new generation still replays the retained journal after a crash.
        accepted.add(signal.id)
        withContext(NonCancellable) {
            journal.submitted(signal.id, generation)
            accepted.remove(signal.id)
        }
        return true
    }

    private val CallSignalRecord.conversation get() = conversationValue to conversationDomain

    /** Native commands address a conversation only. Do not answer a later, distinct queued start. */
    private suspend fun requireCurrentStart(callId: String) {
        val signals = journal.signals()
        val source = checkNotNull(signals.firstOrNull { it.id == callId }) { "Incoming call signal is unavailable" }
        // Group start broadcasts also arbitrate concurrent conferences. A distinct broadcast does
        // not prove that AVS replaced the ringing call, so leave group identity decisions to AVS.
        if (source.isGroup || source.signalType() != "SETUP") return
        val session = source.sessionId()
        val superseded = signals.any {
            it.id != callId && it.conversation == source.conversation && it.timeMillis >= source.timeMillis &&
                it.signalType() == "SETUP" && it.isStartRequest() && it.isDistinctSetupFrom(source, session)
        }
        check(!superseded) { "Incoming call was superseded by another start" }
    }

    private fun CallSignalRecord.isDistinctSetupFrom(source: CallSignalRecord, session: String?): Boolean =
        session == null || sessionId() != session || senderValue != source.senderValue || senderDomain != source.senderDomain

    private fun CallSignalRecord.signalType(): String? = runCatching {
        Json.parseToJsonElement(payload).jsonObject["type"]?.jsonPrimitive?.content?.uppercase()
    }.getOrNull()

    private fun CallSignalRecord.sessionId(): String? = runCatching {
        Json.parseToJsonElement(payload).jsonObject["sessid"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun CallSignalRecord.isStartRequest(): Boolean = runCatching {
        val json = Json.parseToJsonElement(payload).jsonObject
        json["type"]?.jsonPrimitive?.content?.uppercase() in setOf("SETUP", "GROUPSTART", "CONFSTART") &&
            json["resp"]?.jsonPrimitive?.booleanOrNull == false
    }.getOrDefault(false)
}
