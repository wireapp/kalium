package com.wire.kalium.api.tools.json.api.conversation

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.api.tools.json.model.QualifiedIDSamples
import com.wire.kalium.network.api.conversation.ConvProtocol
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.conversation.ConversationOtherMembersResponse
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.conversation.ConversationSelfMemberResponse
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
        |                   "domain": "${it.members.otherMembers[0].userId.domain}",
        |                   "id": "${it.members.otherMembers[0].userId.value}"
        |               }
        |           }
        |       ],
        |       "self": {
        |           "qualified_id": {
        |               "domain": "${it.members.self.userId.domain}",
        |               "id": "${it.members.self.userId.value}"
        |           },
        |           "otr_muted_ref": "2022-04-11T14:15:48.044Z",
        |           "otr_muted_status": 0
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
                ConversationSelfMemberResponse(
                    QualifiedIDSamples.one
                ),
                listOf(ConversationOtherMembersResponse(null, QualifiedIDSamples.two))
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
