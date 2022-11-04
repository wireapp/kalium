package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

interface ObserveConversationMembersUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Flow<List<MemberDetails>>
}

class ObserveConversationMembersUseCaseImpl internal constructor(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository
) : ObserveConversationMembersUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend operator fun invoke(conversationId: ConversationId): Flow<List<MemberDetails>> {
        return conversationRepository.observeConversationMembers(conversationId).map { members ->
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
