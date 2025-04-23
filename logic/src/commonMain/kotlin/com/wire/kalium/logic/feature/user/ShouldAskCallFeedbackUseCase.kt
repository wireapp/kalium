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

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mockable
import kotlinx.datetime.Instant

/**
 * Use case that returns [Boolean] if user should be asked for a feedback about call quality or not.
 */
@Mockable
interface ShouldAskCallFeedbackUseCase {
    /**
     * @return [Boolean] if user should be asked for a feedback about call quality or not.
     */
    suspend operator fun invoke(): Boolean
}

@Suppress("FunctionNaming")
internal fun ShouldAskCallFeedbackUseCase(
    userConfigRepository: UserConfigRepository
) = object : ShouldAskCallFeedbackUseCase {

    override suspend fun invoke(): Boolean =
        userConfigRepository.getNextTimeForCallFeedback().map {
            it > 0L && DateTimeUtil.currentInstant() > Instant.fromEpochMilliseconds(it)
        }.getOrElse(true)

}
