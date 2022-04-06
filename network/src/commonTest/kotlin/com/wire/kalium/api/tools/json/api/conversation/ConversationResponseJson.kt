package com.wire.kalium.api.tools.json.api.conversation

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.api.tools.json.model.QualifiedIDSamples
import com.wire.kalium.network.api.conversation.ConvProtocol
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.conversation.ConversationOtherMembersResponse
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.conversation.ConversationSelfMemberResponse

object ConversationResponseJson {

    val validGroup = ValidJsonProvider(
        ConversationResponse(
            "fdf23116-42a5-472c-8316-e10655f5d11e",
            ConversationMembersResponse(
                ConversationSelfMemberResponse(QualifiedIDSamples.one),
                listOf(ConversationOtherMembersResponse(null, QualifiedIDSamples.two))
            ),
            "group name",
            QualifiedIDSamples.one,
            "groupID",
            ConversationResponse.Type.GROUP,
            null,
            "teamID",
            ConvProtocol.PROTEUS
        )
    ) {
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
        |           }
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
        |   "type": ${it.type.ordinal}
        |}
        """.trimMargin()
    }
}
