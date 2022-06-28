package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.user.client.SimpleClientResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.Member as PersistedMember

interface MemberMapper {
    fun fromApiModel(conversationMember:  ConversationMemberDTO.Other): Member
    fun fromApiModel(conversationMembersResponse: ConversationMembersResponse): MembersInfo
    fun fromMapOfClientsResponseToRecipients(qualifiedMap: Map<UserId, List<SimpleClientResponse>>): List<Recipient>
    fun fromApiModelToDaoModel(conversationMembersResponse: ConversationMembersResponse): List<PersistedMember>
    fun fromDaoModel(entity: PersistedMember): Member
    fun fromDaoModel(qualifiedId: QualifiedIDEntity): Member
    fun toDaoModel(member: Member): PersistedMember
}

internal class MemberMapperImpl(private val idMapper: IdMapper) : MemberMapper {

    override fun fromApiModel(conversationMember: ConversationMemberDTO.Other): Member =
        Member(idMapper.fromApiModel(conversationMember.id))

    override fun fromApiModel(conversationMembersResponse: ConversationMembersResponse): MembersInfo {
        val self = Member(idMapper.fromApiModel(conversationMembersResponse.self.id))
        val others = conversationMembersResponse.otherMembers.map { member ->
            Member(idMapper.fromApiModel(member.id))
        }
        return MembersInfo(self, others)
    }

    override fun fromApiModelToDaoModel(conversationMembersResponse: ConversationMembersResponse): List<PersistedMember> {
        val otherMembers = conversationMembersResponse.otherMembers.map { member ->
            PersistedMember(idMapper.fromApiToDao(member.id))
        }
        val selfMember = PersistedMember(idMapper.fromApiToDao(conversationMembersResponse.self.id))
        return otherMembers + selfMember
    }

    override fun toDaoModel(member: Member): PersistedMember =
        PersistedMember(idMapper.toDaoModel(member.id))

    override fun fromMapOfClientsResponseToRecipients(qualifiedMap: Map<UserId, List<SimpleClientResponse>>): List<Recipient> =
        qualifiedMap.entries.map { entry ->
            val id = idMapper.fromApiModel(entry.key)

            val clients = entry.value.map(idMapper::fromSimpleClientResponse)

            Recipient(Member(id), clients)
        }

    override fun fromDaoModel(entity: PersistedMember): Member = Member(idMapper.fromDaoModel(entity.user))
    override fun fromDaoModel(qualifiedId: QualifiedIDEntity): Member = fromDaoModel(PersistedMember(qualifiedId))
}
