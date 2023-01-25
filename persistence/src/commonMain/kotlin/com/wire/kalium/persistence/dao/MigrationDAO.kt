/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.ConversationsQueries
import kotlinx.datetime.Instant

interface MigrationDAO {
    suspend fun insertConversation(conversationList: List<ConversationEntity>)
}

internal class MigrationDAOImpl(
    private val conversationsQueries: ConversationsQueries
) : MigrationDAO {
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
                        if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.keyingMaterialLastUpdate
                        else Instant.fromEpochMilliseconds(MLS_DEFAULT_LAST_KEY_MATERIAL_UPDATE_MILLI),
                        if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.cipherSuite
                        else MLS_DEFAULT_CIPHER_SUITE
                    )
                }
            }
        }
    }
}
