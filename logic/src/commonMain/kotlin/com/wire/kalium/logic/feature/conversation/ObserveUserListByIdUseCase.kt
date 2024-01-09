/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
