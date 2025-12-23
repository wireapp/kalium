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
 * along with this program. If not, see http://www.gnu.org/licenses/>.
 */
package com.wire.kalium.logic.data.sync

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.backup.MessageSyncApi
import com.wire.kalium.network.api.model.ConversationsLastReadResponseDTO
import com.wire.kalium.network.api.model.DeleteMessagesResponseDTO
import com.wire.kalium.network.api.model.MessageSyncFetchResponseDTO
import com.wire.kalium.network.api.model.MessageSyncRequestDTO
import com.wire.kalium.network.api.model.MessageSyncUpsertDTO
import com.wire.kalium.network.api.model.StateBackupUploadResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationSyncDAO
import com.wire.kalium.persistence.dao.conversation.ConversationPendingSyncEntity
import com.wire.kalium.persistence.dao.message.MessageSyncDAO
import com.wire.kalium.persistence.dao.message.MessageToSynchronizeEntity
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import okio.Sink
import okio.Source

/**
 * Domain enum for sync operation types
 */
enum class SyncOperationType {
    UPSERT,
    DELETE
}

/**
 * Domain model for a message to synchronize
 */
data class MessageToSync(
    val conversationId: ConversationId,
    val messageNonce: String,
    val timestamp: Instant,
    val operation: SyncOperationType,
    val payload: String?
)

/**
 * Domain model for a conversation pending sync
 */
data class ConversationPendingSync(
    val conversationId: ConversationId,
    val toUploadLastRead: Long // Last read timestamp (epoch millis)
)

/**
 * Domain model for a message upsert operation
 */
data class MessageSyncUpsert(
    val messageId: String,
    val timestamp: Long,
    val payload: String
)

/**
 * Domain model for a message sync request
 */
data class MessageSyncRequest(
    val userId: String,
    val upserts: Map<String, List<MessageSyncUpsert>>,
    val deletions: Map<String, List<String>>,
    val conversationsLastRead: Map<String, Long> // Map from conversation ID to last read timestamp (epoch millis)
)

/**
 * Domain model for a message sync fetch response
 */
data class MessageSyncFetchResponse(
    val hasMore: Boolean,
    val conversations: Map<String, ConversationMessages>,
    val paginationToken: String?
)

/**
 * Domain model for messages and metadata for a single conversation
 */
data class ConversationMessages(
    val lastRead: Long?, // Last read timestamp (epoch millis)
    val messages: List<MessageSyncResult>
)

/**
 * Domain model for an individual message result from fetch operation
 */
data class MessageSyncResult(
    val timestamp: Long, // Unix timestamp in milliseconds
    val messageId: String,
    val payload: String // JSON-encoded string of BackupMessage
)

/**
 * Repository for remote message synchronization operations.
 * Abstracts access to MessageSyncApi and related DAOs.
 */
@Mockable
interface MessageSyncRepository {
    /**
     * Sync messages to the remote backup service
     */
    suspend fun syncMessages(request: MessageSyncRequest): Either<NetworkFailure, Unit>

    /**
     * Fetch messages from the remote backup service
     */
    suspend fun fetchMessages(
        user: String,
        since: Long?,
        conversation: String?,
        paginationToken: String?,
        size: Int
    ): Either<NetworkFailure, MessageSyncFetchResponse>

    /**
     * Delete messages from the remote backup service
     */
    suspend fun deleteMessages(
        userId: String?,
        conversationId: String?,
        before: Long?
    ): Either<NetworkFailure, DeleteMessagesResponseDTO>

    /**
     * Upload crypto state backup
     */
    suspend fun uploadStateBackup(
        userId: String,
        backupDataSource: () -> Source,
        backupSize: Long
    ): Either<NetworkFailure, Unit>

    /**
     * Download crypto state backup
     * Returns StorageFailure.DataNotFound if no backup exists (404)
     */
    suspend fun downloadStateBackup(
        userId: String,
        tempFileSink: Sink
    ): Either<CoreFailure, Unit>

