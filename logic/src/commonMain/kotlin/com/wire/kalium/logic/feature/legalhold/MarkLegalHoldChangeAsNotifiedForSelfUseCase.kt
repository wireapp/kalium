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
package com.wire.kalium.logic.feature.legalhold

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger

/**
 * Use case that marks the recent legal hold change as already notified to the user.
 */
interface MarkLegalHoldChangeAsNotifiedForSelfUseCase {
    suspend operator fun invoke(): Result

    sealed class Result {
        data object Success : Result()
        data class Failure(val failure: CoreFailure) : Result()
    }
}

internal class MarkLegalHoldChangeAsNotifiedForSelfUseCaseImpl internal constructor(
    val userConfigRepository: UserConfigRepository,
) : MarkLegalHoldChangeAsNotifiedForSelfUseCase {
    override suspend fun invoke(): MarkLegalHoldChangeAsNotifiedForSelfUseCase.Result =
        userConfigRepository.setLegalHoldChangeNotified(true).fold(
            { failure ->
                kaliumLogger.i("Legal hold change notified failure: $failure")
                MarkLegalHoldChangeAsNotifiedForSelfUseCase.Result.Failure(failure)
            },
            { MarkLegalHoldChangeAsNotifiedForSelfUseCase.Result.Success }
        )
}
