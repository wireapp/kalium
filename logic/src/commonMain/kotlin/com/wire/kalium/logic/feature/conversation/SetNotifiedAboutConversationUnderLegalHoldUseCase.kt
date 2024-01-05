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

/**
 * UseCase for setting legal_hold_change_notified flag to true,
 * it means that User was notified about the recent change in legal hold status.
 */
interface SetNotifiedAboutConversationUnderLegalHoldUseCase {
    suspend operator fun invoke(conversationId: ConversationId)
}

internal class SetNotifiedAboutConversationUnderLegalHoldUseCaseImpl internal constructor(
    private val conversationRepository: ConversationRepository
) : SetNotifiedAboutConversationUnderLegalHoldUseCase {
    override suspend fun invoke(conversationId: ConversationId) {
        conversationRepository.setLegalHoldStatusChangeNotified(conversationId)
    }
}
