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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.message.reaction.MessageReactionWithUsers
import com.wire.kalium.logic.data.message.reaction.MessageReactions
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.di.MapperProvider.conversationMapper
import com.wire.kalium.logic.di.MapperProvider.messageMapper
import com.wire.kalium.logic.di.MapperProvider.userMapper
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.reaction.ReactionDAO
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

@Mockable
internal interface BackupRepository {
    suspend fun getUsers(): List<OtherUser>
    suspend fun getConversations(): List<Conversation>
    suspend fun getMessages(pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagedData<Message.Standalone>>
    suspend fun getReactions(pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagedData<MessageReactions>>
    suspend fun insertUsers(users: List<OtherUser>): Either<CoreFailure, Unit>
    suspend fun insertConversations(conversations: List<Conversation>): Either<CoreFailure, Unit>
    suspend fun insertMessages(messages: List<Message.Standalone>): Either<CoreFailure, Unit>
    suspend fun insertReactions(reactions: List<MessageReactions>): Either<CoreFailure, Unit>

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
    private val reactionDAO: ReactionDAO,
    private val userMapper: UserMapper = userMapper(),
    private val conversationMapper: ConversationMapper = conversationMapper(selfUserId),
    private val messageMapper: MessageMapper = messageMapper(selfUserId),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
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

    override suspend fun getMessages(pageSize: Int): Flow<PagedData<Message.Standalone>> {
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
            PagedData(
                data = page.map {
                    messageMapper.fromEntityToMessage(it)
                },
                totalPages = totalPages
            )
        }.buffer()
    }

    override suspend fun getReactions(pageSize: Int): Flow<PagedData<MessageReactions>> {

        val totalReactions = reactionDAO.countMessageReactionsBackup().toInt()
        val totalPages = (totalReactions / pageSize).coerceAtLeast(1)

        return reactionDAO.getPagedReactionsFlow(
            pageSize = pageSize
        ).map { page ->
            PagedData(
                data = page.map { item ->
                    MessageReactions(
                        messageId = item.messageId,
                        conversationId = item.conversationId.toModel(),
                        reactions = item.reactions
                            .groupBy { it.emoji }
                            .map { (emoji, users) ->
                                MessageReactionWithUsers(
                                    emoji = emoji,
                                    users = users.map { it.userId.toModel() }
                                )
                            }
                    )
                },
                totalPages = totalPages,
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

    override suspend fun insertReactions(reactions: List<MessageReactions>) = wrapStorageRequest {
        reactions.forEach { (messageId, conversationId, reactionUsers) ->
            reactionUsers.forEach { (emoji, users) ->
                users.forEach { userId ->
                    reactionDAO.insertReaction(
                        originalMessageId = messageId,
                        conversationId = conversationId.toDao(),
                        senderUserId = idMapper.fromDomainToDao(userId),
                        instant = Clock.System.now(),
                        emoji = emoji,
                    )
                }
            }
        }
    }
}

internal data class PagedData<T>(
    val data: List<T>,
    val totalPages: Int,
)
