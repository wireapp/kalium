package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.user.client.SimpleClientResponse
import com.wire.kalium.persistence.dao.Member as PersistedMember

interface MemberMapper {
    fun fromApiModel(conversationMember: ConversationMemberDTO.Other): Member
    fun fromApiModel(conversationMembersResponse: ConversationMembersResponse): MembersInfo
    fun fromMapOfClientsResponseToRecipients(qualifiedMap: Map<UserId, List<SimpleClientResponse>>): List<Recipient>
    fun fromApiModelToDaoModel(conversationMembersResponse: ConversationMembersResponse): List<PersistedMember>
    fun fromDaoModel(entity: PersistedMember): Member
    fun toDaoModel(member: Member): PersistedMember
}

internal class MemberMapperImpl(private val idMapper: IdMapper, private val roleMapper: ConversationRoleMapper) : MemberMapper {

    override fun fromApiModel(conversationMember: ConversationMemberDTO.Other): Member =
        Member(idMapper.fromApiModel(conversationMember.id), roleMapper.fromApi(conversationMember.conversationRole))

    override fun fromApiModel(conversationMembersResponse: ConversationMembersResponse): MembersInfo {
        val self = with(conversationMembersResponse.self) {
            Member(idMapper.fromApiModel(id), roleMapper.fromApi(conversationRole))
        }
        val others = conversationMembersResponse.otherMembers.map { member ->
            Member(idMapper.fromApiModel(member.id), roleMapper.fromApi(member.conversationRole))
        }
        return MembersInfo(self, others)
    }

    override fun fromApiModelToDaoModel(conversationMembersResponse: ConversationMembersResponse): List<PersistedMember> {
        val otherMembers = conversationMembersResponse.otherMembers.map { member ->
            PersistedMember(idMapper.fromApiToDao(member.id), roleMapper.fromApiModelToDaoModel(member.conversationRole))
        }
        val selfMember = with(conversationMembersResponse.self) {
            PersistedMember(idMapper.fromApiToDao(id), roleMapper.fromApiModelToDaoModel(conversationRole))
        }
        return otherMembers + selfMember
    }

    override fun toDaoModel(member: Member): PersistedMember = with(member) {
        PersistedMember(idMapper.toDaoModel(id), roleMapper.toDAO(role))
    }

    override fun fromMapOfClientsResponseToRecipients(qualifiedMap: Map<UserId, List<SimpleClientResponse>>): List<Recipient> =
        qualifiedMap.entries.map { entry ->
            val id = idMapper.fromApiModel(entry.key)
            val clients = entry.value.map(idMapper::fromSimpleClientResponse)
            Recipient(id, clients)
        }

    override fun fromDaoModel(entity: PersistedMember): Member = with(entity) {
        Member(idMapper.fromDaoModel(user), roleMapper.fromDAO(role))
    }
}

interface ConversationRoleMapper {
    fun toApi(role: Member.Role): String
    fun fromApi(roleDTO: String): Member.Role
    fun fromDAO(roleEntity: String): Member.Role
    fun toDAO(role: Member.Role): String
    fun fromApiModelToDaoModel(roleDTO: String): String
}


internal class ConversationRoleMapperImpl : ConversationRoleMapper {
    override fun toApi(role: Member.Role): String = when (role) {
        Member.Role.Admin -> ADMIN
        Member.Role.Member -> MEMBER
        is Member.Role.Unknown -> role.name
    }

    override fun fromApi(roleDTO: String): Member.Role = when (roleDTO) {
        ADMIN -> Member.Role.Admin
        MEMBER -> Member.Role.Member
        else -> Member.Role.Unknown(roleDTO)
    }

    override fun fromDAO(roleEntity: String): Member.Role = when (roleEntity) {
        ADMIN -> Member.Role.Admin
        MEMBER -> Member.Role.Member
        else -> Member.Role.Unknown(roleEntity)
    }

    override fun toDAO(role: Member.Role): String = when (role) {
        Member.Role.Admin -> ADMIN
        Member.Role.Member -> MEMBER
        is Member.Role.Unknown -> role.name
    }

    override fun fromApiModelToDaoModel(roleDTO: String): String = when (roleDTO) {
        ADMIN -> ADMIN
        MEMBER -> MEMBER
        else -> roleDTO

    }

    private companion object {
        const val ADMIN = "wire_admin"
        const val MEMBER = "wire_member"
    }
}
