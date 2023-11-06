package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onlyRight
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan

/**
 * End call when conversation is deleted, user is not a member anymore or user is deleted.
 */
interface EndCallOnConversationChangeUseCase {
    suspend operator fun invoke()
}

class EndCallOnConversationChangeUseCaseImpl(
    private val callRepository: CallRepository,
    private val conversationRepository: ConversationRepository,
    private val endCallUseCase: EndCallUseCase,
    private val dialogManager: EndCallDialogManager
) : EndCallOnConversationChangeUseCase {
    override suspend operator fun invoke() {
        val callsFlow = callRepository.establishedCallsFlow().map { calls ->
            calls.map { it.conversationId }
        }.distinctUntilChanged().cancellable()

        callsFlow.flatMapLatest { calls ->
            if (calls.isEmpty()) return@flatMapLatest emptyFlow()

            val currentCall = calls.first()

            merge(
                finishCallBecauseOfMembershipChangesFlow(currentCall),
                finishCallBecauseOfVerificationDegradedFlow(currentCall)
            )
        }.collect { conversationId -> endCallUseCase(conversationId) }
    }

    private suspend fun finishCallBecauseOfMembershipChangesFlow(conversationId: ConversationId) =
        conversationRepository.observeConversationDetailsById(conversationId).cancellable()
            .map { conversationDetails ->
                conversationDetails.fold({
                    // conversation deleted
                    true
                }, {
                    // Member blocked or deleted
                    val isOtherUserBlockedOrDeleted = it is ConversationDetails.OneOne
                            && (it.otherUser.deleted || it.otherUser.connectionStatus == ConnectionState.BLOCKED)
                    // Not a member of group anymore
                    val isSelfRemovedFromGroup = it is ConversationDetails.Group && !it.isSelfUserMember

                    isOtherUserBlockedOrDeleted || isSelfRemovedFromGroup
                })
            }
            .filter { it }
            .map { conversationId }

    /**
     * @return [ConversationId] only when the conversation Proteus or MLS verification status was verified in past
     * but became not verified -> means need to finish the call
     */
    private suspend fun finishCallBecauseOfVerificationDegradedFlow(conversationId: ConversationId) =
        conversationRepository.observeConversationDetailsById(conversationId)
            .cancellable()
            .onlyRight()
            .map {
                val isProteusVerified = it.conversation.proteusVerificationStatus == Conversation.VerificationStatus.VERIFIED
                val isMLSVerified = it.conversation.mlsVerificationStatus == Conversation.VerificationStatus.VERIFIED

                isProteusVerified to isMLSVerified
            }
            .scan(ConversationVerificationStatuses()) { prevState, (isProteusVerified, isMLSVerified) ->
                ConversationVerificationStatuses(
                    isProteusVerified = isProteusVerified,
                    wasProteusVerified = prevState.isProteusVerified,
                    isMLSVerified = isMLSVerified,
                    wasMLSVerified = prevState.isMLSVerified
                )
            }
            .filter { it.shouldFinishCall() }
            .map {
                dialogManager.scheduleEndCallDialogEvent(conversationId)
                conversationId
            }

    private data class ConversationVerificationStatuses(
        val isProteusVerified: Boolean = false,
        val wasProteusVerified: Boolean = false,
        val isMLSVerified: Boolean = false,
        val wasMLSVerified: Boolean = false
    ) {
        fun shouldFinishCall(): Boolean = (!isProteusVerified && wasProteusVerified) || (!isMLSVerified && wasMLSVerified)
    }
}
