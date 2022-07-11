package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

// TODO: rename this use case to Observer since member is for conversations and there is no converation here
class ObserveMemberDetailsByIdsUseCase(
    private val userRepository: UserRepository,
    private val syncManager: SyncManager
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend operator fun invoke(userIdList: List<UserId>): Flow<List<User>> {
        syncManager.startSyncIfIdle()
        return flowOf(userIdList).map { members ->
            members.map { userId ->
                userRepository.observeUser(userId)
            }
        }.flatMapLatest { detailsFlows ->
            combine(detailsFlows) { it.toList().filterNotNull() }
        }
    }
}
