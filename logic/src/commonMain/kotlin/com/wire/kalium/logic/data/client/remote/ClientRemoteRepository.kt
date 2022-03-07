package com.wire.kalium.logic.data.client.remote

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.user.client.ClientApi

interface ClientRemoteRepository {
    suspend fun registerClient(param: RegisterClientParam): Either<CoreFailure, Client>
    suspend fun deleteClient(param: DeleteClientParam): Either<NetworkFailure, Unit>
    suspend fun fetchClientInfo(clientId: ClientId): Either<NetworkFailure, Client>
    suspend fun fetchSelfUserClients(): Either<NetworkFailure, List<Client>>
}

class ClientRemoteDataSource(
    private val clientApi: ClientApi,
    private val clientMapper: ClientMapper
) : ClientRemoteRepository {

    override suspend fun registerClient(param: RegisterClientParam): Either<NetworkFailure, Client> =
        wrapApiRequest { clientApi.registerClient(clientMapper.toRegisterClientRequest(param)) }
            .fold(
                { networkFailure -> Either.Left(networkFailure) },
                { clientResponse -> Either.Right(clientMapper.fromClientResponse(clientResponse)) }
            )

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
}
