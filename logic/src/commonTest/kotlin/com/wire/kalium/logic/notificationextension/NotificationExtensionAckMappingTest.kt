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

package com.wire.kalium.logic.notificationextension

import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventDataDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponseToStore
import com.wire.kalium.network.api.base.authenticated.notification.EventAcknowledgeResult
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NotificationExtensionAckMappingTest {
    @Test
    fun givenLocalWriterAcceptance_whenMappingAck_thenAcceptedIsReturned() {
        assertEquals(
            NotificationExtensionLogicTransportAckStatus.ACCEPTED_BY_LOCAL_WRITER,
            EventAcknowledgeResult.ACCEPTED_BY_LOCAL_WRITER.toLogicTransportAckStatus()
        )
    }

    @Test
    fun givenRetryableWriterFailure_whenMappingAck_thenRetryableRejectionIsReturned() {
        assertEquals(
            NotificationExtensionLogicTransportAckStatus.REJECTED_RETRYABLE,
            EventAcknowledgeResult.RETRYABLE_FAILURE.toLogicTransportAckStatus()
        )
    }

    @Test
    fun givenTerminalWriterFailure_whenMappingAck_thenTerminalRejectionIsReturned() {
        assertEquals(
            NotificationExtensionLogicTransportAckStatus.REJECTED_TERMINAL,
            EventAcknowledgeResult.TERMINAL_FAILURE.toLogicTransportAckStatus()
        )
    }

    @Test
    fun givenUnsupportedItemEmitsNoMessage_whenSelectingNextIndex_thenFollowingMessageRemainsContiguous() {
        val afterUnsupported = nextReceiveChildIndex(currentIndex = 0, emittedMessageCount = 0)
        val afterMessage = nextReceiveChildIndex(currentIndex = afterUnsupported, emittedMessageCount = 1)

        assertEquals(0, afterUnsupported)
        assertEquals(1, afterMessage)
    }

    @Test
    fun givenMlsHandshakeBeforeMessage_whenAssigningChildren_thenFollowingPayloadDoesNotCollide() {
        val emittedAfterHandshakeOnlyBundle = 0
        val mlsMessageIndex = nextEmittedMlsChildIndex(
            itemIndex = 0,
            emittedMessageCount = emittedAfterHandshakeOnlyBundle
        )
        val nextPayloadIndex = nextReceiveChildIndex(
            currentIndex = 0,
            emittedMessageCount = emittedAfterHandshakeOnlyBundle + 1
        )

        assertEquals(0, mlsMessageIndex)
        assertEquals(1, nextPayloadIndex)
    }

    @Test
    fun givenExactConsumableWireBytes_whenMappingNseEvent_thenUnknownFieldsRemainInRawEnvelope() {
        val raw = CONSUMABLE_EVENT_WITH_UNKNOWN_FIELDS.encodeToByteArray()
        val event: WebSocketEvent.BinaryPayloadReceived<ConsumableNotificationResponse> =
            WebSocketEvent.BinaryPayloadReceived(
            payload = ConsumableNotificationResponse.EventNotification(
                EventDataDTO(
                    deliveryTag = 7u,
                    event = EventResponseToStore(id = "event-id", payload = "[]")
                )
            ),
            rawPayload = raw
        )

        val frame = assertIs<NotificationExtensionLogicTransportFrame.Event>(event.toLogicFrame())

        assertEquals(EXACT_EVENT_SUBOBJECT, frame.rawEnvelope.decodeToString())
        assertEquals("event-id", decodeNotificationExtensionStoredEvent(frame.rawEnvelope).id)
        assertTrue(frame.rawEnvelope.decodeToString().contains("\"future_inner\""))
        assertTrue(!frame.rawEnvelope.decodeToString().contains("delivery_tag"))
        assertTrue(!frame.rawEnvelope.decodeToString().contains("future_outer"))
    }

    @Test
    fun givenDuplicateDataMembers_whenMappingNseEvent_thenFrameIsRejected() {
        val event: WebSocketEvent.BinaryPayloadReceived<ConsumableNotificationResponse> =
            WebSocketEvent.BinaryPayloadReceived(
                payload = ConsumableNotificationResponse.EventNotification(
                    EventDataDTO(
                        deliveryTag = 8u,
                        event = EventResponseToStore(id = "event-b", payload = "[]")
                    )
                ),
                rawPayload = CONSUMABLE_EVENT_WITH_DUPLICATE_DATA.encodeToByteArray()
            )

        assertFailsWith<IllegalStateException> { event.toLogicFrame() }
    }

    @Test
    fun givenDuplicateEventMembers_whenMappingNseEvent_thenFrameIsRejected() {
        val event: WebSocketEvent.BinaryPayloadReceived<ConsumableNotificationResponse> =
            WebSocketEvent.BinaryPayloadReceived(
                payload = ConsumableNotificationResponse.EventNotification(
                    EventDataDTO(
                        deliveryTag = 8u,
                        event = EventResponseToStore(id = "event-b", payload = "[]")
                    )
                ),
                rawPayload = CONSUMABLE_EVENT_WITH_DUPLICATE_EVENT.encodeToByteArray()
            )

        assertFailsWith<IllegalStateException> { event.toLogicFrame() }
    }

    private companion object {
        const val CONSUMABLE_EVENT_WITH_UNKNOWN_FIELDS =
            """{"type":"event","data":{"delivery_tag":7,"event":{"id":"event-id","payload":[],"transient":false,"future_inner":"kept"}},"future_outer":{"version":2}}"""
        const val EXACT_EVENT_SUBOBJECT =
            """{"id":"event-id","payload":[],"transient":false,"future_inner":"kept"}"""
        const val CONSUMABLE_EVENT_WITH_DUPLICATE_DATA =
            """{"type":"event","data":{"delivery_tag":7,"event":{"id":"event-a","payload":[]}},"d\u0061ta":{"delivery_tag":8,"event":{"id":"event-b","payload":[]}}}"""
        const val CONSUMABLE_EVENT_WITH_DUPLICATE_EVENT =
            """{"type":"event","data":{"delivery_tag":8,"event":{"id":"event-a","payload":[]},"e\u0076ent":{"id":"event-b","payload":[]}}}"""
    }
}
