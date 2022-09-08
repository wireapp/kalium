package com.wire.kalium.logic.data.client.remote

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.data.client.OtherUserClient
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.api.user.client.MLSPublicKeyTypeDTO
import com.wire.kalium.network.api.user.client.UpdateClientRequest
import com.wire.kalium.network.api.user.pushToken.PushTokenBody

interface ClientRemoteRepository {
    suspend fun registerClient(param: RegisterClientParam): Either<NetworkFailure, Client>
    suspend fun registerMLSClient(clientId: ClientId, publicKey: String): Either<NetworkFailure, Unit>
    suspend fun deleteClient(param: DeleteClientParam): Either<NetworkFailure, Unit>
    suspend fun fetchClientInfo(clientId: ClientId): Either<NetworkFailure, Client>
    suspend fun fetchSelfUserClients(): Either<NetworkFailure, List<Client>>
    suspend fun registerToken(body: PushTokenBody): Either<NetworkFailure, Unit>
    suspend fun deregisterToken(pid: String): Either<NetworkFailure, Unit>
    suspend fun fetchOtherUserClients(userIdList: List<UserId>): Either<NetworkFailure, List<Pair<UserId, List<OtherUserClient>>>>
}

class ClientRemoteDataSource(
    private val clientApi: ClientApi,
    private val clientConfig: ClientConfig,
    private val clientMapper: ClientMapper = MapperProvider.clientMapper(clientConfig),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : ClientRemoteRepository {

    override suspend fun registerClient(param: RegisterClientParam): Either<NetworkFailure, Client> =
        wrapApiRequest { clientApi.registerClient(clientMapper.toRegisterClientRequest(param)) }
            .map { clientResponse -> clientMapper.fromClientResponse(clientResponse) }

    override suspend fun registerMLSClient(clientId: ClientId, publicKey: String): Either<NetworkFailure, Unit> =
        wrapApiRequest { clientApi.updateClient(UpdateClientRequest(mapOf(Pair(MLSPublicKeyTypeDTO.ED25519, publicKey))), clientId.value) }

    override suspend fun deleteClient(param: DeleteClientParam): Either<NetworkFailure, Unit> =
        wrapApiRequest { clientApi.deleteClient(param.password, param.clientId.value) }

    override suspend fun fetchClientInfo(clientId: ClientId): Either<NetworkFailure, Client> =
        wrapApiRequest { clientApi.fetchClientInfo(clientId.value) }
            .map { clientResponse -> clientMapper.fromClientResponse(clientResponse) }

    override suspend fun fetchSelfUserClients(): Either<NetworkFailure, List<Client>> =
        wrapApiRequest { clientApi.fetchSelfUserClient() }
            .map { clientResponseList ->
                clientResponseList.map { clientMapper.fromClientResponse(it) }
            }

    override suspend fun registerToken(body: PushTokenBody): Either<NetworkFailure, Unit> = wrapApiRequest {
        clientApi.registerToken(body)
    }

    override suspend fun deregisterToken(pid: String): Either<NetworkFailure, Unit> = wrapApiRequest {
        clientApi.deregisterToken(pid)
    }

    override suspend fun fetchOtherUserClients(userIdList: List<UserId>): Either<NetworkFailure, List<Pair<UserId, List<OtherUserClient>>>> {
        val networkUserId = userIdList.map { idMapper.toNetworkUserId(it) }
        return wrapApiRequest { clientApi.listClientsOfUsers(networkUserId) }.map { userIdTOClientListMap ->
            userIdTOClientListMap.map {  keys -> idMapper.fromApiModel(keys.key) to clientMapper.fromOtherUsersClientsDTO(keys.value)  }
        }
    }
}
