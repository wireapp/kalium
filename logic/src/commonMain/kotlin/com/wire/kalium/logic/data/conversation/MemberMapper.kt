package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.conversation.ConversationMember
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.user.client.SimpleClientResponse
import com.wire.kalium.persistence.dao.MemberEntity

interface MemberMapper {
    fun fromApiModel(conversationMembersResponse: ConversationMembersResponse): MembersInfo
    fun fromMapOfClientsResponseToRecipients(qualifiedMap: Map<UserId, List<SimpleClientResponse>>): List<Recipient>
    fun fromApiModelToDaoModel(conversationMembersResponse: ConversationMembersResponse): List<MemberEntity>
    fun fromEventToDaoModel(members: List<ConversationMember>): List<MemberEntity>
}

internal class MemberMapperImpl(private val idMapper: IdMapper) : MemberMapper {

    override fun fromApiModel(conversationMembersResponse: ConversationMembersResponse): MembersInfo {
        val self = Member(idMapper.fromApiModel(conversationMembersResponse.self.userId))
        val others = conversationMembersResponse.otherMembers.map { member ->
            Member(idMapper.fromApiModel(member.userId))
        }
        return MembersInfo(self, others)
    }

    override fun fromApiModelToDaoModel(conversationMembersResponse: ConversationMembersResponse): List<MemberEntity> {
        val otherMembers = conversationMembersResponse.otherMembers.map { member ->
            MemberEntity(idMapper.fromApiToDao(member.userId))
        }
        val selfMember = MemberEntity(idMapper.fromApiToDao(conversationMembersResponse.self.userId))
        return otherMembers + selfMember
    }

    override fun fromEventToDaoModel(members: List<ConversationMember>) = members.map { member ->
        MemberEntity(idMapper.fromApiToDao(member.qualifiedId))
    }

    override fun fromMapOfClientsResponseToRecipients(qualifiedMap: Map<UserId, List<SimpleClientResponse>>): List<Recipient> =
        qualifiedMap.entries.map { entry ->
            val id = idMapper.fromApiModel(entry.key)

            val clients = entry.value.map(idMapper::fromSimpleClientResponse)

            Recipient(Member(id), clients)
        }
}
