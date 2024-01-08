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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Use case that check if self user is able to interact in conversation
 * @param conversationId the id of the conversation where user checks his interaction availability
 * @return an [IsInteractionAvailableResult] containing Success or Failure cases
 */
class ObserveConversationInteractionAvailabilityUseCase internal constructor(
    private val conversationRepository: ConversationRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {
    suspend operator fun invoke(conversationId: ConversationId): Flow<IsInteractionAvailableResult> = withContext(dispatcher.io) {
        conversationRepository.observeConversationDetailsById(conversationId).map { eitherConversation ->
            eitherConversation.fold({ failure -> IsInteractionAvailableResult.Failure(failure) }, { conversationDetails ->
                val availability = when (conversationDetails) {
                    is ConversationDetails.Connection -> InteractionAvailability.DISABLED
                    is ConversationDetails.Group -> {
                        if (conversationDetails.isSelfUserMember) InteractionAvailability.ENABLED
                        else InteractionAvailability.NOT_MEMBER
                    }

                    is ConversationDetails.OneOne -> {
                        when {
                            conversationDetails.otherUser.defederated -> InteractionAvailability.DISABLED
                            conversationDetails.otherUser.deleted -> InteractionAvailability.DELETED_USER
                            conversationDetails.otherUser.connectionStatus == ConnectionState.BLOCKED ->
                                InteractionAvailability.BLOCKED_USER

                            else -> InteractionAvailability.ENABLED
                        }
                    }

                    is ConversationDetails.Self, is ConversationDetails.Team -> InteractionAvailability.DISABLED
                }
                IsInteractionAvailableResult.Success(availability)
            })
        }
    }
}

sealed class IsInteractionAvailableResult {
    data class Success(val interactionAvailability: InteractionAvailability) : IsInteractionAvailableResult()
    data class Failure(val coreFailure: CoreFailure) : IsInteractionAvailableResult()
}

enum class InteractionAvailability {
    /**User is able to send messages and make calls */
    ENABLED,

    /**Self user is no longer conversation member */
    NOT_MEMBER,

    /**Other user is blocked by self user */
    BLOCKED_USER,

    /**Other team member or public user has been removed */
    DELETED_USER,

    /**Conversation type doesn't support messaging */
    DISABLED
}
