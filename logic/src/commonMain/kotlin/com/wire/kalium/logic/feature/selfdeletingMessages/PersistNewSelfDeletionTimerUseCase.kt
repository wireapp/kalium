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
package com.wire.kalium.logic.feature.selfdeletingMessages

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

/**
 * Use case to persist the new self deletion timer for a given conversation to memory.
 */
interface PersistNewSelfDeletionTimerUseCase {
    /**
     * @param conversationId the conversation id for which the self deletion timer should be updated
     */
    suspend operator fun invoke(conversationId: ConversationId, newSelfDeletionTimer: SelfDeletionTimer)
}

class PersistNewSelfDeletionTimerUseCaseImpl(
    private val conversationRepository: ConversationRepository
) : PersistNewSelfDeletionTimerUseCase {
    override suspend fun invoke(conversationId: ConversationId, newSelfDeletionTimer: SelfDeletionTimer) =
        conversationRepository.updateUserSelfDeletionTimer(conversationId, newSelfDeletionTimer).fold({
            kaliumLogger.e("Failure while persisting self deleting messages status $it")
        }, { kaliumLogger.d("Successfully updated self deleting messages status $newSelfDeletionTimer") })
}
