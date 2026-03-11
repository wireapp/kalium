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

package com.wire.kalium.logic.data.message

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapFlowStorageRequest
import com.wire.kalium.common.error.wrapStorageNullableRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.fromDaoModelToType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.persistence.dao.message.MessageThreadDAO
import com.wire.kalium.persistence.dao.message.GlobalThreadSummaryEntity
import com.wire.kalium.persistence.dao.message.MessageThreadRootEntity
import com.wire.kalium.persistence.dao.message.MessageThreadSummaryEntity
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity

internal data class MessageThreadRoot(
    val conversationId: ConversationId,
    val rootMessageId: String,
    val threadId: String,
    val createdAt: Instant,
    val visibleReplyCount: Long = 0,
    val lastReplyDate: Instant? = null,
)

internal data class MessageThreadSummary(
    val conversationId: ConversationId,
    val rootMessageId: String,
    val threadId: String,
    val visibleReplyCount: Long,
)

internal data class GlobalThreadSummary(
    val conversationId: ConversationId,
    val conversationName: String?,
    val conversationType: Conversation.Type,
    val otherUserPreviewAssetId: UserAssetId?,
    val otherUserAvailabilityStatus: UserAvailabilityStatus,
    val otherUserConnectionStatus: ConnectionState?,
    val otherUserId: UserId?,
    val otherUserAccentId: Int?,
    val otherUserDeleted: Boolean,
    val rootMessageId: String,
    val threadId: String,
    val visibleReplyCount: Long,
    val createdAt: Instant,
    val lastReplyDate: Instant?,
    val rootMessage: MessagePreview,
    val rootMessageSelfDeletionDurationMillis: Long?,
)

@Mockable
internal interface MessageThreadRepository {
    suspend fun upsertThreadRoot(
        conversationId: ConversationId,
        rootMessageId: String,
        threadId: String,
        createdAt: Instant,
    ): Either<StorageFailure, Unit>

    suspend fun upsertThreadItem(
        conversationId: ConversationId,
        messageId: String,
        threadId: String,
        isRoot: Boolean,
        creationDate: Instant,
        visibility: Message.Visibility,
    ): Either<StorageFailure, Unit>

    suspend fun getThreadByRootMessage(
        conversationId: ConversationId,
        rootMessageId: String,
    ): Either<StorageFailure, MessageThreadRoot?>

    suspend fun getRootMessageIdByThreadId(
        conversationId: ConversationId,
        threadId: String,
    ): Either<StorageFailure, String?>

    suspend fun getThreadIdByMessageId(
        conversationId: ConversationId,
        messageId: String,
    ): Either<StorageFailure, String?>

    fun observeThreadSummariesForRoots(
        conversationId: ConversationId,
        rootMessageIds: List<String>,
    ): Flow<Either<StorageFailure, List<MessageThreadSummary>>>

    fun observeGlobalThreads(): Flow<Either<StorageFailure, List<GlobalThreadSummary>>>

    /**
     * If [threadId] is non-null, upserts the message as a non-root thread reply.
     * No-op when [threadId] is null.
     */
    suspend fun upsertThreadReplyIfNeeded(
        conversationId: ConversationId,
        messageId: String,
        threadId: String?,
        creationDate: Instant,
        visibility: Message.Visibility,
    )
}

