package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.user.client.SimpleClientResponse

class MemberMapper(private val idMapper: IdMapper) {

    fun fromApiModel(conversationMembersResponse: ConversationMembersResponse): MembersInfo {
        val self = Member(idMapper.fromApiModel(conversationMembersResponse.self.userId))
        val others = conversationMembersResponse.otherMembers.map { member ->
            Member(idMapper.fromApiModel(member.userId))
        }
        return MembersInfo(self, others)
    }

    fun fromMapOfClientsResponseToRecipients(qualifiedMap: Map<UserId, List<SimpleClientResponse>>): List<Recipient> =
        qualifiedMap.entries.map { entry ->
            val id = idMapper.fromApiModel(entry.key)

            val clients = entry.value.map(idMapper::fromSimpleClientResponse)

            Recipient(Member(id), clients)
        }
}
