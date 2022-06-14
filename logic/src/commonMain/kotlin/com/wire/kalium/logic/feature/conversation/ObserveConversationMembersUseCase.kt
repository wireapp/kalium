package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.UserTypeMapper
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class ObserveConversationMembersUseCase(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val syncManager: SyncManager,
    private val userTypeMapper: UserTypeMapper,
) {

    suspend operator fun invoke(conversationId: ConversationId): Flow<List<MemberDetails>> {
        syncManager.startSyncIfIdle()
        val selfDetailsFlow = userRepository.observeSelfUser()
        val selfUser = selfDetailsFlow.first()

        return conversationRepository.observeConversationMembers(conversationId).map { members ->
            members.map {
                if (it.id == selfUser.id) {
                    selfDetailsFlow.map(MemberDetails::Self)
                } else {
                    userRepository.getKnownUser(it.id).filterNotNull().map { otherUser ->
                        MemberDetails.Other(
                            otherUser = otherUser,
                            userType = userTypeMapper.fromOtherUserAndSelfUser(otherUser, selfUser)
                        )
                    }
                }
            }
        }.flatMapLatest { detailsFlows ->
            combine(detailsFlows) { it.toList() }
        }
    }
}
