/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.sync.receiving.CallSignalJournal
import com.wire.kalium.logic.sync.receiving.CallSignalRecord
import platform.Foundation.NSLock
import kotlin.time.Clock

/** Close callbacks carry only a conversation. Capture their signal boundary before queued work can suspend. */
internal class AppleCallSignalScope(private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }) {
    private val lock = NSLock()
    private val signals = mutableMapOf<ConversationId, MutableMap<String, Long>>()

    fun include(signal: CallSignalRecord) = locked {
        expire()
        val conversation = ConversationId(signal.conversationValue, signal.conversationDomain)
        signals.getOrPut(conversation) { mutableMapOf() }[signal.id] = signal.storedAtMillis
    }

    fun snapshot(conversation: ConversationId): Set<String> = locked {
        expire()
        signals[conversation]?.keys?.toSet().orEmpty()
    }

    fun forget(conversation: ConversationId, signalIds: Set<String>) = locked {
        signals[conversation]?.let {
            it.keys.removeAll(signalIds)
            if (it.isEmpty()) signals.remove(conversation)
        }
        Unit
    }

    fun clear() = locked { signals.clear() }

    // Tracking must not retain expired journal IDs through hours-long group calls.
    private fun expire() {
        val cutoff = nowMillis() - CallSignalJournal.SIGNAL_RETENTION_MS
        signals.values.forEach { entries -> entries.entries.removeAll { it.value <= cutoff } }
        signals.entries.removeAll { it.value.isEmpty() }
    }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        return try { block() } finally { lock.unlock() }
    }
}
