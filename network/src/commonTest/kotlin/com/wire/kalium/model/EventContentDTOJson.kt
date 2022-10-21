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

    private val jsonProvider = { serializable: EventContentDTO.Conversation.AccessUpdate ->
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
        |       "access_role_v2": [team_member]
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
        jsonProvider
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
