/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.model

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembers
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationUsers
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationAccessInfoDTO
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.base.model.ConversationId
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

    private val jsonProviderAccessUpdateWithDeprecatedAccessRoleField = { serializable: EventContentDTO.Conversation.AccessUpdate ->
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
            members = ConversationUsers(emptyList(), emptyList())
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
}
