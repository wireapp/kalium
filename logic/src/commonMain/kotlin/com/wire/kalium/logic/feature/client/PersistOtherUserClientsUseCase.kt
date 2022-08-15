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

interface PersistOtherUserClientsUseCase {
    suspend operator fun invoke(userId: UserId)
}

internal class PersistOtherUserClientsUseCaseImpl(
    private val clientRemoteRepository: ClientRemoteRepository,
    private val clientRepository: ClientRepository
) : PersistOtherUserClientsUseCase {
    override suspend operator fun invoke(userId: UserId): Unit =
        clientRemoteRepository.otherUserClients(NetworkUserID(userId.value, userId.domain)).fold({
            kaliumLogger.withFeatureId(CLIENTS).e("Failure while fetching other users clients $it")
        }, {
            val clientIdList = arrayListOf<ClientId>()
            val deviceTypesList = arrayListOf<DeviceType>()
            for (item in it) {
                clientIdList.add(ClientId(item.id))
                deviceTypesList.add(item.deviceType)
            }
            clientRepository.saveNewClients(userId, clientIdList, deviceTypesList)
        })
}
