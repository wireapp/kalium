//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//
package com.wire.kalium.models.backend

import com.wire.kalium.tools.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Payload(
    /**
     * TODO: Replace String with something type-safe.
     *   Maybe an Enum? What are the possible values of this status? Bad discoverability too.
     *   Currently known:
     *   "conversation.otr-message-add"
     *   "conversation.member-join"
     *   "conversation.member-leave"
     *   "conversation.delete"
     *   "conversation.create"
     *   "conversation.rename"
     *   "user.connection"
     **/
    @SerialName("type") val type: String,
    @SerialName("conversation") val conversation: @Serializable(with = UUIDSerializer::class) UUID? = null,
    @SerialName("from") val from: @Serializable(with = UUIDSerializer::class) UUID? = null,
    @SerialName("time") val time: String? = null,
    @SerialName("data") val data: Data? = null,
    @SerialName("team") var team: @Serializable(with = UUIDSerializer::class) UUID? = null,
    @SerialName("connection") val connection: Connection? = null,
    @SerialName("user") var user: User? = null,
)

@Serializable
data class Data(
    @SerialName("sender") val sender: String? = null,
    @SerialName("recipient") val recipient: String? = null,
    @SerialName("text") val text: String? = null,
    @SerialName("user_ids") val userIds: List<@Serializable(with = UUIDSerializer::class) UUID>? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("id") val id: String? = null,
    @SerialName("key") val key: String? = null,
    @SerialName("user") val user: @Serializable(with = UUIDSerializer::class) UUID? = null,
    @SerialName("creator") val creator: @Serializable(with = UUIDSerializer::class) UUID? = null,
    @SerialName("members") val members: Members? = null,
)

@Serializable
data class Members(
    @SerialName("others") val others: List<OtherMember>,
    @SerialName("self") val self: SelfMember
) {
    fun allMembers(): List<ConversationMember> = others + self
}

@Serializable
data class Connection(
    /**
     * TODO: Replace String with something type-safe.
     *   Maybe an Enum? What are the possible values of this status? Bad discoverability too.
     *   Currently known: pending, accepted, sent
     **/
    @SerialName("status") val status: String,
    @SerialName("from") val from: @Serializable(with = UUIDSerializer::class) UUID,
    @SerialName("to") val to: @Serializable(with = UUIDSerializer::class) UUID,
    @SerialName("conversation") val conversation: @Serializable(with = UUIDSerializer::class) UUID
)
