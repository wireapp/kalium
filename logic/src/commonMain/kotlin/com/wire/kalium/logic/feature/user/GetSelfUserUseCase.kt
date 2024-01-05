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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for retrieving the current user.
 * fixme: Rename to ObserveSelfUserUseCase
 */
interface GetSelfUserUseCase {

    /**
     * @return a [Flow] of the current user [SelfUser]
     */
    suspend operator fun invoke(): Flow<SelfUser>

}

internal class GetSelfUserUseCaseImpl internal constructor(
    private val userRepository: UserRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : GetSelfUserUseCase {

    override suspend operator fun invoke(): Flow<SelfUser> = withContext(dispatcher.io) {
        userRepository.observeSelfUser()
    }
}
