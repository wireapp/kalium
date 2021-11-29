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
    val type: String,
    val conversation: @Serializable(with = UUIDSerializer::class) UUID? = null,
    val from: @Serializable(with = UUIDSerializer::class) UUID? = null,
    val time: String? = null,
    val data: Data? = null,
    var team: @Serializable(with = UUIDSerializer::class) UUID? = null,
    val connection: Connection? = null,
    var user: User? = null,
)

@Serializable
data class Data(
    val sender: String? = null,
    val recipient: String? = null,
    val text: String? = null,
    val user_ids: List<@Serializable(with = UUIDSerializer::class) UUID>? = null,
    val name: String? = null,
    val id: String? = null,
    val key: String? = null,
    val user: @Serializable(with = UUIDSerializer::class) UUID? = null,
    val creator: @Serializable(with = UUIDSerializer::class) UUID? = null,
    val members: Members? = null,
)

@Serializable
data class Members(
    val others: List<OtherMember>,
    val self: SelfMember
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
    val status: String,

    @Serializable(with = UUIDSerializer::class) val from: UUID,
    @Serializable(with = UUIDSerializer::class) val to: UUID,
    @Serializable(with = UUIDSerializer::class) val conversation: UUID
)
