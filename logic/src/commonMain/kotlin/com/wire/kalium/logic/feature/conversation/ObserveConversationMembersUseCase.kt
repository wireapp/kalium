package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * This use case will observe and return the list of members of a given conversation.
 */
interface ObserveConversationMembersUseCase {
    /**
     * @param conversationId the id of the conversation to observe
     * @return a flow of [Result] with the list of [MemberDetails] of the conversation
     */
    suspend operator fun invoke(conversationId: ConversationId): Flow<List<MemberDetails>>
}

class ObserveConversationMembersUseCaseImpl internal constructor(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveConversationMembersUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend operator fun invoke(conversationId: ConversationId): Flow<List<MemberDetails>> = withContext(dispatcher.default) {
        conversationRepository.observeConversationMembers(conversationId).map { members ->
            members.map { member ->
                userRepository.observeUser(member.id).filterNotNull().map {
                    MemberDetails(it, member.role)
                }
            }
        }.flatMapLatest { detailsFlows ->
            combine(detailsFlows) { it.toList() }
        }
    }
}
