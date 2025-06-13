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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class ConsumableNotificationResponse {
    @Serializable
    @SerialName("event")
    data class EventNotification(
        @SerialName("data") val data: EventDataDTO
    ) : ConsumableNotificationResponse()

    @Serializable
    @SerialName("message_count")
    data class MessageCount(
        @SerialName("data") val data: NotificationCount
    ) : ConsumableNotificationResponse()

    @Serializable
    @SerialName("notifications_missed")
    data object MissedNotification : ConsumableNotificationResponse()
}

@Serializable
data class EventDataDTO(
    @SerialName("delivery_tag")
    val deliveryTag: ULong?,
    @SerialName("event")
    val event: EventResponse
)

@Serializable
enum class EventType {
    @SerialName("event")
    EVENT,

    @SerialName("message_count")
    MESSAGE_COUNT,

    @SerialName("notifications_missed")
    MISSED;

    override fun toString(): String {
        return when (this) {
            EVENT -> "event"
            MESSAGE_COUNT -> "message_count"
            MISSED -> "notifications_missed"
        }
    }
}

@Serializable
data class NotificationCount(
    @SerialName("count")
    val count: ULong
)
