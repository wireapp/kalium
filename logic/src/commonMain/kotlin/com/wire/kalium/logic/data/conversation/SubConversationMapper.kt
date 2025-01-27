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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.network.api.authenticated.conversation.SubconversationMemberDTO
import com.wire.kalium.network.api.authenticated.conversation.SubconversationResponse

fun SubconversationResponse.toModel(): SubConversation {
    return SubConversation(
        id = id.toModel(),
        parentId = parentId.toModel(),
        groupId = GroupID(groupId),
        epoch = epoch,
        epochTimestamp = epochTimestamp,
        mlsCipherSuiteTag = mlsCipherSuiteTag,
        members = members.map { it.toModel() }
    )
}

fun SubconversationMemberDTO.toModel(): SubconversationMember {
    return SubconversationMember(
        clientId = clientId,
        userId = userId,
        domain = domain
    )
}
