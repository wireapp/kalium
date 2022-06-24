package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ObserveMemberDetailsByIdsUseCase(
    private val userRepository: UserRepository,
    private val syncManager: SyncManager
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend operator fun invoke(userIdList: List<UserId>): Flow<List<MemberDetails>> {
        syncManager.startSyncIfIdle()
        val selfDetailsFlow = userRepository.observeSelfUser()
        val selfUser = selfDetailsFlow.first()

        return flowOf(userIdList).map { members ->
            members.map {
                if (it == selfUser.id) {
                    selfDetailsFlow.map(MemberDetails::Self)
                } else {
                    userRepository.getKnownUser(it).map {
                        it?.let { otherUser ->
                            MemberDetails.Other(
                                otherUser = otherUser
                        )}
                    }
                }
            }
        }.flatMapLatest { detailsFlows ->
            combine(detailsFlows) { it.toList().filterNotNull() }
        }
    }
}
