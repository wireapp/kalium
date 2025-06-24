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
package com.wire.kalium.monkeys.model

import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MonkeyId(
    @SerialName("index")
    val index: Int,
    @SerialName("team")
    val team: String,
    @SerialName("client")
    val clientId: Int
) {
    companion object {
        fun dummy() = MonkeyId(-1, "dummy", -1)
    }
}

@Serializable
data class ConversationDef(
    @SerialName("id")
    val id: ConversationId,
    @SerialName("owner")
    val owner: MonkeyId,
    @SerialName("initialMembers")
    val initialMembers: List<MonkeyId>,
    @SerialName("protocol")
    val protocol: ConversationOptions.Protocol
)

@Serializable
data class Event(
    @SerialName("monkeyOrigin")
    val monkeyOrigin: MonkeyId,
    @SerialName("eventType")
    val eventType: EventType
)

@Serializable
sealed class EventType {
    @Serializable
    @SerialName("LOGOUT")
    data object Logout : EventType()

    @Serializable
    @SerialName("LOGIN")
    data object Login : EventType()

    @Serializable
    @SerialName("SEND_MESSAGE")
    data class SendMessage(val conversationId: ConversationId) : EventType()

    @Serializable
    @SerialName("SEND_DIRECT_MESSAGE")
    data class SendDirectMessage(val targetMonkey: MonkeyId) : EventType()

    @Serializable
    @SerialName("CREATE_CONVERSATION")
    data class CreateConversation(val conversation: ConversationDef) : EventType()

    @Serializable
    @SerialName("ADD_USERS_TO_CONVERSATION")
    data class AddUsersToConversation(val conversationId: ConversationId, val newMembers: List<MonkeyId>) : EventType()

    @Serializable
    @SerialName("LEAVE_CONVERSATION")
    data class LeaveConversation(val conversationId: ConversationId) : EventType()

    @Serializable
    @SerialName("DESTROY_CONVERSATION")
    data class DestroyConversation(val conversationId: ConversationId) : EventType()

    @Serializable
    @SerialName("SEND_REQUEST")
    data class SendRequest(val targetMonkey: MonkeyId) : EventType()

    @Serializable
    @SerialName("REQUEST_RESPONSE")
    data class RequestResponse(val targetMonkey: MonkeyId, val shouldAccept: Boolean) : EventType()
}
