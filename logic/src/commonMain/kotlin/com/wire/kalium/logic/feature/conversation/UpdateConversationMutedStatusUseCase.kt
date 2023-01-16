package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

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
    private val conversationRepository: ConversationRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : UpdateConversationMutedStatusUseCase {

    override suspend operator fun invoke(
        conversationId: ConversationId,
        mutedConversationStatus: MutedConversationStatus,
        mutedStatusTimestamp: Long
    ): ConversationUpdateStatusResult = withContext(dispatcher.default) {
        conversationRepository.updateMutedStatusRemotely(conversationId, mutedConversationStatus, mutedStatusTimestamp)
            .flatMap {
                conversationRepository.updateMutedStatusLocally(conversationId, mutedConversationStatus, mutedStatusTimestamp)
            }.fold({
                kaliumLogger.e("Something went wrong when updating the convId ($conversationId) to (${mutedConversationStatus.status}")
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
