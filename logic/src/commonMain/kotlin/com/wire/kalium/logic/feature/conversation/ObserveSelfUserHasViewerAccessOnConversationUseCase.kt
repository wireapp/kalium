/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Use case to observe whether the self user has viewer access to a drive conversation.
 *
 * @param conversationId the id of the conversation to check viewer access for.
 * @return a [Flow] of [Boolean] — `true` if the self user has viewer access, `false` otherwise.
 */
public class ObserveSelfUserHasViewerAccessOnConversationUseCase internal constructor(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
) {

    /**
     * @param conversationId the id of the conversation to check viewer access for.
     * @return a [Flow] of [Boolean] — `true` if the self user has viewer access, `false` otherwise.
     */
    public suspend operator fun invoke(conversationId: ConversationId): Flow<Boolean> =
        withContext(dispatcher.io) {
            val conversationDetailsFlow = conversationRepository
                .observeConversationDetailsById(conversationId)
                .map { it.getOrElse(null) }

            val selfUserFlow = userRepository.observeSelfUser()

            combine(selfUserFlow, conversationDetailsFlow) { selfUser, conversationDetails ->
                if (conversationDetails == null) return@combine true
                val isCellsConversation = (conversationDetails as? ConversationDetails.Group)?.wireCell != null
                val isConversationOwnedBySelfTeam = conversationDetails.conversation.teamId == selfUser.teamId
                !(isCellsConversation && !isConversationOwnedBySelfTeam)
            }
        }
}

