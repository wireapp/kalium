package com.wire.kalium.logic.feature.client

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CLIENTS
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeviceType
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.api.UserId as NetworkUserID

/**
 * Use case to get the other users clients (devices) from remote and save it in our local db so it can be fetched later
 */
interface PersistOtherUserClientsUseCase {
    suspend operator fun invoke(userId: UserId)
}

internal class PersistOtherUserClientsUseCaseImpl(
    private val clientRemoteRepository: ClientRemoteRepository,
    private val clientRepository: ClientRepository
) : PersistOtherUserClientsUseCase {
    override suspend operator fun invoke(userId: UserId): Unit =
        clientRemoteRepository.fetchOtherUserClients(NetworkUserID(userId.value, userId.domain)).fold({
            kaliumLogger.withFeatureId(CLIENTS).e("Failure while fetching other users clients $it")
        }, { otherUserClients ->
            val clientIdList = arrayListOf<ClientId>()
            val deviceTypesList = arrayListOf<DeviceType>()
            otherUserClients.map {
                clientIdList.add(ClientId(it.id))
                deviceTypesList.add(it.deviceType)
            }
            clientRepository.saveNewClients(userId, clientIdList, deviceTypesList)
        })
}
