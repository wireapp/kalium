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
package com.wire.kalium.logic.sync.receiver.user

import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.datetime.Instant

internal fun userUpdateEvent(userId: UserId = SELF_USER_ID) = Event.User.Update(
    id = EVENT_ID,
    userId = userId,
    accentId = null,
    ssoIdDeleted = false,
    name = "newName",
    handle = null,
    email = null,
    previewAssetId = null,
    completeAssetId = null,
    supportedProtocols = null,
)

internal fun clientRemoveEvent(clientId: ClientId = CLIENT_ID_1) = Event.User.ClientRemove(EVENT_ID, clientId)

internal fun userDeleteEvent(userId: UserId = SELF_USER_ID) = Event.User.UserDelete(EVENT_ID, userId)

internal fun newClientEvent(clientId: ClientId = CLIENT_ID_1) = Event.User.NewClient(
    EVENT_ID,
    Client(
        id = clientId,
        type = ClientType.Permanent,
        registrationTime = Instant.DISTANT_PAST,
        lastActive = Instant.DISTANT_PAST,
        deviceType = null,
        model = null,
        label = "label",
        isVerified = false,
        isValid = true,
        mlsPublicKeys = null,
        isMLSCapable = false,
        isAsyncNotificationsCapable = false,
    )
)

internal fun newConnectionEvent(status: ConnectionState = ConnectionState.PENDING) = Event.User.NewConnection(
    EVENT_ID,
    Connection(
        conversationId = CONVERSATION_ID.value,
        from = "from",
        lastUpdate = Instant.UNIX_FIRST_DATE,
        qualifiedConversationId = CONVERSATION_ID,
        qualifiedToId = OTHER_USER_ID,
        status = status,
        toId = "to",
    )
)

internal fun sessionRefreshSuggestedEvent() = Event.User.SessionRefreshSuggested(EVENT_ID)

internal const val EVENT_ID = "eventId"
internal val SELF_USER_ID = UserId("self", "domain")
internal val OTHER_USER_ID = UserId("other", "domain")
internal val CLIENT_ID_1 = ClientId("client-1")
internal val CLIENT_ID_2 = ClientId("client-2")
internal val CONVERSATION_ID = ConversationId("conversation", "domain")
internal val LIVE_DELIVERY_INFO = EventDeliveryInfo(EventSource.LIVE)
internal val PENDING_DELIVERY_INFO = EventDeliveryInfo(EventSource.PENDING)
