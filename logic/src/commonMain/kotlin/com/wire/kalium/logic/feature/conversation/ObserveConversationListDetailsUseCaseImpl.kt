package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

fun interface ObserveConversationListDetailsUseCase {
    suspend operator fun invoke(): Flow<List<ConversationDetails>>
}

internal class ObserveConversationListDetailsUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager,
    private val callRepository: CallRepository,
) : ObserveConversationListDetailsUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend operator fun invoke(): Flow<List<ConversationDetails>> {
        syncManager.startSyncIfIdle()

        val conversationsFlow = conversationRepository.observeConversationList().map { conversations ->
            conversations.map { conversation ->
                conversationRepository.observeConversationDetailsById(conversation.id)
            }
        }.flatMapLatest { flowsOfDetails ->
            combine(flowsOfDetails) { latestValues ->
                latestValues.asList()
                    .filter { it.isRight() } // keeping on list only valid conversations
                    .map { (it as Either.Right<ConversationDetails>).value }
            }
        }

        return combine(conversationsFlow, callRepository.ongoingCallsFlow()) { conversations, calls ->
            conversations.map {
                when (it) {
                    is ConversationDetails.Self,
                    is ConversationDetails.Connection,
                    is ConversationDetails.OneOne -> it

                    is ConversationDetails.Group -> it.copy(
                        hasOngoingCall = (it.conversation.id in calls.map { call -> call.conversationId })
                    )
                }
            }
        }.distinctUntilChanged()
    }
}
