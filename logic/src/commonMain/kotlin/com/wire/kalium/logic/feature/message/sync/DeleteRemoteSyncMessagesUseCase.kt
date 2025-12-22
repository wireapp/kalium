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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.sync.MessageSyncRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logger.KaliumLogger
import io.mockative.Mockable

/**
 * Use case for deleting messages from the remote message sync service.
 * This is used when clearing conversation content to ensure the remote
 * backup service also removes the messages.
 */
@Mockable
interface DeleteRemoteSyncMessagesUseCase {
    /**
     * Deletes all messages for a specific conversation from the remote sync service
     * @param conversationId The conversation to delete messages from
     * @param before Optional timestamp to delete messages before (epoch milliseconds)
     * @return Either containing the number of deleted messages or a failure
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        before: Long? = null
    ): Either<CoreFailure, Int>
}

internal class DeleteRemoteSyncMessagesUseCaseImpl(
    private val messageSyncRepository: MessageSyncRepository,
    private val userId: UserId,
    private val isFeatureEnabled: Boolean,
    kaliumLogger: KaliumLogger = com.wire.kalium.common.logger.kaliumLogger
) : DeleteRemoteSyncMessagesUseCase {
    private val logger = kaliumLogger.withTextTag("DeleteRemoteSyncMessagesUseCase")

    override suspend fun invoke(
        conversationId: ConversationId,
        before: Long?
    ): Either<CoreFailure, Int> {
        if (!isFeatureEnabled) {
            logger.d("Message sync feature is disabled, skipping remote deletion")
            return Either.Right(0)
        }

        logger.i("Deleting remote sync messages for conversation: ${conversationId.toLogString()}")

        return try {
            messageSyncRepository.deleteMessages(
                userId = userId.value,
                conversationId = conversationId.toString(),
                before = before
            ).fold(
                { networkFailure ->
                    logger.w("Failed to delete remote sync messages: API error $networkFailure")
                    Either.Left(networkFailure)
                },
                { response ->
                    logger.i("Successfully deleted ${response.deletedCount} messages from remote sync")
                    Either.Right(response.deletedCount)
                }
            )
        } catch (e: Exception) {
            logger.e("Exception during remote message deletion: ${e.message}", e)
            Either.Left(CoreFailure.Unknown(e))
        }
    }
}