    // Message sync DAO operations
    /**
     * Get messages to sync from local database
     */
    suspend fun getMessagesToSync(limit: Int): Either<CoreFailure, List<MessageToSync>>

    /**
     * Delete synced messages from local database
     */
    suspend fun deleteSyncedMessages(
        messagesToDelete: Map<ConversationId, List<String>>
    ): Either<CoreFailure, Unit>

    /**
     * Upsert message to sync with full payload
     */
    suspend fun upsertMessageToSync(
        conversationId: ConversationId,
        messageNonce: String,
        timestamp: Instant,
        payload: String
    ): Either<CoreFailure, Unit>

    /**
     * Mark a message for deletion in the sync queue
     */
    suspend fun markMessageForDeletion(
        conversationId: ConversationId,
        messageNonce: String
    ): Either<CoreFailure, Unit>

    /**
     * Observe the count of messages pending sync
     */
    fun observePendingMessagesCount(): Flow<Long>

    // Conversation sync DAO operations
    /**
     * Get conversations with pending sync from local database
     */
    suspend fun getConversationsWithPendingSync(): Either<CoreFailure, List<ConversationPendingSync>>

    /**
     * Mark conversation last read as uploaded
     */
    suspend fun markConversationLastReadAsUploaded(conversationId: ConversationId): Either<CoreFailure, Unit>

    /**
     * Upsert conversation sync entity in local database
     */
    suspend fun upsertConversationSync(
        conversationId: ConversationId,
        lastReadTimestamp: Long // Timestamp in epoch milliseconds
    ): Either<CoreFailure, Unit>
}

