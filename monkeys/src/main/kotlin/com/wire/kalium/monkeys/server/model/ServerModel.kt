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
package com.wire.kalium.monkeys.server.model

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddMonkeysRequest(
    @SerialName("conversationId")
    val conversationId: ConversationId,
    @SerialName("monkeys")
    val monkeys: List<UserId>
)
@Serializable
data class RemoveMonkeyRequest(
    @SerialName("conversationId")
    val conversationId: ConversationId,
    @SerialName("monkey")
    val monkey: UserId
)
@Serializable
data class SendDMRequest(
    @SerialName("monkey")
    val monkey: UserId,
    @SerialName("message")
    val message: String
)
@Serializable
data class SendMessageRequest(
    @SerialName("conversationId")
    val conversationId: ConversationId,
    @SerialName("message")
    val message: String
)
@Serializable
data class CreateConversationRequest(
    @SerialName("name")
    val name: String,
    @SerialName("monkeys")
    val monkeys: List<UserId>,
    @SerialName("protocol")
    val protocol: ConversationOptions.Protocol,
    @SerialName("isDestroyable")
    val isDestroyable: Boolean
)
@Serializable
data class ConversationIdRequest(
    @SerialName("conversationId")
    val conversationId: ConversationId,
    @SerialName("creator")
    val creator: UserId
)
