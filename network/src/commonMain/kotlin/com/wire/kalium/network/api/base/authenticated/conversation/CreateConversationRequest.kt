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

package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.base.model.TeamId
import com.wire.kalium.network.api.base.model.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateConversationRequest(
    @SerialName("qualified_users")
    val qualifiedUsers: List<UserId>?,
    @SerialName("name")
    val name: String?,
    @SerialName("access")
    val access: List<ConversationAccessDTO>?,
    @SerialName("access_role_v2")
    val accessRole: List<ConversationAccessRoleDTO>?,
    @SerialName("team")
    val convTeamInfo: ConvTeamInfo?,
    @SerialName("message_timer")
    val messageTimer: Long?, // Per-conversation message time
    // Receipt mode, controls if read receipts are enabled for the conversation.
    // Any positive value is interpreted as enabled.
    @SerialName("receipt_mode")
    val receiptMode: ReceiptMode,
    // Role name, between 2 and 128 chars, 'wire_' prefix is reserved for roles
    // designed by Wire (i.e., no custom roles can have the same prefix)
    @SerialName("conversation_role")
    val conversationRole: String?,
    @SerialName("protocol")
    val protocol: ConvProtocol?,
    // Only needed for MLS conversations
    @SerialName("creator_client")
    val creatorClient: String?
)

@Serializable
internal data class CreateConversationRequestV3(
    @SerialName("qualified_users")
    val qualifiedUsers: List<UserId>?,
    @SerialName("name")
    val name: String?,
    @SerialName("access")
    val access: List<ConversationAccessDTO>?,
    @SerialName("access_role")
    val accessRole: List<ConversationAccessRoleDTO>?,
    @SerialName("team")
    val convTeamInfo: ConvTeamInfo?,
    @SerialName("message_timer")
    val messageTimer: Long?, // Per-conversation message time
    // Receipt mode, controls if read receipts are enabled for the conversation.
    // Any positive value is interpreted as enabled.
    @SerialName("receipt_mode")
    val receiptMode: ReceiptMode,
    // Role name, between 2 and 128 chars, 'wire_' prefix is reserved for roles
    // designed by Wire (i.e., no custom roles can have the same prefix)
    @SerialName("conversation_role")
    val conversationRole: String?,
    @SerialName("protocol")
    val protocol: ConvProtocol?,
    // Only needed for MLS conversations
    @SerialName("creator_client")
    val creatorClient: String?
)

@Serializable
enum class ConvProtocol {
    @SerialName("proteus")
    PROTEUS,

    @SerialName("mls")
    MLS,

    @SerialName("mixed")
    MIXED;

    override fun toString(): String {
        return this.name.lowercase()
    }
}

@Serializable
data class ConvTeamInfo(
    @Deprecated("Not parsed any more")
    @SerialName("managed") val managed: Boolean,
    @SerialName("teamid") val teamId: TeamId
)
