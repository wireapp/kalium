/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.util.serialization.toJsonElement

/**
 * A wrapper that joins [Event] with its [EventDeliveryInfo].
 */
internal data class EventEnvelope(
    val event: Event,
    val deliveryInfo: EventDeliveryInfo
) {
    override fun toString(): String {
        return super.toString()
    }

    internal fun toLogString(): String = toLogMap().toJsonElement().toString()

    internal fun toLogMap(): Map<String, Any?> = mapOf(
        "event" to event.toLogMap(),
        "deliveryInfo" to deliveryInfo.toLogMap()
    )
}

/**
 * Data class representing information about the delivery of an event.
 *
 * @property source Indicates whether the event was received in real-time via WebSocket [EventSource.LIVE]
 * or fetched in batch as a pending event [EventSource.PENDING].
 */
internal data class EventDeliveryInfo(
    val source: EventSource,
) {

    internal fun toLogMap(): Map<String, Any?> = mapOf(
        "source" to source,
    )
}
