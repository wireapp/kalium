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

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldRequestUseCase.Result.LegalHoldRequestAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Use case that allows to observe the legal hold state for the self user.
 */
interface ObserveLegalHoldStateForSelfUserUseCase {
    suspend operator fun invoke(): Flow<LegalHoldStateForSelfUser>
}

internal class ObserveLegalHoldStateForSelfUserUseCaseImpl internal constructor(
    private val selfUserId: UserId,
    private val observeLegalHoldStateForUser: ObserveLegalHoldStateForUserUseCase,
    private val observeLegalHoldRequestUseCase: ObserveLegalHoldRequestUseCase,
) : ObserveLegalHoldStateForSelfUserUseCase {
    override suspend fun invoke(): Flow<LegalHoldStateForSelfUser> =
        combine(
            observeLegalHoldStateForUser(selfUserId),
            observeLegalHoldRequestUseCase(),
        ) { legalHoldState, legalHoldRequest ->
            when {
                legalHoldState is LegalHoldState.Enabled -> LegalHoldStateForSelfUser.Enabled
                legalHoldRequest is LegalHoldRequestAvailable -> LegalHoldStateForSelfUser.PendingRequest
                else -> LegalHoldStateForSelfUser.Disabled
            }
        }
}

sealed class LegalHoldStateForSelfUser {
    data object Enabled : LegalHoldStateForSelfUser()
    data object Disabled : LegalHoldStateForSelfUser()
    data object PendingRequest : LegalHoldStateForSelfUser()
}
