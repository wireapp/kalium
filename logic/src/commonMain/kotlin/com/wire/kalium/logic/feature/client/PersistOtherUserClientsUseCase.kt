package com.wire.kalium.logic.feature.client

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CLIENTS
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

/**
 * Use case to get the other users clients (devices) from remote and save it in our local db so it can be fetched later
 */
interface PersistOtherUserClientsUseCase {
    suspend operator fun invoke(userId: UserId)
}

internal class PersistOtherUserClientsUseCaseImpl(
    private val clientRemoteRepository: ClientRemoteRepository,
    private val clientRepository: ClientRepository,
    private val clientMapper: ClientMapper = MapperProvider.clientMapper()
) : PersistOtherUserClientsUseCase {
    override suspend operator fun invoke(userId: UserId): Unit =
        clientRemoteRepository.fetchOtherUserClients(listOf(userId)).fold({
            kaliumLogger.withFeatureId(CLIENTS).e("Failure while fetching other users clients $it")
        }, {
            it.forEach { (userId, clientList) ->
                clientMapper.toInsertClientParam(clientList, userId).let { insertClientParamList ->
                    clientRepository.storeUserClientListAndRemoveRedundantClients(insertClientParamList)
                }
            }
        })
}
