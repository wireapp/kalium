/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.persistence.dao.message

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.MessageThreadsQueries
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class MessageThreadRootEntity(
    val conversationId: QualifiedIDEntity,
    val rootMessageId: String,
    val threadId: String,
    val createdAt: Instant,
    val visibleReplyCount: Long,
    val lastReplyDate: Instant?,
    val isFollowing: Boolean,
)

data class MessageThreadBackupRootEntity(
    val conversationId: QualifiedIDEntity,
    val rootMessageId: String,
    val threadId: String,
    val createdAt: Instant,
    val isFollowing: Boolean,
)

data class MessageThreadBackupItemEntity(
    val conversationId: QualifiedIDEntity,
    val threadId: String,
    val messageId: String,
    val creationDate: Instant,
    val isRoot: Boolean,
)

data class MessageThreadSummaryEntity(
    val conversationId: QualifiedIDEntity,
    val rootMessageId: String,
    val threadId: String,
    val visibleReplyCount: Long,
    val lastReplyDate: Instant?,
)

data class GlobalThreadSummaryEntity(
    val conversationId: QualifiedIDEntity,
    val conversationName: String?,
    val conversationType: ConversationEntity.Type,
    val otherUserPreviewAssetId: QualifiedIDEntity?,
    val otherUserAvailabilityStatus: UserAvailabilityStatusEntity?,
    val otherUserConnectionStatus: ConnectionEntity.State?,
    val otherUserId: QualifiedIDEntity?,
    val otherUserAccentId: Int?,
    val otherUserDeleted: Boolean?,
    val isChannel: Boolean,
    val rootMessageId: String,
    val threadId: String,
    val visibleReplyCount: Long,
    val createdAt: Instant,
    val lastReplyDate: Instant?,
    val rootMessage: MessagePreviewEntity,
    val rootMessageExpireAfterMillis: Long?,
)

private data class MessageThreadItemStateEntity(
    val threadId: String,
    val isRoot: Boolean,
    val visibility: MessageEntity.Visibility,
    val creationDate: Instant,
)

interface MessageThreadDAO {
    suspend fun upsertThreadRoot(
        conversationId: QualifiedIDEntity,
        rootMessageId: String,
        threadId: String,
        createdAt: Instant,
    )

    suspend fun upsertThreadItem(
        conversationId: QualifiedIDEntity,
        messageId: String,
        threadId: String,
        isRoot: Boolean,
        creationDate: Instant,
        visibility: MessageEntity.Visibility,
    )

    suspend fun upsertMissingThreadRootFromBackup(
        conversationId: QualifiedIDEntity,
        replyMessageId: String,
        threadId: String,
        createdAt: Instant,
        isFollowing: Boolean = true,
    )

    suspend fun upsertThreadRootFromBackup(
        conversationId: QualifiedIDEntity,
        rootMessageId: String,
        threadId: String,
        createdAt: Instant,
        isFollowing: Boolean,
    )

    suspend fun getThreadByRootMessage(
        conversationId: QualifiedIDEntity,
        rootMessageId: String,
    ): MessageThreadRootEntity?

    suspend fun getRootMessageIdByThreadId(
        conversationId: QualifiedIDEntity,
        threadId: String,
    ): String?

    suspend fun getThreadFollowState(
        conversationId: QualifiedIDEntity,
        threadId: String,
    ): Boolean?

    suspend fun updateThreadFollowState(
        conversationId: QualifiedIDEntity,
        threadId: String,
        isFollowing: Boolean,
    )

    fun observeThreadFollowState(
        conversationId: QualifiedIDEntity,
        threadId: String,
    ): Flow<Boolean?>

    suspend fun getThreadIdByMessageId(
        conversationId: QualifiedIDEntity,
        messageId: String,
    ): String?

    suspend fun getThreadParticipantIds(
        conversationId: QualifiedIDEntity,
        threadId: String,
    ): List<QualifiedIDEntity>

    suspend fun moveThreadMessagesToConversation(
        sourceConversationId: QualifiedIDEntity,
        threadId: String,
        targetConversationId: QualifiedIDEntity,
    )

    suspend fun countThreadRootsForBackup(contentTypes: Collection<MessageEntity.ContentType>): Long

    suspend fun getThreadRootsForBackup(
        contentTypes: Collection<MessageEntity.ContentType>,
        limit: Long,
        offset: Long,
    ): List<MessageThreadBackupRootEntity>

