package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.isRight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

fun interface ObserveConversationListDetailsUseCase {
    suspend operator fun invoke(): Flow<ConversationListDetails>
}

internal class ObserveConversationListDetailsUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val callRepository: CallRepository,
    private val observeIsSelfUserMember: ObserveIsSelfUserMemberUseCase
) : ObserveConversationListDetailsUseCase {

    override suspend operator fun invoke(): Flow<ConversationListDetails> {
        return combine(observeLatestConversationDetails(), callRepository.ongoingCallsFlow()) { conversations, calls ->
            conversations.map {
                val result = observeIsSelfUserMember(it.conversation.id).first()
                val isSelfUserMember = if (result is IsSelfUserMemberResult.Success) result.isMember else true
                when (it) {
                    is ConversationDetails.Self,
                    is ConversationDetails.Connection,
                    is ConversationDetails.OneOne -> it
                    is ConversationDetails.Group -> it.copy(
                        hasOngoingCall = (it.conversation.id in calls.map { call -> call.conversationId }),
                        isSelfUserMember = isSelfUserMember
                    )
                }
            }
        }.map { conversationList ->
            ConversationListDetails(
                conversationList = conversationList,
                unreadConversationsCount = conversationRepository.getUnreadConversationCount().fold({ 0 }, { it })
            )
        }.distinctUntilChanged()
    }

    private suspend fun observeLatestConversationDetails(): Flow<List<ConversationDetails>> {
        return conversationRepository.observeConversationList().map { conversations ->
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
    }

}

data class ConversationListDetails(
    val conversationList: List<ConversationDetails>,
    val unreadConversationsCount: Long,
    // TODO: Not implemented yet, therefore passing 0
    val missedCallsCount: Long = 0L,
    val mentionsCount: Long = 0L
)
