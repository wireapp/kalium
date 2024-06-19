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

import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberRemovedDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembers
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationAccessInfoDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationProtocolDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationReceiptModeDTO
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.notification.MemberLeaveReasonDTO
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.model.UserId

object EventContentDTOJson {

    private val jsonProviderAccessUpdate = { serializable: EventContentDTO.Conversation.AccessUpdate ->
        """
        |{
        |  "qualified_conversation" : {
        |    "id" : "${serializable.qualifiedConversation.value}",
        |    "domain" : "${serializable.qualifiedConversation.domain}"
        |  },
        |  "qualified_from" : {
        |     "id" : "${serializable.qualifiedFrom.value}",
        |     "domain" : "${serializable.qualifiedFrom.domain}"
        |  }, 
        |  "data" : {
        |       "access": [code],
        |       "access_role": [team_member]
        |  }
        |}
        """.trimMargin()
    }

    private val jsonProviderAccessUpdateWithDeprecatedAccessRoleField =
        { serializable: EventContentDTO.Conversation.AccessUpdate ->
            """
        |{
        |  "qualified_conversation" : {
        |    "id" : "${serializable.qualifiedConversation.value}",
        |    "domain" : "${serializable.qualifiedConversation.domain}"
        |  },
        |  "qualified_from" : {
        |     "id" : "${serializable.qualifiedFrom.value}",
        |     "domain" : "${serializable.qualifiedFrom.domain}"
        |  }, 
        |  "data" : {
        |       "access": [code],
        |       "access_role_v2": [team_member],
        |       "access_role": "activated"
        |  }
        |}
        """.trimMargin()
        }

    private val jsonProviderMemberJoin = { serializable: EventContentDTO.Conversation.MemberJoinDTO ->
        """
        |{
        |  "qualified_conversation" : {
        |    "id" : "${serializable.qualifiedConversation.value}",
        |    "domain" : "${serializable.qualifiedConversation.domain}"
        |  },
        |  "qualified_from" : {
        |     "id" : "${serializable.qualifiedFrom.value}",
        |     "domain" : "${serializable.qualifiedFrom.domain}"
        |  }, 
        |  "from" : "${serializable.from}",
        |  "time" : "${serializable.time}",
        |  "data" : {
        |       "user_ids" : [],
        |       "users" : []
        |  }
        |}
        """.trimMargin()
    }

    private val jsonProviderMemberLeave = { serializable: EventContentDTO.Conversation.MemberLeaveDTO ->
        """
        |{
        |  "qualified_conversation" : {
        |    "id" : "${serializable.qualifiedConversation.value}",
        |    "domain" : "${serializable.qualifiedConversation.domain}"
        |  },
        |  "qualified_from" : {
        |     "id" : "${serializable.qualifiedFrom.value}",
        |     "domain" : "${serializable.qualifiedFrom.domain}"
        |  }, 
        |  "from" : "${serializable.from}",
        |  "time" : "${serializable.time}",
        |  "data" : {
        |       "user_ids" : [],
        |       qualified_user_ids : [] 
        |  }
        |}
        """.trimMargin()
    }

    private val jsonProviderUpdateConversationReceiptMode =
        { serializable: EventContentDTO.Conversation.ReceiptModeUpdate ->
            """
        |{
        |  "conversation":"${serializable.qualifiedConversation.value}",
        |  "data":{
        |    "receipt_mode":1
        |  },
        |  "from":"${serializable.qualifiedFrom.value}",
        |  "qualified_conversation": {
        |    "id": "${serializable.qualifiedConversation.value}",
        |    "domain": "${serializable.qualifiedConversation.domain}"
        |  },
        |  "qualified_from" : {
        |     "id" : "${serializable.qualifiedFrom.value}",
        |     "domain" : "${serializable.qualifiedFrom.domain}"
        |  },
        |  "time":"2023-01-27T10:35:10.146Z",
        |  "type":"conversation.receipt-mode-update"
        |}
        """.trimMargin()
        }

    private val jsonProviderUpdateConversationProtocol = { serializable: EventContentDTO.Conversation.ProtocolUpdate ->
        """
        |{
        |  "conversation":"${serializable.qualifiedConversation.value}",
        |  "data":{
        |    "protocol": "mixed"
        |  },
        |  "from":"${serializable.qualifiedFrom.value}",
        |  "qualified_conversation": {
        |    "id": "${serializable.qualifiedConversation.value}",
        |    "domain": "${serializable.qualifiedConversation.domain}"
        |  },
        |  "qualified_from" : {
        |     "id" : "${serializable.qualifiedFrom.value}",
        |     "domain" : "${serializable.qualifiedFrom.domain}"
        |  },
        |  "time":"2023-01-27T10:35:10.146Z",
        |  "type":"conversation.protocol-update"
        |}
        """.trimMargin()
    }

