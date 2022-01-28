package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.user.client.SimpleClientResponse
import com.wire.kalium.persistence.dao.Member as MemberDAO

interface MemberMapper {
    fun fromApiModel(conversationMembersResponse: ConversationMembersResponse): MembersInfo
    fun fromMapOfClientsResponseToRecipients(qualifiedMap: Map<UserId, List<SimpleClientResponse>>): List<Recipient>
    fun fromApiModelToDao(conversationMembersResponse: ConversationMembersResponse): List<com.wire.kalium.persistence.dao.Member>
}

internal class MemberMapperImpl(private val idMapper: IdMapper) : MemberMapper {

    override fun fromApiModel(conversationMembersResponse: ConversationMembersResponse): MembersInfo {
        val self = Member(idMapper.fromApiModel(conversationMembersResponse.self.userId))
        val others = conversationMembersResponse.otherMembers.map { member ->
            Member(idMapper.fromApiModel(member.userId))
        }
        return MembersInfo(self, others)
    }

    override fun fromApiModelToDao(conversationMembersResponse: ConversationMembersResponse): List<MemberDAO> {
        val otherMembers = conversationMembersResponse.otherMembers.map { member ->
            MemberDAO( idMapper.fromApiToDao(member.userId))
        }
        val selfMember = MemberDAO( idMapper.fromApiToDao(conversationMembersResponse.self.userId))
        return otherMembers + selfMember
    }

    override fun fromMapOfClientsResponseToRecipients(qualifiedMap: Map<UserId, List<SimpleClientResponse>>): List<Recipient> =
        qualifiedMap.entries.map { entry ->
            val id = idMapper.fromApiModel(entry.key)

            val clients = entry.value.map(idMapper::fromSimpleClientResponse)

            Recipient(Member(id), clients)
        }
}
