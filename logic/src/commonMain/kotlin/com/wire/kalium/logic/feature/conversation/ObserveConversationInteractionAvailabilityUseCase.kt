package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case that check if self user is able to interact in conversation
 * @param conversationId the id of the conversation where user checks his interaction availability
 * @return an [IsInteractionAvailableResult] containing Success or Failure cases
 */
class ObserveConversationInteractionAvailabilityUseCase internal constructor(
    private val conversationRepository: ConversationRepository
) {
    suspend operator fun invoke(conversationId: ConversationId): Flow<IsInteractionAvailableResult> {
        return conversationRepository.observeConversationDetailsById(conversationId).map { eitherConversation ->
            eitherConversation.fold({ failure -> IsInteractionAvailableResult.Failure(failure) }, { conversationDetails ->
                val availability = when (conversationDetails) {
                    is ConversationDetails.Connection -> InteractionAvailability.DISABLED
                    is ConversationDetails.Group -> {
                        if (conversationDetails.isSelfUserMember) InteractionAvailability.ENABLED
                        else InteractionAvailability.NOT_MEMBER
                    }
                    is ConversationDetails.OneOne -> {
                        when {
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