    val validAccessUpdate = ValidJsonProvider(
        EventContentDTO.Conversation.AccessUpdate(
            qualifiedConversation = ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            qualifiedFrom = UserId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            data = ConversationAccessInfoDTO(
                setOf(ConversationAccessDTO.CODE),
                setOf(ConversationAccessRoleDTO.TEAM_MEMBER)
            )
        ),
        jsonProviderAccessUpdate
    )

    val validAccessUpdateWithDeprecatedAccessRoleField = ValidJsonProvider(
        EventContentDTO.Conversation.AccessUpdate(
            qualifiedConversation = ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            qualifiedFrom = UserId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            data = ConversationAccessInfoDTO(
                setOf(ConversationAccessDTO.CODE),
                setOf(ConversationAccessRoleDTO.TEAM_MEMBER)
            )
        ),
        jsonProviderAccessUpdateWithDeprecatedAccessRoleField
    )

    val validMemberJoin = ValidJsonProvider(
        EventContentDTO.Conversation.MemberJoinDTO(
            qualifiedConversation = ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            qualifiedFrom = UserId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            from = "ebafd3d4-1548-49f2-ac4e-b2757e6ca44b",
            time = "2021-05-31T10:52:02.671Z",
            members = ConversationMembers(emptyList(), emptyList())
        ),
        jsonProviderMemberJoin
    )

    val validMemberLeave = ValidJsonProvider(
        EventContentDTO.Conversation.MemberLeaveDTO(
            qualifiedConversation = ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            qualifiedFrom = UserId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            from = "ebafd3d4-1548-49f2-ac4e-b2757e6ca44b",
            time = "2021-05-31T10:52:02.671Z",
            removedUsers = ConversationMemberRemovedDTO(emptyList(), MemberLeaveReasonDTO.LEFT)
        ),
        jsonProviderMemberLeave
    )

    val validNullAccessRole = """
        |{
        |  "qualified_conversation" : {
        |    "id" : "conv_id",
        |    "domain" : "conv_domain"
        |  },
        |  "qualified_from" : {
        |     "id" : "userId",
        |     "domain" : "user_domain"
        |  }, 
        |  "data" : {
        |       "access": [code]
        |  }
        |}
        """.trimMargin()

    val validUpdateReceiptMode = ValidJsonProvider(
        EventContentDTO.Conversation.ReceiptModeUpdate(
            qualifiedConversation = QualifiedID(
                value = "conversationId",
                domain = "conversationDomain"
            ),
            qualifiedFrom = QualifiedID(
                value = "qualifiedFromId",
                domain = "qualifiedFromDomain"
            ),
            data = ConversationReceiptModeDTO(receiptMode = ReceiptMode.ENABLED)
        ),
        jsonProviderUpdateConversationReceiptMode
    )

    val validUpdateProtocol = ValidJsonProvider(
        EventContentDTO.Conversation.ProtocolUpdate(
            qualifiedConversation = QualifiedID(
                value = "conversationId",
                domain = "conversationDomain"
            ),
            qualifiedFrom = QualifiedID(
                value = "qualifiedFromId",
                domain = "qualifiedFromDomain"
            ),
            data = ConversationProtocolDTO(ConvProtocol.MIXED)
        ),
        jsonProviderUpdateConversationProtocol
    )

    val validGenerateGuestRoomLink = """
        |{
        |  "qualified_conversation" : {
                    "domain": "wire.com",
                    "id": "f2520615-f860-****-****-9ace3b5f6c37"
        },
        |  "type" : "conversation.code-update",
        |  "time" : "2018-02-15T17:44:54.351Z",
        |  "qualified_from" : {
                    "domain": "wire.com",
                    "id": "f52eed1b-aa64-****-****-96529f72105f"
        },
        |  "data" : {
        |     "uri" : "https:\/\/wire-webapp-staging.zinfra.io\/join\/?key=NHRSj7****JkEZV5qsPd&code=755Asq****nITN_0AHV9",
        |     "key" : "NHRSj7****JkEZV5qsPd",
        |     "code" : "755Asq****nITN_0AHV9"
        |  }
        |}
        """.trimMargin()

    val jsonProviderMemberJoinFailureUnreachable =
        """
        |{
        |   "unreachable_backends": ["foma.wire.link"]
        |}
        """.trimMargin()
}
