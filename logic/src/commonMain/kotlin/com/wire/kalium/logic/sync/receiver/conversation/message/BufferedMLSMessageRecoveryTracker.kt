/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SubconversationId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Tracks prolonged CoreCrypto MLS message buffering so the client can initiate stale epoch recovery.
 *
 * Buffered event timestamps are tracked per conversation and subconversation. Recovery is requested when
 * their timestamp span exceeds [recoveryThreshold], allowing short-lived out-of-order delivery to resolve
 * without intervention. Tracking is kept in memory for the lifetime of the current user session and does not
 * survive process or session recreation.
 */
internal class BufferedMLSMessageRecoveryTracker(
    private val recoveryThreshold: Duration = DEFAULT_RECOVERY_THRESHOLD
) {
    private val bufferedMessageRanges = mutableMapOf<ConversationKey, TimestampRange>()
    private val mutex = Mutex()
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    suspend fun observeBufferedMessage(
        conversationId: ConversationId,
        subConversationId: SubconversationId?,
        timestamp: Instant
    ): Boolean = mutex.withLock {
        val key = ConversationKey(conversationId, subConversationId)
        val updatedRange = bufferedMessageRanges[key]?.include(timestamp) ?: TimestampRange(timestamp, timestamp)
        val bufferedTimeSpan = updatedRange.latest - updatedRange.earliest
        val shouldRecover = bufferedTimeSpan > recoveryThreshold

        logBufferedMessageObserved(key, timestamp, updatedRange, bufferedTimeSpan, shouldRecover)

        if (shouldRecover) {
            bufferedMessageRanges[key] = TimestampRange(timestamp, timestamp)
            true
        } else {
            bufferedMessageRanges[key] = updatedRange
            false
        }
    }

    suspend fun clear(
        conversationId: ConversationId,
        subConversationId: SubconversationId?
    ): Unit = mutex.withLock {
        val key = ConversationKey(conversationId, subConversationId)
        bufferedMessageRanges.remove(key)?.let { removedRange ->
            logTrackingCleared(key, removedRange)
        }
    }

    private data class ConversationKey(
        val conversationId: ConversationId,
        val subConversationId: SubconversationId?
    )

    private data class TimestampRange(
        val earliest: Instant,
        val latest: Instant
    ) {
        fun include(timestamp: Instant) = TimestampRange(
            earliest = minOf(earliest, timestamp),
            latest = maxOf(latest, timestamp)
        )
    }

    private fun logBufferedMessageObserved(
        key: ConversationKey,
        timestamp: Instant,
        range: TimestampRange,
        bufferedTimeSpan: Duration,
        shouldRecover: Boolean
    ) {
        logger.logStructuredJson(
            level = KaliumLogLevel.DEBUG,
            leadingMessage = if (shouldRecover) {
                "Buffered MLS message recovery triggered"
            } else {
                "Buffered MLS message recovery tracking updated"
            },
            jsonStringKeyValues = mapOf(
                "conversationId" to key.conversationId.toLogString(),
                "subConversationId" to key.subConversationId?.toLogString(),
                "messageTimestamp" to timestamp,
                "earliestBufferedTimestamp" to range.earliest,
                "latestBufferedTimestamp" to range.latest,
                "bufferedTimeSpan" to bufferedTimeSpan.toString(),
                "recoveryThreshold" to recoveryThreshold.toString()
            )
        )
    }

    private fun logTrackingCleared(key: ConversationKey, range: TimestampRange) {
        logger.logStructuredJson(
            level = KaliumLogLevel.DEBUG,
            leadingMessage = "Buffered MLS message recovery tracking cleared",
            jsonStringKeyValues = mapOf(
                "conversationId" to key.conversationId.toLogString(),
                "subConversationId" to key.subConversationId?.toLogString(),
                "earliestBufferedTimestamp" to range.earliest,
                "latestBufferedTimestamp" to range.latest
            )
        )
    }

    private companion object {
        val DEFAULT_RECOVERY_THRESHOLD = 1.minutes
    }
}
