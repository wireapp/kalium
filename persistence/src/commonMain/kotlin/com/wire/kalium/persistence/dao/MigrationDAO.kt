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

package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.MigrationQueries
import com.wire.kalium.persistence.UnreadEventsQueries
import com.wire.kalium.persistence.content.ButtonContentQueries
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.MLS_DEFAULT_CIPHER_SUITE
import com.wire.kalium.persistence.dao.conversation.MLS_DEFAULT_EPOCH
import com.wire.kalium.persistence.dao.conversation.MLS_DEFAULT_LAST_KEY_MATERIAL_UPDATE_MILLI
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageInsertExtension
import com.wire.kalium.persistence.dao.message.MessageInsertExtensionImpl
import kotlinx.datetime.Instant

interface MigrationDAO {
    suspend fun insertConversation(conversationList: List<ConversationEntity>)

    suspend fun insertMessages(messageList: List<MessageEntity>)
}

internal class MigrationDAOImpl(
    private val migrationQueries: MigrationQueries,
    messagesQueries: MessagesQueries,
    private val unreadEventsQueries: UnreadEventsQueries,
    private val conversationsQueries: ConversationsQueries,
    buttonContentQueries: ButtonContentQueries,
    selfUserIDEntity: UserIDEntity,
) : MigrationDAO, MessageInsertExtension by MessageInsertExtensionImpl(
    messagesQueries,
    unreadEventsQueries,
    conversationsQueries,
    buttonContentQueries,
    selfUserIDEntity
) {
    override suspend fun insertConversation(conversationList: List<ConversationEntity>) {
        migrationQueries.transaction {
            conversationList.forEach {
                with(it) {
                    unreadEventsQueries.deleteUnreadEvents(lastReadDate, id)
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

    override suspend fun insertMessages(messageList: List<MessageEntity>) {
        migrationQueries.transaction {
            for (message in messageList) {
                // do not add withContext
                if (isValidAssetMessageUpdate(message)) {
                    updateAssetMessage(message)
                    continue
                } else {
                    insertMessageOrIgnore(message)
                }
            }
        }
    }

}
