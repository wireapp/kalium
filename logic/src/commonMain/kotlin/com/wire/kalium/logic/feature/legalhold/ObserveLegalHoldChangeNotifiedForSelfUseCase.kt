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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Use case that observes if the legal hold change should be notified to the user or if it has been already notified.
 */
interface ObserveLegalHoldChangeNotifiedForSelfUseCase {
    suspend operator fun invoke(): Flow<Result>

    sealed class Result {
        data class ShouldNotify(val legalHoldState: LegalHoldState) : Result()
        data object AlreadyNotified : Result()
        data class Failure(val failure: CoreFailure) : Result()
    }
}

internal class ObserveLegalHoldChangeNotifiedForSelfUseCaseImpl internal constructor(
    private val selfUserId: UserId,
    val userConfigRepository: UserConfigRepository,
    val observeLegalHoldForUserUseCase: ObserveLegalHoldStateForUserUseCase
) : ObserveLegalHoldChangeNotifiedForSelfUseCase {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun invoke(): Flow<ObserveLegalHoldChangeNotifiedForSelfUseCase.Result> =
        userConfigRepository.observeLegalHoldChangeNotified()
            .flatMapLatest {
                it.fold(
                    { failure ->
                        kaliumLogger.i("Legal hold change notified failure: $failure")
                        flowOf(ObserveLegalHoldChangeNotifiedForSelfUseCase.Result.Failure(failure))
                    },
                    { isNotified ->
                        if (isNotified)
                            flowOf(ObserveLegalHoldChangeNotifiedForSelfUseCase.Result.AlreadyNotified)
                        else
                            observeLegalHoldForUserUseCase(selfUserId)
                                .map { legalHoldState ->
                                    ObserveLegalHoldChangeNotifiedForSelfUseCase.Result.ShouldNotify(legalHoldState)
                                }
                    }
                )
            }
}
