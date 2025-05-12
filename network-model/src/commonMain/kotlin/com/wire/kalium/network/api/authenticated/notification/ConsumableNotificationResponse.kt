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
package com.wire.kalium.network.api.authenticated.notification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConsumableNotificationResponse(
    @SerialName("type")
    val type: EventType,
    @SerialName("data")
    val data: EventDataDTO?
)

@Serializable
data class EventDataDTO(
    @SerialName("delivery_tag")
    val deliveryTag: ULong,
    @SerialName("event")
    val event: ConsumableEventDTO
)

@Serializable
enum class EventType {
    @SerialName("event")
    EVENT,

    @SerialName("notifications.missed")
    MISSED;

    override fun toString(): String {
        return when (this) {
            EVENT -> "event"
            MISSED -> "notifications.missed"
        }
    }
}

@Serializable
data class ConsumableEventDTO(
    @SerialName("id")
    val id: String,
    @SerialName("payload")
    val payload: List<EventContentDTO>
)
