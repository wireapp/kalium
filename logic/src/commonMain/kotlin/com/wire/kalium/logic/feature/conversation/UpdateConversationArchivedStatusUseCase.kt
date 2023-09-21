/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.DateTimeUtil

interface UpdateConversationArchivedStatusUseCase {
    /**
     * Use case that allows a conversation to mark a conversation as archived or not.
     *
     * @param conversationId the id of the conversation where status wants to be changed
     * @param isConversationArchived new archived status to be updated on the given conversation
     * @return an [ConversationUpdateStatusResult] containing Success or Failure cases
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        isConversationArchived: Boolean,
        archivedStatusTimestamp: Long = DateTimeUtil.currentInstant().toEpochMilliseconds()
    ): ArchiveStatusUpdateResult
}

internal class UpdateConversationArchivedStatusUseCaseImpl(
    private val conversationRepository: ConversationRepository
) : UpdateConversationArchivedStatusUseCase {

    override suspend operator fun invoke(
        conversationId: ConversationId,
        isConversationArchived: Boolean,
        archivedStatusTimestamp: Long
    ): ArchiveStatusUpdateResult =
        conversationRepository.updateArchivedStatusRemotely(conversationId, isConversationArchived, archivedStatusTimestamp)
            .flatMap {
                conversationRepository.updateArchivedStatusLocally(conversationId, isConversationArchived, archivedStatusTimestamp)
            }.fold({
                kaliumLogger.e("Something went wrong when updating convId (${conversationId.toLogString()}) to ($isConversationArchived")
                ArchiveStatusUpdateResult.Failure
            }, {
                ArchiveStatusUpdateResult.Success
            })

}

sealed class ArchiveStatusUpdateResult {
    object Success : ArchiveStatusUpdateResult()
    object Failure : ArchiveStatusUpdateResult()
}
