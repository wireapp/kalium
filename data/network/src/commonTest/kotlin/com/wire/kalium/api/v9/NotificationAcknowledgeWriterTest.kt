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

package com.wire.kalium.api.v9

import com.wire.kalium.network.api.base.authenticated.notification.EventAcknowledgeResult
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.v9.authenticated.acknowledgeWithLocalWriterFlush
import com.wire.kalium.network.api.v9.authenticated.decodeConsumableNotificationFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NotificationAcknowledgeWriterTest {
    @Test
    fun givenUnknownWireFields_whenDecodingConsumableEvent_thenExactBytesAreRetained() {
        val raw = CONSUMABLE_EVENT_WITH_UNKNOWN_FIELDS.encodeToByteArray()

        val decoded = decodeConsumableNotificationFrame(raw)

        assertIs<ConsumableNotificationResponse.EventNotification>(decoded.payload)
        assertTrue(requireNotNull(decoded.rawPayload).contentEquals(raw))
        assertTrue(decoded.rawPayload!!.decodeToString().contains("\"future_outer\""))
    }

    @Test
    fun givenOversizedConsumableFrame_whenDecoding_thenItIsRejectedBeforeJsonProcessing() {
        val oversized = ByteArray(com.wire.kalium.network.api.v9.authenticated.MAX_CONSUMABLE_NOTIFICATION_FRAME_BYTES + 1)

        assertFailsWith<IllegalArgumentException> {
            decodeConsumableNotificationFrame(oversized)
        }
    }

    @Test
    fun givenWriterRejectsFrame_whenAcknowledging_thenFailureIsRetryableAndFlushIsSkipped() = runTest {
        val channel = Channel<Unit>(Channel.RENDEZVOUS)
        var flushCalls = 0

        val result = acknowledgeWithLocalWriterFlush(
            enqueue = { channel.trySend(Unit) },
            flush = { flushCalls += 1 },
            writerIsActive = { true }
        )

        assertEquals(EventAcknowledgeResult.RETRYABLE_FAILURE, result)
        assertEquals(0, flushCalls)
        channel.close()
    }

    @Test
    fun givenWriterAcceptsAndFlushesFrame_whenAcknowledging_thenAcceptanceIsReported() = runTest {
        val channel = Channel<Unit>(Channel.UNLIMITED)
        var flushCalls = 0

        val result = acknowledgeWithLocalWriterFlush(
            enqueue = { channel.trySend(Unit) },
            flush = { flushCalls += 1 },
            writerIsActive = { true }
        )

        assertEquals(EventAcknowledgeResult.ACCEPTED_BY_LOCAL_WRITER, result)
        assertEquals(1, flushCalls)
        channel.close()
    }

    @Test
    fun givenFlushFails_whenAcknowledging_thenFailureIsRetryable() = runTest {
        val channel = Channel<Unit>(Channel.UNLIMITED)

        val result = acknowledgeWithLocalWriterFlush(
            enqueue = { channel.trySend(Unit) },
            flush = { error("writer-flush-failed") },
            writerIsActive = { true }
        )

        assertEquals(EventAcknowledgeResult.RETRYABLE_FAILURE, result)
        channel.close()
    }

    @Test
    fun givenFlushIsCancelled_whenAcknowledging_thenCancellationPropagates() = runTest {
        val channel = Channel<Unit>(Channel.UNLIMITED)

        assertFailsWith<CancellationException> {
            acknowledgeWithLocalWriterFlush(
                enqueue = { channel.trySend(Unit) },
                flush = { throw CancellationException("cancelled") },
                writerIsActive = { true }
            )
        }
        channel.close()
    }

    @Test
    fun givenTerminatedWriterWithNoOpFlush_whenAcknowledging_thenFailureIsRetryable() = runTest {
        val channel = Channel<Unit>(Channel.UNLIMITED)

        val result = acknowledgeWithLocalWriterFlush(
            enqueue = { channel.trySend(Unit) },
            flush = {},
            writerIsActive = { false }
        )

        assertEquals(EventAcknowledgeResult.RETRYABLE_FAILURE, result)
        channel.close()
    }

    private companion object {
        const val CONSUMABLE_EVENT_WITH_UNKNOWN_FIELDS =
            """{"type":"event","data":{"delivery_tag":7,"event":{"id":"event-id","payload":[],"transient":false,"future_inner":"kept"}},"future_outer":{"version":2}}"""
    }
}
