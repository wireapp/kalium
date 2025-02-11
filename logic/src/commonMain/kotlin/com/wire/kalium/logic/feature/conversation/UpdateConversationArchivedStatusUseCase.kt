/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.DateTimeUtil

interface UpdateConversationArchivedStatusUseCase {
    /**
     * Use case that allows a conversation to mark a conversation as archived or not.
     *
     * @param conversationId the id of the conversation where status wants to be changed
     * @param shouldArchiveConversation new archived status to be updated on the given conversation
     * @param onlyLocally controls if archived status should only be updated locally
     * @param archivedStatusTimestamp the timestamp when the archiving event occurred
     * @return an [ConversationUpdateStatusResult] containing Success or Failure cases
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        shouldArchiveConversation: Boolean,
        onlyLocally: Boolean,
        archivedStatusTimestamp: Long = DateTimeUtil.currentInstant().toEpochMilliseconds()
    ): ArchiveStatusUpdateResult
}

internal class UpdateConversationArchivedStatusUseCaseImpl(
    private val conversationRepository: ConversationRepository
) : UpdateConversationArchivedStatusUseCase {

    override suspend operator fun invoke(
        conversationId: ConversationId,
        shouldArchiveConversation: Boolean,
        onlyLocally: Boolean,
        archivedStatusTimestamp: Long
    ): ArchiveStatusUpdateResult =
        if (!onlyLocally) {
            archiveRemotely(conversationId, shouldArchiveConversation, archivedStatusTimestamp)
        } else {
            Either.Right(Unit)
        }
            .flatMap {
                conversationRepository.updateArchivedStatusLocally(conversationId, shouldArchiveConversation, archivedStatusTimestamp)
            }.fold({
                kaliumLogger.e(
                    "Something went wrong when updating locally convId (${conversationId.toLogString()}) archiving " +
                            "status to archived = ($shouldArchiveConversation)"
                )
                ArchiveStatusUpdateResult.Failure
            }, {
                kaliumLogger.d(
                    "Successfully updated locally convId (${conversationId.toLogString()}) archiving " +
                            "status to archived = ($shouldArchiveConversation)"
                )

                ArchiveStatusUpdateResult.Success
            })

    private suspend fun archiveRemotely(
        conversationId: ConversationId,
        shouldArchiveConversation: Boolean,
        archivedStatusTimestamp: Long
    ) = conversationRepository.updateArchivedStatusRemotely(conversationId, shouldArchiveConversation, archivedStatusTimestamp)
        .onFailure {
            kaliumLogger.e(
                "Something went wrong when updating remotely convId (${conversationId.toLogString()}) archiving " +
                        "status to archived = ($shouldArchiveConversation)"
            )
        }
        .onSuccess {
            kaliumLogger.d(
                "Successfully updated remotely convId (${conversationId.toLogString()}) archiving " +
                        "status to archived = ($shouldArchiveConversation)"
            )
        }
}

sealed class ArchiveStatusUpdateResult {
    data object Success : ArchiveStatusUpdateResult()
    data object Failure : ArchiveStatusUpdateResult()
}
