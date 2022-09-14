package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.client.Client
import com.wire.kalium.logic.data.user.UserId as LogicUserId
import com.wire.kalium.persistence.dao.Member as PersistedMember

interface MemberMapper {
    fun fromApiModel(conversationMember: ConversationMemberDTO.Other): Conversation.Member
    fun fromApiModel(conversationMembersResponse: ConversationMembersResponse): MembersInfo
    fun fromMapOfClientsEntityToRecipients(qualifiedMap: Map<QualifiedIDEntity, List<Client>>): List<Recipient>
    fun fromMapOfClientsToRecipients(qualifiedMap: Map<LogicUserId, List<Client>>): List<Recipient>
    fun fromApiModelToDaoModel(conversationMembersResponse: ConversationMembersResponse): List<PersistedMember>
    fun fromDaoModel(entity: PersistedMember): Conversation.Member
    fun toDaoModel(member: Conversation.Member): PersistedMember
}

internal class MemberMapperImpl(private val idMapper: IdMapper, private val roleMapper: ConversationRoleMapper) : MemberMapper {

    override fun fromApiModel(conversationMember: ConversationMemberDTO.Other): Conversation.Member =
        Conversation.Member(idMapper.fromApiModel(conversationMember.id), roleMapper.fromApi(conversationMember.conversationRole))

    override fun fromApiModel(conversationMembersResponse: ConversationMembersResponse): MembersInfo {
        val self = with(conversationMembersResponse.self) {
            Conversation.Member(idMapper.fromApiModel(id), roleMapper.fromApi(conversationRole))
        }
        val others = conversationMembersResponse.otherMembers.map { member ->
            Conversation.Member(idMapper.fromApiModel(member.id), roleMapper.fromApi(member.conversationRole))
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

    override fun toDaoModel(member: Conversation.Member): PersistedMember = with(member) {
        PersistedMember(idMapper.toDaoModel(id), roleMapper.toDAO(role))
    }

    override fun fromMapOfClientsEntityToRecipients(qualifiedMap: Map<QualifiedIDEntity, List<Client>>): List<Recipient> =
        qualifiedMap.entries.map { entry ->
            val id = idMapper.fromDaoModel(entry.key)
            val clients = entry.value.map(idMapper::fromClient)
            Recipient(id, clients)
        }

    override fun fromMapOfClientsToRecipients(qualifiedMap: Map<LogicUserId, List<Client>>): List<Recipient> =
        qualifiedMap.entries.map { entry ->
            val id = entry.key
            val clients = entry.value.map(idMapper::fromClient)
            Recipient(id, clients)
        }

    override fun fromDaoModel(entity: PersistedMember): Conversation.Member = with(entity) {
        Conversation.Member(idMapper.fromDaoModel(user), roleMapper.fromDAO(role))
    }
}

interface ConversationRoleMapper {
    fun toApi(role: Conversation.Member.Role): String
    fun fromApi(roleDTO: String): Conversation.Member.Role
    fun fromDAO(roleEntity: PersistedMember.Role): Conversation.Member.Role
    fun toDAO(role: Conversation.Member.Role): PersistedMember.Role
    fun fromApiModelToDaoModel(roleDTO: String): PersistedMember.Role
}

internal class ConversationRoleMapperImpl : ConversationRoleMapper {
    override fun toApi(role: Conversation.Member.Role): String = when (role) {
        Conversation.Member.Role.Admin -> ADMIN
        Conversation.Member.Role.Member -> MEMBER
        is Conversation.Member.Role.Unknown -> role.name
    }

    override fun fromApi(roleDTO: String): Conversation.Member.Role = when (roleDTO) {
        ADMIN -> Conversation.Member.Role.Admin
        MEMBER -> Conversation.Member.Role.Member
        else -> Conversation.Member.Role.Unknown(roleDTO)
    }

    override fun fromDAO(roleEntity: PersistedMember.Role): Conversation.Member.Role = when (roleEntity) {
        PersistedMember.Role.Admin -> Conversation.Member.Role.Admin
        PersistedMember.Role.Member -> Conversation.Member.Role.Member
        is PersistedMember.Role.Unknown -> Conversation.Member.Role.Unknown(roleEntity.name)
    }

    override fun toDAO(role: Conversation.Member.Role): PersistedMember.Role = when (role) {
        Conversation.Member.Role.Admin -> PersistedMember.Role.Admin
        Conversation.Member.Role.Member -> PersistedMember.Role.Member
        is Conversation.Member.Role.Unknown -> PersistedMember.Role.Unknown(role.name)
    }

    override fun fromApiModelToDaoModel(roleDTO: String): PersistedMember.Role = when (roleDTO) {
        ADMIN -> PersistedMember.Role.Admin
        MEMBER -> PersistedMember.Role.Member
        else -> PersistedMember.Role.Unknown(roleDTO)
    }

    private companion object {
        const val ADMIN = "wire_admin"
        const val MEMBER = "wire_member"
    }
}
