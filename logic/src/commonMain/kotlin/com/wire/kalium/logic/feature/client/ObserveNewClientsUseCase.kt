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
import com.wire.kalium.logic.feature.user.ObserveValidAccountsUseCase
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.mapToRightOr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Observes new Clients for all the users that are logged in on device
 * returns [NewClientResult] which may be:
 * [NewClientResult.InCurrentAccount] if new Clients appears for the user that is currently used.
 * [NewClientResult.InOtherAccount] if new Clients appears for the user that is logged in on device, but not currently used.
 * [NewClientResult.Error] in case of error, in most cases it means that the user for which new Client appeared
 * is no longer logged it on the device.
 *
 * Note:
 * If there are new Clients for more then one logged in User, CurrentUser has higher priority and will be returned first.
 */
interface ObserveNewClientsUseCase {
    suspend operator fun invoke(): Flow<NewClientResult>
}

class ObserveNewClientsUseCaseImpl internal constructor(
    private val sessionRepository: SessionRepository,
    private val observeValidAccounts: ObserveValidAccountsUseCase,
    private val userSessionScopeProvider: UserSessionScopeProvider
) : ObserveNewClientsUseCase {

    override suspend operator fun invoke(): Flow<NewClientResult> = observeValidAccounts()
        .flatMapLatest {
            val flows = it.map { (selfUser, _) -> observeNewClientsFoUser(selfUser.id) }
            combine(flows) { array ->
                array.groupBy({ (_, userId) -> userId }) { (clients, _) -> clients }
                    .mapValues { (_, lists) -> lists.flatten() }
            }

        }
        .map { groupByUser ->
            if (groupByUser.isEmpty()) return@map NewClientResult.Empty

            sessionRepository.currentSession()
                .map { currentAccInfo ->
                    if (groupByUser.containsKey(currentAccInfo.userId)) {
                        NewClientResult.InCurrentAccount(
                            groupByUser.getOrElse(currentAccInfo.userId) { listOf() },
                            currentAccInfo.userId
                        )
                    } else {
                        observeValidAccounts()
                            .firstOrNull()
                            ?.map { it.first }
                            ?.firstOrNull { selfUser -> groupByUser.containsKey(selfUser.id) }
                            ?.let { selfUser ->
                                NewClientResult.InOtherAccount(
                                    groupByUser.getOrElse(selfUser.id) { listOf() },
                                    selfUser.id,
                                    selfUser.name,
                                    selfUser.handle
                                )
                            } ?: NewClientResult.Error
                    }
                }
                .getOrElse(NewClientResult.Error)
        }
        .filter {
            // if newClients list is empty mean no NewClients - do not emit anything
            (it is NewClientResult.InCurrentAccount && it.newClients.isNotEmpty()) ||
                    (it is NewClientResult.InOtherAccount && it.newClients.isNotEmpty()) ||
                    it is NewClientResult.Error
        }

    private suspend fun observeNewClientsFoUser(userId: UserId) = userSessionScopeProvider.getOrCreate(userId)
        .clientRepository
        .observeNewClients()
        .mapToRightOr(listOf<Client>())
        .map { it to userId }

}

sealed class NewClientResult {
    object Error : NewClientResult()
    object Empty : NewClientResult()
    data class InCurrentAccount(val newClients: List<Client>, val userId: UserId) : NewClientResult()
    data class InOtherAccount(
        val newClients: List<Client>,
        val userId: UserId,
        val userName: String?,
        val userHandle: String?
    ) : NewClientResult()
}
