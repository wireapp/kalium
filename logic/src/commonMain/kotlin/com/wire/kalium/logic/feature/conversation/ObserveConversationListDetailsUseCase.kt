package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class ObserveConversationListDetailsUseCase(
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager,
    private val callRepository: CallRepository,
    private val connectionRepository: ConnectionRepository
) {

    suspend operator fun invoke(): Flow<List<ConversationDetails>> {
        syncManager.startSyncIfIdle()

        val conversationsFlow = conversationRepository.observeConversationList().map { conversations ->
            conversations.map { conversation ->
                conversationRepository.observeConversationDetailsById(conversation.id)
            }
        }.flatMapLatest { flowsOfDetails ->
            combine(flowsOfDetails) { latestValues -> latestValues.asList() }
        }

        return combine(
            conversationsFlow,
            callRepository.ongoingCallsFlow(),
            connectionRepository.observeConnectionListAsDetails()
        ) { conversations, calls, connections ->
            conversations.map {
                when (it) {
                    is ConversationDetails.Self,
                    is ConversationDetails.Connection -> it
                    is ConversationDetails.OneOne ->
                        connections.firstOrNull { conn -> it.conversation.id == conn.conversation.id } ?: it
                    is ConversationDetails.Group -> it.copy(
                        hasOngoingCall = (it.conversation.id in calls.map { call -> call.conversationId })
                    )
                }
            }
        }
    }
}
