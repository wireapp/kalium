/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.event.stream.async

import com.benasher44.uuid.uuid4
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.event.EventDataSource.Companion.TAG
import com.wire.kalium.logic.data.event.stream.BaseEventStream
import com.wire.kalium.logic.data.event.stream.EventStreamStatus
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.persistence.dao.event.EventDAO
import com.wire.kalium.persistence.dao.event.EventEntity
import com.wire.kalium.persistence.dao.event.NewEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Backed by the new Events Websocket API
 */
internal class AsyncEventStream(
    private val networkRequestProvider: () -> Flow<WebSocketEvent<ConsumableNotificationResponse>>,
    private val eventStorageFlow: Flow<EventEntity>,
    private val eventDAO: EventDAO,
) : BaseEventStream(eventDAO) {

    override suspend fun eventStreamStatusFlow(): Flow<EventStreamStatus> = flow {
        networkRequestProvider().collect { websocketEvent ->
            when (val websocketEvent = websocketEvent) {
                is WebSocketEvent.BinaryPayloadReceived<ConsumableNotificationResponse> -> {
                    when (val event: ConsumableNotificationResponse = websocketEvent.payload) {
                        is ConsumableNotificationResponse.EventNotification -> {
                            if (clearOnFirstWSMessage.value) {
                                clearOnFirstWSMessage.emit(false)
                                kaliumLogger.d("$TAG clear processed events before ${event.data.event.id.obfuscateId()}")
                                clearProcessedEvents(event.data.event.id)
                            }
                            event.data.event.let { eventResponse ->
                                kaliumLogger.d("$TAG insert event ${eventResponse.id.obfuscateId()} from WS")
                                wrapStorageRequest {
                                    eventDAO.insertEvents(
                                        listOf(
                                            NewEventEntity(
                                                eventId = eventResponse.id,
                                                payload = KtxSerializer.json.encodeToString(eventResponse),
                                                isLive = true // TODO Yamil we need to decide when set it as false for async notificaitons
                                            )
                                        )
                                    )
                                }.onSuccess {
                                    event.data.deliveryTag?.let {
                                        ackEvent(it)
                                    }
                                    if (!event.data.event.transient) {
                                        updateLastSavedEventId(event.data.event.id)
                                    }
                                }
                            }
                        }

                        ConsumableNotificationResponse.MissedNotification -> {
                            wrapStorageRequest {
                                val eventId = uuid4().toString()
                                eventDAO.insertEvents(
                                    listOf(
                                        NewEventEntity(
                                            eventId = eventId,
                                            payload = KtxSerializer.json.encodeToString(
                                                EventResponse(
                                                    eventId,
                                                    payload = listOf(EventContentDTO.AsyncMissedNotification)
                                                )
                                            ),
                                            isLive = true
                                        )
                                    )
                                )
                            }
                        }
                    }
                }

                is WebSocketEvent.Close<*> -> onClose()
                is WebSocketEvent.NonBinaryPayloadReceived<*> -> {
                    // TODO: LOG
                }

                is WebSocketEvent.Open<*> -> {
                    // Nothing for Async Events, we only care about payloads.
                }
            }
        }
    }
}
