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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelAddPermission
import com.wire.kalium.logic.data.user.SupportedProtocol

data class CreateConversationParam(
    val access: Set<Conversation.Access>? = null,
    val accessRole: Set<Conversation.AccessRole>? = null,
    val readReceiptsEnabled: Boolean = false,
    val wireCellEnabled: Boolean = false,
    val protocol: Protocol = Protocol.PROTEUS,
    val creatorClientId: ClientId? = null,
    val groupType: GroupType = GroupType.REGULAR_GROUP,
    val channelAddPermission: ChannelAddPermission = ChannelAddPermission.ADMINS
) {
    enum class Protocol {
        PROTEUS, MLS;

        companion object {
            fun fromSupportedProtocolToConversationOptionsProtocol(supportedProtocol: SupportedProtocol): Protocol =
                when (supportedProtocol) {
                    SupportedProtocol.MLS -> MLS
                    SupportedProtocol.PROTEUS -> PROTEUS
                }
        }
    }

    enum class GroupType(val value: String) {
        REGULAR_GROUP("group_conversation"),
        CHANNEL("channel")
    }
}
