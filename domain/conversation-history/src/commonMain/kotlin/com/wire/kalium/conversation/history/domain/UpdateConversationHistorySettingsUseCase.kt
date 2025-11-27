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
package com.wire.kalium.conversation.history.domain

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.conversation.history.data.ConversationHistoryRepository
import com.wire.kalium.logic.data.conversation.ConversationHistorySettings
import com.wire.kalium.logic.data.id.QualifiedID

/**
 * Use case responsible for updating the conversation history settings for a specific conversation.
 *
 * This operation allows modifying how the history of a conversation is managed,
 * such as enabling or disabling history sharing or configuring the retention period for shared messages.
 *
 * @param conversationId The unique identifier of the conversation for which the history settings should be updated.
 * @param historySettings The desired history settings to be applied to the conversation.
 * @return Either a [CoreFailure] indicating an issue that occurred during the operation, or [Unit] upon success.
 */
public interface UpdateConversationHistorySettingsUseCase {
    public suspend operator fun invoke(
        conversationId: QualifiedID,
        historySettings: ConversationHistorySettings
    ): Either<CoreFailure, Unit>
}

public fun UpdateConversationHistorySettingsUseCase(
    conversationHistoryRepository: ConversationHistoryRepository
): UpdateConversationHistorySettingsUseCase = object : UpdateConversationHistorySettingsUseCase {
    override suspend fun invoke(
        conversationId: QualifiedID,
        historySettings: ConversationHistorySettings
    ): Either<CoreFailure, Unit> = conversationHistoryRepository.updateSettingsForConversation(
        conversationId,
        historySettings
    )
}
