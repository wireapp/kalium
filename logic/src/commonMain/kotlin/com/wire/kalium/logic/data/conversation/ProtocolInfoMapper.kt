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
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import io.mockative.Mockable

@Mockable
interface ProtocolInfoMapper {
    fun fromEntity(protocolInfo: ConversationEntity.ProtocolInfo): Conversation.ProtocolInfo
    fun toEntity(protocolInfo: Conversation.ProtocolInfo): ConversationEntity.ProtocolInfo
}

class ProtocolInfoMapperImpl(
    val idMapper: IdMapper = MapperProvider.idMapper()
) : ProtocolInfoMapper {
    override fun fromEntity(protocolInfo: ConversationEntity.ProtocolInfo) =
        when (protocolInfo) {
            is ConversationEntity.ProtocolInfo.Proteus -> Conversation.ProtocolInfo.Proteus
            is ConversationEntity.ProtocolInfo.MLS -> Conversation.ProtocolInfo.MLS(
                idMapper.fromGroupIDEntity(protocolInfo.groupId),
                protocolInfo.groupState.toDomain(),
                protocolInfo.epoch,
                protocolInfo.keyingMaterialLastUpdate,
                CipherSuite.fromTag(protocolInfo.cipherSuite.cipherSuiteTag)
            )
            is ConversationEntity.ProtocolInfo.Mixed -> Conversation.ProtocolInfo.Mixed(
                idMapper.fromGroupIDEntity(protocolInfo.groupId),
                protocolInfo.groupState.toDomain(),
                protocolInfo.epoch,
                protocolInfo.keyingMaterialLastUpdate,
                CipherSuite.fromTag(protocolInfo.cipherSuite.cipherSuiteTag)
            )
        }

    override fun toEntity(protocolInfo: Conversation.ProtocolInfo) =
        when (protocolInfo) {
            is Conversation.ProtocolInfo.Proteus -> ConversationEntity.ProtocolInfo.Proteus
            is Conversation.ProtocolInfo.MLS -> ConversationEntity.ProtocolInfo.MLS(
                idMapper.toGroupIDEntity(protocolInfo.groupId),
                protocolInfo.groupState.toEntity(),
                protocolInfo.epoch,
                protocolInfo.keyingMaterialLastUpdate,
                ConversationEntity.CipherSuite.fromTag(protocolInfo.cipherSuite.tag)
            )
            is Conversation.ProtocolInfo.Mixed -> ConversationEntity.ProtocolInfo.Mixed(
                idMapper.toGroupIDEntity(protocolInfo.groupId),
                protocolInfo.groupState.toEntity(),
                protocolInfo.epoch,
                protocolInfo.keyingMaterialLastUpdate,
                ConversationEntity.CipherSuite.fromTag(protocolInfo.cipherSuite.tag)
            )
        }

    private inline fun Conversation.ProtocolInfo.MLSCapable.GroupState.toEntity() = when(this) {
        Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_CREATION -> ConversationEntity.GroupState.PENDING_CREATION
        Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN -> ConversationEntity.GroupState.PENDING_JOIN
        Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_WELCOME_MESSAGE -> ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE
        Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED -> ConversationEntity.GroupState.ESTABLISHED
        Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_AFTER_RESET -> ConversationEntity.GroupState.PENDING_AFTER_RESET
    }

    private inline fun ConversationEntity.GroupState.toDomain() = when(this) {
        ConversationEntity.GroupState.PENDING_CREATION -> Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_CREATION
        ConversationEntity.GroupState.PENDING_JOIN -> Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN
        ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE -> Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_WELCOME_MESSAGE
        ConversationEntity.GroupState.ESTABLISHED -> Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED
        ConversationEntity.GroupState.PENDING_AFTER_RESET -> Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_AFTER_RESET
    }
}
