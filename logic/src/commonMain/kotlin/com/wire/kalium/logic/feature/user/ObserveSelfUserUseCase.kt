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
import kotlinx.coroutines.flow.Flow

/**
 * This use case is responsible for observing the current user.
 */
interface ObserveSelfUserUseCase {

    /**
     * @return a [Flow] of the current user [SelfUser]
     */
    suspend operator fun invoke(): Flow<SelfUser>

}

internal class ObserveSelfUserUseCaseImpl internal constructor(
    private val userRepository: UserRepository,
) : ObserveSelfUserUseCase {

    override suspend operator fun invoke(): Flow<SelfUser> = userRepository.observeSelfUser()

}
