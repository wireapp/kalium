package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.kaliumLogger
import kotlinx.datetime.Clock

interface UpdateConversationMutedStatusUseCase {
    /**
     * Use case that allows a conversation to change its muted status to:
     * [MutedConversationStatus.ALL_MUTED], [MutedConversationStatus.ALL_ALLOWED] or [MutedConversationStatus.ONLY_MENTIONS_ALLOWED]
     *
     * @param conversationId the id of the conversation where status wants to be changed
     * @param mutedConversationStatus new status to set the given conversation
     * @return an [ConversationUpdateStatusResult] containing Success or Failure casesø
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        mutedConversationStatus: MutedConversationStatus,
        mutedStatusTimestamp: Long = Clock.System.now().toEpochMilliseconds()
    ): ConversationUpdateStatusResult
}

internal class UpdateConversationMutedStatusUseCaseImpl(
    private val conversationRepository: ConversationRepository
) : UpdateConversationMutedStatusUseCase {

    override suspend operator fun invoke(
        conversationId: ConversationId,
        mutedConversationStatus: MutedConversationStatus,
        mutedStatusTimestamp: Long
    ): ConversationUpdateStatusResult = suspending {
        conversationRepository.updateMutedStatus(conversationId, mutedConversationStatus, mutedStatusTimestamp)
            .coFold({
                kaliumLogger.e("Something went wrong when updating the convId ($conversationId) to (${mutedConversationStatus.name}")
                ConversationUpdateStatusResult.Failure
            }, {
                ConversationUpdateStatusResult.Success
            })
    }
}

sealed class ConversationUpdateStatusResult {
    object Success : ConversationUpdateStatusResult()
    object Failure : ConversationUpdateStatusResult()
}
