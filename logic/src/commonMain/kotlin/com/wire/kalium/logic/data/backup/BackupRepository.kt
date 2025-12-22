/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.backup

import com.wire.backup.data.BackupMessage
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException

@Mockable
interface BackupRepository {
    suspend fun getUsers(): List<OtherUser>
    suspend fun getConversations(): List<Conversation>
    suspend fun getMessages(pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagedMessages>
    suspend fun insertUsers(users: List<OtherUser>): Either<CoreFailure, Unit>
    suspend fun insertConversations(conversations: List<Conversation>): Either<CoreFailure, Unit>
    suspend fun insertMessages(messages: List<Message.Standalone>): Either<CoreFailure, Unit>

    /**
     * Parses a backup message from a JSON payload string
     * @param payload The JSON string to parse
     * @return BackupMessage if parsing succeeds, null if parsing fails
     */
    fun parseBackupMessage(payload: String): com.wire.backup.data.BackupMessage?

    private companion object {
        const val DEFAULT_PAGE_SIZE = 1000
    }
}

@Suppress("LongParameterList")
internal class BackupDataSource(
    private val selfUserId: UserId,
    private val userDAO: UserDAO,
    private val messageDAO: MessageDAO,
    private val conversationDAO: ConversationDAO,
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId),
    private val messageMapper: MessageMapper = MapperProvider.messageMapper(selfUserId),
) : BackupRepository {

    override suspend fun getUsers(): List<OtherUser> =
        userDAO.getAllUsersDetails()
            .map { list ->
                list.map { userMapper.fromUserDetailsEntityToOtherUser(it) }
            }.firstOrNull() ?: emptyList()

    override suspend fun getConversations(): List<Conversation> =
        conversationDAO.getAllConversations()
            .map { it.map(conversationMapper::fromDaoModel) }
            .firstOrNull() ?: emptyList()

    override suspend fun getMessages(pageSize: Int): Flow<PagedMessages> {
        val contentTypes = listOf(
            MessageEntity.ContentType.TEXT,
            MessageEntity.ContentType.ASSET,
            MessageEntity.ContentType.LOCATION,
        )

        val totalMessages = messageDAO.countMessagesForBackup(contentTypes).toInt()
        val totalPages = (totalMessages / pageSize).coerceAtLeast(1)

        return messageDAO.getPagedMessagesFlow(
            contentTypes = listOf(
                MessageEntity.ContentType.TEXT,
                MessageEntity.ContentType.ASSET,
                MessageEntity.ContentType.LOCATION,
            ),
            pageSize = pageSize,
        ).map { page ->
            PagedMessages(
                messages = page.map {
                    messageMapper.fromEntityToMessage(it)
                },
                totalPages = totalPages
            )
        }.buffer()
    }

    override suspend fun insertUsers(users: List<OtherUser>) = wrapStorageRequest {
        userDAO.insertOrIgnoreUsers(users.map { userMapper.fromOtherToUserEntity(it) })
    }

    override suspend fun insertConversations(conversations: List<Conversation>) = wrapStorageRequest {
        conversationDAO.insertOrUpdateLastModified(conversations.map { conversationMapper.fromMigrationModel(it) })
    }

    override suspend fun insertMessages(messages: List<Message.Standalone>) = wrapStorageRequest {
        messageDAO.insertOrIgnoreMessages(
            messages = messages.map { messageMapper.fromMessageToEntity(it) },
            withUnreadEvents = false,
            checkAssetUpdate = false,
        )
    }

    override fun parseBackupMessage(payload: String): BackupMessage? {
        return try {
            KtxSerializer.json.decodeFromString<BackupMessage>(payload)
        } catch (e: SerializationException) {
            logger.w("Failed to parse BackupMessage from payload: ${e.message}")
            null
        } catch (e: Exception) {
            logger.w("Unexpected error parsing BackupMessage: ${e.message}")
            null
        }
    }

    private companion object {
        private val logger = kaliumLogger.withTextTag("BackupRepository")
    }
}

data class PagedMessages(
    val messages: List<Message.Standalone>,
    val totalPages: Int,
)
