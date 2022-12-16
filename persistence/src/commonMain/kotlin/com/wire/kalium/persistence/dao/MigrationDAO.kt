package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.ConversationsQueries

interface MigrationDAO {
    suspend fun insertConversation(conversationList: List<ConversationEntity>)
}

internal class MigrationDAOImpl(
    private val conversationsQueries: ConversationsQueries
): MigrationDAO {
    override suspend fun insertConversation(conversationList: List<ConversationEntity>) {
        conversationsQueries.transaction {
            conversationList.forEach {
                with(it) {
                    conversationsQueries.insertMigrationOnly(
                        id,
                        name,
                        type,
                        teamId,
                        if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.groupId
                        else null,
                        if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.groupState
                        else ConversationEntity.GroupState.ESTABLISHED,
                        if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.epoch.toLong()
                        else MLS_DEFAULT_EPOCH,
                        if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) ConversationEntity.Protocol.MLS
                        else ConversationEntity.Protocol.PROTEUS,
                        mutedStatus,
                        mutedTime,
                        creatorId,
                        lastModifiedDate,
                        lastNotificationDate,
                        access,
                        accessRole,
                        lastReadDate,
                        if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.keyingMaterialLastUpdate.epochSeconds
                        else MLS_DEFAULT_LAST_KEY_MATERIAL_UPDATE,
                        if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.cipherSuite
                        else MLS_DEFAULT_CIPHER_SUITE
                    )
                }
            }
        }

    }

}
