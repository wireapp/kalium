package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.GroupID
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
                GroupID(protocolInfo.groupId),
                Conversation.ProtocolInfo.MLS.GroupState.valueOf(protocolInfo.groupState.name),
                protocolInfo.epoch,
                protocolInfo.keyingMaterialLastUpdate,
                Conversation.CipherSuite.fromTag(protocolInfo.cipherSuite.cipherSuiteTag)
            )
        }

    override fun toEntity(protocolInfo: Conversation.ProtocolInfo) =
        when (protocolInfo) {
            is Conversation.ProtocolInfo.Proteus -> ConversationEntity.ProtocolInfo.Proteus
            is Conversation.ProtocolInfo.MLS -> ConversationEntity.ProtocolInfo.MLS(
                protocolInfo.groupId.value,
                ConversationEntity.GroupState.valueOf(protocolInfo.groupState.name),
                protocolInfo.epoch,
                protocolInfo.keyingMaterialLastUpdate,
                ConversationEntity.CipherSuite.fromTag(protocolInfo.cipherSuite.cipherSuiteTag)
            )
        }
}