internal class MessageThreadRepositoryImpl internal constructor(
    private val dao: MessageThreadDAO,
    selfUserId: UserId,
) : MessageThreadRepository {

    internal val messageMapper: MessageMapper = MapperProvider.messageMapper(selfUserId)

    override suspend fun upsertThreadRoot(
        conversationId: ConversationId,
        rootMessageId: String,
        threadId: String,
        createdAt: Instant,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        dao.upsertThreadRoot(conversationId.toDao(), rootMessageId, threadId, createdAt)
    }

    override suspend fun upsertThreadItem(
        conversationId: ConversationId,
        messageId: String,
        threadId: String,
        isRoot: Boolean,
        creationDate: Instant,
        visibility: Message.Visibility,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        dao.upsertThreadItem(
            conversationId = conversationId.toDao(),
            messageId = messageId,
            threadId = threadId,
            isRoot = isRoot,
            creationDate = creationDate,
            visibility = visibility.toEntityVisibility(),
        )
    }

    override suspend fun getThreadByRootMessage(
        conversationId: ConversationId,
        rootMessageId: String,
    ): Either<StorageFailure, MessageThreadRoot?> = wrapStorageNullableRequest {
        dao.getThreadByRootMessage(conversationId.toDao(), rootMessageId)
    }.map { it?.toModel() }

    override suspend fun getRootMessageIdByThreadId(
        conversationId: ConversationId,
        threadId: String,
    ): Either<StorageFailure, String?> = wrapStorageNullableRequest {
        dao.getRootMessageIdByThreadId(conversationId.toDao(), threadId)
    }

    override suspend fun getThreadIdByMessageId(
        conversationId: ConversationId,
        messageId: String,
    ): Either<StorageFailure, String?> = wrapStorageNullableRequest {
        dao.getThreadIdByMessageId(conversationId.toDao(), messageId)
    }

    override fun observeThreadSummariesForRoots(
        conversationId: ConversationId,
        rootMessageIds: List<String>,
    ): Flow<Either<StorageFailure, List<MessageThreadSummary>>> =
        wrapFlowStorageRequest {
            dao.observeThreadSummariesForRoots(conversationId.toDao(), rootMessageIds)
                .map { summaries -> summaries.map(MessageThreadSummaryEntity::toModel) }
        }

    override fun observeGlobalThreads(): Flow<Either<StorageFailure, List<GlobalThreadSummary>>> =
        wrapFlowStorageRequest {
            dao.observeGlobalThreads().map { summaries ->
                summaries.map(::toModel)
            }
        }

    override suspend fun upsertThreadReplyIfNeeded(
        conversationId: ConversationId,
        messageId: String,
        threadId: String?,
        creationDate: Instant,
        visibility: Message.Visibility,
    ) {
        val resolvedThreadId = threadId ?: return
        upsertThreadItem(
            conversationId = conversationId,
            messageId = messageId,
            threadId = resolvedThreadId,
            isRoot = false,
            creationDate = creationDate,
            visibility = visibility,
        ).onFailure {
            kaliumLogger.e("Failed to upsert thread item for messageId=$messageId")
        }
    }
}

private fun MessageThreadRootEntity.toModel() = MessageThreadRoot(
    conversationId = conversationId.toModel(),
    rootMessageId = rootMessageId,
    threadId = threadId,
    createdAt = createdAt,
    visibleReplyCount = visibleReplyCount,
    lastReplyDate = lastReplyDate,
)

private fun MessageThreadSummaryEntity.toModel() = MessageThreadSummary(
    conversationId = conversationId.toModel(),
    rootMessageId = rootMessageId,
    threadId = threadId,
    visibleReplyCount = visibleReplyCount,
)

private fun MessageThreadRepositoryImpl.toModel(summary: GlobalThreadSummaryEntity) = GlobalThreadSummary(
    conversationId = summary.conversationId.toModel(),
    conversationName = summary.conversationName,
    conversationType = summary.conversationType.fromDaoModelToType(summary.isChannel),
    otherUserPreviewAssetId = summary.otherUserPreviewAssetId?.toModel(),
    otherUserAvailabilityStatus = summary.otherUserAvailabilityStatus.toModel(),
    otherUserConnectionStatus = summary.otherUserConnectionStatus.toModel(),
    otherUserId = summary.otherUserId?.toModel(),
    otherUserAccentId = summary.otherUserAccentId,
    otherUserDeleted = summary.otherUserDeleted == true,
    rootMessageId = summary.rootMessageId,
    threadId = summary.threadId,
    visibleReplyCount = summary.visibleReplyCount,
    createdAt = summary.createdAt,
    lastReplyDate = summary.lastReplyDate,
    rootMessage = messageMapper.fromEntityToMessagePreview(summary.rootMessage),
    rootMessageSelfDeletionDurationMillis = summary.rootMessageExpireAfterMillis,
)

private fun UserAvailabilityStatusEntity?.toModel(): UserAvailabilityStatus = when (this) {
    UserAvailabilityStatusEntity.AVAILABLE -> UserAvailabilityStatus.AVAILABLE
    UserAvailabilityStatusEntity.BUSY -> UserAvailabilityStatus.BUSY
    UserAvailabilityStatusEntity.AWAY -> UserAvailabilityStatus.AWAY
    null, UserAvailabilityStatusEntity.NONE -> UserAvailabilityStatus.NONE
}

private fun ConnectionEntity.State?.toModel(): ConnectionState = when (this) {
    ConnectionEntity.State.PENDING -> ConnectionState.PENDING
    ConnectionEntity.State.SENT -> ConnectionState.SENT
    ConnectionEntity.State.BLOCKED -> ConnectionState.BLOCKED
    ConnectionEntity.State.IGNORED -> ConnectionState.IGNORED
    ConnectionEntity.State.CANCELLED -> ConnectionState.CANCELLED
    ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT -> ConnectionState.MISSING_LEGALHOLD_CONSENT
    ConnectionEntity.State.ACCEPTED -> ConnectionState.ACCEPTED
    null, ConnectionEntity.State.NOT_CONNECTED -> ConnectionState.NOT_CONNECTED
}
