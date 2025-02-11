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
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.DateTimeUtil

interface UpdateConversationMutedStatusUseCase {
    /**
     * Use case that allows a conversation to change its muted status to:
     * [MutedConversationStatus.AllMuted], [MutedConversationStatus.AllAllowed] or [MutedConversationStatus.OnlyMentionsAndRepliesAllowed]
     *
     * @param conversationId the id of the conversation where status wants to be changed
     * @param mutedConversationStatus new status to set the given conversation
     * @return an [ConversationUpdateStatusResult] containing Success or Failure cases
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        mutedConversationStatus: MutedConversationStatus,
        mutedStatusTimestamp: Long = DateTimeUtil.currentInstant().toEpochMilliseconds()
    ): ConversationUpdateStatusResult
}

internal class UpdateConversationMutedStatusUseCaseImpl(
    private val conversationRepository: ConversationRepository
) : UpdateConversationMutedStatusUseCase {

    override suspend operator fun invoke(
        conversationId: ConversationId,
        mutedConversationStatus: MutedConversationStatus,
        mutedStatusTimestamp: Long
    ): ConversationUpdateStatusResult =
        conversationRepository.updateMutedStatusRemotely(conversationId, mutedConversationStatus, mutedStatusTimestamp)
            .flatMap {
                conversationRepository.updateMutedStatusLocally(conversationId, mutedConversationStatus, mutedStatusTimestamp)
            }.fold({
                kaliumLogger.e("Something went wrong when updating the convId: " +
                        "(${conversationId.toLogString()}) to (${mutedConversationStatus.status}")
                ConversationUpdateStatusResult.Failure
            }, {
                ConversationUpdateStatusResult.Success
            })

}

sealed class ConversationUpdateStatusResult {
    data object Success : ConversationUpdateStatusResult()
    data object Failure : ConversationUpdateStatusResult()
}
