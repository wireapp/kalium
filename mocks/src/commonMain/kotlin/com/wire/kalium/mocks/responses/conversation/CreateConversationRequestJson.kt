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

package com.wire.kalium.mocks.responses.conversation

import com.wire.kalium.mocks.responses.ValidJsonProvider
import com.wire.kalium.mocks.responses.samples.QualifiedIDSamples
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.ConvTeamInfo
import com.wire.kalium.network.api.base.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO

object CreateConversationRequestJson {

    private val createConversationRequest = CreateConversationRequest(
        listOf(QualifiedIDSamples.one),
        name = "NameOfThisGroupConversation",
        listOf(ConversationAccessDTO.PRIVATE),
        listOf(ConversationAccessRoleDTO.TEAM_MEMBER),
        ConvTeamInfo(false, "teamID"),
        0,
        ReceiptMode.DISABLED,
        "WIRE_MEMBER",
        ConvProtocol.PROTEUS,
        creatorClient = null
    )

    val v0 = ValidJsonProvider(
        createConversationRequest
    ) {
        """
        |{
        |   "access": [
        |       "${it.access?.get(0)}"
        |   ],
        |   "access_role_v2": [
        |       "${it.accessRole?.get(0)}"
        |   ],
        |   "conversation_role": "${it.conversationRole}",
        |   "message_timer": ${it.messageTimer},
        |   "name": "${it.name}",
        |   "protocol": "${it.protocol}",
        |   "qualified_users": [
        |       {
        |           "domain": "${it.qualifiedUsers?.get(0)?.domain}",
        |           "id": "${it.qualifiedUsers?.get(0)?.value}"
        |       }
        |   ],
        |   "receipt_mode": ${it.receiptMode.value},
        |   "team": {
        |       "managed": false,
        |       "teamid": "${it.convTeamInfo?.teamId}"
        |   }
        |}
        """.trimMargin()
        }

    val v3 = ValidJsonProvider(
        createConversationRequest
    ) {
        """
        |{
        |   "access": [
        |       "${it.access?.get(0)}"
        |   ],
        |   "access_role": [
        |       "${it.accessRole?.get(0)}"
        |   ],
        |   "conversation_role": "${it.conversationRole}",
        |   "message_timer": ${it.messageTimer},
        |   "name": "${it.name}",
        |   "protocol": "${it.protocol}",
        |   "qualified_users": [
        |       {
        |           "domain": "${it.qualifiedUsers?.get(0)?.domain}",
        |           "id": "${it.qualifiedUsers?.get(0)?.value}"
        |       }
        |   ],
        |   "receipt_mode": ${it.receiptMode.value},
        |   "team": {
        |       "managed": false,
        |       "teamid": "${it.convTeamInfo?.teamId}"
        |   }
        |}
        """.trimMargin()
    }

}
