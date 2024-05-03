/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.network.api.base.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.client.Client
import com.wire.kalium.persistence.dao.member.MemberEntity

interface MemberMapper {
    fun fromApiModel(conversationMember: ConversationMemberDTO.Other): Conversation.Member
    fun fromApiModel(conversationMembersResponse: ConversationMembersResponse): MembersInfo
    fun fromMapOfClientsResponseToRecipients(qualifiedMap: Map<UserId, List<SimpleClientResponse>>): List<Recipient>
    fun fromMapOfClientsEntityToRecipients(qualifiedMap: Map<QualifiedIDEntity, List<Client>>): List<Recipient>
    fun fromApiModelToDaoModel(conversationMembersResponse: ConversationMembersResponse): List<MemberEntity>
    fun fromDaoModel(entity: MemberEntity): Conversation.Member
    fun toDaoModel(member: Conversation.Member): MemberEntity
}

internal class MemberMapperImpl(private val idMapper: IdMapper, private val roleMapper: ConversationRoleMapper) : MemberMapper {

    override fun fromApiModel(conversationMember: ConversationMemberDTO.Other): Conversation.Member =
        Conversation.Member(conversationMember.id.toModel(), roleMapper.fromApi(conversationMember.conversationRole))

    override fun fromApiModel(conversationMembersResponse: ConversationMembersResponse): MembersInfo {
        val self = with(conversationMembersResponse.self) {
            Conversation.Member(id.toModel(), roleMapper.fromApi(conversationRole))
        }
        val others = conversationMembersResponse.otherMembers.map { member ->
            Conversation.Member(member.id.toModel(), roleMapper.fromApi(member.conversationRole))
        }
        return MembersInfo(self, others)
    }

    override fun fromApiModelToDaoModel(conversationMembersResponse: ConversationMembersResponse): List<MemberEntity> {
        val otherMembers = conversationMembersResponse.otherMembers.map { member ->
            MemberEntity(member.id.toDao(), roleMapper.fromApiModelToDaoModel(member.conversationRole))
        }
        val selfMember = with(conversationMembersResponse.self) {
            MemberEntity(id.toDao(), roleMapper.fromApiModelToDaoModel(conversationRole))
        }
        return otherMembers + selfMember
    }

    override fun toDaoModel(member: Conversation.Member): MemberEntity = with(member) {
        MemberEntity(id.toDao(), roleMapper.toDAO(role))
    }

    override fun fromMapOfClientsResponseToRecipients(qualifiedMap: Map<UserId, List<SimpleClientResponse>>): List<Recipient> =
        qualifiedMap.entries.map { entry ->
            val id = entry.key.toModel()
            val clients = entry.value.map(idMapper::fromSimpleClientResponse)
            Recipient(id, clients)
        }

    override fun fromMapOfClientsEntityToRecipients(qualifiedMap: Map<QualifiedIDEntity, List<Client>>): List<Recipient> =
        qualifiedMap.entries.map { entry ->
            val id = entry.key.toModel()
            val clients = entry.value.map(idMapper::fromClient)
            Recipient(id, clients)
        }

    override fun fromDaoModel(entity: MemberEntity): Conversation.Member = with(entity) {
        Conversation.Member(user.toModel(), roleMapper.fromDAO(role))
    }
}

interface ConversationRoleMapper {
    fun toApi(role: Conversation.Member.Role): String
    fun fromApi(roleDTO: String): Conversation.Member.Role
    fun fromDAO(roleEntity: MemberEntity.Role): Conversation.Member.Role
    fun toDAO(role: Conversation.Member.Role): MemberEntity.Role
    fun fromApiModelToDaoModel(roleDTO: String): MemberEntity.Role
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

    override fun fromDAO(roleEntity: MemberEntity.Role): Conversation.Member.Role = when (roleEntity) {
        MemberEntity.Role.Admin -> Conversation.Member.Role.Admin
        MemberEntity.Role.Member -> Conversation.Member.Role.Member
        is MemberEntity.Role.Unknown -> Conversation.Member.Role.Unknown(roleEntity.name)
    }

    override fun toDAO(role: Conversation.Member.Role): MemberEntity.Role = when (role) {
        Conversation.Member.Role.Admin -> MemberEntity.Role.Admin
        Conversation.Member.Role.Member -> MemberEntity.Role.Member
        is Conversation.Member.Role.Unknown -> MemberEntity.Role.Unknown(role.name)
    }

    override fun fromApiModelToDaoModel(roleDTO: String): MemberEntity.Role = when (roleDTO) {
        ADMIN -> MemberEntity.Role.Admin
        MEMBER -> MemberEntity.Role.Member
        else -> MemberEntity.Role.Unknown(roleDTO)
    }

    private companion object {
        const val ADMIN = "wire_admin"
        const val MEMBER = "wire_member"
    }
}
