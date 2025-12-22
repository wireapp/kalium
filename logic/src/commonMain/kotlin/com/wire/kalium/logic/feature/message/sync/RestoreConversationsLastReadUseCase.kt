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
package com.wire.kalium.logic.feature.message.sync

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.sync.MessageSyncRepository
import com.wire.kalium.logic.data.user.UserId
import io.mockative.Mockable
import kotlinx.datetime.Instant

/**
 * Result of restoring conversations last read status
 */
sealed class RestoreConversationsLastReadResult {
    /**
     * Successfully restored conversation last read status
     * @param conversationCount Number of conversations whose last read status was restored
     */
    data class Success(val conversationCount: Int) : RestoreConversationsLastReadResult()

    /**
     * No conversations last read data found on server
     */
    data object NoDataFound : RestoreConversationsLastReadResult()

    /**
     * Failed to restore conversations last read status
     */
    data class Failure(val error: CoreFailure) : RestoreConversationsLastReadResult()
}

/**
 * Use case for restoring conversation last read status from the backup service.
 * This is used during login to restore unread badges on conversations.
 */
@Mockable
interface RestoreConversationsLastReadUseCase {
    /**
     * Fetches and applies conversation last read data from the server
     * @return Result indicating success with count, no data found, or failure
     */
    suspend operator fun invoke(): RestoreConversationsLastReadResult
}

internal class RestoreConversationsLastReadUseCaseImpl(
    private val messageSyncRepository: MessageSyncRepository,
    private val conversationRepository: ConversationRepository,
    private val userId: UserId,
    private val isFeatureEnabled: Boolean,
    private val qualifiedIdMapper: QualifiedIdMapper,
    kaliumLogger: KaliumLogger = com.wire.kalium.common.logger.kaliumLogger
) : RestoreConversationsLastReadUseCase {

    private val logger = kaliumLogger.withTextTag("RestoreConversationsLastRead")

    override suspend fun invoke(): RestoreConversationsLastReadResult {
        // Check if feature is enabled
        if (!isFeatureEnabled) {
            logger.d("Message synchronization disabled, skipping conversation last read restore")
            return RestoreConversationsLastReadResult.NoDataFound
        }

        logger.i("Fetching conversation last read data for user: ${userId.value}")

        val result = messageSyncRepository.fetchConversationsLastRead(userId.value).flatMap { response ->
            val conversationsLastRead = response.conversationsLastRead

            if (conversationsLastRead.isEmpty()) {
                logger.i("No conversation last read data found")
                Either.Right(RestoreConversationsLastReadResult.NoDataFound)
            } else {
                logger.i("Restoring last read status for ${conversationsLastRead.size} conversations")
                applyConversationsLastRead(conversationsLastRead).map { count ->
                    logger.i("Successfully restored last read status for $count conversations")
                    RestoreConversationsLastReadResult.Success(count)
                }
            }
        }

        return when (result) {
            is Either.Left -> {
                logger.e("Failed to fetch or apply conversation last read data: ${result.value}")
                RestoreConversationsLastReadResult.Failure(result.value)
            }
            is Either.Right -> result.value
        }
    }

    private suspend fun applyConversationsLastRead(
        conversationsLastRead: Map<String, String>
    ): Either<CoreFailure, Int> {
        var successCount = 0
        var failureCount = 0

        conversationsLastRead.forEach { (conversationIdString, lastReadTimestamp) ->
            try {
                // Parse conversation ID
                val conversationId = qualifiedIdMapper.fromStringToQualifiedID(conversationIdString)

                // Parse timestamp (stored as epoch milliseconds string)
                val timestamp = Instant.fromEpochMilliseconds(lastReadTimestamp.toLong())

                // Update conversation read date
                val result = conversationRepository.updateConversationReadDate(conversationId, timestamp)

                if (result is Either.Right) {
                    successCount++
                    logger.d("Restored last read for conversation: $conversationIdString to timestamp: $lastReadTimestamp")
                } else {
                    failureCount++
                    logger.w("Failed to update last read for conversation: $conversationIdString")
                }
            } catch (e: Exception) {
                failureCount++
                logger.w("Error processing conversation $conversationIdString: ${e.message}")
            }
        }

        logger.i("Applied last read status: $successCount successful, $failureCount failed")
        return Either.Right(successCount)
    }
}
