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

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.flatMapRightWithEither
import com.wire.kalium.common.functional.mapRight
import com.wire.kalium.common.functional.mapToRightOr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * UseCase for observing if User was notified about conversation being subject of legal hold
 */
interface ObserveConversationUnderLegalHoldNotifiedUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Flow<Boolean>
}

internal class ObserveConversationUnderLegalHoldNotifiedUseCaseImpl internal constructor(
    private val conversationRepository: ConversationRepository
) : ObserveConversationUnderLegalHoldNotifiedUseCase {

    override suspend fun invoke(conversationId: ConversationId): Flow<Boolean> =
        conversationRepository.observeLegalHoldStatus(conversationId)
            .flatMapRightWithEither { legalHoldStatus ->
                conversationRepository.observeLegalHoldStatusChangeNotified(conversationId)
                    .mapRight { isUserNotifiedAboutStatusChange ->
                        when (legalHoldStatus) {
                            Conversation.LegalHoldStatus.ENABLED -> isUserNotifiedAboutStatusChange
                            else -> true // we only need to notify if legal hold was enabled
                        }
                    }
            }
            .mapToRightOr(true)
            .distinctUntilChanged()
}
