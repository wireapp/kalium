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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly", "konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.backup

import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupQualifiedId
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.backup.mapper.toMessage
import com.wire.kalium.network.api.base.authenticated.backup.MessageSyncApi
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.persistence.dao.message.MessageDAO
import kotlinx.serialization.SerializationException

private fun BackupQualifiedId.toQualifiedId() = QualifiedID(
    value = id,
    domain = domain
)

/**
 * Use case for restoring messages from a remote backup service
 */
interface RestoreRemoteBackupUseCase {
    /**
     * Restores messages from the remote backup service for the current user
     * @return Either a CoreFailure or the number of messages restored
     */
    suspend operator fun invoke(): Either<CoreFailure, Int>
}

internal class RestoreRemoteBackupUseCaseImpl(
    private val selfUserId: UserId,
    private val messageSyncApi: MessageSyncApi,
    private val backupRepository: BackupRepository,
    private val messageDAO: MessageDAO,
    private val serializer: KtxSerializer = KtxSerializer
) : RestoreRemoteBackupUseCase {

    private val logger by lazy {
        kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SYNC)
    }

    override suspend fun invoke(): Either<CoreFailure, Int> {
        logger.i("Starting remote backup restoration for user: ${selfUserId.value}")

        var totalRestored = 0
        var since: Long? = null
        var hasMore = true

        while (hasMore) {
            val result = fetchAndProcessPage(since)
            result.fold(
                { failure ->
                    logger.e("Failed to fetch or process page: $failure")
                    return Either.Left(failure)
                },
                { page ->
                    totalRestored += page.restoredCount
                    since = page.nextCursor
                    hasMore = page.hasMore
                    logger.i("Processed page: ${page.restoredCount} messages restored, hasMore: $hasMore")
                }
            )
        }

        logger.i("Remote backup restoration completed: $totalRestored messages restored")
        return Either.Right(totalRestored)
    }

    private suspend fun fetchAndProcessPage(since: Long?): Either<CoreFailure, PageResult> {
        return wrapApiRequest {
            messageSyncApi.fetchMessages(
                userId = selfUserId.value,
                since = since,
                conversationId = null,
                order = "asc",
                size = DEFAULT_PAGE_SIZE
            )
        }.flatMap { response ->
            processMessages(response.results.mapNotNull { result ->
                parseBackupMessage(result.payload)
            }).fold(
                { failure -> Either.Left(failure) },
                { restoredCount ->
                    val nextCursor = response.results.lastOrNull()?.timestamp?.toLongOrNull()
                    Either.Right(PageResult(restoredCount, nextCursor, response.hasMore))
                }
            )
        }
    }

    private fun parseBackupMessage(payload: String): BackupMessage? {
        return try {
            serializer.json.decodeFromString<BackupMessage>(payload)
        } catch (e: SerializationException) {
            logger.w("Failed to parse BackupMessage from payload: ${e.message}")
            null
        } catch (e: Exception) {
            logger.w("Unexpected error parsing BackupMessage: ${e.message}")
            null
        }
    }

    private suspend fun processMessages(backupMessages: List<BackupMessage>): Either<CoreFailure, Int> {
        if (backupMessages.isEmpty()) {
            return Either.Right(0)
        }

        // Filter out messages that already exist in the database
        val newMessages = backupMessages.mapNotNull { backupMessage ->
            val messageExists = wrapStorageRequest {
                messageDAO.getMessageById(
                    id = backupMessage.id,
                    conversationId = backupMessage.conversationId.toQualifiedId().toDao()
                ) != null
            }.fold(
                { false }, // If there's an error, assume message doesn't exist
                { it }     // Return the actual boolean result
            )

            if (messageExists) {
                logger.d("Message ${backupMessage.id} already exists, skipping")
                null
            } else {
                backupMessage.toMessage(selfUserId)
            }
        }

        if (newMessages.isEmpty()) {
            logger.d("All messages in this batch already exist, skipping insertion")
            return Either.Right(0)
        }

        return backupRepository.insertMessages(newMessages).fold(
            { failure ->
                logger.e("Failed to insert messages: $failure")
                Either.Left(failure)
            },
            {
                logger.d("Successfully inserted ${newMessages.size} new messages")
                Either.Right(newMessages.size)
            }
        )
    }

    private data class PageResult(
        val restoredCount: Int,
        val nextCursor: Long?,
        val hasMore: Boolean
    )

    private companion object {
        const val DEFAULT_PAGE_SIZE = 100
    }
}
