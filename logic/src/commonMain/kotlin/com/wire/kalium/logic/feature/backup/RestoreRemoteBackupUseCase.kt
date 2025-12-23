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
package com.wire.kalium.logic.feature.backup

import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupQualifiedId
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.MessageContentOrderPolicy
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.MessageSyncFetchResponse
import com.wire.kalium.logic.data.sync.MessageSyncRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.backup.mapper.toMessage
import io.mockative.Mockable
import kotlinx.datetime.Instant

private fun BackupQualifiedId.toQualifiedId() = QualifiedID(
    value = id,
    domain = domain
)

/**
 * Use case for restoring messages from a remote backup service
 */
@Mockable
interface RestoreRemoteBackupUseCase {
    /**
     * Restores messages from the remote backup service for the current user
     * @return Either a CoreFailure or the number of messages restored
     */
    suspend operator fun invoke(): Either<CoreFailure, Int>
}

internal class RestoreRemoteBackupUseCaseImpl(
    private val selfUserId: UserId,
    private val messageSyncRepository: MessageSyncRepository,
    private val backupRepository: BackupRepository,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository
) : RestoreRemoteBackupUseCase {

    private val logger by lazy {
        kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SYNC)
    }

    override suspend fun invoke(): Either<CoreFailure, Int> {
        logger.i("Starting remote backup restoration for user: ${selfUserId.value}")

        var totalRestored = 0
        var paginationToken: String? = null
        var hasMore = true

        while (hasMore) {
            val result = fetchAndProcessPage(paginationToken)
            result.fold(
                { failure ->
                    logger.e("Failed to fetch or process page: $failure")
                    return Either.Left(failure)
                },
                { page ->
                    totalRestored += page.restoredCount
                    paginationToken = page.nextPaginationToken
                    hasMore = page.hasMore
                    logger.i("Processed page: ${page.restoredCount} messages restored, hasMore: $hasMore")
                }
            )
        }

        logger.i("Remote backup restoration completed: $totalRestored messages restored")
        return Either.Right(totalRestored)
    }

    private suspend fun fetchAndProcessPage(paginationToken: String?): Either<CoreFailure, PageResult> {
        return messageSyncRepository.fetchMessages(
            user = selfUserId.value,
            since = null,
            conversation = null,
            paginationToken = paginationToken,
            size = DEFAULT_PAGE_SIZE
        ).flatMap { response ->

            // Extract all messages from all conversations
            val allMessages = response.conversations.values.flatMap { conversationData ->
                conversationData.messages
            }

            // Parse and process the messages
            processMessages(allMessages.mapNotNull { result ->
                backupRepository.parseBackupMessage(result.payload)
            }).fold(
                { failure -> Either.Left(failure) },
                { restoredCount ->
                    // Update conversation last_read timestamps
                    updateConversationLastRead(response)

                    Either.Right(PageResult(
                        restoredCount = restoredCount,
                        nextPaginationToken = response.paginationToken,
                        hasMore = response.hasMore
                    ))
                }
            )


        }
    }

    private suspend fun updateConversationLastRead(response: MessageSyncFetchResponse) {
        response.conversations.forEach { (conversationIdStr, conversationData) ->
            conversationData.lastRead?.let { lastReadTimestamp ->
                val conversationId = QualifiedID(
                    value = conversationIdStr.substringBefore('@'),
                    domain = conversationIdStr.substringAfter('@')
                )

                // Convert the last read timestamp (epoch milliseconds) to Instant
                val backupLastReadInstant = Instant.fromEpochMilliseconds(lastReadTimestamp)

                // Get the current conversation to check its local last read timestamp
                conversationRepository.getConversationById(conversationId).fold(
                    { failure ->
                        logger.w("Failed to get conversation $conversationIdStr: $failure")
                    },
                    { conversation ->
                        conversationRepository.updateConversationReadDate(
                            qualifiedID = conversationId,
                            date = backupLastReadInstant
                        ).fold(
                            { failure ->
                                logger.w("Failed to update last read for conversation $conversationIdStr: $failure")
                            },
                            {
                                logger.d("Updated last read for conversation $conversationIdStr to $backupLastReadInstant")

                                // Populate unread events for messages that are newer than the last read date
                                conversationRepository.populateUnreadEventsForConversation(conversationId).fold(
                                    { failure ->
                                        logger.w("Failed to populate unread events for conversation $conversationIdStr: $failure")
                                    },
                                    {
                                        logger.d("Populated unread events for conversation $conversationIdStr")
                                    }
                                )
                            }
                        )

                    }
                )
            }
        }
    }

    private suspend fun processMessages(backupMessages: List<BackupMessage>): Either<CoreFailure, Int> {
        if (backupMessages.isEmpty()) {
            return Either.Right(0)
        }

        // Filter out messages that already exist in the database
        val newMessages = backupMessages.mapNotNull { backupMessage ->
            val messageExists = messageRepository.getMessageById(
                conversationId = backupMessage.conversationId.toQualifiedId(),
                messageUuid = backupMessage.id
            ).fold(
                { false }, // If there's an error (including not found), assume message doesn't exist
                { true }   // Message was found, so it exists
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

        return backupRepository.insertMessages(newMessages).flatMap {
            updateConversationDatesAfterRestore(newMessages)
        }.fold(
            { failure ->
                logger.e("Failed to insert messages or update conversation dates: $failure")
                Either.Left(failure)
            },
            {
                logger.d("Successfully inserted ${newMessages.size} new messages and updated conversation dates")
                Either.Right(newMessages.size)
            }
        )
    }

    private suspend fun updateConversationDatesAfterRestore(
        messages: List<com.wire.kalium.logic.data.message.Message.Standalone>
    ): Either<CoreFailure, Unit> {
        val affectedConversationIds = messages
            .map { it.conversationId.toDao() }
            .distinct()

        if (affectedConversationIds.isEmpty()) {
            return Either.Right(Unit)
        }
        return conversationRepository.updateConversationsModifiedDateFromMessages(
            conversationIds = affectedConversationIds,
            qualifyingContentTypes = MessageContentOrderPolicy.getQualifyingContentTypes()
        )
    }

    private data class PageResult(
        val restoredCount: Int,
        val nextPaginationToken: String?,
        val hasMore: Boolean
    )

    private companion object {
        const val DEFAULT_PAGE_SIZE = 100
    }
}
