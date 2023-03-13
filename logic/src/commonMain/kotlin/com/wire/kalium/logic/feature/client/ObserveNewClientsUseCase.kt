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
import com.wire.kalium.logic.feature.user.GetSelfUserUseCase
import com.wire.kalium.logic.feature.user.ObserveValidAccountsUseCase
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

/**
 * Observes new Clients for all the users that are logged in on device
 * returns [NewClientResult] which may be:
 * [NewClientResult.InCurrentAccount] if new Client appears for the user that is currently used.
 * [NewClientResult.InOtherAccount] if new Client appears for the user that is logged in on device, but not currently used.
 * [NewClientResult.Error] in case of error, in most cases it means that the user for which new Client appeared
 * is no longer logged it on the device.
 */
interface ObserveNewClientsUseCase {
    suspend operator fun invoke(): Flow<NewClientResult>
}

class ObserveNewClientsUseCaseImpl internal constructor(
    private val sessionRepository: SessionRepository,
    private val observeValidAccounts: ObserveValidAccountsUseCase,
    private val newClientManager: NewClientManager
) : ObserveNewClientsUseCase {

    override suspend operator fun invoke(): Flow<NewClientResult> = newClientManager.observeNewClients()
        .map { (newClient, userId) ->
            sessionRepository.currentSession()
                .map { currentAccInfo ->
                    if (currentAccInfo.userId == userId) NewClientResult.InCurrentAccount(newClient)
                    else observeValidAccounts().firstOrNull()
                        ?.firstOrNull { (selfUser, _) -> selfUser.id == userId }
                        ?.let { (selfUser, _) ->
                            NewClientResult.InOtherAccount(newClient, userId, selfUser.name, selfUser.handle)
                        } ?: NewClientResult.Error
                }
                .getOrElse(NewClientResult.Error)
        }
}

sealed class NewClientResult {
    object Error : NewClientResult()
    data class InCurrentAccount(val newClient: Client) : NewClientResult()
    data class InOtherAccount(
        val newClient: Client,
        val userId: UserId,
        val userName: String?,
        val userHandle: String?
    ) : NewClientResult()
}
