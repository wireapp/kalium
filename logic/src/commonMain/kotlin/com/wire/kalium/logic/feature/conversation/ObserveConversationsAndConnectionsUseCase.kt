package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.feature.connection.ObserveConnectionListUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

fun interface ObserveConversationsAndConnectionsUseCase {
    /**
     * Convenience UseCase that allows to get conversations and connections merged and desc sorted by date
     * Using this will allow clients not handling specific sorting or merging logic
     */
    suspend operator fun invoke(): Flow<ConversationListDetails>
}

internal class ObserveConversationsAndConnectionsUseCaseImpl(
    private val observeConversationListDetailsUseCase: ObserveConversationListDetailsUseCase,
    private val observeConnectionListUseCase: ObserveConnectionListUseCase
) : ObserveConversationsAndConnectionsUseCase {
    override suspend fun invoke(): Flow<ConversationListDetails> {
        return combine(observeConversationListDetailsUseCase(), observeConnectionListUseCase()) { conversationListDetails, connections ->
            val sortedConversationWithConnections = (conversationListDetails.conversationList + connections).sortedWith(
                compareByDescending<ConversationDetails> { it.conversation.lastModifiedDate }
                    .thenBy(nullsLast()) { it.conversation.name?.lowercase() }
            )

            conversationListDetails.copy(conversationList = sortedConversationWithConnections)
        }.distinctUntilChanged()
    }
}