    suspend fun countThreadItemsForBackup(contentTypes: Collection<MessageEntity.ContentType>): Long

    suspend fun getThreadItemsForBackup(
        contentTypes: Collection<MessageEntity.ContentType>,
        limit: Long,
        offset: Long,
    ): List<MessageThreadBackupItemEntity>

    suspend fun refreshThreadMetadata(
        conversationId: QualifiedIDEntity,
        threadId: String,
    )

    fun observeThreadSummariesForRoots(
        conversationId: QualifiedIDEntity,
        rootMessageIds: List<String>,
    ): Flow<List<MessageThreadSummaryEntity>>

    fun observeGlobalThreads(): Flow<List<GlobalThreadSummaryEntity>>

    fun observeConversationThreads(conversationId: QualifiedIDEntity): Flow<List<GlobalThreadSummaryEntity>>
}

@Suppress("TooManyFunctions")
internal class MessageThreadDAOImpl internal constructor(
    private val queries: MessageThreadsQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : MessageThreadDAO {

    override suspend fun upsertThreadRoot(
        conversationId: QualifiedIDEntity,
        rootMessageId: String,
        threadId: String,
        createdAt: Instant,
    ) {
        withContext(writeDispatcher.value) {
            queries.transaction {
                queries.upsertThreadRoot(
                    conversation_id = conversationId,
                    root_message_id = rootMessageId,
                    thread_id = threadId,
                    created_at = createdAt,
                )
                queries.refreshThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = threadId,
                )
                queries.deleteMainListItemsForThreadReplies(
                    conversation_id = conversationId,
                    thread_id = threadId,
                )
            }
        }
    }

    override suspend fun upsertThreadItem(
        conversationId: QualifiedIDEntity,
        messageId: String,
        threadId: String,
        isRoot: Boolean,
        creationDate: Instant,
        visibility: MessageEntity.Visibility,
    ) {
        withContext(writeDispatcher.value) {
            queries.transaction {
                val previous = queries.getThreadItemByMessageId(
                    conversation_id = conversationId,
                    message_id = messageId,
                    mapper = ::MessageThreadItemStateEntity
                ).executeAsOneOrNull()

                queries.upsertThreadItem(
                    conversation_id = conversationId,
                    thread_id = threadId,
                    message_id = messageId,
                    creation_date = creationDate,
                    is_root = isRoot,
                    visibility = visibility,
                )

                val threadHasRoot = queries.getRootMessageIdByThreadId(
                    conversation_id = conversationId,
                    thread_id = threadId
                ).executeAsOneOrNull() != null

                val missingRootCreatedAt = if (!isRoot && !threadHasRoot) {
                    upsertMissingThreadRootFromReply(
                        conversationId = conversationId,
                        replyMessageId = messageId,
                        threadId = threadId,
                    )
                } else {
                    null
                }

                if (isRoot || !threadHasRoot) {
                    queries.upsertMainListItem(
                        conversation_id = conversationId,
                        message_id = if (isRoot) messageId else threadId,
                        creation_date = if (isRoot) creationDate else missingRootCreatedAt ?: creationDate,
                        visibility = if (isRoot) visibility else MessageEntity.Visibility.VISIBLE,
                    )
                }

                if (!isRoot) {
                    queries.deleteMainListItem(
                        conversation_id = conversationId,
                        message_id = messageId,
                    )
                }

                updateThreadCountersAfterUpsert(
                    conversationId = conversationId,
                    previous = previous,
                    threadId = threadId,
                    isRoot = isRoot,
                    creationDate = creationDate,
                    visibility = visibility,
                )
            }
        }
    }

    override suspend fun upsertMissingThreadRootFromBackup(
        conversationId: QualifiedIDEntity,
        replyMessageId: String,
        threadId: String,
        createdAt: Instant,
        isFollowing: Boolean,
    ) {
        withContext(writeDispatcher.value) {
            queries.transaction {
                upsertMissingThreadRootFromReply(
                    conversationId = conversationId,
                    replyMessageId = replyMessageId,
                    threadId = threadId,
                    createdAt = createdAt,
                    isFollowing = isFollowing,
                )
                queries.upsertMainListItem(
                    conversation_id = conversationId,
                    message_id = threadId,
                    creation_date = createdAt,
                    visibility = MessageEntity.Visibility.VISIBLE,
                )
            }
        }
    }

    override suspend fun upsertThreadRootFromBackup(
        conversationId: QualifiedIDEntity,
        rootMessageId: String,
        threadId: String,
        createdAt: Instant,
        isFollowing: Boolean,
    ) {
        withContext(writeDispatcher.value) {
            queries.transaction {
                queries.upsertThreadRootWithFollowState(
                    conversation_id = conversationId,
                    root_message_id = rootMessageId,
                    thread_id = threadId,
                    created_at = createdAt,
                    is_following = isFollowing,
                )
                queries.refreshThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = threadId,
                )
                queries.deleteMainListItemsForThreadReplies(
                    conversation_id = conversationId,
                    thread_id = threadId,
                )
            }
        }
    }

    private suspend fun upsertMissingThreadRootFromReply(
        conversationId: QualifiedIDEntity,
        replyMessageId: String,
        threadId: String,
        createdAt: Instant = Clock.System.now(),
        isFollowing: Boolean = true,
    ): Instant {
        queries.insertMissingThreadRootMessageFromReply(
            thread_id = threadId,
            conversation_id = conversationId,
            created_at = createdAt,
            reply_message_id = replyMessageId,
        )
        queries.upsertThreadRootWithFollowState(
            conversation_id = conversationId,
            root_message_id = threadId,
            thread_id = threadId,
            created_at = createdAt,
            is_following = isFollowing,
        )
        queries.upsertThreadItem(
            conversation_id = conversationId,
            thread_id = threadId,
            message_id = threadId,
            creation_date = createdAt,
            is_root = true,
            visibility = MessageEntity.Visibility.VISIBLE,
        )
        return createdAt
    }

    @Suppress("LongMethod", "ReturnCount")
    private suspend fun updateThreadCountersAfterUpsert(
        conversationId: QualifiedIDEntity,
        previous: MessageThreadItemStateEntity?,
        threadId: String,
        isRoot: Boolean,
        creationDate: Instant,
        visibility: MessageEntity.Visibility,
    ) {
        if (isRoot) {
            if (previous?.isRoot == false && previous.visibility == MessageEntity.Visibility.VISIBLE) {
                queries.decrementThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = previous.threadId,
                )
            }
            return
        }

        if (previous == null) {
            if (visibility == MessageEntity.Visibility.VISIBLE) {
                queries.incrementThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = threadId,
                    reply_date = creationDate,
                )
            }
            return
        }

        if (previous.isRoot) {
            if (visibility == MessageEntity.Visibility.VISIBLE) {
                queries.incrementThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = threadId,
                    reply_date = creationDate,
                )
            }
            return
        }

        if (previous.threadId != threadId) {
            if (previous.visibility == MessageEntity.Visibility.VISIBLE) {
                queries.decrementThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = previous.threadId,
                )
            }
            if (visibility == MessageEntity.Visibility.VISIBLE) {
                queries.incrementThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = threadId,
                    reply_date = creationDate,
                )
            }
            return
        }

        if (previous.visibility == visibility) return

        when {
            previous.visibility == MessageEntity.Visibility.VISIBLE -> {
                queries.decrementThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = threadId,
                )
            }

            visibility == MessageEntity.Visibility.VISIBLE -> {
                queries.incrementThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = threadId,
                    reply_date = creationDate,
                )
            }
        }
    }

    override suspend fun getThreadByRootMessage(
        conversationId: QualifiedIDEntity,
        rootMessageId: String,
    ): MessageThreadRootEntity? = withContext(readDispatcher.value) {
        queries.getThreadByRootMessage(
            conversation_id = conversationId,
            root_message_id = rootMessageId,
            mapper = ::MessageThreadRootEntity
        ).executeAsOneOrNull()
    }

    override suspend fun getRootMessageIdByThreadId(conversationId: QualifiedIDEntity, threadId: String): String? =
        withContext(readDispatcher.value) {
            queries.getRootMessageIdByThreadId(
                conversation_id = conversationId,
                thread_id = threadId
            ).executeAsOneOrNull()
        }

    override suspend fun getThreadFollowState(
        conversationId: QualifiedIDEntity,
        threadId: String,
    ): Boolean? = withContext(readDispatcher.value) {
        queries.getThreadFollowState(
            conversation_id = conversationId,
            thread_id = threadId,
        ).executeAsOneOrNull()
    }

    override suspend fun updateThreadFollowState(
        conversationId: QualifiedIDEntity,
        threadId: String,
        isFollowing: Boolean,
    ) {
        withContext(writeDispatcher.value) {
            queries.updateThreadFollowState(
                conversation_id = conversationId,
                thread_id = threadId,
                is_following = isFollowing,
            )
        }
    }

    override fun observeThreadFollowState(
        conversationId: QualifiedIDEntity,
        threadId: String,
    ): Flow<Boolean?> =
        queries.getThreadFollowState(
            conversation_id = conversationId,
            thread_id = threadId,
        )
            .asFlow()
            .mapToOneOrNull()
            .flowOn(readDispatcher.value)

    override suspend fun getThreadIdByMessageId(
        conversationId: QualifiedIDEntity,
        messageId: String,
    ): String? = withContext(readDispatcher.value) {
        queries.getThreadIdByMessageId(
            conversation_id = conversationId,
            message_id = messageId
        ).executeAsOneOrNull()
    }

    override suspend fun getThreadParticipantIds(
        conversationId: QualifiedIDEntity,
        threadId: String,
    ): List<QualifiedIDEntity> = withContext(readDispatcher.value) {
        queries.selectThreadParticipantIds(
            conversation_id = conversationId,
            thread_id = threadId,
        ).executeAsList()
    }

    override suspend fun moveThreadMessagesToConversation(
        sourceConversationId: QualifiedIDEntity,
        threadId: String,
        targetConversationId: QualifiedIDEntity,
    ) {
        withContext(writeDispatcher.value) {
            queries.transaction {
                queries.moveThreadMessagesToConversation(
                    source_conversation_id = sourceConversationId,
                    thread_id = threadId,
                    target_conversation_id = targetConversationId,
                )
                queries.upsertMovedThreadMessagesIntoMainList(
                    conversation_id = targetConversationId,
                    thread_id = threadId,
                )
                queries.deleteThreadItemsByThreadId(
                    conversation_id = targetConversationId,
                    thread_id = threadId,
                )
                queries.deleteThreadRootByThreadId(
                    conversation_id = targetConversationId,
                    thread_id = threadId,
                )
            }
        }
    }

    override suspend fun countThreadRootsForBackup(contentTypes: Collection<MessageEntity.ContentType>): Long =
        withContext(readDispatcher.value) {
            queries.countThreadRootsForBackup(content_type = contentTypes).executeAsOne()
        }

    override suspend fun getThreadRootsForBackup(
        contentTypes: Collection<MessageEntity.ContentType>,
        limit: Long,
        offset: Long,
    ): List<MessageThreadBackupRootEntity> = withContext(readDispatcher.value) {
        queries.selectThreadRootsForBackup(
            content_type = contentTypes,
            limit = limit,
            offset = offset,
            mapper = ::MessageThreadBackupRootEntity,
        ).executeAsList()
    }

    override suspend fun countThreadItemsForBackup(contentTypes: Collection<MessageEntity.ContentType>): Long =
        withContext(readDispatcher.value) {
            queries.countThreadItemsForBackup(content_type = contentTypes).executeAsOne()
        }

    override suspend fun getThreadItemsForBackup(
        contentTypes: Collection<MessageEntity.ContentType>,
        limit: Long,
        offset: Long,
    ): List<MessageThreadBackupItemEntity> = withContext(readDispatcher.value) {
        queries.selectThreadItemsForBackup(
            content_type = contentTypes,
            limit = limit,
            offset = offset,
            mapper = ::MessageThreadBackupItemEntity,
        ).executeAsList()
    }

    override suspend fun refreshThreadMetadata(
        conversationId: QualifiedIDEntity,
        threadId: String,
    ) = withContext(writeDispatcher.value) {
        queries.refreshThreadVisibleReplyCount(
            conversation_id = conversationId,
            thread_id = threadId,
        )
        Unit
    }

    override fun observeThreadSummariesForRoots(
        conversationId: QualifiedIDEntity,
        rootMessageIds: List<String>,
    ): Flow<List<MessageThreadSummaryEntity>> {
        if (rootMessageIds.isEmpty()) return flowOf(emptyList())
        return queries.selectThreadSummariesByRootIds(
            conversation_id = conversationId,
            root_message_ids = rootMessageIds,
            mapper = ::MessageThreadSummaryEntity
        )
            .asFlow()
            .mapToList()
            .flowOn(readDispatcher.value)
    }

    override fun observeGlobalThreads(): Flow<List<GlobalThreadSummaryEntity>> =
        queries.selectGlobalThreads(
            mapper = ::toGlobalThreadSummaryEntity
        )
            .asFlow()
            .mapToList()
            .flowOn(readDispatcher.value)

    override fun observeConversationThreads(conversationId: QualifiedIDEntity): Flow<List<GlobalThreadSummaryEntity>> =
        queries.selectConversationThreads(
            conversation_id = conversationId,
            mapper = ::toGlobalThreadSummaryEntity
        )
            .asFlow()
            .mapToList()
            .flowOn(readDispatcher.value)

    @Suppress("LongParameterList", "UnusedParameter")
    private fun toGlobalThreadSummaryEntity(
        conversationId: QualifiedIDEntity,
        conversationName: String?,
        conversationType: ConversationEntity.Type,
        otherUserPreviewAssetId: QualifiedIDEntity?,
        otherUserAvailabilityStatus: UserAvailabilityStatusEntity?,
        otherUserConnectionStatus: ConnectionEntity.State?,
        otherUserId: QualifiedIDEntity?,
        otherUserAccentId: Int?,
        otherUserDeleted: Boolean?,
        isChannel: Boolean,
        rootMessageId: String,
        threadId: String,
        visibleReplyCount: Long,
        createdAt: Instant,
        lastReplyDate: Instant?,
        rootMessageExpireAfterMillis: Long?,
        previewId: String,
        previewConversationId: QualifiedIDEntity,
        previewContentType: MessageEntity.ContentType,
        previewDate: Instant,
        previewVisibility: MessageEntity.Visibility,
        previewSenderUserId: UserIDEntity,
        previewIsEphemeral: Boolean,
        previewSenderName: String?,
        previewSenderConnectionStatus: ConnectionEntity.State?,
        previewSenderIsDeleted: Boolean?,
        previewSelfUserId: QualifiedIDEntity?,
        previewIsSelfMessage: Boolean,
        previewMemberChangeList: String?,
        previewMemberChangeType: String?,
        previewUpdateConversationName: String?,
        previewConversationName: String?,
        previewIsMentioningSelfUser: Boolean,
        previewIsQuotingSelfUser: Boolean?,
        previewText: String?,
        previewAssetMimeType: String?,
        previewIsUnread: Boolean,
        previewShouldNotify: Long,
        previewMutedStatus: ConversationEntity.MutedStatus?,
        previewConversationType: ConversationEntity.Type?,
    ): GlobalThreadSummaryEntity = GlobalThreadSummaryEntity(
        conversationId = conversationId,
        conversationName = conversationName,
        conversationType = conversationType,
        otherUserPreviewAssetId = otherUserPreviewAssetId,
        otherUserAvailabilityStatus = otherUserAvailabilityStatus,
        otherUserConnectionStatus = otherUserConnectionStatus,
        otherUserId = otherUserId,
        otherUserAccentId = otherUserAccentId,
        otherUserDeleted = otherUserDeleted,
        isChannel = isChannel,
        rootMessageId = rootMessageId,
        threadId = threadId,
        visibleReplyCount = visibleReplyCount,
        createdAt = createdAt,
        lastReplyDate = lastReplyDate,
        rootMessage = MessageMapper.toPreviewEntity(
            id = previewId,
            conversationId = previewConversationId,
            contentType = previewContentType,
            date = previewDate,
            visibility = previewVisibility,
            senderUserId = previewSenderUserId,
            isEphemeral = previewIsEphemeral,
            senderName = previewSenderName,
            senderConnectionStatus = previewSenderConnectionStatus,
            senderIsDeleted = previewSenderIsDeleted,
            selfUserId = previewSelfUserId,
            isSelfMessage = previewIsSelfMessage,
            memberChangeList = previewMemberChangeList,
            memberChangeType = previewMemberChangeType,
            updateConversationName = previewUpdateConversationName,
            conversationName = previewConversationName,
            isMentioningSelfUser = previewIsMentioningSelfUser,
            isQuotingSelfUser = previewIsQuotingSelfUser,
            text = previewText,
            assetMimeType = previewAssetMimeType,
            isUnread = previewIsUnread,
            shouldNotify = previewShouldNotify,
            mutedStatus = previewMutedStatus,
            conversationType = previewConversationType,
        ),
        rootMessageExpireAfterMillis = rootMessageExpireAfterMillis,
    )
}
