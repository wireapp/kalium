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
package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.UserClientRepositoryProvider
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.user.ObserveValidAccountsUseCase
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.mapToRightOr
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Observes new Clients for all the users that are logged in on device
 * returns [NewClientResult] which may be:
 * [NewClientResult.InCurrentAccount] if new Clients appears for the user that is currently used.
 * [NewClientResult.InOtherAccount] if new Clients appears for the user that is logged in on device, but not currently used.
 * [NewClientResult.Empty] if there are no new Clients for any of the logged-in Users.
 * [NewClientResult.Error] in case of error, in most cases it means that the user for which new Client appeared
 * is no longer logged it on the device.
 *
 * Note:
 * If there are new Clients for more than one logged-in User, CurrentUser has higher priority and will be returned first.
 */
interface ObserveNewClientsUseCase {
    suspend operator fun invoke(): Flow<NewClientResult>
}

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveNewClientsUseCaseImpl internal constructor(
    private val sessionRepository: SessionRepository,
    private val observeValidAccounts: ObserveValidAccountsUseCase,
    private val clientRepositoryProvider: UserClientRepositoryProvider
) : ObserveNewClientsUseCase {

    override suspend operator fun invoke(): Flow<NewClientResult> = observeValidAccounts()
        .flatMapLatest { validAccs ->
            val users = validAccs.map { it.first }
            observeAllNewClients(users)
                .map { it.filter { (_, clients) -> clients.isNotEmpty() } }
                .map { groupByUser ->
                    if (groupByUser.isEmpty()) return@map NewClientResult.Empty

                    sessionRepository.currentSession().map { currentAccInfo ->
                        if (groupByUser.containsKey(currentAccInfo.userId)) {
                            NewClientResult.InCurrentAccount(
                                groupByUser.getOrElse(currentAccInfo.userId) { listOf() },
                                currentAccInfo.userId
                            )
                        } else {
                            users.firstOrNull { selfUser -> groupByUser.containsKey(selfUser.id) }
                                ?.let { selfUser ->
                                    NewClientResult.InOtherAccount(
                                        groupByUser.getOrElse(selfUser.id) { listOf() },
                                        selfUser.id,
                                        selfUser.name,
                                        selfUser.handle
                                    )
                                } ?: NewClientResult.Empty
                        }
                    }.getOrElse(NewClientResult.Error)
                }
        }
        .map {
            when {
                it is NewClientResult.InCurrentAccount && it.newClients.isEmpty() -> NewClientResult.Empty
                it is NewClientResult.InOtherAccount && it.newClients.isEmpty() -> NewClientResult.Empty
                else -> it
            }
        }
        .distinctUntilChanged()

    private suspend fun observeNewClientsForUser(userId: UserId) = clientRepositoryProvider.provide(userId)
        .observeNewClients()
        .mapToRightOr(listOf())
        .map { it to userId }

    private suspend fun observeAllNewClients(validAccs: List<SelfUser>): Flow<Map<UserId, List<Client>>> {
        if (validAccs.isEmpty()) return flowOf(emptyMap())

        val observeNewClientsFlows = validAccs.map { selfUser -> observeNewClientsForUser(selfUser.id) }

        return combine(observeNewClientsFlows) { newClientsListWithUserId ->
            newClientsListWithUserId
                .groupBy({ (_, userId) -> userId }) { (clients, _) -> clients }
                .mapValues { (_, lists) -> lists.flatten() }
                .filter { (_, newClients) -> newClients.isNotEmpty() }
        }
    }
}

sealed class NewClientResult {
    data object Error : NewClientResult()
    data object Empty : NewClientResult()
    data class InCurrentAccount(val newClients: List<Client>, val userId: UserId) : NewClientResult()
    data class InOtherAccount(
        val newClients: List<Client>,
        val userId: UserId,
        val userName: String?,
        val userHandle: String?
    ) : NewClientResult()
}
