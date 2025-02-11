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

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CLIENTS
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger

/**
 * Use case to get the other users clients (devices) from remote and save it in our local db so it can be fetched later
 */
interface FetchUsersClientsFromRemoteUseCase {
    suspend operator fun invoke(userIdList: List<UserId>)
}

internal class FetchUsersClientsFromRemoteUseCaseImpl(
    private val clientRemoteRepository: ClientRemoteRepository,
    private val clientRepository: ClientRepository,
    private val clientMapper: ClientMapper = MapperProvider.clientMapper()
) : FetchUsersClientsFromRemoteUseCase {
    override suspend operator fun invoke(userIdList: List<UserId>): Unit =
        clientRemoteRepository.fetchOtherUserClients(userIdList).fold({
            kaliumLogger.withFeatureId(CLIENTS).e("Failure while fetching other users clients $it")
        }, {
            it.forEach { (userId, clientList) ->
                clientMapper.toInsertClientParam(clientList, userId).let { insertClientParamList ->
                    clientRepository.storeUserClientListAndRemoveRedundantClients(insertClientParamList)
                }
            }
        })
}
