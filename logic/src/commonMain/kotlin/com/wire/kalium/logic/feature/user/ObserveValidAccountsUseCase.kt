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

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.common.functional.flatMapRight
import com.wire.kalium.common.functional.mapToRightOr
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

/**
 * This gets and observes the list of valid accounts, and it's associated team.
 */
@Mockable
interface ObserveValidAccountsUseCase {

    /**
     * @return a [Flow] of the list of valid accounts and their associated team.
     */
    suspend operator fun invoke(): Flow<List<Pair<SelfUser, Team?>>>
}

internal class ObserveValidAccountsUseCaseImpl internal constructor(
    private val sessionRepository: SessionRepository,
    private val userSessionScopeProvider: UserSessionScopeProvider,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ObserveValidAccountsUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun invoke(): Flow<List<Pair<SelfUser, Team?>>> =
        sessionRepository.allValidSessionsFlow()
            .flatMapRight { accountList ->
                if (accountList.isEmpty()) {
                    flowOf(listOf())
                } else {
                    val flowsOfSelfUsers = accountList.map { accountInfo ->
                        userSessionScopeProvider.getOrCreate(accountInfo.userId).let {
                            it.users.getSelfUserWithTeam()
                        }
                    }
                    combine(flowsOfSelfUsers) { it.asList() }
                }
            }
            .mapToRightOr(emptyList())
            .flowOn(ioDispatcher)
}
