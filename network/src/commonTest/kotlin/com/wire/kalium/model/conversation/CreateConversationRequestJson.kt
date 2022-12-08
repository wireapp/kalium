package com.wire.kalium.model.conversation

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.api.json.model.QualifiedIDSamples
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
        |   "receipt_mode": ${it.receiptMode?.value},
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
        |   "receipt_mode": ${it.receiptMode?.value},
        |   "team": {
        |       "managed": false,
        |       "teamid": "${it.convTeamInfo?.teamId}"
        |   }
        |}
        """.trimMargin()
    }

}
