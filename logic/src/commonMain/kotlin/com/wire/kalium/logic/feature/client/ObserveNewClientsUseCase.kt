/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMap
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface ObserveNewClientsUseCase {
    suspend operator fun invoke(): Flow<NewClientResult>
}

class ObserveNewClientsUseCaseImpl(
    private val sessionRepository: SessionRepository,
    private val userSessionScopeProvider: UserSessionScopeProvider,
    private val newClientManager: NewClientManager
) : ObserveNewClientsUseCase {

    override suspend operator fun invoke(): Flow<NewClientResult> = newClientManager.observeNewClients()
        .flatMapLatest { (newClient, userId) ->
            sessionRepository.currentSession()
                .map { currentAccInfo ->
                    if (currentAccInfo.userId == userId) flowOf(NewClientResult.InCurrentAccount(newClient))
                    else userSessionScopeProvider.get(userId)?.let {
                        it.users
                            .getSelfUser()
                            .map { selfUser -> NewClientResult.InOtherAccount(newClient, userId, selfUser.name, selfUser.handle) }
                    } ?: flowOf()
                }.getOrElse(flowOf(NewClientResult.Error))
        }
}

sealed class NewClientResult {
    object Error : NewClientResult()
    data class InCurrentAccount(val newClient: Client) : NewClientResult()
    data class InOtherAccount(
        val newClient: Client,
        val userId: UserId,
        val userName: String?,
        val userHandler: String?
    ) : NewClientResult()
}
