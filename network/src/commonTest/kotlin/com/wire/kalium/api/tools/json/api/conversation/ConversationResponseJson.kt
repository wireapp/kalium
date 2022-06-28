package com.wire.kalium.api.tools.json.api.conversation

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.api.tools.json.model.QualifiedIDSamples
import com.wire.kalium.network.api.conversation.ConvProtocol
import com.wire.kalium.network.api.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.conversation.MutedStatus

object ConversationResponseJson {

    val conversationResponseSerializer = { it: ConversationResponse ->
        """
        |{
        |   "creator": "${it.creator}",
        |   "group_id": "${it.groupId}",
        |   "id": "99db9768-04e3-4b5d-9268-831b6a25c4ab",
        |   "members": {
        |       "others": [
        |           {
        |               "qualified_id": {
        |                   "domain": "${it.members.otherMembers[0].id.domain}",
        |                   "id": "${it.members.otherMembers[0].id.value}"
        |               }
        |           }
        |       ],
        |       "self": {
        |           "qualified_id": {
        |               "domain": "${it.members.self.id.domain}",
        |               "id": "${it.members.self.id.value}"
        |           },
        |           "conversation_role" : "${it.members.self.conversationRole}"
        |           "otr_muted_ref": "${it.members.self.otrMutedRef}",
        |           "otr_muted_status": ${it.members.self.otrMutedStatus}
        |       }
        |   },
        |   "message_timer": ${it.messageTimer},
        |   "name": "${it.name}",
        |   "protocol": "${it.protocol}",
        |   "qualified_id": {
        |       "domain": "${it.id.domain}",
        |       "id": "${it.id.value}"
        |   },
        |   "team": "${it.teamId}",
        |   "type": ${it.type.ordinal},
        |   "last_event_time":"${it.lastEventTime}"
        |}
        """.trimMargin()
    }

    val validGroup = ValidJsonProvider(
        ConversationResponse(
            "fdf23116-42a5-472c-8316-e10655f5d11e",
            ConversationMembersResponse(
                ConversationMemberDTO.Self(
                    QualifiedIDSamples.one,
                    "wire_admin",
                    otrMutedRef = "2022-04-11T14:15:48.044Z",
                    otrMutedStatus = MutedStatus.fromOrdinal(0)
                ),
                listOf(ConversationMemberDTO.Other( id = QualifiedIDSamples.two, conversationRole = "wire_member"))
            ),
            "group name",
            QualifiedIDSamples.one,
            "groupID",
            ConversationResponse.Type.GROUP,
            null,
            "teamID",
            ConvProtocol.PROTEUS,
            lastEventTime = "2022-03-30T15:36:00.000Z"
        ), conversationResponseSerializer
    )
}
