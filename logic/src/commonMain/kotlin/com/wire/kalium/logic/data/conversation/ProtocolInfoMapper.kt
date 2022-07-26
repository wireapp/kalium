package com.wire.kalium.logic.data.conversation

import com.wire.kalium.persistence.dao.ConversationEntity

interface ProtocolInfoMapper {
    fun fromEntity(protocolInfo: ConversationEntity.ProtocolInfo): ProtocolInfo
    fun toEntity(protocolInfo: ProtocolInfo): ConversationEntity.ProtocolInfo
}

class ProtocolInfoMapperImpl : ProtocolInfoMapper {
    override fun fromEntity(protocolInfo: ConversationEntity.ProtocolInfo) =
        when (protocolInfo) {
            is ConversationEntity.ProtocolInfo.Proteus -> ProtocolInfo.Proteus
            is ConversationEntity.ProtocolInfo.MLS -> ProtocolInfo.MLS(
                protocolInfo.groupId,
                ProtocolInfo.MLS.GroupState.valueOf(protocolInfo.groupState.name)
            )
        }

    override fun toEntity(protocolInfo: ProtocolInfo) =
        when (protocolInfo) {
            is ProtocolInfo.Proteus -> ConversationEntity.ProtocolInfo.Proteus
            is ProtocolInfo.MLS -> ConversationEntity.ProtocolInfo.MLS(
                protocolInfo.groupId,
                ConversationEntity.GroupState.valueOf(protocolInfo.groupState.name)
            )
        }
}


