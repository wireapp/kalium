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

package com.wire.kalium.calling.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultAvsCallNotificationProcessorTest {

    @Test
    fun givenMultipleEvents_whenProcessing_thenStartsProcessesAllEventsAndEnds() {
        val gateway = FakeAvsCallNotificationNativeGateway()
        val processor = DefaultAvsCallNotificationProcessor(gateway)

        val result = processor.process(listOf(event("first"), event("second")))

        assertEquals(AvsCallNotificationProcessingResult.Success, result)
        assertEquals(
            listOf("start", "process:first:0", "process:second:1", "end"),
            gateway.calls
        )
    }

    @Test
    fun givenEmptyEvents_whenProcessing_thenReturnsEmptyEventsWithoutNativeCalls() {
        val gateway = FakeAvsCallNotificationNativeGateway()
        val processor = DefaultAvsCallNotificationProcessor(gateway)

        val result = processor.process(emptyList())

        assertEquals(
            AvsCallNotificationProcessingResult.Failure(AvsCallNotificationProcessingFailure.EmptyEvents),
            result
        )
        assertEquals(emptyList(), gateway.calls)
    }

    @Test
    fun givenEventWithEmptyPayload_whenProcessing_thenReturnsEmptyPayloadWithoutNativeCalls() {
        val gateway = FakeAvsCallNotificationNativeGateway()
        val processor = DefaultAvsCallNotificationProcessor(gateway)

        val result = processor.process(listOf(event("first"), event("second", payload = byteArrayOf())))

        assertEquals(
            AvsCallNotificationProcessingResult.Failure(AvsCallNotificationProcessingFailure.EmptyPayload(eventIndex = 1)),
            result
        )
        assertEquals(emptyList(), gateway.calls)
    }

    @Test
    fun givenNativeStartFailure_whenProcessing_thenReturnsNativeFailureWithoutEnding() {
        val failure = nativeFailure(AvsCallNotificationNativeOperation.Start)
        val gateway = FakeAvsCallNotificationNativeGateway(startResult = AvsCallNotificationNativeResult.Failure(failure))
        val processor = DefaultAvsCallNotificationProcessor(gateway)

        val result = processor.process(listOf(event("first")))

        assertEquals(AvsCallNotificationProcessingResult.Failure(failure), result)
        assertEquals(listOf("start"), gateway.calls)
    }

    @Test
    fun givenNativeProcessFailure_whenProcessing_thenReturnsFailureAndEnds() {
        val failure = nativeFailure(AvsCallNotificationNativeOperation.Process, eventIndex = 1)
        val gateway = FakeAvsCallNotificationNativeGateway(
            processResults = mapOf(1 to AvsCallNotificationNativeResult.Failure(failure))
        )
        val processor = DefaultAvsCallNotificationProcessor(gateway)

        val result = processor.process(listOf(event("first"), event("second"), event("third")))

        assertEquals(AvsCallNotificationProcessingResult.Failure(failure), result)
        assertEquals(
            listOf("start", "process:first:0", "process:second:1", "end"),
            gateway.calls
        )
    }

    @Test
    fun givenNativeEndFailure_whenProcessing_thenReturnsEndFailure() {
        val failure = nativeFailure(AvsCallNotificationNativeOperation.End)
        val gateway = FakeAvsCallNotificationNativeGateway(endResult = AvsCallNotificationNativeResult.Failure(failure))
        val processor = DefaultAvsCallNotificationProcessor(gateway)

        val result = processor.process(listOf(event("first")))

        assertEquals(AvsCallNotificationProcessingResult.Failure(failure), result)
        assertEquals(listOf("start", "process:first:0", "end"), gateway.calls)
    }

    @Test
    fun givenProcessorClosed_whenClosing_thenNativeGatewayIsClosed() {
        val gateway = FakeAvsCallNotificationNativeGateway()
        val processor = DefaultAvsCallNotificationProcessor(gateway)

        processor.close()

        assertEquals(listOf("close"), gateway.calls)
    }

    private fun event(id: String, payload: ByteArray = byteArrayOf(1)): AvsCallNotification = AvsCallNotification(
        payload = payload,
        currentTimeSeconds = 10u,
        messageTimeSeconds = 5u,
        conversationId = id,
        senderUserId = "sender-user",
        senderClientId = "sender-client",
        conversationType = AvsCallNotificationConversationType.OneOnOne
    )

    private fun nativeFailure(
        operation: AvsCallNotificationNativeOperation,
        eventIndex: Int? = null
    ): AvsCallNotificationProcessingFailure.NativeFailure =
        AvsCallNotificationProcessingFailure.NativeFailure(
            operation = operation,
            code = -1,
            eventIndex = eventIndex
        )

    private class FakeAvsCallNotificationNativeGateway(
        private val startResult: AvsCallNotificationNativeResult = AvsCallNotificationNativeResult.Success,
        private val processResults: Map<Int, AvsCallNotificationNativeResult> = emptyMap(),
        private val endResult: AvsCallNotificationNativeResult = AvsCallNotificationNativeResult.Success
    ) : AvsCallNotificationNativeGateway {

        val calls = mutableListOf<String>()

        override fun start(): AvsCallNotificationNativeResult {
            calls += "start"
            return startResult
        }

        override fun process(notification: AvsCallNotification, notificationIndex: Int): AvsCallNotificationNativeResult {
            calls += "process:${notification.conversationId}:$notificationIndex"
            return processResults[notificationIndex] ?: AvsCallNotificationNativeResult.Success
        }

        override fun end(): AvsCallNotificationNativeResult {
            calls += "end"
            return endResult
        }

        override fun close() {
            calls += "close"
        }
    }
}
