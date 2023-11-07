package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * End call when conversation is deleted, user is not a member anymore or user is deleted.
 */
interface EndCallOnConversationChangeUseCase {
    suspend operator fun invoke()
}

internal class EndCallOnConversationChangeUseCaseImpl(
    private val callRepository: CallRepository,
    private val conversationRepository: ConversationRepository,
    private val endCallUseCase: EndCallUseCase
) : EndCallOnConversationChangeUseCase {
    override suspend operator fun invoke() {
        val callsFlow = callRepository.establishedCallsFlow().map { calls ->
            calls.map { it.conversationId }
        }.distinctUntilChanged().cancellable()

        callsFlow.collectLatest { calls ->
            if (calls.isNotEmpty()) {
                conversationRepository.observeConversationDetailsById(calls.first()).cancellable().collect { conversationDetails ->
                    conversationDetails.fold({
                        // conversation deleted
                        endCallUseCase(calls.first())
                    }, {
                        if (it is ConversationDetails.Group) {
                            // Not a member anymore
                            if (!it.isSelfUserMember) {
                                endCallUseCase(calls.first())
                            }
                        } else if (it is ConversationDetails.OneOne) {
                            // Member blocked or deleted
                            if (it.otherUser.deleted || it.otherUser.connectionStatus == ConnectionState.BLOCKED) {
                                endCallUseCase(calls.first())
                            }
                        }
                    })
                }
            }
        }
    }
}