internal class MessageSyncDataSource(
    private val messageSyncApi: MessageSyncApi,
    private val messageSyncDAO: MessageSyncDAO,
    private val conversationSyncDAO: ConversationSyncDAO
) : MessageSyncRepository {

    override suspend fun syncMessages(request: MessageSyncRequest): Either<NetworkFailure, Unit> {
        // Convert domain model to DTO
        val dto = MessageSyncRequestDTO(
            userId = request.userId,
            upserts = request.upserts.mapValues { (_, upserts) ->
                upserts.map { upsert ->
                    MessageSyncUpsertDTO(
                        messageId = upsert.messageId,
                        timestamp = upsert.timestamp,
                        payload = upsert.payload
                    )
                }
            },
            deletions = request.deletions,
            conversationsLastRead = request.conversationsLastRead
        )

        return wrapApiRequest {
            messageSyncApi.syncMessages(dto)
        }.map { Unit }
    }

    override suspend fun fetchMessages(
        user: String,
        since: Long?,
        conversation: String?,
        paginationToken: String?,
        size: Int
    ): Either<NetworkFailure, MessageSyncFetchResponse> {
        val result = wrapApiRequest {
            messageSyncApi.fetchMessages(user, since, conversation, paginationToken, size)
        }

        return when (result) {
            is Either.Left -> {
                // 404 means no messages found - return empty response instead of error
                if (is404NotFound(result.value)) {
                    Either.Right(MessageSyncFetchResponse(
                        hasMore = false,
                        conversations = emptyMap(),
                        paginationToken = null
                    ))
                } else {
                    Either.Left(result.value)
                }
            }
            is Either.Right -> Either.Right(result.value.toDomain())
        }
    }

    private fun MessageSyncFetchResponseDTO.toDomain() = MessageSyncFetchResponse(
        hasMore = hasMore,
        conversations = conversations.mapValues { (_, dto) ->
            ConversationMessages(
                lastRead = dto.lastRead,
                messages = dto.messages.map { messageDto ->
                    MessageSyncResult(
                        timestamp = messageDto.timestamp,
                        messageId = messageDto.messageId,
                        payload = messageDto.payload
                    )
                }
            )
        },
        paginationToken = paginationToken
    )

    override suspend fun deleteMessages(
        userId: String?,
        conversationId: String?,
        before: Long?
    ): Either<NetworkFailure, DeleteMessagesResponseDTO> {
        return wrapApiRequest {
            messageSyncApi.deleteMessages(userId, conversationId, before)
        }
    }

    override suspend fun uploadStateBackup(
        userId: String,
        backupDataSource: () -> Source,
        backupSize: Long
    ): Either<NetworkFailure, Unit> {
        return wrapApiRequest {
            messageSyncApi.uploadStateBackup(userId, backupDataSource, backupSize)
        }.map { Unit }
    }

    override suspend fun downloadStateBackup(
        userId: String,
        tempFileSink: Sink
    ): Either<CoreFailure, Unit> {
        val result = wrapApiRequest {
            messageSyncApi.downloadStateBackup(userId, tempFileSink)
        }

        return when (result) {
            is Either.Left -> {
                // 404 means no backup found - return DataNotFound instead of network error
                if (is404NotFound(result.value)) {
                    Either.Left(com.wire.kalium.common.error.StorageFailure.DataNotFound)
                } else {
                    Either.Left(result.value)
                }
            }
            is Either.Right -> Either.Right(Unit)
        }
    }

    /**
     * Helper function to detect 404 Not Found errors
     */
    private fun is404NotFound(failure: NetworkFailure): Boolean {
        return failure is NetworkFailure.ServerMiscommunication &&
                failure.kaliumException is com.wire.kalium.network.exceptions.KaliumException.InvalidRequestError &&
                (failure.kaliumException as com.wire.kalium.network.exceptions.KaliumException.InvalidRequestError).errorResponse.code == 404
    }

    override suspend fun getMessagesToSync(limit: Int): Either<CoreFailure, List<MessageToSync>> {
        return wrapStorageRequest {
            messageSyncDAO.getMessagesToSync(limit)
        }.map { entities ->
            entities.map { entity ->
                MessageToSync(
                    conversationId = entity.conversationId.toModel(),
                    messageNonce = entity.messageNonce,
                    timestamp = entity.timestamp,
                    operation = when (entity.operation) {
                        com.wire.kalium.persistence.dao.message.SyncOperationType.UPSERT -> SyncOperationType.UPSERT
                        com.wire.kalium.persistence.dao.message.SyncOperationType.DELETE -> SyncOperationType.DELETE
                    },
                    payload = entity.payload
                )
            }
        }
    }

    override suspend fun deleteSyncedMessages(
        messagesToDelete: Map<ConversationId, List<String>>
    ): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            messageSyncDAO.deleteSyncedMessages(
                messagesToDelete.mapKeys { (conversationId, _) -> conversationId.toDao() }
            )
        }
    }

    override suspend fun getConversationsWithPendingSync(): Either<CoreFailure, List<ConversationPendingSync>> {
        return wrapStorageRequest {
            conversationSyncDAO.getConversationsWithPendingSync()
        }.map { entities ->
            entities.map { entity ->
                ConversationPendingSync(
                    conversationId = entity.conversationId.toModel(),
                    toUploadLastRead = entity.toUploadLastRead
                )
            }
        }
    }

    override suspend fun markConversationLastReadAsUploaded(conversationId: ConversationId): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            conversationSyncDAO.markAsUploaded(conversationId.toDao())
        }
    }

    override suspend fun upsertConversationSync(
        conversationId: ConversationId,
        lastReadTimestamp: Long
    ): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            conversationSyncDAO.upsertConversationSync(conversationId.toDao(), lastReadTimestamp)
        }
    }

    override suspend fun upsertMessageToSync(
        conversationId: ConversationId,
        messageNonce: String,
        timestamp: Instant,
        payload: String
    ): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            messageSyncDAO.upsertMessageToSync(conversationId.toDao(), messageNonce, timestamp, payload)
        }
    }

    override suspend fun markMessageForDeletion(
        conversationId: ConversationId,
        messageNonce: String
    ): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            messageSyncDAO.markMessageForDeletion(conversationId.toDao(), messageNonce)
        }
    }

    override fun observePendingMessagesCount(): Flow<Long> {
        return messageSyncDAO.observePendingMessagesCount()
    }
}
