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

package com.wire.kalium.mocks.responses

import com.wire.kalium.mocks.extensions.toJsonString
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventDataDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponseToStore
import com.wire.kalium.network.tools.KtxSerializer

object ConsumableNotificationEventsResponseJson {
    val validEventDataJson = ConsumableNotificationResponse.EventNotification(
        data = EventDataDTO(
            (ULong.MAX_VALUE),
            EventResponseToStore(
                "some_id",
                KtxSerializer.json.encodeToString(listOf(EventContentDTOJson.validMemberJoin.serializableData))
            )
        )
    ).toJsonString()

    val validMissedNotificationsJson = ConsumableNotificationResponse.MissedNotification.toJsonString()

}
