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
import com.wire.kalium.logic.data.id.ConversationId
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
import com.wire.kalium.persistence.dao.message.MessageThreadDAO
import com.wire.kalium.persistence.dao.reaction.ReactionDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Suppress("TooManyFunctions")
internal interface BackupRepository {
    suspend fun getUsers(): List<OtherUser>
    suspend fun getConversations(): List<Conversation>
    suspend fun getMessages(pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagedData<Message.Standalone>>
    suspend fun getReactions(pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagedData<MessageReactions>>
    suspend fun getThreadIdForMessage(conversationId: ConversationId, messageId: String): String?
    suspend fun getThreadRoots(pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagedData<BackupThreadRootData>>
    suspend fun getThreadItems(pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagedData<BackupThreadItemData>>
    suspend fun insertUsers(users: List<OtherUser>): Either<CoreFailure, Unit>
    suspend fun insertConversations(conversations: List<Conversation>): Either<CoreFailure, Unit>
    suspend fun insertMessages(messages: List<Message.Standalone>): Either<CoreFailure, Unit>
    suspend fun insertReactions(reactions: List<MessageReactions>): Either<CoreFailure, Unit>
    suspend fun insertThreadRoots(threadRoots: List<BackupThreadRootData>): Either<CoreFailure, Unit>
    suspend fun insertThreadItems(threadItems: List<BackupThreadItemData>): Either<CoreFailure, Unit>
    suspend fun refreshThreadMetadata(threads: Set<BackupThreadReference>): Either<CoreFailure, Unit>
    suspend fun insertThreadData(threadData: List<BackupThreadData>): Either<CoreFailure, Unit>

    private companion object {
        const val DEFAULT_PAGE_SIZE = 1000
    }
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class BackupDataSource(
    private val selfUserId: UserId,
    private val userDAO: UserDAO,
    private val messageDAO: MessageDAO,
    private val messageThreadDAO: MessageThreadDAO,
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
        val totalMessages = messageDAO.countMessagesForBackup(BACKUP_MESSAGE_CONTENT_TYPES).toInt()
        val totalPages = totalMessages.toPageCount(pageSize)

        return messageDAO.getPagedMessagesFlow(
            contentTypes = BACKUP_MESSAGE_CONTENT_TYPES,
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

    override suspend fun getThreadIdForMessage(conversationId: ConversationId, messageId: String): String? =
        messageThreadDAO.getThreadIdByMessageId(
            conversationId = conversationId.toDao(),
            messageId = messageId,
        )

    override suspend fun getThreadRoots(pageSize: Int): Flow<PagedData<BackupThreadRootData>> {
        val totalRoots = messageThreadDAO.countThreadRootsForBackup(BACKUP_THREAD_CONTENT_TYPES).toInt()
        val totalPages = totalRoots.toPageCount(pageSize)

        return flow {
            var offset = 0
            while (offset < totalRoots) {
                val page = messageThreadDAO.getThreadRootsForBackup(
                    contentTypes = BACKUP_THREAD_CONTENT_TYPES,
                    limit = pageSize.toLong(),
                    offset = offset.toLong(),
                )
                if (page.isEmpty()) break
                emit(
                    PagedData(
                        data = page.map { root ->
                            BackupThreadRootData(
                                conversationId = root.conversationId.toModel(),
                                rootMessageId = root.rootMessageId,
                                threadId = root.threadId,
                                createdAt = root.createdAt,
                            )
                        },
                        totalPages = totalPages,
                    )
                )
                offset += page.size
            }
        }.buffer()
    }

    override suspend fun getThreadItems(pageSize: Int): Flow<PagedData<BackupThreadItemData>> {
        val totalItems = messageThreadDAO.countThreadItemsForBackup(BACKUP_THREAD_CONTENT_TYPES).toInt()
        val totalPages = totalItems.toPageCount(pageSize)

        return flow {
            var offset = 0
            while (offset < totalItems) {
                val page = messageThreadDAO.getThreadItemsForBackup(
                    contentTypes = BACKUP_THREAD_CONTENT_TYPES,
                    limit = pageSize.toLong(),
                    offset = offset.toLong(),
                )
                if (page.isEmpty()) break
                emit(
                    PagedData(
                        data = page.map { item ->
                            BackupThreadItemData(
                                conversationId = item.conversationId.toModel(),
                                messageId = item.messageId,
                                threadId = item.threadId,
                                isRoot = item.isRoot,
                                creationDate = item.creationDate,
                            )
                        },
                        totalPages = totalPages,
                    )
                )
                offset += page.size
            }
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

    override suspend fun insertThreadRoots(threadRoots: List<BackupThreadRootData>) = wrapStorageRequest {
        threadRoots.forEach { root ->
            val conversationId = root.conversationId.toDao()
            if (messageDAO.getMessageById(root.rootMessageId, conversationId) == null) {
                pendingMissingThreadRootCreatedAt[BackupThreadReference(root.conversationId, root.threadId)] = root.createdAt
                return@forEach
            }
            messageThreadDAO.upsertThreadRoot(
                conversationId = conversationId,
                rootMessageId = root.rootMessageId,
                threadId = root.threadId,
                createdAt = root.createdAt,
            )
        }
    }

    override suspend fun insertThreadItems(threadItems: List<BackupThreadItemData>) = wrapStorageRequest {
        threadItems.forEach { item ->
            val conversationId = item.conversationId.toDao()
            val threadReference = BackupThreadReference(item.conversationId, item.threadId)
            if (messageDAO.getMessageById(item.messageId, conversationId) == null) {
                if (item.isRoot) {
                    pendingMissingThreadRootCreatedAt[threadReference] = item.creationDate
                }
                return@forEach
            }
            if (item.isRoot) {
                pendingMissingThreadRootCreatedAt.remove(threadReference)
            } else {
                pendingMissingThreadRootCreatedAt.remove(threadReference)?.let { missingRootCreatedAt ->
                    messageThreadDAO.upsertMissingThreadRootFromBackup(
                        conversationId = conversationId,
                        replyMessageId = item.messageId,
                        threadId = item.threadId,
                        createdAt = missingRootCreatedAt,
                    )
                }
            }
            messageThreadDAO.upsertThreadItem(
                conversationId = conversationId,
                messageId = item.messageId,
                threadId = item.threadId,
                isRoot = item.isRoot,
                creationDate = item.creationDate,
                visibility = MessageEntity.Visibility.VISIBLE,
            )
        }
    }

    override suspend fun refreshThreadMetadata(threads: Set<BackupThreadReference>) = wrapStorageRequest {
        threads.forEach { thread ->
            messageThreadDAO.refreshThreadMetadata(
                conversationId = thread.conversationId.toDao(),
                threadId = thread.threadId,
            )
        }
    }

    override suspend fun insertThreadData(threadData: List<BackupThreadData>) = wrapStorageRequest {
        threadData.filter { it.isRoot }.forEach { thread ->
            messageThreadDAO.upsertThreadRoot(
                conversationId = thread.conversationId.toDao(),
                rootMessageId = thread.messageId,
                threadId = thread.threadId,
                createdAt = thread.creationDate,
            )
        }

        threadData.forEach { thread ->
            messageThreadDAO.upsertThreadItem(
                conversationId = thread.conversationId.toDao(),
                messageId = thread.messageId,
                threadId = thread.threadId,
                isRoot = thread.isRoot,
                creationDate = thread.creationDate,
                visibility = MessageEntity.Visibility.VISIBLE,
            )
        }

        threadData
            .map { BackupThreadReference(it.conversationId, it.threadId) }
            .toSet()
            .forEach { thread ->
                messageThreadDAO.refreshThreadMetadata(
                    conversationId = thread.conversationId.toDao(),
                    threadId = thread.threadId,
                )
            }
    }

    private companion object {
        val BACKUP_MESSAGE_CONTENT_TYPES = listOf(
            MessageEntity.ContentType.TEXT,
            MessageEntity.ContentType.ASSET,
            MessageEntity.ContentType.LOCATION,
            MessageEntity.ContentType.MULTIPART,
            MessageEntity.ContentType.COMPOSITE,
        )
        val BACKUP_THREAD_CONTENT_TYPES = BACKUP_MESSAGE_CONTENT_TYPES + MessageEntity.ContentType.MISSING_THREAD_ROOT
    }

    private val pendingMissingThreadRootCreatedAt = mutableMapOf<BackupThreadReference, Instant>()
}

internal data class PagedData<T>(
    val data: List<T>,
    val totalPages: Int,
)

internal data class BackupThreadData(
    val conversationId: ConversationId,
    val messageId: String,
    val threadId: String,
    val isRoot: Boolean,
    val creationDate: Instant,
)

internal data class BackupThreadRootData(
    val conversationId: ConversationId,
    val rootMessageId: String,
    val threadId: String,
    val createdAt: Instant,
)

internal data class BackupThreadItemData(
    val conversationId: ConversationId,
    val messageId: String,
    val threadId: String,
    val isRoot: Boolean,
    val creationDate: Instant,
)

internal data class BackupThreadReference(
    val conversationId: ConversationId,
    val threadId: String,
)

private fun Int.toPageCount(pageSize: Int): Int =
    if (this == 0) 1 else ((this + pageSize - 1) / pageSize).coerceAtLeast(1)
