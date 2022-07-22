package com.wire.kalium.logic.data.conversation

import com.wire.kalium.persistence.dao.ConversationEntity

interface ProtocolInfoMapper {
    fun fromEntity(protocolInfo: ConversationEntity.ProtocolInfo): Conversation.ProtocolInfo
    fun toEntity(protocolInfo: Conversation.ProtocolInfo): ConversationEntity.ProtocolInfo
}

class ProtocolInfoMapperImpl : ProtocolInfoMapper {
    override fun fromEntity(protocolInfo: ConversationEntity.ProtocolInfo) =
        when (protocolInfo) {
            is ConversationEntity.ProtocolInfo.Proteus -> Conversation.ProtocolInfo.Proteus
            is ConversationEntity.ProtocolInfo.MLS -> Conversation.ProtocolInfo.MLS(
                protocolInfo.groupId,
                Conversation.ProtocolInfo.MLS.GroupState.valueOf(protocolInfo.groupState.name),
                protocolInfo.epoch
            )
        }

    override fun toEntity(protocolInfo: Conversation.ProtocolInfo) =
        when (protocolInfo) {
            is Conversation.ProtocolInfo.Proteus -> ConversationEntity.ProtocolInfo.Proteus
            is Conversation.ProtocolInfo.MLS -> ConversationEntity.ProtocolInfo.MLS(
                protocolInfo.groupId,
                ConversationEntity.GroupState.valueOf(protocolInfo.groupState.name),
                protocolInfo.epoch
            )
        }
}


