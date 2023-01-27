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

import com.wire.kalium.persistence.MigrationQueries
import kotlinx.datetime.Instant

interface MigrationDAO {
    suspend fun insertConversation(conversationList: List<ConversationEntity>)
}

internal class MigrationDAOImpl(
    private val migrationQueries: MigrationQueries
) : MigrationDAO {
    override suspend fun insertConversation(conversationList: List<ConversationEntity>) {
        migrationQueries.transaction {
            conversationList.forEach {
                with(it) {
                    migrationQueries.insertConversation(
                        id,
                        name,
                        type,
                        teamId,
                        null,
                        ConversationEntity.GroupState.ESTABLISHED,
                        MLS_DEFAULT_EPOCH,
                        ConversationEntity.Protocol.PROTEUS,
                        mutedStatus,
                        mutedTime,
                        creatorId,
                        lastModifiedDate,
                        lastNotificationDate,
                        access,
                        accessRole,
                        lastReadDate,
                        Instant.fromEpochMilliseconds(MLS_DEFAULT_LAST_KEY_MATERIAL_UPDATE_MILLI),
                        MLS_DEFAULT_CIPHER_SUITE
                    )
                }
            }
        }
    }
}
