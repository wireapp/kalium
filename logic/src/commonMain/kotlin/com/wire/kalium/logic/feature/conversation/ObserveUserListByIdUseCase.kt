package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * This use case will observe and return the list of users objects, contacts and connections.
 * Using a list of user ids to obverse.
 *
 * @see User
 */
class ObserveUserListByIdUseCase internal constructor(
    private val userRepository: UserRepository,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend operator fun invoke(userIdList: List<UserId>): Flow<List<User>> {
        return flowOf(userIdList).map { members ->
            members.map { userId ->
                userRepository.observeUser(userId)
            }
        }.flatMapLatest { detailsFlows ->
            combine(detailsFlows) { it.toList().filterNotNull() }
        }
    }
}
