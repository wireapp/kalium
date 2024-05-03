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

package com.wire.kalium.logic.feature.user.typingIndicator

import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * UseCase allowing to observe changes of the global configuration flag regarding User Typing Indicator feature
 */
interface ObserveTypingIndicatorEnabledUseCase {
    suspend operator fun invoke(): Flow<Boolean>
}

internal class ObserveTypingIndicatorEnabledUseCaseImpl(
    val userPropertyRepository: UserPropertyRepository,
) : ObserveTypingIndicatorEnabledUseCase {

    override suspend fun invoke(): Flow<Boolean> {
        return userPropertyRepository.observeTypingIndicatorStatus().map { result ->
            result.fold({
                true
            }, {
                it
            })
        }
    }

}
