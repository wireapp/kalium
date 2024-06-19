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
package com.wire.kalium.mocks.responses

import com.wire.kalium.mocks.extensions.toJsonString
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembers
import com.wire.kalium.network.api.base.authenticated.conversation.ServiceReferenceDTO
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.AddServiceResponse
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.UserId

object AddServiceResponseJson {

    val valid = AddServiceResponse(
            event = EventContentDTO.Conversation.MemberJoinDTO(
                qualifiedConversation = ConversationId(
                    value = "value",
                    domain = "domain"
                ),
                qualifiedFrom = UserId(
                    value = "value2",
                    domain = "domain2"
                ),
                time = "some_time",
                members = ConversationMembers(
                    userIds = listOf("value3@domain3"),
                    users = listOf(
                        ConversationMemberDTO.Other(
                            id = UserId(
                                value = "value3",
                                domain = "domain3"
                            ),
                            conversationRole = "role",
                            service = ServiceReferenceDTO(
                                id = "serviceId",
                                provider = "providerId"
                            )
                        )
                    )
                ),
                from = "from"
            )
        ).toJsonString()
}
